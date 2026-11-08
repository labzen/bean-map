/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

/**
 * 映射中止异常：当映射因策略（覆盖度、必填、不可清空、冲突等）需要中断时抛出。
 *
 * <p>
 * 携带语义化的 {@link #getCode()}，供上层（如 {@code cn.labzen:web}）映射为 HTTP 状态码，
 * 但本库本身不依赖任何 Web 框架。
 */
public class BeanMapAbortException extends BeanMapException {

  private static final long serialVersionUID = 1L;

  private final int code;

  public BeanMapAbortException(int code, String message) {
    super(message);
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
