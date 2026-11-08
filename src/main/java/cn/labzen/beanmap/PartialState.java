/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.util.*;

/**
 * 一次写入的作用域：参与写入的全部字段声明 + 写入级断言 + 写入结果
 */
final class PartialState {

  private final List<BeanField<?, ?, ?>> fields = new ArrayList<>();
  private final Set<String> nested = new LinkedHashSet<>();
  private final Set<String> excluded = new LinkedHashSet<>();

  private Object rootDomain;
  private SourceAccessor rootDto;
  private boolean requireAnyProvided;
  private int requireAnyProvidedCode;
  private String requireAnyProvidedMessage;

  void declare(BeanField<?, ?, ?> field) {
    fields.add(field);
  }

  /**
   * 声明"<b>本入口不管</b>"的字段（{@code BeanMapper#excludes}）：恒不写入、也不计入"有没有输入" （未在链上声明的字段本来就是这个行为，本声明把它变成<b>显式决定</b>，并禁止再声明为可写）
   */
  void exclude(String property) {
    excluded.add(property);
  }

  /**
   * 该字段是否已被本入口显式排除
   */
  boolean isExcluded(String property) {
    return excluded.contains(property);
  }

  /**
   * 本链显式排除的字段（覆盖度断言把它算作"已做过决定"）
   */
  Set<String> excludedProperties() {
    return Set.copyOf(excluded);
  }

  /**
   * 记下根 domain：写入结果（{@code BeanMapResource#domain()}）要交回同一个实例
   */
  void rootDomain(Object domain) {
    this.rootDomain = domain;
  }

  /**
   * 记下根 DTO：覆盖度断言按"根 DTO 的字段"比对（子对象块的字段属子 DTO，不由本级断言负责）
   */
  void rootDto(SourceAccessor source) {
    this.rootDto = source;
  }

  /**
   * 本链所属的根 DTO 类型（覆盖度断言的比对基准）
   */
  Class<?> dtoType() {
    return rootDto == null ? null : rootDto.type();
  }

  /**
   * 声明子对象块：父级字段名也算"参与写入的声明"（声明先于"该块本次是否被处理"的判定 —— 覆盖度断言不依赖负载）
   */
  void declareNested(SourceAccessor source, String property) {
    if (source == rootDto) {
      nested.add(property);
    }
  }

  /**
   * 本链声明的 <b>DTO 侧</b>属性名（根节点的有效字段 + 子对象块的字段名）—— 覆盖度断言的输入
   */
  Set<String> declaredProperties() {
    Set<String> declared = new LinkedHashSet<>(nested);
    for (BeanField<?, ?, ?> field : effectiveFields()) {
      if (field.source == rootDto) {
        declared.add(field.dtoProperty());
      }
    }
    return declared;
  }

  /**
   * 声明写入级断言：声明的字段里至少有一个在负载里出现过，否则以给定状态码中断业务处理
   */
  void requireAnyProvided(int code, String message) {
    this.requireAnyProvided = true;
    this.requireAnyProvidedCode = code;
    this.requireAnyProvidedMessage = message;
  }

  /**
   * 本链声明的字段里，是否有任一在负载里出现过（与"是否写入"无关：被 {@code ignore*} 跳过的也算出现过）
   */
  boolean anyProvided() {
    return effectiveFields().stream().anyMatch(BeanField::isProvided);
  }

  /**
   * 本次真正生效的字段：<b>显式声明覆盖同名直写</b>（同名直写声明出来的字段，若其 domain 属性又被显式 {@code map(...)} 声明过，则只保留显式那一份）
   */
  private List<BeanField<?, ?, ?>> effectiveFields() {
    Set<String> explicit = new LinkedHashSet<>();
    for (BeanField<?, ?, ?> field : fields) {
      if (!field.isAuto()) {
        explicit.add(field.domainProperty());
      }
    }

    List<BeanField<?, ?, ?>> effective = new ArrayList<>();
    for (BeanField<?, ?, ?> field : fields) {
      if (!field.isAuto() || !explicit.contains(field.domainProperty())) {
        effective.add(field);
      }
    }
    return effective;
  }

  /**
   * 按声明顺序执行全部字段，并给出本次写入的结果
   *
   * <p>
   * 写入级断言在<b>任何字段之前</b>判定 —— 保证"拒绝"发生在任何副作用之前。
   *
   * @param <R>
   *          根 domain 类型（与调用方链节点上的 R 一致）
   */
  @SuppressWarnings("unchecked")
  <R> BeanMapResource<R> apply() {
    if (requireAnyProvided && !anyProvided()) {
      throw new BeanMapAbortException(requireAnyProvidedCode, requireAnyProvidedMessage);
    }
    for (BeanField<?, ?, ?> field : effectiveFields()) {
      field.write();
    }
    return resource((R) rootDomain);
  }

  /**
   * 汇总写入结果：声明过的 domain 属性 + 实际写入的字段 + 是否有真正的值改动 （同名属性另记，查询时拒绝）
   */
  private <R> BeanMapResource<R> resource(R domain) {
    Set<String> declared = new LinkedHashSet<>();
    Map<String, Object> writtenValues = new LinkedHashMap<>();
    Set<String> nullableProperties = new LinkedHashSet<>();
    Set<String> ambiguous = new LinkedHashSet<>();
    Set<String> changedProperties = new LinkedHashSet<>();
    boolean anyChanged = false;

    for (BeanField<?, ?, ?> field : effectiveFields()) {
      String property = field.domainProperty();
      if (!declared.add(property)) {
        ambiguous.add(property);
        continue;
      }
      if (field.isNullable()) {
        nullableProperties.add(property);
      }
      if (field.isWritten()) {
        writtenValues.put(property, field.writtenValue());
        boolean changed = field.isChanged();
        if (changed) {
          changedProperties.add(property);
        }
        anyChanged |= changed;
      }
    }
    return new BeanMapResource<>(
            domain,
            declared,
            writtenValues,
            nullableProperties,
            ambiguous,
            changedProperties,
            anyChanged);
  }
}
