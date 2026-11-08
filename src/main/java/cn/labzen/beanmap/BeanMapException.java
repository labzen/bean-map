/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

/**
 * BeanMapper 基础异常，直接继承 {@link RuntimeException}。
 *
 * <p>
 * 消息支持 {@code {}} 占位符的顺序替换。
 */
public class BeanMapException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public BeanMapException(String message) {
    super(message);
  }

  public BeanMapException(String message, Object... args) {
    super(format(message, args));
  }

  public BeanMapException(Throwable cause, String message) {
    super(message, cause);
  }

  public BeanMapException(Throwable cause, String message, Object... args) {
    super(format(message, args), cause);
  }

  private static String format(String template, Object... args) {
    if (template == null) {
      return null;
    }
    if (args == null || args.length == 0) {
      return template;
    }
    StringBuilder sb = new StringBuilder();
    int argIdx = 0;
    int i = 0;
    int n = template.length();
    while (i < n) {
      char c = template.charAt(i);
      if (c == '{' && i + 1 < n && template.charAt(i + 1) == '}') {
        if (argIdx < args.length) {
          sb.append(args[argIdx++]);
        } else {
          sb.append("{}");
        }
        i += 2;
      } else {
        sb.append(c);
        i++;
      }
    }
    return sb.toString();
  }
}
