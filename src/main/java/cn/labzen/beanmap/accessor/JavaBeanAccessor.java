/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.accessor;

import cn.labzen.beanmap.exception.*;

import java.util.Map;
import java.util.Objects;

/**
 * {@link SourceAccessor} 的默认实现：对 JavaBean / POJO 走字段反射，对 record 走组件 accessor，
 * 三态语义退化为两态（{@link #isProvided(String)} 恒返回 {@code true}）。
 *
 * <p>
 * 构造时校验源形状：Map 源请用 {@link MapAccessor}，其它不具备可解析形状的对象请自行实现
 * {@link SourceAccessor}；不合法形状在声明期即失败并给出可读提示，不静默退化。
 */
public class JavaBeanAccessor implements SourceAccessor {

  private final Object source;

  public JavaBeanAccessor(Object source) {
    this.source = Objects.requireNonNull(source, "源对象不能为空");
    Class<?> type = source.getClass();
    if (Map.class.isAssignableFrom(type)) {
      throw new BeanMapException("Map 源请使用 MapAccessor 并指定字段描述类型：{}", type.getSimpleName());
    }
    if (type.isArray() || type.isPrimitive() || type.isEnum()
            || source instanceof CharSequence || source instanceof Number
            || source instanceof Boolean || source instanceof Character) {
      throw new BeanMapException("源对象 {} 不具备可解析的 JavaBean 形状，请实现 SourceAccessor", type.getSimpleName());
    }
  }

  @Override
  public boolean isProvided(String path) {
    return true;
  }

  @Override
  public Object getValue(String path) {
    return DtoReflection.value(source, path);
  }

  @Override
  public Class<?> type() {
    return source.getClass();
  }
}
