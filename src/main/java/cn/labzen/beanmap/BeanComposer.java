/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import cn.labzen.beanmap.annotation.BeanUnmapped;
import org.springframework.core.convert.ConversionService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多源合并工具：把多个 {@link SourceAccessor} 产出的同名属性聚合进一个目标对象。
 *
 * <p>
 * 每个 {@code from(...)} 源先产出局部结果，不直接写目标；{@link #apply()} 汇总各源产出的值，按 {@link ConflictPolicy}
 * 裁决同名字段后统一写入。目标上没有被任何源产出的属性保持原值。
 *
 * <pre>{@code
 * UserView view = BeanComposer.compose(new UserView())
 *         .from(new JavaBeanAccessor(user))
 *         .from(new JavaBeanAccessor(profile))
 *         .from(new MapAccessor(ExtraDto.class, extra), "remark")
 *         .excludes("internalNote")
 *         .conflict(lastWins())
 *         .apply();
 * }</pre>
 *
 * <p>
 * 候选属性取「源类型的字段 ∩ 目标类型的字段」；{@code from(source, properties...)} 可按白名单指定该源只产出哪些属性。
 * 三态源（如 {@link MapAccessor}）只产出 {@link SourceAccessor#isProvided(String)} 为 {@code true} 的属性，
 * 未出现的属性不参与合并，也不参与冲突判定。
 *
 * <p>
 * 写入按 setter 参数类型做隐式转换，转换器与中断扩展点由本次合并作用域提供，与 {@link BeanMapper} 的链级注入一致。
 *
 * @param <T>
 *          目标对象类型
 */
public final class BeanComposer<T> {

  private final T target;
  private final PartialState state = new PartialState();
  private final List<SourceSpec> sources = new ArrayList<>();
  private final Set<String> excludes = new LinkedHashSet<>();
  private ConflictPolicy policy = ConflictPolicy.LAST_WINS;

  private BeanComposer(T target) {
    this.target = Objects.requireNonNull(target, "目标对象不能为空");
  }

  /**
   * 开始一次多源合并：{@code target} 是待装配的目标对象，{@link #apply()} 就地写入并返回同一个实例
   */
  public static <T> BeanComposer<T> compose(T target) {
    return new BeanComposer<>(target);
  }

  /**
   * 追加一个源：该源产出「源类型字段 ∩ 目标类型字段」中所有出现过的属性
   */
  public BeanComposer<T> from(SourceAccessor source) {
    Objects.requireNonNull(source, "源访问器不能为空");
    sources.add(new SourceSpec(source, null));
    return this;
  }

  /**
   * 追加一个源，并限定它只产出给定的目标属性；属性不存在于目标时声明期即抛 {@link BeanMapException}
   */
  public BeanComposer<T> from(SourceAccessor source, String... properties) {
    Objects.requireNonNull(source, "源访问器不能为空");
    Objects.requireNonNull(properties, "属性不能为空");
    Set<String> selected = new LinkedHashSet<>(Arrays.asList(properties));
    for (String property : selected) {
      Objects.requireNonNull(property, "属性名不能为空");
    }
    sources.add(new SourceSpec(source, selected));
    return this;
  }

  /**
   * 禁止任何源写入这些目标属性
   */
  public BeanComposer<T> excludes(String... properties) {
    Objects.requireNonNull(properties, "属性不能为空");
    for (String property : properties) {
      excludes.add(Objects.requireNonNull(property, "属性名不能为空"));
    }
    return this;
  }

  /**
   * 指定同名字段的冲突裁决策略，默认 {@link ConflictPolicy#LAST_WINS}
   */
  public BeanComposer<T> conflict(ConflictPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "冲突策略不能为空");
    return this;
  }

  /**
   * 指定本次合并使用的类型转换器，默认 Spring {@code DefaultConversionService} 共享实例
   */
  public BeanComposer<T> withConversionService(ConversionService conversionService) {
    state.conversionService(conversionService);
    return this;
  }

  /**
   * 指定本次合并的中断处理扩展点，默认抛 {@link BeanMapAbortException}
   */
  public BeanComposer<T> withAbortHandler(AbortHandler abortHandler) {
    state.abortHandler(abortHandler);
    return this;
  }

  /**
   * 汇总各源产出的值、裁决冲突并写入目标，返回同一个目标实例
   */
  public T apply() {
    Map<String, List<Write>> writes = new LinkedHashMap<>();
    for (Field field : BeanMapCoverage.fieldsOf(target.getClass())) {
      if (!field.isAnnotationPresent(BeanUnmapped.class)) {
        writes.put(field.getName(), new ArrayList<>());
      }
    }

    for (int index = 0; index < sources.size(); index++) {
      SourceSpec spec = sources.get(index);
      for (String property : candidatesOf(spec, writes.keySet())) {
        if (!spec.source.isProvided(property)) {
          continue;
        }
        writes.get(property).add(new Write(index, spec.source.getValue(property)));
      }
    }

    for (Map.Entry<String, List<Write>> entry : writes.entrySet()) {
      List<Write> candidates = entry.getValue();
      if (!candidates.isEmpty()) {
        write(entry.getKey(), choose(entry.getKey(), candidates));
      }
    }
    return target;
  }

  /**
   * 该源本次参与合并的属性：白名单形态按声明顺序，全量形态取源类型与目标类型的字段交集
   */
  private Set<String> candidatesOf(SourceSpec spec, Set<String> targetFields) {
    if (spec.selected != null) {
      for (String property : spec.selected) {
        if (!targetFields.contains(property)) {
          throw new BeanMapException("{} 上不存在可写属性：{}", target.getClass().getSimpleName(), property);
        }
        if (excludes.contains(property)) {
          throw new BeanMapException("属性 {} 已被 excludes(...) 排除，不能再由 from(...) 指定", property);
        }
      }
      return spec.selected;
    }

    Set<String> candidates = new LinkedHashSet<>();
    Class<?> sourceType = spec.source.type();
    if (sourceType == null) {
      candidates.addAll(targetFields);
    } else {
      for (Field field : BeanMapCoverage.fieldsOf(sourceType)) {
        if (targetFields.contains(field.getName())) {
          candidates.add(field.getName());
        }
      }
    }
    candidates.removeAll(excludes);
    return candidates;
  }

  private Object choose(String property, List<Write> candidates) {
    if (candidates.size() == 1) {
      return candidates.get(0).value;
    }
    return switch (policy) {
      case FIRST_WINS -> candidates.get(0).value;
      case LAST_WINS -> candidates.get(candidates.size() - 1).value;
      case THROW_ON_CONFLICT -> throw conflict(property, candidates);
    };
  }

  private RuntimeException conflict(String property, List<Write> candidates) {
    String origins = candidates.stream()
            .map(write -> "#" + (write.sourceIndex + 1))
            .collect(Collectors.joining("、"));
    return state.abort(AbortCode.FIELD_CONFLICT, "字段 " + property + " 被多处源写入：" + origins);
  }

  private void write(String property, Object value) {
    Method setter = PartialChain.setterOf(target.getClass(), property);
    Class<?> declaredType = setter.getParameterTypes()[0];
    Object converted = convert(property, value, PartialChain.box(declaredType));
    if (converted == null && declaredType.isPrimitive()) {
      throw state.abort(AbortCode.TYPE_MISMATCH, "字段 " + property + "：不接受 null（该字段为基本类型）");
    }
    PartialChain.invokeSetter(target, property, converted);
  }

  private Object convert(String property, Object value, Class<?> targetType) {
    if (value == null || targetType.isInstance(value)) {
      return value;
    }
    ConversionService conversion = state.conversionService();
    if (conversion.canConvert(value.getClass(), targetType)) {
      return conversion.convert(value, targetType);
    }
    throw state.abort(
            AbortCode.TYPE_MISMATCH,
            "字段 " + property + "：值类型无法写入该字段：" + value.getClass().getSimpleName());
  }

  /**
   * 一次 {@code from(...)} 声明：源访问器 + 可选的属性白名单（为空表示取字段交集全量）
   */
  private static final class SourceSpec {

    private final SourceAccessor source;
    private final Set<String> selected;

    private SourceSpec(SourceAccessor source, Set<String> selected) {
      this.source = source;
      this.selected = selected;
    }
  }

  /**
   * 某源对某属性的一次写入，记下源序号供冲突裁决与提示使用
   */
  private static final class Write {

    private final int sourceIndex;
    private final Object value;

    private Write(int sourceIndex, Object value) {
      this.sourceIndex = sourceIndex;
      this.value = value;
    }
  }
}
