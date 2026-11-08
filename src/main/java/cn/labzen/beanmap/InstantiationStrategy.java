/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.lang.reflect.Constructor;

/**
 * 实例化策略：为元素级映射创建目标实例。
 *
 * <p>
 * 默认实现走无参构造器；无默认构造器的类型（如 record / 不可变对象）由调用方按入口注入自定义实现。
 */
@FunctionalInterface
public interface InstantiationStrategy {

  /**
   * 默认实现：反射调用无参构造器
   */
  InstantiationStrategy DEFAULT = type -> {
    try {
      Constructor<?> ctor = type.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new BeanMapException(e, "类型 {} 没有可用的无参构造器，请提供 InstantiationStrategy", type.getSimpleName());
    }
  };

  /**
   * 创建指定类型的实例
   *
   * @param type
   *          目标类型
   */
  Object create(Class<?> type);
}
