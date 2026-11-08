/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
/**
 * 三态写入（部分更新）工具：把「本次负载里出现过的字段」写进一个 domain 实例，缺省字段一律不动。
 *
 * <h3>它解决什么</h3>
 * <p>
 * 更新请求需要区分负载里的三种写法，三者语义不同：
 *
 * <ul>
 * <li>字段<b>缺省</b>（{@code {}}）⇒ 本次不涉及该字段，保持库中原值；
 * <li>字段<b>显式 null</b>（{@code {"x": null}}）⇒ 明确要求写入 {@code NULL}；
 * <li>字段<b>有值</b>（含空串、纯空白串）⇒ 照原值写入。
 * </ul>
 * <p>
 * 判据只有一条：负载里是否出现过该字段 —— 由 {@link cn.labzen.beanmap.SourceAccessor#isProvided(String)}
 * 提供（属性名的存在性由源对象实现决定；默认 {@link cn.labzen.beanmap.JavaBeanAccessor} 退化为两态）。
 *
 * <p>
 * "出现了吗"的查询也在链上（业务域不必自己写字符串属性名）：{@link PartialChain#anyProvided()} 问"本次写入有没有任何输入" （"一个可改字段都没传 ⇒
 * 304"的判据）。值级约束同样在链上声明：{@code required}（必填）、{@code notClearable} （可选但不可清空）；{@code nullable()}（允许被显式 null 清空）；写入级约束用
 * {@link BeanMapper#requireAnyProvided()} （一个可改字段都没传 ⇒ 以 304 中断）。 一次写入的结果是 {@link BeanMapResource}（{@code apply()}
 * 的返回值）：{@link BeanMapResource#written(BeanGetter)} 给出"该 domain
 * 属性本次要落库的值"，{@link BeanMapResource#willWrite(BeanGetter)} /
 * {@link BeanMapResource#ifWillWrite(BeanGetter, java.util.function.Consumer)} 供落库侧决定"这一列动不动" （含"写成 NULL"）。
 *
 * <p>
 * 声明字段除逐个 {@code map(...)}（可挂修饰符）外，还可<b>同名直写</b>：{@link BeanMapper#map(BeanGetter[])} 按白名单、 {@link BeanMapper#mapAll()}
 * 按"DTO 的非 {@code @BeanUnmapped} 字段"整份配对（要求两侧属性同名、类型一致， 声明期校验）—— 需要修饰符的字段仍用 {@code map(...)} 显式声明，显式声明<b>覆盖</b>同名直写。
 *
 * <p>
 * 字段修饰符的<b>默认规则</b>可由 DTO 注解声明：{@code @BeanClearable}（允许被显式 null 清空， {@code nullable()}
 * 的默认）、{@code @BeanUnmappedBlank}（String 字段空白视为没传，{@code ignoreBlank()} 的默认），二者都可标在 DTO
 * 类（全局默认）或字段（覆盖类级，{@code value = false} 表示豁免）上。 生效优先级：<b>链上显式调用 &gt; 字段注解 &gt; 类注解 &gt; 默认关闭</b>。
 *
 * <p>
 * <b>两侧类型不同</b>的字段（派生 / 组合，如身份集合 ⇒ 身份串）用 {@code map(getter, setter, converter)}： 属性名同样来自取值引用，<b>转换器当场给出</b>（另有
 * {@code BiFunction} 重载可拿到负载做多字段联动）。该形态下 按值判定的修饰符有类型分工（声明期校验）：{@code
 * withDefault} / {@code ignoreValue*} 的实参必须是转换前 （DTO 侧）的类型、{@code ignoreIf*} 一律不可用，{@code
 * required(...)} 的谓词作用于转换后的值。
 *
 * <p>
 * <b>入口级策略</b>用链首的 {@link BeanMapper#excludes(BeanGetter[])} 声明："本入口不管这些字段"（恒不写入、不计入 "有没有输入"、{@code
 * mapAll()} 会跳过它），且同一入口若再声明它们 ⇒ 声明期报错。它与覆盖度断言配套： DTO 每个字段的归宿 = 某个入口链上声明（可写）／{@code @BeanUnmapped}（永不写入）／某入口
 * {@code excludes} （本入口不管），三者都不是即构建期断言失败（写在域的测试里）—— <b>不需要任何手抄清单</b>。 "拒绝"一律以抛 {@code BeanMapAbortException}（4xx）表达，与项目的 4xx
 * 表达一致（框架的异常过滤器渲染成标准响应信封）。
 *
 * <h3>怎么用</h3>
 *
 * <pre>{@code
 * BeanMapResource<User> updated = BeanMapper.of(user, new JavaBeanAccessor(resource))
 *         .map(resource::getEmail, user::setEmail).ignoreBlank()
 *         .map(resource::getNickname, user::setNickname)
 *         .map(resource::getIdentities, User::setIdentity, identityProvider::composeIdentities)
 *         .apply();
 * }</pre>
 * <p>
 * 声明顺序即执行顺序；{@code apply()} 返回 {@link BeanMapResource}；值不满足 {@code required} / {@code notClearable} 时抛 4xx。
 * 子对象块用 {@code sub(...)} 声明，其 DTO 侧属性类型须实现 {@link SourceAccessor}。
 *
 * <h3>包内结构</h3>
 *
 * <ul>
 * <li>{@link cn.labzen.beanmap.BeanMapper} — 入口；
 * <li>{@link cn.labzen.beanmap.NestedPartial} — 子对象块（类型上没有 {@code apply()}）；
 * <li>{@link cn.labzen.beanmap.BeanField} — 字段句柄与字段处理管线；
 * <li>{@link cn.labzen.beanmap.BeanMapResource} — 写入结果（写入了哪些字段、各列要落库的值）；
 * <li>{@link cn.labzen.beanmap.BeanGetter} — 已绑定取值引用（{@code resource::getEmail}），用于解析属性名；
 * <li>{@link cn.labzen.beanmap.BeanSetter} — 未绑定设值引用（{@code User::setEmail}），写入时作用于当前链节点的 domain；
 * <li>{@link cn.labzen.beanmap.BeanMapCoverage} — 覆盖度断言（构建期：由域的测试调用，运行期不做声明校验）；
 * <li>相关的源对象载体见 {@code SourceAccessor#isProvided}，标注见 {@code @BeanUnmapped} / {@code @BeanClearable} /
 * {@code @BeanUnmappedBlank}，异常见 {@code BeanMapException}。
 * </ul>
 */
package cn.labzen.beanmap;
