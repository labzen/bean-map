/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.annotation;

import java.lang.annotation.*;

/**
 * 标记请求 DTO 上<b>不参与三态写入</b>的字段（只读 / 受保护字段，如租户、身份、冻结、审计列）。
 *
 * <p>
 * 供 {@code BeanMapCoverage#verify} 的启动期覆盖度断言使用：被本注解标记的字段必须出现在该断言的排除集里， 且不得出现在域的写入白名单里。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BeanUnmapped {
}
