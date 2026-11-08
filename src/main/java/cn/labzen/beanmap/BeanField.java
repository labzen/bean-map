/*
 * Copyright (c) 2021-2026 Labzen. All rights reserved.
 */
package cn.labzen.beanmap;

import cn.labzen.beanmap.annotation.BeanClearable;
import cn.labzen.beanmap.annotation.BeanUnmappedBlank;
import org.springframework.core.convert.ConversionService;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 字段句柄：所有修饰符都作用于最近一次 {@code map(...)} 声明的字段（由类型系统保证不会有"悬挂的修饰符"）。
 *
 * <p>
 * 处理管线：① 缺省分流（声明了 {@code required} 的字段不适用"缺省即跳过"）→ ② {@code ignore*} 判定 → ③ {@code
 * withDefault}（出现且值为 null ⇒ 换默认值）／{@code notClearable}（出现且值为 null ⇒ 中断，二者互斥） → ④ {@code
 * trim}（转换之前）→ ⑤ {@code withConverter}（未声明时按 setter 参数类型转换） → ⑥ {@code required} 判定 → ⑦ 写入。
 *
 * <p>
 * 修饰符一律作用于<b>负载原值</b>：{@code null} 不参与判定（既不 equals 也不进区间），仍按"出现 ⇒ 写入"处理。
 *
 * <p>
 * <b>类型分工</b>（{@code map(getter, setter, converter)} 这类两侧类型不同的字段尤其要注意）：按 <b>值</b> 判定的 修饰符（{@code withDefault} /
 * {@code ignoreValue*} / {@code ignoreIf*}）作用于<b>转换前</b>的原值，故实参必须是 <b>DTO 侧</b>类型 —— 声明期即校验（{@code ignoreIf*}
 * 的谓词形参无法比对类型，两侧不同时直接拒绝）； {@code required(...)} 的谓词作用于<b>转换后</b>的值，按写入侧类型书写。
 */
public final class BeanField<P, R, T> extends PartialChain<P, R> {

  private final String path;
  private final ValueWriter writer;
  private final String domainProperty;
  private final Class<?> sourceType;
  private final Class<?> targetType;
  private final boolean auto;
  private final boolean crossTyped;

  private final List<IgnoreRule> ignores = new ArrayList<>();
  private final boolean annotatedNullable;
  private final boolean annotatedBlank;
  private boolean trim;
  private boolean hasDefault;
  private Object defaultValue;
  private boolean notClearable;
  private String notClearableMessage;
  private boolean nullable;
  private Converter converter;
  private Function<Object, Object> elementConverter;
  private boolean ignoreNullElement;
  private boolean required;
  private RequiredRule requiredRule;
  private String requiredMessage;

  private boolean written;
  private Object writtenValue;
  private boolean changed;

  BeanField(
          PartialState state,
          SourceAccessor source,
          P domain,
          boolean gated,
          String path,
          ValueWriter writer,
          Serializable reference,
          boolean crossTyped) {
    this(
            state,
            source,
            domain,
            gated,
            path,
            writer,
            LambdaNames.propertyName(reference, "setter 方法引用", path),
            LambdaNames.parameterTypeOf(reference, "setter 方法引用").orElse(null),
            false,
            crossTyped);
  }

  /**
   * 同名直写（{@code map(getter...)} / {@code mapAll()}）创建的字段：domain 属性名与目标类型取自解析结果， 不来自方法引用（两侧类型相同由 {@code
   * declareMap} 在声明期保证）
   */
  BeanField(
          PartialState state,
          SourceAccessor source,
          P domain,
          boolean gated,
          String path,
          ValueWriter writer,
          String domainProperty,
          Class<?> targetType,
          boolean auto) {
    this(state, source, domain, gated, path, writer, domainProperty, targetType, auto, false);
  }

  /**
   * 主构造器
   *
   * @param crossTyped
   *          本字段两侧类型<b>不同</b>（由 {@code map(getter, setter, converter)} 声明）：按 <b>值</b> 判定的修饰符据此做声明期守卫（见
   *          {@link #requireRawValue} / {@link #requireSameTypeForPredicate}）—— 不能靠比对 {@code sourceType} /
   *          {@code targetType} 推得，因为后者的方法引用解析并不总是成功
   */
  private BeanField(
          PartialState state,
          SourceAccessor source,
          P domain,
          boolean gated,
          String path,
          ValueWriter writer,
          String domainProperty,
          Class<?> targetType,
          boolean auto,
          boolean crossTyped) {
    super(state, source, domain, gated);
    this.path = path;
    this.writer = writer;
    this.domainProperty = domainProperty;
    this.sourceType = source == null ? null : DtoReflection.fieldType(source.type(), path);
    this.targetType = targetType;
    this.auto = auto;
    this.crossTyped = crossTyped;
    this.annotatedNullable = resolveAnnotatedNullable(source, path);
    this.annotatedBlank = resolveAnnotatedBlank(source, path, this.sourceType);
  }

