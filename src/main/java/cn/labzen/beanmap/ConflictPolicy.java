/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

/**
 * 多源合并时同一目标属性被多处源写入的裁决策略。
 *
 * <p>
 * 由 {@link BeanComposer#conflict(ConflictPolicy)} 按一次合并指定，默认 {@link #LAST_WINS}。
 * 静态工厂供 {@code import static} 后在链上直接书写策略名。
 */
public enum ConflictPolicy {

  /** 保留最先声明的源产出的值 */
  FIRST_WINS,
  /** 保留最后声明的源产出的值 */
  LAST_WINS,
  /** 同名字段被多处源写入时以 {@link AbortCode#FIELD_CONFLICT} 中断 */
  THROW_ON_CONFLICT;

  /** 保留最先声明的源产出的值 */
  public static ConflictPolicy firstWins() {
    return FIRST_WINS;
  }

  /** 保留最后声明的源产出的值 */
  public static ConflictPolicy lastWins() {
    return LAST_WINS;
  }

  /** 同名字段被多处源写入时中断 */
  public static ConflictPolicy throwOnConflict() {
    return THROW_ON_CONFLICT;
  }
}
