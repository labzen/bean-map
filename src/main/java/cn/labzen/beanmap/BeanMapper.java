/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import cn.labzen.beanmap.annotation.BeanUnmapped;
import org.springframework.core.convert.ConversionService;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * 三态写入工具：把"本次负载里出现过的字段"写进一个 domain 实例，缺省字段一律不动。
 *
 * <p>
 * 判据只有一条 —— 负载里是否出现过该字段（{@link SourceAccessor#isProvided(String)}）；字段处理管线见 {@link BeanField}（缺省分流 → ignore* → withDefault →
 * trim → 转换 → required → 写入）。
 *
 * <pre>{@code
 * BeanMapResource updated = BeanMapper.of(user, new JavaBeanAccessor(resource))
 *         .map(resource::getEmail, user::setEmail).ignoreBlank()
 *         .map(resource::getNickname, user::setNickname)
 *         .apply();
 * }</pre>
 *
 * @param <P>
 *          domain 类型
 */
@SuppressWarnings("SafeVarargsOnNonReifiableType")
public final class BeanMapper<P> extends PartialChain<P, P> {

  private BeanMapper(PartialState state, P domain, SourceAccessor source) {
    super(state, source, domain, false);
    // 根 domain 记在状态上：写入结果（BeanMapResource#domain）要能交回同一个实例
    state.rootDomain(domain);
    // 根 DTO 记在状态上：覆盖度断言按"根 DTO 的字段"比对（链尾节点可能是子对象块）
    state.rootDto(source);
  }

  /**
   * 开始一次写入：{@code domain} 是待写入的领域对象，{@code source} 是本请求的负载
   */
  public static <P> BeanMapper<P> of(P domain, SourceAccessor source) {
    Objects.requireNonNull(domain, "domain 不能为空");
    Objects.requireNonNull(source, "请求负载不能为空");
    return new BeanMapper<>(new PartialState(), domain, source);
  }

  /**
   * 写入级断言：本链声明的字段里<b>至少有一个</b>在负载里出现过，否则以 {@code 304 NOT_MODIFIED} 中断业务处理（抛 {@code
   * BeanMapAbortException}，由框架渲染成标准响应信封）。
   *
   * <p>
   * 语义固定为"没有改动"：状态码与消息都取 {@code 304 NOT_MODIFIED}（消息 = 状态短语， 与框架自身的无数据响应同形）。它声明在链首（此时还没有任何字段）—— 断言属于<b>整次写入</b>，
   * 不属于任何单个字段；判定发生在 {@link #apply()} 的<b>任何字段之前</b>，因此"拒绝"先于任何副作用。
   *
   * <p>
   * 需要别的状态码时不要用本断言：那说明不是"无改动"场景，应改用链上的 {@code anyProvided()} 自行判定。
   */
  public BeanMapper<P> requireAnyProvided() {
    // AbortCode.NO_CHANGE：语义固定为"没有改动"，消息取状态短语，与框架无数据响应同形
    state.requireAnyProvided();
    return this;
  }

  /**
   * 指定本次写入使用的类型转换器（链首声明，作用于本次映射的全部字段）。
   *
   * <p>
   * 未指定时使用 Spring {@code DefaultConversionService} 的共享实例。要扩展类型转换规则，
   * 向 {@code ConfigurableConversionService} 注册 {@code Converter} 后传入即可。
   *
   * @param conversionService
   *          类型转换器，不能为空
   */
  public BeanMapper<P> withConversionService(ConversionService conversionService) {
    state.conversionService(conversionService);
    return this;
  }

  /**
   * 指定本次写入的中断处理扩展点（链首声明，作用于本次映射的全部中断点）。
   *
   * <p>
   * 默认抛出 {@link BeanMapAbortException}。实现方返回自定义异常，由库在中断点抛出；
   * 返回值为空时回退到默认异常。
   *
   * @param abortHandler
   *          中断处理扩展点，不能为空
   */
  public BeanMapper<P> withAbortHandler(AbortHandler abortHandler) {
    state.abortHandler(abortHandler);
    return this;
  }

  /**
   * 指定本次写入的实例化策略（链首声明，作用于元素级映射创建目标元素）。
   *
   * <p>
   * 默认走无参构造器；record / 不可变类型等无默认构造器的元素目标，由实现方按类型返回实例。
   *
   * @param instantiation
   *          实例化策略，不能为空
   */
  public BeanMapper<P> withInstantiationStrategy(InstantiationStrategy instantiation) {
    state.instantiation(instantiation);
    return this;
  }

  /**
   * 应用一组编程式默认策略（链首声明，作用于本次映射的全部字段）。
   *
   * <p>
   * 配置对象承载类型转换器、中断扩展点与实例化策略；它与各 {@code withXxx(...)} 声明按调用顺序生效，后声明者覆盖先声明者。
   *
   * @param configuration
   *          映射配置，不能为空
   */
  public BeanMapper<P> withConfiguration(BeanMapConfiguration configuration) {
    Objects.requireNonNull(configuration, "映射配置不能为空");
    configuration.applyTo(state);
    return this;
  }

  /**
   * 按声明顺序执行全部字段，返回本次写入的结果（{@link BeanMapResource}）； 客户端的值不满足 {@code required} / {@code
   * notClearable} 时默认抛 {@code BeanMapAbortException}（4xx），中断异常可由 {@link AbortHandler} 按入口替换
   */
  public BeanMapResource<P> apply() {
    return state.apply();
  }

  /**
   * 声明<b>本入口不管</b>的字段（入口级策略）：恒不写入、也不计入"有没有输入"（等价于"负载里没有这个 key"）。
   *
   * <pre>{@code
   * BeanMapper.of(duty, new JavaBeanAccessor(resource))
   *         .requireAnyProvided()
   *         .excludes(resource::getOrganizationId, resource::getJobTitleId) // 仅创建入口可写
   *         .map(resource::getServedAt, UserDuty::setServedAt)
   *         .apply();
   * }</pre>
   * <p>
   * 三点作用：
   *
   * <ol>
   * <li><b>显式化</b>：未在链上声明的字段本来就是"不写"，本声明把"这是有意的"从隐含变成白纸黑字；
   * <li><b>禁令</b>：同一入口若再 {@code map(...)} / {@code map(...)} 声明它 ⇒ 声明期即抛 {@code
   *       BeanMapException}（这是"防止有人把受限字段加进本入口"的<b>唯一有效</b>手段 —— 覆盖度断言按 DTO 取并集，抓不到单入口越权）；
   * <li><b>归宿</b>：覆盖度断言把被排除的字段算作"已做过决定"，故无需再手抄任何清单。
   * </ol>
   * <p>
   * {@code mapAll()} 会跳过被排除的字段（"整份 DTO 除这几个之外都直写"）。标了 {@code @BeanUnmapped} 的字段（永不写入，DTO 级事实）无需在此重复，断言会报错提示。
   *
   * <p>
   * ⚠️ 与 {@code ignore*} 修饰符的区别：那些是<b>运行时按值</b>的跳过（字段仍在声明内，仍计入"有没有输入"）； 本方法是<b>声明期按入口</b>的排除。
   *
   * <p>
   * 只声明在链首（根 DTO 的入口级策略）—— 子对象块的字段由子 DTO 自己的覆盖度断言负责。
   *
   * @param getters
   *          DTO 侧<b>已绑定</b>取值方法引用（{@code resource::getOrganizationId}），只用来取属性名
   */
  @SafeVarargs
  public final BeanMapper<P> excludes(BeanGetter<?>... getters) {
    Objects.requireNonNull(getters, "属性不能为空");
    for (BeanGetter<?> getter : getters) {
      String property = LambdaNames.propertyOf(getter, "取值方法引用");
      // 声明期断言：属性必须存在；不得与已声明的可写字段冲突（两个方向都要挡住）
      DtoReflection.fieldType(source.type(), property);
      if (state.declaredProperties().contains(property)) {
        throw new BeanMapException("字段 {} 已声明为可写，不能再被 excludes(...) 排除", property);
      }
      state.exclude(property);
    }
    return this;
  }

  /**
   * <b>同名直写</b>（白名单式）：逐个给出 domain 属性（setter 方法引用），自动配对 DTO 侧<b>同名</b>属性 —— 一次声明一批"不需要修饰符"的字段，省掉 DTO 侧引用的重复书写。
   *
   * <pre>{@code
   * BeanMapper.of(dictionaryValue, new JavaBeanAccessor(resource))
   *         .map(resource::getTitle, resource::getValue, resource::getRank)
   *         .map(resource::getColor, DictionaryValue::setColor).nullable() // 需要修饰符的仍显式声明
   *         .apply();
   * }</pre>
   *
   * <b>白名单语义</b>：只有列进 {@code map(...)} 的属性才可写（新增 DTO 字段不会自动获得写入能力）； 声明期即校验"DTO 上有该属性 + domain 上有同名可写属性 + 两侧类型一致（允许装箱）"，
   * 不通过抛 {@code BeanMapException}。
   *
   * <p>
   * 需要修饰符（{@code nullable} / {@code notClearable} / 转换 / 跳过）或两侧属性名不一致的字段， 用 {@code map(...)} 显式声明 ——
   * <b>显式声明会覆盖</b>同名的同名直写声明。
   *
   * @param getters
   *          DTO 侧<b>已绑定</b>取值方法引用（{@code resource::getTitle}），只用来取属性名
   */
  @SafeVarargs
  public final BeanMapper<P> map(BeanGetter<?>... getters) {
    Objects.requireNonNull(getters, "属性不能为空");
    for (BeanGetter<?> getter : getters) {
      declareMap(LambdaNames.propertyOf(getter, "取值方法引用"));
    }
    return this;
  }

  /**
   * 同名直写 + 必填（批量）：{@code requires(dto::getCode, dto::getTitle, ...)} 等价于逐个 {@code
   * map(dto::getCode, domain::setCode).required()} —— 属性名从取值方法引用解析，domain 侧 同名属性自动配对（存在性与两侧类型一致在声明期校验）。不满足（字段缺省或值为
   * {@code null}）时以 {@code 400} 中断，提示为默认口径（"字段 x：必须有值"）；需要自定义提示的字段，用 {@code map(...).required(消息)} 显式声明 —— 显式声明会覆盖同名的
   * requires 声明。
   *
   * <p>
   * 典型场景：创建入口的 {@code NOT NULL} 列族 —— 一行声明替代成排的 {@code field + required}。
   *
   * @param getters
   *          DTO 侧<b>已绑定</b>取值方法引用（{@code dto::getCode}），只用来取属性名
   */
  @SafeVarargs
  public final BeanMapper<P> requires(BeanGetter<?>... getters) {
    Objects.requireNonNull(getters, "属性不能为空");
    for (BeanGetter<?> getter : getters) {
      declareMap(LambdaNames.propertyOf(getter, "取值方法引用")).required();
    }
    return this;
  }

  /**
   * 同名直写 + 不可清空（批量）：{@code notClearables(dto::getCode, ...)} 等价于逐个 {@code map(dto::getCode,
   * domain::setCode).notClearable()} —— 编辑入口的 {@code NOT NULL} 列族： 缺省照常跳过，只有客户端<b>显式</b>写 {@code
   * null} 才以 {@code 400} 中断，提示为默认口径 （"字段 x：不允许清空"）。需要自定义提示的字段，用 {@code map(...).notClearable(消息)} 显式声明 ——
   * 显式声明会覆盖同名的批量声明。
   *
   * @param getters
   *          DTO 侧<b>已绑定</b>取值方法引用（{@code dto::getCode}），只用来取属性名
   */
  @SafeVarargs
  public final BeanMapper<P> notClearables(BeanGetter<?>... getters) {
    Objects.requireNonNull(getters, "属性不能为空");
    for (BeanGetter<?> getter : getters) {
      declareMap(LambdaNames.propertyOf(getter, "取值方法引用")).notClearable();
    }
    return this;
  }

  /**
   * <b>同名直写</b>（整份 DTO）：{@code dto} 上除 {@link BeanUnmapped} 外的<b>全部</b>属性都参与配对。
   *
   * <pre>{@code
   * BeanMapper.of(dictionaryValue, new JavaBeanAccessor(resource))
   *         .requireAnyProvided()
   *         .mapAll() // title / value / rank / ...
   *         .map(resource::getColor, DictionaryValue::setColor).nullable() // 需要修饰符的显式声明
   *         .apply();
   * }</pre>
   * <p>
   * ⚠️ 这是<b>黑名单语义</b>：DTO 上新增一个非忽略字段，只要 domain 侧有同名可写属性，它就<b>自动获得写入能力</b> ⇒ 仅适用于"该 DTO
   * 的非忽略字段全都可以写"的入口；像"创建可写、编辑不可写"的字段（例如任职的 {@code organizationId}）必须留在 {@code map(...)} 白名单或 {@code map(...)} 里。
   *
   * <p>
   * 声明期逐个校验：非忽略字段必须在 domain 上有同名可写属性且类型一致（允许装箱），否则抛 {@code BeanMapException} —— 因此它同时充当"覆盖度断言"（不存在"漏声明"的字段）。
   *
   * @see #map(BeanGetter[])
   */
  public BeanMapper<P> mapAll() {
    for (Field field : BeanMapCoverage.fieldsOf(source.type())) {
      if (!field.isAnnotationPresent(BeanUnmapped.class) && !state.isExcluded(field.getName())) {
        declareMap(field.getName());
      }
    }
    return this;
  }
}