  // ===================================================================================================================
  // 修饰符
  // ===================================================================================================================

  @SuppressWarnings("unchecked")
  private static int compare(Object current, Object expected) {
    return ((Comparable<Object>) current).compareTo(expected);
  }

  /**
   * 解析 {@code @BeanClearable} 注解默认（字段级覆盖类级；类上 {@code value = false} 与未标注同义）
   */
  private static boolean resolveAnnotatedNullable(SourceAccessor source, String path) {
    if (source == null) {
      return false;
    }
    BeanClearable onField = DtoReflection.fieldOf(source.type(), path).getAnnotation(BeanClearable.class);
    if (onField != null) {
      return onField.value();
    }
    BeanClearable onType = source.type().getAnnotation(BeanClearable.class);
    return onType != null && onType.value();
  }

  /**
   * 解析 {@code @BeanUnmappedBlank} 注解默认（字段级覆盖类级；类级只作用于 String 字段， 字段级标注在非 String 字段上即声明期报错）
   */
  private static boolean resolveAnnotatedBlank(SourceAccessor source, String path, Class<?> sourceType) {
    if (source == null) {
      return false;
    }
    Field field = DtoReflection.fieldOf(source.type(), path);
    BeanUnmappedBlank onField = field.getAnnotation(BeanUnmappedBlank.class);
    if (onField != null) {
      if (sourceType != null && !String.class.equals(sourceType)) {
        throw new BeanMapException(
                "@BeanUnmappedBlank 只适用于 String 字段，字段 {} 的 DTO 类型是 {}",
                path,
                sourceType.getSimpleName());
      }
      return onField.value();
    }
    BeanUnmappedBlank onType = source.type().getAnnotation(BeanUnmappedBlank.class);
    return onType != null && onType.value() && String.class.equals(sourceType);
  }

  /**
   * 值等于给定值 ⇒ 与"字段缺省"相同（跳过）
   */
  public BeanField<P, R, T> ignoreValue(T value) {
    requireRawValue(value, "ignoreValue");
    ignores.add((raw, ignored) -> raw != null && raw.equals(value));
    return this;
  }

  /**
   * 值严格小于给定值 ⇒ 跳过
   */
  public BeanField<P, R, T> ignoreLessThan(T value) {
    requireComparable("ignoreLessThan");
    requireRawValue(value, "ignoreLessThan");
    ignores.add((raw, ignored) -> raw != null && value != null && compare(raw, value) < 0);
    return this;
  }

  /**
   * 值严格大于给定值 ⇒ 跳过
   */
  public BeanField<P, R, T> ignoreGreaterThan(T value) {
    requireComparable("ignoreGreaterThan");
    requireRawValue(value, "ignoreGreaterThan");
    ignores.add((raw, ignored) -> raw != null && value != null && compare(raw, value) > 0);
    return this;
  }

  /**
   * 值落在闭区间 {@code [min, max]} 内 ⇒ 跳过
   */
  public BeanField<P, R, T> ignoreBetween(T min, T max) {
    requireComparable("ignoreBetween");
    requireRawValue(min, "ignoreBetween");
    requireRawValue(max, "ignoreBetween");
    ignores.add(
            (raw, ignored) -> raw != null
                    && min != null
                    && max != null
                    && compare(raw, min) >= 0
                    && compare(raw, max) <= 0);
    return this;
  }

  /**
   * 早于给定值 ⇒ 跳过（{@code ignoreLessThan} 的日期语义别名）
   */
  public BeanField<P, R, T> ignoreBefore(T value) {
    return ignoreLessThan(value);
  }

  /**
   * 晚于给定值 ⇒ 跳过（{@code ignoreGreaterThan} 的日期语义别名）
   */
  public BeanField<P, R, T> ignoreAfter(T value) {
    return ignoreGreaterThan(value);
  }

