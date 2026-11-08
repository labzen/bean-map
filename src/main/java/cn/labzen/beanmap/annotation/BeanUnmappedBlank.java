/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.annotation;

import java.lang.annotation.*;

/**
 * 声明请求 DTO 的 <b>String</b> 字段「空白串视为没传」，作为三态写入链上 {@code ignoreBlank()} 的默认规则：
 *
 * <ul>
 * <li>标在 <b>DTO 类</b>上 ⇒ 该 DTO 全部 String 字段默认空白跳过（非 String 字段不受影响）；
 * <li>标在<b>字段</b>上 ⇒ 仅该字段生效，并覆盖类级声明（{@code value = false} 用于从类级默认中豁免）； 字段级标注在非 String 字段上 ⇒ 三态写入声明期报错。
 * </ul>
 * <p>
 * 生效优先级：链上显式调用（{@code ignoreBlank()} 等）&gt; 字段注解 &gt; 类注解 &gt; 默认不跳过。
 *
 * <p>
 * 与三态写入的配套语义见 {@code cn.labzen.beanmap} 包说明。
 */
@Documented
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BeanUnmappedBlank {

  /**
   * String 字段是否空白跳过（字段级标注 {@code false} 表示从类级默认中豁免）
   */
  boolean value() default true;
}
