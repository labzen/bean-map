/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.core;

import cn.labzen.beanmap.accessor.*;
import cn.labzen.beanmap.exception.*;
import cn.labzen.beanmap.spi.*;


/**
 * 子对象块：只声明字段与更深层子对象，<b>没有 {@code apply()}</b> —— 写入只由最外层的 {@link BeanMapper#apply()} 统一执行（编译期即不可能"提前 apply"）。
 *
 * @param <Q>
 *          子对象的 domain 类型
 * @param <R>
 *          根 domain 类型
 */
public final class NestedPartial<Q, R> extends PartialChain<Q, R> {

  NestedPartial(PartialState state, SourceAccessor source, Q domain, boolean gated) {
    super(state, source, domain, gated);
  }
}
