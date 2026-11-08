/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.annotation;

import cn.labzen.beanmap.processor.BeanMapProcessor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式声明 {@link BeanMap} 接口方法的映射形态，由 {@link BeanMapProcessor} 在编译期读取。
 *
 * <p>
 * 标注后按 {@link MappingType} 生成实现，方法名不再参与分派；未标注的方法按签名回退分派并产生编译告警。
 * 回退判据：双参 {@code void} 且带 {@link BeanTarget} 的参数按 {@code UPDATE_DOMAIN}，{@code toDomain} 前缀按
 * {@code TO_DOMAIN}，{@code copyDto} 按 {@code COPY_DTO}，其余单参有返回值的方法按读方向处理。
 *
 * @since 2026-11-08
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface BeanOp {

  /** 该方法的映射形态 */
  MappingType value();
}
