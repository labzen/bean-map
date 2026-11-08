/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.util.Objects;

/**
 * 映射中止异常：当映射因策略（覆盖度、必填、不可清空、冲突等）需要中断时抛出。
 *
 * <p>
 * 携带 {@link AbortCode} 语义码；{@link #getCode()} 给出等价的整型状态码，
 * 供上层（如 {@code cn.labzen:web}）映射为 HTTP 状态，但本库本身不依赖任何 Web 框架。
 */
public class BeanMapAbortException extends BeanMapException {

  private static final long serialVersionUID = 1L;

  private final AbortCode abortCode;

  public BeanMapAbortException(AbortCode abortCode, String message) {
    super(message);
    this.abortCode = Objects.requireNonNull(abortCode, "中断语义码不能为空");
  }

  public AbortCode getAbortCode() {
    return abortCode;
  }

  public int getCode() {
    return abortCode.code();
  }
}
