/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap.core;

import cn.labzen.beanmap.accessor.*;
import cn.labzen.beanmap.exception.*;
import cn.labzen.beanmap.spi.*;

import cn.labzen.beanmap.annotation.BeanUnmapped;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 覆盖度断言（<b>构建期</b>）：比对「请求 DTO 的字段」与「各入口链上声明的可写字段 ∪ {@link BeanUnmapped} 排除的字段 ∪ 各入口 {@code
 * excludes(...)} 的字段」。
 *
 * <p>
 * 它是"<b>声明是否写齐</b>"的校验 —— 属代码问题、不属运行期，故由域的<b>测试</b>显式调用（不挂 {@code @PostConstruct}、 不做成配置开关）：运行期只有 {@code map(...)} /
 * {@code excludes(...)} 的<b>声明期</b>断言。
 *
 * <p>
 * 另外校验一条 <b>DTO 形态约定</b>（与"声明"无关，先于覆盖度判定）：请求 DTO <b>一律用包装类型</b> （primitive 表达不了显式 {@code null} ⇒ 会静默误写）。
 *
 * <p>
 * ⚠️ "集合 / 数组字段不参与三态写入"<b>不由类型判定</b>（JSON 列里的集合值是合法的，类型判不出"关系表整体替换"）—— 它由 <b>{@code @BeanUnmapped}</b> + 本断言保证：关系型集合（如
 * {@code duties} / {@code roles}）标忽略， 谁把它们加进链，本断言即报"链上声明 ∩ 忽略集 ≠ ∅"。
 *
 * <p>
 * DTO 字段的三个归宿（各归其位，不再手抄清单）：
 *
 * <ol>
 * <li><b>某个入口的链上声明了</b> ⇒ 可写 —— 真相源是<b>链</b>（{@code map(...)} / {@code map(getter...)} / {@code
 *       mapAll()}）， 既用于执行、也用于本断言，不可能漂移；
 * <li><b>标了 {@link BeanUnmapped}</b> ⇒ 永不接受负载写入 —— 真相源是<b>字段上的注解</b>；
 * <li><b>某个入口 {@code excludes(...)} 了</b> ⇒ 该入口显式"不管它"（入口级策略的显式记录）；
 * <li>三者都不是 ⇒ <b>报错</b>（新增字段后没人做过决定，正是本断言要抓的）。
 * </ol>
 * <p>
 * "某字段在本入口不可写、在别的入口可写"= 在<b>别的入口的链上声明</b> + 在<b>本入口 {@code excludes}</b> ⇒ 不需要任何手写字段清单，而且"有人把它加进本入口"会在声明期被
 * {@code excludes} 挡下。
 *
 * <p>
 * 没有归宿只是"该字段不可写"（保守，不会误写），但漏声明往往源于拼写错误或新增字段（后果是运行期静默不写： 前端提交了、值没保存），越早暴露越好。断言不通过即测试失败。
 */
public final class BeanMapCoverage {

  private BeanMapCoverage() {
  }

