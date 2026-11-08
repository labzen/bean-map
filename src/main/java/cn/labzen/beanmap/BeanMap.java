/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 bean-to-bean 映射接口，由 {@link BeanMapProcessor} 在编译期生成实现（{@code *BeanMapImpl}）。
 *
 * <p>
 * 方法形态由方法名前缀与 {@link BeanTarget} 识别：
 * <ul>
 * <li>{@code toDomain(Dto)} —— new 出 domain 后同名直写，返回 domain；</li>
 * <li>{@code updateDomain(Dto, @BeanTarget Domain)} —— 对传入 domain 就地同名直写；</li>
 * <li>{@code copyDto(Src, Tgt)} —— 同类型 DTO 拷贝，走 {@link BeanReader} 同名直读；</li>
 * <li>{@code toDto(Domain)} / {@code toChain(Domain)} —— 读方向装配，可用 {@link BeanMapField} 声明重命名 / 转换 / 忽略。</li>
 * </ul>
 *
 * 生成的实现带 {@code @Component}，字段映射委托给 {@link BeanMapper} / {@link BeanReader}；未识别的方法形态生成抛 {@link UnsupportedOperationException} 的方法体。
 *
 * @since 2026-11-07
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface BeanMap {
}
