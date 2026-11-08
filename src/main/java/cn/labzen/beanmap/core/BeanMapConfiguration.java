/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.core;

import cn.labzen.beanmap.accessor.*;
import cn.labzen.beanmap.exception.*;
import cn.labzen.beanmap.spi.*;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.Objects;

/**
 * 一次映射的编程式默认策略集合：类型转换器、中断扩展点、实例化策略与多源冲突策略。
 *
 * <p>
 * 由调用方显式构造，经 {@link BeanMapper#withConfiguration(BeanMapConfiguration)} 或
 * {@link BeanComposer#withConfiguration(BeanMapConfiguration)} 传入，作用域为单次映射。
 * 库不读取配置文件，也不维护全局默认状态。
 *
 * <pre>{@code
 * BeanMapConfiguration config = BeanMapConfiguration.builder()
 *         .conversionService(conversion)
 *         .abortHandler((code, message) -> new MyBusinessException(code, message))
 *         .conflict(ConflictPolicy.firstWins())
 *         .build();
 *
 * BeanMapper.of(user, source).withConfiguration(config).mapAll().apply();
 * }</pre>
 *
 * <p>
 * 配置对象可复用在多条链上；链上单独声明的 {@code withConversionService} / {@code withAbortHandler} /
 * {@code withInstantiationStrategy} / {@code conflict} 与 {@code withConfiguration} 按调用顺序生效，后声明者覆盖先声明者。
 */
public final class BeanMapConfiguration {

  private final ConversionService conversionService;
  private final AbortHandler abortHandler;
  private final InstantiationStrategy instantiation;
  private final ConflictPolicy conflictPolicy;

  private BeanMapConfiguration(Builder builder) {
    this.conversionService = builder.conversionService;
    this.abortHandler = builder.abortHandler;
    this.instantiation = builder.instantiation;
    this.conflictPolicy = builder.conflictPolicy;
  }

  /** 构造一个可逐项覆盖默认值的 builder */
  public static Builder builder() {
    return new Builder();
  }

  /** 库内默认策略集合 */
  public static BeanMapConfiguration defaults() {
    return builder().build();
  }

  public ConversionService conversionService() {
    return conversionService;
  }

  public AbortHandler abortHandler() {
    return abortHandler;
  }

  public InstantiationStrategy instantiation() {
    return instantiation;
  }

  public ConflictPolicy conflictPolicy() {
    return conflictPolicy;
  }

  /** 把配置应用到一次写入 / 合并的作用域 */
  void applyTo(PartialState state) {
    state.conversionService(conversionService);
    state.abortHandler(abortHandler);
    state.instantiation(instantiation);
  }

  /** 默认策略：Spring 共享转换器、抛 {@link BeanMapAbortException}、无参构造器、后写覆盖 */
  public static final class Builder {

    private ConversionService conversionService = DefaultConversionService.getSharedInstance();
    private AbortHandler abortHandler = AbortHandler.THROWING;
    private InstantiationStrategy instantiation = InstantiationStrategy.DEFAULT;
    private ConflictPolicy conflictPolicy = ConflictPolicy.LAST_WINS;

    private Builder() {
    }

    public Builder conversionService(ConversionService conversionService) {
      this.conversionService = Objects.requireNonNull(conversionService, "类型转换器不能为空");
      return this;
    }

    public Builder abortHandler(AbortHandler abortHandler) {
      this.abortHandler = Objects.requireNonNull(abortHandler, "中断处理扩展点不能为空");
      return this;
    }

    public Builder instantiation(InstantiationStrategy instantiation) {
      this.instantiation = Objects.requireNonNull(instantiation, "实例化策略不能为空");
      return this;
    }

    public Builder conflict(ConflictPolicy conflictPolicy) {
      this.conflictPolicy = Objects.requireNonNull(conflictPolicy, "冲突策略不能为空");
      return this;
    }

    public BeanMapConfiguration build() {
      return new BeanMapConfiguration(this);
    }
  }
}
