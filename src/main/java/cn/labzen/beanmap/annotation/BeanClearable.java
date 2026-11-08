/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.annotation;

import java.lang.annotation.*;

/**
 * 声明请求 DTO 字段<b>允许被显式 null 清空</b>，作为三态写入链上 {@code nullable()} 的默认规则：
 *
 * <ul>
 * <li>标在 <b>DTO 类</b>上 ⇒ 该 DTO 全部字段默认可清空；
 * <li>标在<b>字段</b>上 ⇒ 仅该字段可清空，并覆盖类级声明（{@code value = false} 用于从类级默认中豁免）。
 * </ul>
 * <p>
 * 生效优先级：链上显式调用（{@code nullable()} / {@code notClearable()}）&gt; 字段注解 &gt; 类注解 &gt; 默认不可清空。
 *
 * <p>
 * 与三态写入的配套语义见 {@code cn.labzen.beanmap} 包说明。
 */
@Documented
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BeanClearable {

  /**
   * 字段是否允许被显式 null 清空（字段级标注 {@code false} 表示从类级默认中豁免）
   */
  boolean value() default true;
}
