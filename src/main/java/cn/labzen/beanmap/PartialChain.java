/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@code BeanMapper} 链的公共部分：声明字段（{@code map}）与子对象块（{@code sub}）。
 *
 * <p>
 * 每个链节点各自携带它所属的源对象与 domain：根节点是最外层的领域对象，子对象块是嵌套的领域对象。 {@code gated} 为真表示本节点所处位置"父级字段缺省或为 null"，其字段一律不处理。
 *
 * @param <P>
 *          本节点对应的 domain 类型
 * @param <R>
 *          根 domain 类型（{@code BeanMapResource#domain()} 的返回类型；根节点上 R 与 P 相同）
 */
public abstract class PartialChain<P, R> {

  private static final Map<String, Method> DOMAIN_SETTERS = new ConcurrentHashMap<>();

  final PartialState state;
  final SourceAccessor source;
  final P domain;
  final boolean gated;

  PartialChain(PartialState state, SourceAccessor source, P domain, boolean gated) {
    this.state = state;
    this.source = source;
    this.domain = domain;
    this.gated = gated;
  }

  /**
   * 按属性名解析 domain 侧 setter（带缓存）：{@code property} ⇒ {@code setXxx(单参)}
   */
  static Method setterOf(Class<?> domainType, String property) {
    return DOMAIN_SETTERS.computeIfAbsent(
            domainType.getName() + "#" + property,
            ignored -> {
              String expected = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
              for (Method method : domainType.getMethods()) {
                if (method.getName().equals(expected) && method.getParameterCount() == 1) {
                  return method;
                }
              }
              throw new BeanMapException("{} 上找不到可写属性：{}", domainType.getSimpleName(), property);
            });
  }

  static void invokeSetter(Object target, String property, Object value) {
    try {
      setterOf(target.getClass(), property).invoke(target, value);
    } catch (ReflectiveOperationException e) {
      throw new BeanMapException(e, "无法写入 {}.{}", target.getClass().getSimpleName(), property);
    }
  }

  static Class<?> box(Class<?> type) {
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

  @SuppressWarnings("unchecked")
  private static <P, T> ValueWriter writerOf(BeanSetter<P, T> setter) {
    BeanSetter<Object, Object> writer = (BeanSetter<Object, Object>) setter;
    return writer::accept;
  }

  /**
   * 声明字段（<b>字符串属性名</b>）：{@code dtoField} 必须是 DTO 的属性名（支持点号路径），取值按该名字反射。
   *
   * <p>
   * ⚠️ 保留字符串形态只为一件事：<b>没有实例可绑定取值引用</b>的场合 —— ① 点号路径（子对象可能为 {@code null}）； ② 断言"属性名拼错 ⇒ 声明期报错"的测试。其余一律用
   * {@code map(getter, …)}（编译期校验属性名）。
   */
  public <T> BeanField<P, R, T> map(String dtoField, BeanSetter<P, T> setter) {
    Objects.requireNonNull(setter, "setter 不能为空");
    return declare(dtoField, writerOf(setter), setter);
  }

  /**
   * 声明字段（兼容旧写法的已绑定 setter）：{@code setter} 为已绑定引用（如 {@code user::setEmail}），内部适配为 {@link BeanSetter}。
   */
  public <T> BeanField<P, R, T> map(String dtoField, Consumer<T> setter) {
    Objects.requireNonNull(setter, "setter 不能为空");
    return map(dtoField, (p, t) -> setter.accept(t));
  }

  /**
   * 声明字段（<b>同名直写</b>且需要修饰符）：属性名从取值方法引用解析，domain 侧同名 setter 自动配对 （存在性与两侧类型一致在声明期校验，不通过抛 {@code
   * BeanMapException}）。
   *
   * <pre>{@code
   * .map(resource::getUri).nullable()   // 比 map(resource::getUri, domain::setUri) 省去 setter 引用
   * }</pre>
   * <p>
   * 不需要修饰符的同名字段用 {@code BeanMapper#map(BeanGetter[])} 批量声明；两侧属性名不一致或需要转换器的 字段，仍用 {@code map(getter,
   * setter[, converter])}。
   */
  public <T> BeanField<P, R, T> map(BeanGetter<T> getter) {
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    String property = LambdaNames.propertyOf(getter, "取值方法引用");
    if (state.isExcluded(property)) {
      throw new BeanMapException("字段 {} 已被 excludes(...) 声明为「本入口不管」，不能再声明为可写", property);
    }
    Class<?> sourceType = DtoReflection.fieldType(source.type(), property);
    Method setter = setterOf(domain.getClass(), property);
    Class<?> targetType = setter.getParameterTypes()[0];
    if (!box(sourceType).equals(box(targetType))) {
      throw new BeanMapException(
              "属性 {} 在 DTO（{}）与 domain（{}）上的类型不一致：请改用 map(getter, setter, converter) 显式声明",
              property,
              sourceType.getSimpleName(),
              targetType.getSimpleName());
    }
    BeanField<P, R, T> field = new BeanField<>(
            state,
            source,
            domain,
            gated,
            property,
            (target, value) -> invokeSetter(target, property, value),
            property,
            targetType,
            false);
    state.declare(field);
    return field;
  }

  /**
   * 声明字段：属性名从取值方法引用解析（{@code resource::getEmailAddr} ⇒ {@code emailAddr}），setter 已绑定实例
   */
  public <T> BeanField<P, R, T> map(BeanGetter<T> getter, BeanSetter<P, T> setter) {
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    return map(LambdaNames.propertyOf(getter, "取值方法引用"), setter);
  }

  /**
   * 声明字段（兼容旧写法的已绑定 setter）：{@code setter} 为已绑定引用（如 {@code user::setEmail}），内部适配为 {@link BeanSetter}。
   *
   * <p>
   * 已绑定 setter 的 domain 属性名取<b>声明侧（DTO getter）的属性名</b>，故要求两侧属性同名（{@code user::setEmail} 须配 {@code dto::getEmail}）。
   * 两侧属性名不同时（如 DTO 侧 {@code getIdentities}、domain 侧 {@code setIdentity}）须改用未绑定引用 {@code map(getter, Domain::setXxx)}。
   */
  public <T> BeanField<P, R, T> map(BeanGetter<T> getter, Consumer<T> setter) {
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    Objects.requireNonNull(setter, "设值方法引用不能为空");
    return map(LambdaNames.propertyOf(getter, "取值方法引用"), (p, t) -> setter.accept(t));
  }

  /**
   * 声明字段（<b>两侧类型不同</b>）：DTO 侧属性名从取值引用解析，domain 侧属性名从<b>未绑定</b> setter 引用解析（两侧可不同），取值由 {@code converter} 转成 setter
   * 的参数类型。
   *
   * <pre>{@code
   * .map(resource::getIdentities, User::setIdentity, userIdentityProvider::composeIdentities)
   * }</pre>
   * <p>
   * 与 {@link #map(BeanGetter, BeanSetter)} 的分工：那一支要求两侧<b>同类型</b>（编译期对齐）；本支允许不同 （{@code
   * Set<UserIdentity>} ⇒ {@code String} 这类派生 / 组合字段），代价是<b>转换器当场必填</b> —— 不存在 "转了却没说"的隐藏路径，也就不会落到
   * {@code DefaultConversionService} 的隐式转换上。
   *
   * <p>
   * ⚠️ <b>类型分工</b>（两侧不同时最易写错）：{@code withDefault} / {@code ignoreValue*} 作用于<b>转换前</b>的原值 ⇒ 实参必须是 DTO 侧类型（声明期校验，不符即抛
   * {@code BeanMapException}）；{@code ignoreIf*} 的谓词形参 按写入侧类型标注、却作用于转换前的值 ⇒ 本支声明的字段上<b>一律拒绝</b>；{@code required(...)}
   * 的谓词作用于 <b>转换后</b>的值 ⇒ 按写入侧类型书写。
   *
   * @param converter
   *          DTO 侧原值 ⇒ setter 参数类型
   */
  public <S, T> BeanField<P, R, T> map(
          BeanGetter<S> getter, BeanSetter<P, T> setter, Function<S, T> converter) {
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    Objects.requireNonNull(converter, "转换器不能为空");
    BeanField<P, R, T> declared = declare(LambdaNames.propertyOf(getter, "取值方法引用"), writerOf(setter), setter,
            true);
    return declared.withConverter(converter);
  }

  /**
   * 声明字段（两侧类型不同）：转换器额外拿到本节点的 DTO 实例（多字段联动），其余同 {@link #map(BeanGetter, BeanSetter, Function)}
   */
  public <S, T> BeanField<P, R, T> map(
          BeanGetter<S> getter, BeanSetter<P, T> setter, BiFunction<S, SourceAccessor, T> converter) {
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    Objects.requireNonNull(converter, "转换器不能为空");
    BeanField<P, R, T> declared = declare(LambdaNames.propertyOf(getter, "取值方法引用"), writerOf(setter), setter,
            true);
    return declared.withConverter(converter);
  }

  /**
   * 声明字段（兼容旧写法的已绑定 setter）：{@code setter} 为已绑定引用（如 {@code user::setIdentity}），内部适配为 {@link BeanSetter}。
   */
  public <S, T> BeanField<P, R, T> map(
          BeanGetter<S> getter, Consumer<T> setter, Function<S, T> converter) {
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    Objects.requireNonNull(converter, "转换器不能为空");
    return map(getter, (p, t) -> setter.accept(t), converter);
  }

  /**
   * 声明字段（兼容旧写法的已绑定 setter）：{@code setter} 为已绑定引用，内部适配为 {@link BeanSetter}。
   */
  public <S, T> BeanField<P, R, T> map(
          BeanGetter<S> getter, Consumer<T> setter, BiFunction<S, SourceAccessor, T> converter) {
    Objects.requireNonNull(getter, "取值方法引用不能为空");
    Objects.requireNonNull(converter, "转换器不能为空");
    return map(getter, (p, t) -> setter.accept(t), converter);
  }

  /**
   * 本链声明的字段里，是否有任一在负载里出现过 —— "一个可改字段都没传" ⇒ {@code 304} 的判据。
   *
   * <p>
   * ⚠️ 与写入结果的含义不同：本方法问"<b>入参出现了吗</b>"（在任何 {@code ignore*} 判定之前）， {@code
   * BeanMapResource#anyWritten()} 是"<b>是否真的写入了字段</b>"（被 {@code ignore*} 跳过的算未写入）。 契约里的"没传 ⇒ 304"问的是前者。
   *
   * <p>
   * 作用域是<b>整次写入</b>（声明清单在链上共享）；由于声明链是流式的（末次 {@code map(...)} 返回字段句柄）， 该判据只能挂在链上，无法只挂在 {@code
   * BeanMapper} 上。
   */
  public boolean anyProvided() {
    return state.anyProvided();
  }

  /**
   * 声明子对象块：domain 子对象尚不存在时抛异常（提示先创建）
   */
  public <Q> NestedPartial<Q, R> sub(
          BeanGetter<? extends SourceAccessor> dtoGetter, BeanGetter<Q> domainGetter) {
    return sub(dtoGetter, domainGetter, null);
  }

  /**
   * 声明子对象块：domain 子对象尚不存在时用 factory 创建，并挂到父 domain 上（按属性名找 setter）
   *
   * <p>
   * 两侧引用的形态与 {@code map(...)} 对称：DTO 侧用已绑定的 {@code BeanGetter}（取属性名）， domain 侧用未绑定的 {@code
   * BeanGetter}（在本节点的 domain 上取子对象）。
   */
  public <Q> NestedPartial<Q, R> sub(
          BeanGetter<? extends SourceAccessor> dtoGetter, BeanGetter<Q> domainGetter, Supplier<Q> factory) {
    Objects.requireNonNull(dtoGetter, "子对象取值方法引用不能为空");
    Objects.requireNonNull(domainGetter, "子对象取值方法引用不能为空");

    String property = LambdaNames.propertyOf(dtoGetter, "子对象取值方法引用");
    // 声明先于"本次是否处理该块"的判定：覆盖度断言读的是声明，不依赖某个具体负载
    state.declareNested(source, property);
    if (source == null || !source.isProvided(property)) {
      // 父级字段缺省 ⇒ 整块不处理
      return new NestedPartial<>(state, null, null, true);
    }

    SourceAccessor childSource = childSourceOf(property);
    if (childSource == null) {
      // 父级值为显式 null ⇒ 不做"删除整块"
      return new NestedPartial<>(state, null, null, true);
    }

    Q childDomain = domainGetter.get();
    if (childDomain == null) {
      if (factory == null) {
        throw new BeanMapException("子对象尚不存在（{}），请先创建，或改用带创建方式的重载", property);
      }
      childDomain = factory.get();
      attach(domain, property, childDomain);
    }
    return new NestedPartial<>(state, childSource, childDomain, false);
  }

  private <T> BeanField<P, R, T> declare(
          String dtoField, ValueWriter writer, Serializable reference) {
    return declare(dtoField, writer, reference, false);
  }

  /**
   * @param crossTyped
   *          本字段两侧类型<b>不同</b>（{@code map(getter, setter, converter)} 声明）—— 供"按值判定的 修饰符"在声明期挡住写错的实参 / 谓词（见
   *          {@code BeanField}）
   */
  private <T> BeanField<P, R, T> declare(
          String dtoField, ValueWriter writer, Serializable reference, boolean crossTyped) {
    if (state.isExcluded(dtoField)) {
      throw new BeanMapException("字段 {} 已被 excludes(...) 声明为「本入口不管」，不能再声明为可写", dtoField);
    }
    BeanField<P, R, T> field = new BeanField<>(state, source, domain, gated, dtoField, writer, reference,
            crossTyped);
    state.declare(field);
    return field;
  }

  /**
   * 声明"<b>同名直写</b>"字段：DTO 侧与 domain 侧属性同名，故只需给出属性名 —— 两侧的<b>存在性</b>与 <b>类型一致性</b>都在声明期校验（不通过即抛
   * {@code BeanMapException}，不会静默少写或错配）。
   *
   * <p>
   * 它只处理"不需要修饰符"的属性；需要 {@code nullable} / {@code notClearable} / 转换 / 跳过的字段， 仍用 {@code
   * map(...)} 显式声明（显式声明会<b>覆盖</b>同名的同名直写声明）。
   */
  BeanField<P, R, Object> declareMap(String property) {
    Objects.requireNonNull(property, "属性名不能为空");
    if (state.isExcluded(property)) {
      throw new BeanMapException("字段 {} 已被 excludes(...) 声明为「本入口不管」，不能再声明为可写", property);
    }
    Class<?> sourceType = DtoReflection.fieldType(source.type(), property);
    Method setter = setterOf(domain.getClass(), property);
    Class<?> targetType = setter.getParameterTypes()[0];
    if (!box(sourceType).equals(box(targetType))) {
      throw new BeanMapException(
              "属性 {} 在 DTO（{}）与 domain（{}）上的类型不一致：请改用 map(...) 显式声明",
              property,
              sourceType.getSimpleName(),
              targetType.getSimpleName());
    }

    BeanField<P, R, Object> field = new BeanField<>(
            state,
            source,
            domain,
            gated,
            property,
            (target, value) -> invokeSetter(target, property, value),
            property,
            targetType,
            true);
    state.declare(field);
    return field;
  }

  @SuppressWarnings("unchecked")
  private SourceAccessor childSourceOf(String property) {
    Object value = source.getValue(property);
    if (value instanceof SourceAccessor child) {
      return child;
    }
    if (value != null) {
      throw new BeanMapException("子对象必须实现 SourceAccessor：{}", property);
    }
    return null;
  }

  private void attach(Object target, String property, Object child) {
    try {
      setterOf(target.getClass(), property).invoke(target, child);
    } catch (ReflectiveOperationException e) {
      throw new BeanMapException(e, "无法挂载新建的子对象：{}", property);
    }
  }

  /**
   * 写入动作：{@code target} 是本节点的 domain 实例（未绑定实例的 setter 用它）
   */
  @FunctionalInterface
  interface ValueWriter {

    void write(Object target, Object value);
  }
}