  /**
   * 谓词为真 ⇒ 跳过
   */
  @SuppressWarnings("unchecked")
  public BeanField<P, R, T> ignoreIf(Predicate<T> predicate) {
    requireSameTypeForPredicate("ignoreIf");
    ignores.add((raw, ignored) -> raw != null && predicate.test((T) raw));
    return this;
  }

  /**
   * 谓词为真 ⇒ 跳过；谓词额外拿到本节点的 DTO 实例
   */
  @SuppressWarnings("unchecked")
  public BeanField<P, R, T> ignoreIf(BiPredicate<T, SourceAccessor> predicate) {
    requireSameTypeForPredicate("ignoreIf");
    ignores.add((raw, current) -> raw != null && predicate.test((T) raw, (SourceAccessor) current));
    return this;
  }

  /**
   * 值为空白串 ⇒ 跳过（仅 String 字段）
   *
   * <p>
   * DTO 类 / 字段上的 {@code @BeanUnmappedBlank} 是本修饰符的默认声明（链上显式调用优先于注解）。
   */
  public BeanField<P, R, T> ignoreBlank() {
    requireString("ignoreBlank");
    ignores.add((raw, ignored) -> raw instanceof String text && text.isBlank());
    return this;
  }

  /**
   * 值为空串 ⇒ 跳过（仅 String 字段）
   */
  public BeanField<P, R, T> ignoreEmpty() {
    requireString("ignoreEmpty");
    ignores.add((raw, ignored) -> raw instanceof String text && text.isEmpty());
    return this;
  }

  /**
   * 写入前 trim（只改写入的值，不改判定；在 {@code withConverter} 之前执行，仅 String 字段）
   */
  public BeanField<P, R, T> trim() {
    requireString("trim");
    this.trim = true;
    return this;
  }

  /**
   * 值转换：转换为写入 setter 的值
   */
  @SuppressWarnings("unchecked")
  public <S> BeanField<P, R, T> withConverter(Function<S, T> converter) {
    this.converter = (raw, ignored) -> converter.apply((S) raw);
    return this;
  }

  /**
   * 值转换：转换器额外拿到本节点的 DTO 实例（多字段联动）
   */
  @SuppressWarnings("unchecked")
  public <S> BeanField<P, R, T> withConverter(BiFunction<S, SourceAccessor, T> converter) {
    this.converter = (raw, current) -> converter.apply((S) raw, (SourceAccessor) current);
    return this;
  }

  /**
   * 出现且值为 {@code null} ⇒ 写默认值；字段缺省仍跳过（与 {@code required} 互斥）
   */
  public BeanField<P, R, T> withDefault(T value) {
    requireRawValue(value, "withDefault");
    if (required) {
      throw new BeanMapException("字段 {} 不能同时声明 withDefault 与 required", path);
    }
    if (notClearable) {
      throw new BeanMapException("字段 {} 不能同时声明 withDefault 与 notClearable", path);
    }
    this.hasDefault = true;
    this.defaultValue = value;
    return this;
  }

  /**
   * 出现且值为 {@code null} ⇒ 中断业务处理（4xx）：用于 {@code NOT NULL} 之类的"<b>可选、但不可清空</b>"字段。
   *
   * <p>
   * 与 {@code required} 的区别：{@code required} 是"<b>必填</b>"（缺省、显式 null 都报错），本修饰符是
   * "可选、但不可清空"（<b>缺省仍按契约跳过</b>，只有客户端<b>显式</b>写了 {@code null} 才拒绝）。
   *
   * <p>
   * "拒绝"默认以抛 {@code BeanMapAbortException}（4xx）表达，与 {@code required} 同一套机制 —— 项目里 4xx 的
   * 统一表达就是该异常（框架的异常过滤器会把它渲染成标准响应信封），业务域无需再手写 {@code if + return}；
   * 中断异常可由 {@link AbortHandler} 按入口替换。
   *
   * <p>
   * 与 {@code withDefault} 互斥（一个把显式 null 换成默认值，一个拒绝显式 null）。
   */
  public BeanField<P, R, T> notClearable() {
    return notClearable(null);
  }

  /**
   * 出现且值为 {@code null} ⇒ 中断业务处理（4xx），并指定不满足时的提示
   *
   * @see #notClearable()
   */
  public BeanField<P, R, T> notClearable(String message) {
    if (hasDefault) {
      throw new BeanMapException("字段 {} 不能同时声明 withDefault 与 notClearable", path);
    }
    if (nullable) {
      throw new BeanMapException("字段 {} 不能同时声明 nullable 与 notClearable", path);
    }
    this.notClearable = true;
    this.notClearableMessage = message;
    return this;
  }

