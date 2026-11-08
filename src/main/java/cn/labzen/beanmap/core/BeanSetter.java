/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.core;

import cn.labzen.beanmap.accessor.*;
import cn.labzen.beanmap.exception.*;
import cn.labzen.beanmap.spi.*;

import java.io.Serializable;
import java.util.function.BiConsumer;

/**
 * 设值方法引用（domain 侧，未绑定实例）：如 {@code User::setEmail}。
 *
 * <p>
 * 写入时作用于当前链节点的 domain 上；取值侧用 {@link BeanGetter}（已绑定形态）。
 */
@FunctionalInterface
public interface BeanSetter<P, T> extends BiConsumer<P, T>, Serializable {
}
