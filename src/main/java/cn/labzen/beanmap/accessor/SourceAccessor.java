/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.accessor;

/**
 * 源对象访问抽象：把"值是否出现"的门控与"如何读值"从映射引擎分离，使其面向任意来源工作。
 *
 * <ul>
 *   <li>{@link #isProvided(String)}：该路径上的值是否"出现"（三态门控判据）。</li>
 *   <li>{@link #getValue(String)}：按路径读取源值（默认由反射实现）。</li>
 *   <li>{@link #type()}：源类型，供字段注解解析与反射使用。</li>
 * </ul>
 *
 * <p>
 * 默认实现 {@link JavaBeanAccessor} 对任意 JavaBean / POJO 以反射读取，三态语义退化为两态
 * （{@link #isProvided(String)} 恒返回 {@code true}）。持有三态语义的来源自行实现本接口即可接入。
 */
public interface SourceAccessor {

  /**
   * 该属性路径上的值是否在本次操作中"出现"（被客户端显式传递）。
   *
   * @param path 属性路径（点分隔）
   * @return 是否出现；两态来源恒返回 {@code true}
   */
  boolean isProvided(String path);

  /**
   * 按路径读取源对象上的值（默认由反射实现）。
   *
   * @param path 属性路径（点分隔）
   * @return 读取到的值，未设置时返回 {@code null}
   */
  Object getValue(String path);

  /**
   * 源对象的类型（用于字段注解解析与反射）。
   *
   * @return 源类型，可能为 {@code null}
   */
  Class<?> type();
}
