/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.annotation;

import cn.labzen.beanmap.core.*;
import cn.labzen.beanmap.processor.BeanMapProcessor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 bean-to-bean 映射接口，由 {@link BeanMapProcessor} 在编译期生成实现（{@code *BeanMapImpl}）。
 *
 * <p>
 * 方法形态优先由方法上的 {@link BeanOp} + {@link MappingType} 声明；未标注时按签名回退分派并产生编译告警：
 * <ul>
 * <li>双参 {@code void} 且其中一个参数带 {@link BeanTarget} —— 对传入 domain 就地同名直写；</li>
 * <li>{@code toDomain} 前缀 + 单参 + 有返回值 —— new 出返回类型后同名直写；</li>
 * <li>{@code copyDto} + 双参 + {@code void} —— 走 {@link BeanReader} 同名直读；</li>
 * <li>其余单参有返回值的方法 —— 读方向装配，可用 {@link BeanMapField} 声明重命名 / 转换 / 忽略。</li>
 * </ul>
 *
 * 生成类的注册方式由 {@code -Abeanmap.registry} 决定（缺省 {@code spring} 带 {@code @Component}）；字段映射委托给
 * {@link BeanMapper} / {@link BeanReader}；未识别的方法形态生成抛 {@link UnsupportedOperationException} 的方法体。
 *
 * @since 2026-11-07
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface BeanMap {
}
