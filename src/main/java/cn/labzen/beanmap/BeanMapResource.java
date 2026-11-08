/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 一次三态写入的<b>结果</b>：把"发生了什么"作为可查询的事实交给业务域（典型用途 = 决定落库哪些列）。
 *
 * <p>
 * 判据是"<b>写入过</b>"（真的写进了 domain），不是"负载里出现过" —— 被 {@code ignore*} 跳过的、缺省未声明的、 以及子对象块未进入的字段，都算未写入。
 *
 * <pre>{@code
 * BeanMapResource<UserDuty> updated = BeanMapper.of(duty, new JavaBeanAccessor(resource))
 *         .requireAnyProvided()
 *         .map(resource::getServedAt, UserDuty::setServedAt)
 *         .notClearable("担任时间不允许清空")
 *         .apply();
 *
 * userDutyService.updateFields(dutyId, updated.written(duty::isPrimary), updated.written(duty::getServedAt));
 * }</pre>
 *
 * <p>
 * ⚠️ {@link #written(BeanGetter)} 用 {@code null} 兼作"<b>不写该列</b>"的信号（与"按 null 判断是否 SET 该列"的落库口径一致） ⇒ "写成 NULL"
 * 必须<b>显式声明</b>：字段上声明 {@code nullable()} 后，{@code written} 才返回 {@code null} 而不抛； 未声明的字段本次要写 NULL 会抛
 * {@code BeanMapException}（防止"以为写了 NULL、实际被静默跳过"）。 落库侧用 {@link #willWrite(BeanGetter)} /
 * {@link #ifWillWrite(BeanGetter, Consumer)} 表达"这一列要不要写"。
 */
public final class BeanMapResource<R> {

  private final R domain;
  private final Set<String> declared;
  private final Map<String, Object> writtenValues;
  private final Set<String> nullableProperties;
  private final Set<String> ambiguous;
  private final Set<String> changedProperties;
  private final boolean anyChanged;

  BeanMapResource(
          R domain,
          Set<String> declared,
          Map<String, Object> writtenValues,
          Set<String> nullableProperties,
          Set<String> ambiguous,
          Set<String> changedProperties,
          boolean anyChanged) {
    this.domain = domain;
    this.declared = declared;
    this.writtenValues = writtenValues;
    this.nullableProperties = nullableProperties;
    this.ambiguous = ambiguous;
    this.changedProperties = changedProperties;
    this.anyChanged = anyChanged;
  }

  /**
   * 本次写入作用的根 domain 实例 —— 就是 {@code BeanMapper.of(domain, …)} 传入的那个实例 （<b>就地写入、不是副本</b>），其内容是"原始数据 + 本次写入"
   */
  public R domain() {
    return domain;
  }

  /**
   * 本次是否写入了至少一个字段（不等于"值真的变了"）
   */
  public boolean anyWritten() {
    return !writtenValues.isEmpty();
  }

  /**
   * 本次是否至少有一个字段写入了<b>与原来不同的值</b>（写入同值 ⇒ 不算改动）
   *
   * <p>
   * 与 {@link #anyWritten()} 的区别：客户端显式把某字段设成与库中相同的值 ⇒ 两者都"写入了"，但没有"改动"。 ⚠️ 原值按 domain 属性名反射读取（写入之前读）；读不到（如计算型
   * setter）时按"已改变"保守处理。
   */
  public boolean anyChanged() {
    return anyChanged;
  }

  /**
   * 本次<b>值真的变了</b>的 domain 属性集合（{@link #changed(BeanGetter)} 的批量形态）—— 审计 / 日志可用
   */
  public Set<String> changedProperties() {
    return Set.copyOf(changedProperties);
  }

  /**
   * 该 domain 属性本次<b>要不要写</b>：写入过（含"写成 NULL"）⇒ {@code true}；未写入、或未在本次声明中 ⇒ {@code false}。
   *
   * <p>
   * 与 {@link #written(BeanGetter)} 的分工：本方法是<b>安全查询</b>（未声明不抛），供落库侧决定"这一列动不动"； 值为 {@code null} 时它仍为 {@code true}（表示"要写成
   * NULL"）。
   *
   * @param domainGetter
   *          domain 侧<b>未绑定</b>的取值方法引用（如 {@code DictionaryValue::getTitle}），只用于定位字段
   * @throws BeanMapException
   *           该属性在本次声明中出现多次（同名属性），无法判定
   */
  public <T> boolean willWrite(BeanGetter<T> domainGetter) {
    Objects.requireNonNull(domainGetter, "取值方法引用不能为空");
    String property = LambdaNames.propertyOf(domainGetter, "取值方法引用");
    if (ambiguous.contains(property)) {
      throw new BeanMapException("属性 {} 在本次写入声明中出现多次（同名属性），无法判定", property);
    }
    return writtenValues.containsKey(property);
  }

  /**
   * 该 domain 属性本次是否写入了<b>与原来不同的值</b>（写入同值 ⇒ {@code false}；未写入 / 未声明 ⇒ {@code false}）。
   *
   * <p>
   * 与 {@link #willWrite(BeanGetter)} 的分工：`willWrite` 只回答"这一列动不动"，本方法回答"值真的变了没" —— 供"某列变了 ⇒ 顺带联动另一列"的场景使用（例：邮箱变了 ⇒ 把
   * {@code isEmailValidated} 重置为 {@code false}）。
   *
   * @param domainGetter
   *          domain 侧<b>未绑定</b>的取值方法引用，只用于定位字段
   * @throws BeanMapException
   *           该属性在本次声明中出现多次（同名属性），无法判定
   */
  public <T> boolean changed(BeanGetter<T> domainGetter) {
    Objects.requireNonNull(domainGetter, "取值方法引用不能为空");
    String property = LambdaNames.propertyOf(domainGetter, "取值方法引用");
    if (ambiguous.contains(property)) {
      throw new BeanMapException("属性 {} 在本次写入声明中出现多次（同名属性），无法判定", property);
    }
    return changedProperties.contains(property);
  }

  /**
   * 要写时把值交给 {@code sink}（值为 {@code null} 表示"写成 NULL"）；未写入或未声明 ⇒ 什么都不做。
   *
   * <p>
   * 落库侧的推荐用法（同时表达"写不写"与"写什么值"，不必自己判 {@code null}）：
   *
   * <pre>{@code
   * updated.ifWillWrite(DictionaryValue::getColor, value -> chain.set(COLUMNS.COLOR, value));
   * }</pre>
   *
   * @throws BeanMapException
   *           本次要写入 {@code null} 而该字段未声明 {@code nullable()}
   */
  public <T> void ifWillWrite(BeanGetter<T> domainGetter, Consumer<T> sink) {
    Objects.requireNonNull(sink, "落库动作不能为空");
    if (willWrite(domainGetter)) {
      sink.accept(written(domainGetter));
    }
  }

  /**
   * 该 domain 属性本次<b>要落库的值</b>：写入过 ⇒ 返回写入的值（已是 {@code trim} / {@code withConverter} 之后的结果）； 未写入 ⇒ {@code null}（调用方跳过该列）。
   *
   * <p>
   * ⚠️ {@code null} 有两种含义，靠 {@link #willWrite(BeanGetter)} 区分：<b>未写入</b>（该列不动）／<b>本次要写入 NULL</b> （该字段需已声明
   * {@code nullable()}）。落库侧推荐用 {@link #ifWillWrite(BeanGetter, Consumer)}，它一次表达 "写不写"与"写什么值"。
   *
   * @param domainGetter
   *          domain 侧<b>未绑定</b>的取值方法引用（如 {@code DictionaryValue::getTitle}），只用于定位字段
   * @throws BeanMapException
   *           该属性不在本次写入声明中；声明中出现多个同名属性；或本次要写入 {@code null} 而该字段未声明 {@code
   *                          nullable()}
   */
  @SuppressWarnings("unchecked")
  public <T> T written(BeanGetter<T> domainGetter) {
    Objects.requireNonNull(domainGetter, "取值方法引用不能为空");
    String property = LambdaNames.propertyOf(domainGetter, "取值方法引用");

    if (!declared.contains(property)) {
      throw new BeanMapException("属性 {} 不在本次写入声明中", property);
    }
    if (ambiguous.contains(property)) {
      throw new BeanMapException("属性 {} 在本次写入声明中出现多次（同名属性），无法判定", property);
    }
    if (!writtenValues.containsKey(property)) {
      return null;
    }
    Object value = writtenValues.get(property);
    if (value == null) {
      if (!nullableProperties.contains(property)) {
        throw new BeanMapException(
                "属性 {} 本次要写入 NULL，但该字段未声明 nullable()：null 兼作\"不写该列\"的信号", property);
      }
      return null;
    }
    return (T) value;
  }
}
