/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DTO 属性反射：按属性名取 {@link Field} / 声明类型 / 值，支持点号路径（{@code profile.userNo}）。
 *
 * <p>
 * 属性名是唯一事实来源，{@link Field} 结果按"类型 + 属性名"缓存；属性名（含路径中任一段）不存在立即抛异常。
 */
public final class DtoReflection {

  private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();

  private DtoReflection() {
  }

  /**
   * 属性（或点号路径最后一段）的声明类型
   */
  public static Class<?> fieldType(Class<?> type, String path) {
    Class<?> current = type;
    for (String segment : segments(path)) {
      current = field(current, segment).getType();
    }
    return current;
  }

  /**
   * 属性（或点号路径最后一段）的 {@link Field}：点号路径逐段下钻到最后一段所在的类型
   */
  public static Field fieldOf(Class<?> type, String path) {
    String[] segments = segments(path);
    Class<?> current = type;
    for (int i = 0; i < segments.length - 1; i++) {
      current = field(current, segments[i]).getType();
    }
    return field(current, segments[segments.length - 1]);
  }

  /**
   * 取属性值；点号路径逐段下钻，中途为 null 时返回 null
   */
  public static Object value(Object source, String path) {
    Object current = source;
    for (String segment : segments(path)) {
      if (current == null) {
        return null;
      }
      current = read(current, segment);
    }
    return current;
  }

  private static Field field(Class<?> type, String name) {
    return FIELDS.computeIfAbsent(type.getName() + "#" + name, ignored -> findField(type, name));
  }

  private static Object read(Object source, String segment) {
    Class<?> type = source.getClass();
    if (type.isRecord()) {
      // record 生成裸 accessor（x()），并可能位于强封装模块中，走公开组件方法而非私有字段
      try {
        Method accessor = type.getDeclaredMethod(segment);
        accessor.setAccessible(true);
        return accessor.invoke(source);
      } catch (NoSuchMethodException e) {
        throw new BeanMapException("record 组件不存在：{}", type.getSimpleName() + "." + segment);
      } catch (ReflectiveOperationException e) {
        throw new BeanMapException(e, "无法读取 record 组件：{}", type.getSimpleName() + "." + segment);
      }
    }
    try {
      return field(type, segment).get(source);
    } catch (IllegalAccessException e) {
      throw new BeanMapException(e, "无法读取属性：{}", type.getSimpleName() + "." + segment);
    }
  }

  private static String[] segments(String path) {
    return path.split("\\.");
  }

  private static Field findField(Class<?> type, String name) {
    Class<?> current = type;
    while (current != null) {
      try {
        Field declared = current.getDeclaredField(name);
        declared.setAccessible(true);
        return declared;
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new BeanMapException(
            "属性不存在：{}", Objects.requireNonNull(type).getSimpleName() + "." + name);
  }
}
