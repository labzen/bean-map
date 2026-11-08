/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.core;

import cn.labzen.beanmap.accessor.*;
import cn.labzen.beanmap.exception.*;
import cn.labzen.beanmap.spi.*;

import cn.labzen.beanmap.annotation.BeanUnmapped;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 读方向工具：把源对象（通常是领域对象）的属性，按同名直读或显式映射，装配进目标对象（响应 DTO / Chain 视图）。
 *
 * <p>
 * 与 {@link BeanMapper}（写方向，按 {@link SourceAccessor#isProvided(String)} 做三态门控）互补：本类的值来自任意源对象、无条件拷贝，用于「domain → 响应 DTO
 * / Chain」这类读场景。装配能力（重命名 / 类型转换）由 {@link #map(String, Function, BiConsumer)} 与
 * {@link #map(String, Function, BiConsumer, Function)} 承载， 同名直读由 {@link #mapAll()} 承载，二者可并存。
 *
 * <pre>{@code
 * TenantDto dto = BeanReader.read(tenant, new TenantDto())
 *         .map("mobileValidated", Tenant::getMobileNumberValidated, TenantDto::setMobileValidated)
 *         .mapAll()
 *         .apply();
 * }</pre>
 *
 * @param <S>
 *          源对象类型（如领域对象）
 * @param <T>
 *          目标对象类型（如响应 DTO）
 */
public final class BeanReader<S, T> {

  private final S source;
  private final T target;
  private final List<ReadMapping> mappings = new ArrayList<>();
  private final Set<String> declared = new HashSet<>();
  private final Set<String> ignored = new HashSet<>();

  private BeanReader(S source, T target) {
    this.source = source;
    this.target = target;
  }

  /**
   * 开始一次读装配：{@code source} 是值的来源（领域对象），{@code target} 是待装配的目标（新建的响应 DTO / Chain）
   */
  public static <S, T> BeanReader<S, T> read(S source, T target) {
    Objects.requireNonNull(source, "源对象不能为空");
    Objects.requireNonNull(target, "目标对象不能为空");
    return new BeanReader<>(source, target);
  }

  /**
   * 显式映射（重命名 / 类型不同需 converter）：登记目标属性名，{@link #mapAll()} 会跳过它。
   *
   * @param targetProperty
   *          目标属性名（仅用于排重，与方法引用保持一致）
   * @param getter
   *          源侧取值方法引用（如 {@code Tenant::getMobileNumberValidated}）
   * @param setter
   *          目标侧设值方法引用（如 {@code TenantDto::setMobileValidated}）
   */
  public <V> BeanReader<S, T> map(String targetProperty, Function<S, V> getter, BiConsumer<T, V> setter) {
    Objects.requireNonNull(targetProperty, "目标属性名不能为空");
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    Objects.requireNonNull(setter, "设值方法引用不能为空");
    declared.add(targetProperty);
    mappings.add(new ReadMapping(getter, setter, null));
    return this;
  }

  /**
   * 显式映射 + 类型转换：两侧类型不同时用 {@code converter} 把源值转成目标类型。
   */
  public <V, W> BeanReader<S, T> map(
          String targetProperty,
          Function<S, V> getter,
          BiConsumer<T, W> setter,
          Function<V, W> converter) {
    Objects.requireNonNull(targetProperty, "目标属性名不能为空");
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    Objects.requireNonNull(setter, "设值方法引用不能为空");
    Objects.requireNonNull(converter, "转换器不能为空");
    declared.add(targetProperty);
    mappings.add(new ReadMapping(getter, setter, converter));
    return this;
  }

  /**
   * 排除目标属性（不拷贝，{@link #mapAll()} 跳过）
   */
  public BeanReader<S, T> ignore(String... properties) {
    for (String p : properties) {
      ignored.add(p);
      declared.add(p);
    }
    return this;
  }

  /**
   * 同名直读：目标上（除已声明 / 已忽略外）每个同名属性，从源读同名字段写入；两侧类型不一致的属性跳过（交由显式 {@code map(...)} 处理）。
   */
  public BeanReader<S, T> mapAll() {
    for (Field field : BeanMapCoverage.fieldsOf(target.getClass())) {
      String name = field.getName();
      if (field.isAnnotationPresent(BeanUnmapped.class) || ignored.contains(name) || declared.contains(name)) {
        continue;
      }
      Method getter = findGetter(source.getClass(), name);
      if (getter == null) {
        continue;
      }
      Method setter = PartialChain.setterOf(target.getClass(), name);
      if (!box(getter.getReturnType()).equals(box(setter.getParameterTypes()[0]))) {
        declared.add(name);
        continue;
      }
      String finalName = name;
      Function<S, Object> g = s -> invokeGetter(getter, s);
      BiConsumer<T, Object> st = (t, v) -> invokeSetter(setter, t, v);
      declared.add(name);
      mappings.add(new ReadMapping(g, st, null));
    }
    return this;
  }

  /**
   * 执行全部映射，返回装配好的目标对象
   */
  public T apply() {
    for (ReadMapping m : mappings) {
      m.apply(source, target);
    }
    return target;
  }

  private static final class ReadMapping {
    private final Function<Object, Object> getter;
    private final BiConsumer<Object, Object> setter;
    private final Function<Object, Object> converter;

    @SuppressWarnings("unchecked")
    private ReadMapping(Function<?, ?> getter, BiConsumer<?, ?> setter, Function<?, ?> converter) {
      this.getter = (Function<Object, Object>) getter;
      this.setter = (BiConsumer<Object, Object>) setter;
      this.converter = converter == null ? null : (Function<Object, Object>) converter;
    }

    private void apply(Object source, Object target) {
      Object value = getter.apply(source);
      if (converter != null) {
        value = converter.apply(value);
      }
      setter.accept(target, value);
    }
  }

  private static Method findGetter(Class<?> type, String property) {
    String cap = Character.toUpperCase(property.charAt(0)) + property.substring(1);
    for (String prefix : new String[]{"get", "is"}) {
      try {
        return type.getMethod(prefix + cap);
      } catch (NoSuchMethodException ignored) {
        // 尝试下一个前缀
      }
    }
    return null;
  }

  private static Object invokeGetter(Method getter, Object source) {
    try {
      return getter.invoke(source);
    } catch (ReflectiveOperationException e) {
      throw new BeanMapException(e, "无法读取 {}.{}", source.getClass().getSimpleName(), getter.getName());
    }
  }

  private static void invokeSetter(Method setter, Object target, Object value) {
    try {
      setter.invoke(target, value);
    } catch (ReflectiveOperationException e) {
      throw new BeanMapException(e, "无法写入 {}.{}", target.getClass().getSimpleName(), setter.getName());
    }
  }

  private static Class<?> box(Class<?> type) {
    return switch (type.getName()) {
      case "boolean" -> Boolean.class;
      case "byte" -> Byte.class;
      case "char" -> Character.class;
      case "short" -> Short.class;
      case "int" -> Integer.class;
      case "long" -> Long.class;
      case "float" -> Float.class;
      case "double" -> Double.class;
      default -> type;
    };
  }
}
