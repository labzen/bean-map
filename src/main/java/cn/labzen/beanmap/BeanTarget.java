/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 {@code updateDomain} 方法里"被写入的领域对象"参数， 用以与创建入口（{@code toDomain} 内部 new 出 domain）区分。
 *
 * <p>
 * 仅用于 {@link BeanMap} 接口的方法参数；{@link BeanMapProcessor} 据此识别"编辑"语义 （就地三态写入传入实例）。
 *
 * @since 2026-11-07
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface BeanTarget {
}