  /**
   * @param writes
   *          该 DTO 全部入口的写入声明（链的<b>任一节点</b>皆可：链首或链尾字段句柄；同一 DTO 可传多条链，按 DTO 取并集）
   */
  @SuppressWarnings("SafeVarargsOnNonReifiableType")
  @SafeVarargs
  public static void verify(PartialChain<?, ?>... writes) {
    if (writes == null || writes.length == 0) {
      throw new BeanMapException("覆盖度断言至少需要一条写入声明");
    }

    Class<?> dtoType = null;
    Set<String> declared = new LinkedHashSet<>();
    Set<String> excluded = new LinkedHashSet<>();
    for (PartialChain<?, ?> write : writes) {
      Class<?> type = write.state.dtoType();
      if (type == null) {
        throw new BeanMapException("覆盖度断言的写入声明没有绑定请求 DTO");
      }
      if (dtoType == null) {
        dtoType = type;
      } else if (!dtoType.equals(type)) {
        throw new BeanMapException(
                "覆盖度断言只针对同一个 DTO，却同时收到 {} 与 {}：请按 DTO 分别调用",
                dtoType.getSimpleName(),
                type.getSimpleName());
      }
      declared.addAll(write.state.declaredProperties());
      excluded.addAll(write.state.excludedProperties());
    }

    List<Field> fields = fieldsOf(dtoType);
    Set<String> names = new LinkedHashSet<>();
    Set<String> ignored = new LinkedHashSet<>();
    Set<String> primitives = new LinkedHashSet<>();
    for (Field field : fields) {
      names.add(field.getName());
      if (field.isAnnotationPresent(BeanUnmapped.class)) {
        ignored.add(field.getName());
      }
      if (field.getType().isPrimitive()) {
        primitives.add(field.getName());
      }
    }

    // DTO 形态检查（与"声明"无关，先于覆盖度判定）：
    // ① primitive 无法表达"显式 null"（JSON 里会被 Jackson 换成类型默认值，而"出现过"仍被记录）⇒ 静默误写
    if (!primitives.isEmpty()) {
      throw new BeanMapException(
              "{} 的字段用了 primitive 类型：{} —— JSON 无法表达显式 null（会被换成类型默认值 ⇒ 静默误写），"
                      + "请求 DTO 一律用包装类型（Boolean / Integer / Long …）",
              dtoType.getSimpleName(),
              primitives);
    }
    Set<String> uncovered = new LinkedHashSet<>(names);
    uncovered.removeAll(declared);
    uncovered.removeAll(ignored);
    uncovered.removeAll(excluded);
    if (!uncovered.isEmpty()) {
      throw new BeanMapException(
              "{} 的字段既未在任何入口的链上声明写入、也未标 @BeanUnmapped、也未被任何入口 excludes(...)：{}",
              dtoType.getSimpleName(),
              uncovered);
    }

    Set<String> excludedIgnores = new LinkedHashSet<>(excluded);
    excludedIgnores.retainAll(ignored);
    if (!excludedIgnores.isEmpty()) {
      throw new BeanMapException(
              "{} 的字段已标 @BeanUnmapped（永不写入）⇒ 无需再 excludes(...)：{}",
              dtoType.getSimpleName(),
              excludedIgnores);
    }

    Set<String> writableIgnores = new LinkedHashSet<>(declared);
    writableIgnores.retainAll(ignored);
    if (!writableIgnores.isEmpty()) {
      throw new BeanMapException(
              "{} 的字段不能既在链上声明写入、又被 @BeanUnmapped 排除：{}", dtoType.getSimpleName(), writableIgnores);
    }
  }

  /**
   * DTO 的可写字段：自身到 {@code Object}（不含）之间的全部实例字段
   *
   * <p>
   * 若传入的是 cn.labzen.web 分页代理（{@code PageableDelegator} 在运行期生成的子类），其自身声明的 {@code _paging} / {@code _target} 等框架字段不属业务
   * DTO，须从真实用户 DTO 类（代理的父类）开始枚举， 否则 mapAll 会把这些框架字段当业务字段去配对 domain 的 setter 而抛 BeanMapException。
   */
  static List<Field> fieldsOf(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = stripPageableProxy(type);
    while (current != null && !Object.class.equals(current)) {
      for (Field field : current.getDeclaredFields()) {
        if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
          fields.add(field);
        }
      }
      current = current.getSuperclass();
    }
    return fields;
  }

  /**
   * 剥去运行期分页代理的子类包装，返回其父类（真实用户 DTO 类）。 判定依据：代理类自身声明了 {@code _target} 字段（指向被代理的原始 Bean，见 cn.labzen.web 的
   * PageableDelegator）。
   */
  private static Class<?> stripPageableProxy(Class<?> type) {
    Class<?> current = type;
    while (current != null && isPageableProxy(current)) {
      current = current.getSuperclass();
    }
    return current;
  }

  private static boolean isPageableProxy(Class<?> type) {
    if (type == null || type == Object.class) {
      return false;
    }
    for (Field field : type.getDeclaredFields()) {
      if ("_target".equals(field.getName())) {
        return true;
      }
    }
    return false;
  }
}
