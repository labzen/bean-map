/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

/**
 * 策略中断的语义码，携带等价的整型状态码与默认提示。
 *
 * <p>
 * 库本身不依赖任何 Web 框架，整型码由上层映射为 HTTP 状态。
 */
public enum AbortCode {

  /** 声明的字段一个都没在负载里出现 */
  NO_CHANGE(304, "Not Modified"),
  /** 必填字段没有可用值 */
  REQUIRED(400, "字段必须有值"),
  /** 不允许清空的字段被显式传 null */
  NOT_CLEARABLE(400, "字段不允许清空"),
  /** 字段值无法转换或写入目标类型 */
  TYPE_MISMATCH(400, "字段值类型不匹配"),
  /** 多源合并时同一字段出现冲突 */
  FIELD_CONFLICT(409, "字段冲突");

  private final int code;
  private final String defaultMessage;

  AbortCode(int code, String defaultMessage) {
    this.code = code;
    this.defaultMessage = defaultMessage;
  }

  public int code() {
    return code;
  }

  public String defaultMessage() {
    return defaultMessage;
  }
}