  /**
   * 声明本字段<b>允许被显式 null 清空</b>（"写成 NULL"是契约允许的一种写入）。
   *
   * <p>
   * 默认（不声明）视为<b>不可清空</b>：本次要把该列写成 {@code null} 时，{@code BeanMapResource#written} 会抛 {@code
   * BeanMapException} —— 理由是 {@code null} 兼作落库侧"不写该列"的信号，若不管就会"以为写了 NULL、 实际被静默跳过"。声明后该值合法：取值得到 {@code null}，落库侧用
   * {@code BeanMapResource#willWrite} / {@code #ifWillWrite} 表达"这一列要写 NULL"。
   *
   * <p>
   * DTO 类 / 字段上的 {@code @BeanClearable} 是本修饰符的默认声明；与本修饰符相反方向的默认由 {@code notClearable()} 表达（链上显式调用优先于注解）。
   *
   * <p>
   * 与 {@code notClearable} 互斥。
   */
  public BeanField<P, R, T> nullable() {
    if (notClearable) {
      throw new BeanMapException("字段 {} 不能同时声明 nullable 与 notClearable", path);
    }
    this.nullable = true;
    return this;
  }

  /**
   * 必须有值：等价于 {@code required(v -> v != null)}
   */
  public BeanField<P, R, T> required() {
    return declareRequired(null, null);
  }

  /**
   * 必须有值，并指定不满足时的提示
   */
  public BeanField<P, R, T> required(String message) {
    return declareRequired(null, message);
  }

  /**
   * 值必须符合条件（值为 {@code null} 时直接判为不满足，不求值谓词）
   */
  public BeanField<P, R, T> required(Predicate<T> predicate) {
    return required(predicate, null);
  }

  /**
   * 值必须符合条件，并指定不满足时的提示
   */
  @SuppressWarnings("unchecked")
  public BeanField<P, R, T> required(Predicate<T> predicate, String message) {
    return declareRequired(
            predicate == null ? null : (value, ignored) -> predicate.test((T) value), message);
  }

  /**
   * 值必须符合条件；谓词额外拿到本节点的 DTO 实例
   */
  public BeanField<P, R, T> required(BiPredicate<T, SourceAccessor> predicate) {
    return required(predicate, null);
  }

  /**
   * 值必须符合条件，并指定不满足时的提示；谓词额外拿到本节点的 DTO 实例
   */
  @SuppressWarnings("unchecked")
  public BeanField<P, R, T> required(BiPredicate<T, SourceAccessor> predicate, String message) {
    return declareRequired(
            predicate == null ? null : (value, current) -> predicate.test((T) value, (SourceAccessor) current),
            message);
  }

  /**
   * 按声明顺序执行全部字段，返回本次写入的结果
   */
  public BeanMapResource<R> apply() {
    return state.apply();
  }

  /**
   * 本字段在负载里是否出现过（{@link PartialState#anyProvided()} 的判据；未进入的子对象块恒为 false）
   */
  boolean isProvided() {
    return !gated && source != null && source.isProvided(path);
  }

  /**
   * 本字段对应的 domain 属性名（供 {@link BeanMapResource#written} 匹配）。
   *
   * <p>
   * 取自此字段声明时传入的 setter 引用：未绑定引用（{@code Domain::setXxx}）直接解析出 {@code xxx}；已绑定引用（{@code domain::setXxx}）回退到声明侧属性名，
   * 故要求两侧属性同名。两侧属性名不同时必须用未绑定引用声明。
   */
  String domainProperty() {
    return domainProperty;
  }

  /**
   * 本字段本次是否真的写进了 domain
   */
  boolean isWritten() {
    return written;
  }

  /**
   * 本字段本次写入的值（未写入时为 {@code null}）
   */
  Object writtenValue() {
    return writtenValue;
  }

  /**
   * 本字段本次是否真的写了一样的值（原值与写入值不同）
   */
  boolean isChanged() {
    return changed;
  }

  /**
   * 本字段是否允许被显式 null 清空（链上显式 {@code nullable()}、DTO 注解默认，二者其一为 true 且 未显式声明 {@code notClearable()}）
   */
  boolean isNullable() {
    return !notClearable && (nullable || annotatedNullable);
  }

