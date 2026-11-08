/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.spi;

import cn.labzen.beanmap.exception.BeanMapAbortException;

/**
 * 策略中断的扩展点：由调用方决定一次中断要抛出的异常。
 *
 * <p>
 * 实现方可返回自定义异常以携带更多上下文，由库在中断点执行 {@code throw}；返回值不能为空，
 * 为空时库回退到 {@link BeanMapAbortException}。
 */
@FunctionalInterface
public interface AbortHandler {

  /**
   * 默认扩展点：抛出 {@link BeanMapAbortException}
   */
  AbortHandler THROWING = (code, message) -> new BeanMapAbortException(code, message);

  /**
   * 构造本次中断要抛出的异常
   *
   * @param code
   *          中断语义码
   * @param message
   *          中断提示
   */
  RuntimeException onAbort(AbortCode code, String message);
}
