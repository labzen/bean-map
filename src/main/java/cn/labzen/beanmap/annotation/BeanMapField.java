/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 读方向字段映射声明：标注在 {@link BeanMap} 接口的 {@code toDto} / {@code toChain} 方法上， 描述「领域对象 → 响应 DTO / Chain」的单字段装配。
 *
 * <p>
 * 未标注的方法，其全部同名属性由 {@code BeanReader#mapAll()} 自动装配；标注的字段走显式映射（可重命名 / 转换 / 忽略）。
 *
 * <pre>
 * {
 *   &#64;code
 *   &#64;BeanMap
 *   public interface TenantBeanMap {
 *     TenantDto toDto(Tenant domain);
 *
 *     @BeanMapField(source = "mobileNumberValidated", target = "mobileValidated")
 *     TenantDto toDtoRenamed(Tenant domain);
 *   }
 * }
 * </pre>
 */
@Repeatable(BeanMapFields.class)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface BeanMapField {

  /** 源（领域对象）属性名 */
  String source();

  /** 目标（响应 DTO）属性名；缺省表示与 {@link #source()} 同名 */
  String target() default "";

  /** 是否忽略该字段（不拷贝） */
  boolean ignore() default false;

  /** 类型转换（源值 → 目标类型）；{@code void.class} 表示无转换 */
  Class<?> converter() default void.class;
}
