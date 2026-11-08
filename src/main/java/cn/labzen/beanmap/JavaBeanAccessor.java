/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

/**
 * {@link SourceAccessor} 的默认实现：对任意 JavaBean / POJO 以反射读取，
 * 三态语义退化为两态（{@link #isProvided(String)} 恒返回 {@code true}）。
 */
public class JavaBeanAccessor implements SourceAccessor {

  private final Object source;

  public JavaBeanAccessor(Object source) {
    this.source = source;
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
    return source == null ? null : source.getClass();
  }
}