  /**
   * 本字段是否来自"同名直写"（{@code map(getter...)} / {@code mapAll()}）—— 显式声明会覆盖它
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  boolean isAuto() {
    return auto;
  }

  /**
   * 本字段在 <b>DTO</b> 侧的属性名（覆盖度断言按它比对；domain 侧属性名见 {@link #domainProperty()}， 两侧可不同：{@code
   * map(dto::getEmailAddr, User::setEmail)}）
   */
  String dtoProperty() {
    return path;
  }

  /**
   * 本字段的处理管线
   */
  void write() {
    if (gated) {
      return;
    }

    boolean provided = source.isProvided(path);
    if (!provided && !required) {
      return;
    }

    Object raw = provided ? source.getValue(path) : null;
    if (isIgnored(raw)) {
      return;
    }
    if (notClearable && provided && raw == null) {
      throw requestError(AbortCode.NOT_CLEARABLE, notClearableMessage == null ? "不允许清空" : notClearableMessage);
    }

    Object value = raw;
    if (value == null && hasDefault) {
      value = defaultValue;
    }
    if (trim && value instanceof String text) {
      value = text.trim();
    }

    Object converted = value == null ? null : convert(value);
    if (required && !satisfied(converted)) {
      throw requestError(AbortCode.REQUIRED, requiredMessage == null ? "必须有值" : requiredMessage);
    }
    if (converted == null && targetType != null && targetType.isPrimitive()) {
      throw requestError(AbortCode.TYPE_MISMATCH, "不接受 null（该字段为基本类型）");
    }

    // 原值在写入之前读：读不到（domain 上无同名属性，如计算型 setter）按"已改变"保守处理
    boolean changed = true;
    try {
      changed = !Objects.equals(DtoReflection.value(domain, domainProperty), converted);
    } catch (BeanMapException ignored) {
      // 保守视为已改变
    }

    try {
      writer.write(domain, converted);
    } catch (NullPointerException e) {
      throw requestError(AbortCode.TYPE_MISMATCH, "不接受 null（该字段为基本类型）");
    }
    written = true;
    writtenValue = converted;
    this.changed = changed;
  }

  private BeanField<P, R, T> declareRequired(RequiredRule rule, String message) {
    if (hasDefault) {
      throw new BeanMapException("字段 {} 不能同时声明 withDefault 与 required", path);
    }
    this.required = true;
    this.requiredRule = rule;
    this.requiredMessage = message;
    return this;
  }

  private boolean isIgnored(Object raw) {
    if (annotatedBlank && raw instanceof String text && text.isBlank()) {
      return true;
    }
    return ignores.stream().anyMatch(rule -> rule.ignore(raw, source));
  }

  private boolean satisfied(Object value) {
    if (value == null) {
      return false;
    }
    return requiredRule == null || requiredRule.satisfied(value, source);
  }

  /**
   * 逐元素转换集合 / 数组 / Map 的值：Iterable 逐元素转换（Set 保持容器语义）、Map 转换 value 并保留 key、数组按 List 语义返回。
   */
  public BeanField<P, R, T> element(Function<Object, Object> converter) {
    this.elementConverter = Objects.requireNonNull(converter, "元素转换器不能为空");
    return this;
  }

  /**
   * 逐元素映射：优先走链上 {@code ConversionService}，无法转换时按实例化策略创建目标实例并做同名直读。
   */
  public BeanField<P, R, T> element(Class<?> sourceType, Class<?> targetType) {
    Objects.requireNonNull(sourceType, "元素源类型不能为空");
    Objects.requireNonNull(targetType, "元素目标类型不能为空");
    return element(value -> {
      if (!sourceType.isInstance(value)) {
        throw new BeanMapException("元素类型不匹配：期望 {}，实际 {}",
                sourceType.getSimpleName(), value.getClass().getSimpleName());
      }
      ConversionService conversion = state.conversionService();
      if (conversion.canConvert(value.getClass(), targetType)) {
        return conversion.convert(value, targetType);
      }
      Object target = state.instantiation().create(targetType);
      BeanReader.read(value, target).mapAll().apply();
      return target;
    });
  }

  /**
   * 跳过容器中的 null 元素；未声明时保留 null 元素。空容器原样返回。
   */
  public BeanField<P, R, T> ignoreNullElement() {
    this.ignoreNullElement = true;
    return this;
  }

