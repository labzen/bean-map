/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
/**
 * 通用对象映射基础库：三态部分更新、两态全量读写、多源合并与覆盖度断言，面向管理后台等中低吞吐场景。
 *
 * <h3>包结构</h3>
 *
 * <ul>
 * <li>{@code cn.labzen.beanmap.core}：以 {@link cn.labzen.beanmap.core.BeanMapper} 为入口的三态写入链、读方向装配
 * {@link cn.labzen.beanmap.core.BeanReader}、多源合并 {@link cn.labzen.beanmap.core.BeanComposer}、
 * 写入结果 {@link cn.labzen.beanmap.core.BeanMapResource} 与覆盖度断言 {@link cn.labzen.beanmap.core.BeanMapCoverage}；
 * <li>{@code cn.labzen.beanmap.accessor}：以 {@link cn.labzen.beanmap.accessor.SourceAccessor} 为核心的源对象访问抽象，
 * 提供 JavaBean / Map 源实现与 DTO 属性反射；
 * <li>{@code cn.labzen.beanmap.annotation}：映射接口注解（{@link cn.labzen.beanmap.annotation.BeanMap}、
 * {@link cn.labzen.beanmap.annotation.BeanOp}、{@link cn.labzen.beanmap.annotation.BeanMapField}、
 * {@link cn.labzen.beanmap.annotation.BeanTarget} 等）与 DTO 字段注解
 * （{@link cn.labzen.beanmap.annotation.BeanUnmapped}、{@link cn.labzen.beanmap.annotation.BeanClearable}、
 * {@link cn.labzen.beanmap.annotation.BeanUnmappedBlank}）；
 * <li>{@code cn.labzen.beanmap.spi}：中断处理、实例化、冲突裁决等可替换策略；
 * <li>{@code cn.labzen.beanmap.exception}：映射异常与策略中断异常；
 * <li>{@code cn.labzen.beanmap.processor}：编译期生成映射接口实现的
 * {@link cn.labzen.beanmap.processor.BeanMapProcessor}。
 * </ul>
 *
 * <h3>三态写入语义</h3>
 * <p>
 * 更新请求区分三种写法：字段<b>缺省</b>（本次不涉及该字段，保持库中原值）、字段<b>显式 null</b>（明确要求写入
 * {@code NULL}）、字段<b>有值</b>（含空串、纯空白串，照原值写入）。判据只有一条：负载里是否出现过该字段，
 * 由 {@link cn.labzen.beanmap.accessor.SourceAccessor#isProvided(String)} 提供；默认
 * {@link cn.labzen.beanmap.accessor.JavaBeanAccessor} 恒返回 {@code true}，语义退化为两态。
 *
 * <p>
 * 链上可声明值级约束（{@code required}、{@code notClearable}、{@code nullable}、{@code ignoreBlank}），
 * 入口级约束用 {@link cn.labzen.beanmap.core.BeanMapper#requireAnyProvided()}；字段默认规则由 DTO 注解声明，
 * 生效优先级为<b>链上显式调用 &gt; 字段注解 &gt; 类注解 &gt; 默认关闭</b>。一次写入的结果是
 * {@link cn.labzen.beanmap.core.BeanMapResource}，给出本次要落库的值以及「这一列动不动」。
 *
 * <p>
 * 字段声明支持逐个 {@code map(...)}（可挂修饰符）与同名直写
 * {@link cn.labzen.beanmap.core.BeanMapper#mapAll()}；两侧类型不同的字段用
 * {@code map(getter, setter, converter)} 当场给出转换器；子对象块用 {@code sub(...)} 声明。
 *
 * <h3>用法示例</h3>
 *
 * <pre>{@code
 * BeanMapResource<User> updated = BeanMapper.of(user, new JavaBeanAccessor(resource))
 *         .map(resource::getEmail, user::setEmail).ignoreBlank()
 *         .map(resource::getNickname, user::setNickname)
 *         .apply();
 * }</pre>
 *
 * <h3>覆盖度断言</h3>
 * <p>
 * DTO 每个字段的归宿 = 某个入口链上声明（可写）／{@code @BeanUnmapped}（永不写入）／某入口 {@code excludes}
 * （本入口不管），三者都不是即由 {@link cn.labzen.beanmap.core.BeanMapCoverage} 在构建期断言失败，
 * 不需要手抄字段清单。
 */
package cn.labzen.beanmap;
