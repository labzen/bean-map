/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import java.io.Serializable;
import java.util.function.Supplier;

/**
 * 取值方法引用（已绑定实例）：如 {@code resource::getEmail} / {@code domain::getProfile}。
 *
 * <p>
 * 只用来取属性名（编译期校验拼写）与取值；写入侧用 {@link BeanSetter}。DTO 侧与 domain 侧统一为同一种形态（都从已绑定实例取值）。
 */
@FunctionalInterface
public interface BeanGetter<T> extends Supplier<T>, Serializable {
}