  private Object convert(Object value) {
    if (elementConverter != null) {
      return convertElements(value);
    }
    if (converter != null) {
      return converter.convert(value, source);
    }

    Class<?> target = targetType == null ? null : (targetType.isPrimitive() ? box(targetType) : targetType);

    if (target == null || target.isInstance(value)) {
      return value;
    }
    ConversionService conversion = state.conversionService();
    if (conversion.canConvert(value.getClass(), target)) {
      return conversion.convert(value, target);
    }
    throw requestError(AbortCode.TYPE_MISMATCH, "值类型无法写入该字段：" + value.getClass().getSimpleName());
  }

  private Object convertElements(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object item = entry.getValue();
        if (item == null) {
          if (!ignoreNullElement) {
            result.put(entry.getKey(), null);
          }
          continue;
        }
        result.put(entry.getKey(), elementConverter.apply(item));
      }
      return result;
    }
    if (value instanceof Iterable<?> iterable) {
      List<Object> result = new ArrayList<>();
      for (Object item : iterable) {
        if (item == null && ignoreNullElement) {
          continue;
        }
        result.add(item == null ? null : elementConverter.apply(item));
      }
      return value instanceof Set<?> ? new LinkedHashSet<>(result) : result;
    }
    if (value.getClass().isArray()) {
      List<Object> result = new ArrayList<>();
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        Object item = Array.get(value, i);
        if (item == null && ignoreNullElement) {
          continue;
        }
        result.add(item == null ? null : elementConverter.apply(item));
      }
      return result;
    }
    throw new BeanMapException("element(...) 要求字段 {} 的值为 Collection / Map / 数组", path);
  }

  private RuntimeException requestError(AbortCode code, String detail) {
    return state.abort(code, "字段 " + path + "：" + detail);
  }

  /**
   * 声明期校验：按值判定的修饰符作用于<b>转换前</b>的原值 ⇒ 实参必须是本字段在 <b>DTO 侧</b>的类型。
   *
   * <p>
   * 它在"两侧类型不同"（{@code map(getter, setter, converter)}）的字段上真正起作用：那里的形参按<b>写入侧</b>
   * 类型标注，传写入侧的值会在这里被挡住，而不是等到运行期才发现错配（{@code null} 不参与判定，故跳过）。
   */
  private void requireRawValue(Object value, String modifier) {
    if (value == null || sourceType == null || box(sourceType).isInstance(value)) {
      return;
    }
    throw new BeanMapException(
            "{} 的实参必须是字段 {} 在 DTO 侧（转换前）的类型 {}，而传入的是 {}：" + "两侧类型不同的字段请把该判定放进转换器或入口逻辑",
            modifier,
            path,
            sourceType.getSimpleName(),
            value.getClass().getSimpleName());
  }

  /**
   * 声明期校验：谓词的<b>形参</b>按写入侧类型标注，而判定作用于转换前的原值 —— 形参类型无法在声明期比对， 故"两侧类型不同"（{@code map(getter, setter,
   * converter)}）时直接拒绝，避免"谓词看着对、 实际作用在另一个类型的值上"。
   */
  private void requireSameTypeForPredicate(String modifier) {
    if (!crossTyped) {
      return;
    }
    throw new BeanMapException(
            "{} 的谓词按写入侧类型书写，却作用于转换前的值（字段 {}）：两侧类型不同的字段" + "请把该判定放进转换器或入口逻辑", modifier, path);
  }

  private void requireComparable(String modifier) {
    if (sourceType != null && !Comparable.class.isAssignableFrom(sourceType)) {
      throw new BeanMapException(
              "{} 只适用于可比较类型，字段 {} 的 DTO 类型是 {}", modifier, path, sourceType.getSimpleName());
    }
  }

  private void requireString(String modifier) {
    if (sourceType != null && !String.class.equals(sourceType)) {
      throw new BeanMapException(
              "{} 只适用于 String 字段，字段 {} 的 DTO 类型是 {}", modifier, path, sourceType.getSimpleName());
    }
  }

  /**
   * 跳过判定（作用于负载原值）
   */
  @FunctionalInterface
  private interface IgnoreRule {

    boolean ignore(Object raw, SourceAccessor source);
  }

  /**
   * 值转换
   */
  @FunctionalInterface
  private interface Converter {

    Object convert(Object raw, SourceAccessor source);
  }

  /**
   * 必填判定（值为 null 时不会被调用）
   */
  @FunctionalInterface
  private interface RequiredRule {

    boolean satisfied(Object value, SourceAccessor source);
  }
}
