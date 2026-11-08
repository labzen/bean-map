/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.annotation;

/**
 * 映射形态，由 {@link BeanOp} 在 {@link BeanMap} 接口的方法上显式声明。
 *
 * <p>
 * 源与目标类型由方法签名推断：{@code void} 形态的目标取带 {@link BeanTarget} 的参数或第二个参数，其余形态的目标取返回类型。
 *
 * @since 2026-11-08
 */
public enum MappingType {

  /** 由 DTO 新建 domain（两态全量） */
  TO_DOMAIN,

  /** 由 DTO 更新已有 domain（三态） */
  UPDATE_DOMAIN,

  /** DTO 拷贝（两态全量，同名直读） */
  COPY_DTO,

  /** 由 domain 装配响应 DTO（两态全量） */
  TO_DTO,

  /** 由 domain 装配 Chain 视图（两态全量） */
  TO_CHAIN
}
