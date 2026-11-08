![LOGO](http://r7jiu5wkl.hd-bkt.clouddn.com/images/2022/02/19/16-34-57-167.png)

# Labzen BeanMap

![Labzen BeanMap](https://img.shields.io/badge/Labzen-BeanMap-green)
![Maven Central](https://img.shields.io/maven-central/v/cn.labzen/bean-map)
![GitHub](https://img.shields.io/github/license/labzen/bean-map)
![JDK](https://img.shields.io/badge/JDK-21-blue)

通用对象映射基础库：三态部分更新、两态全量读写、多源合并、覆盖度断言、入口级字段封禁。字段声明基于方法引用，属性名在编译期校验，避免字符串拼写错误导致的静默漏写。

## Installation

Maven（JDK 21+）：

```xml
<dependency>
  <groupId>cn.labzen</groupId>
  <artifactId>bean-map</artifactId>
  <version>1.1.1</version>
</dependency>
```

POM 直接依赖 `cn.labzen:spring`、`cn.labzen:tool`、`cn.labzen:logger`，源码实际使用 `spring-core` 的 `ConversionService` 做隐式类型转换。APT 生成类的注册方式由 `-Abeanmap.registry` 决定：默认 `spring` 模式生成带 `@Component` 的类（消费方需提供 `spring-context`），`spi` 模式生成不带注册注解的普通 `public` 类。

## 三态语义

写方向（部分更新）对每个目标字段区分三种情况：

| 态 | 负载 | 目标字段 |
|---|---|---|
| 未出现 | 没有该 key | 保持原值，不写 |
| 出现且有值 | 传了具体值 | 写入该值 |
| 出现且显式 null | 传了 `null` | 由 `nullable()` / `notClearable()` 决定 |

判据只有一条：该字段在本次负载里有没有出现，由 `SourceAccessor#isProvided(String)` 提供。

```java
package cn.labzen.beanmap;

public interface SourceAccessor {
  boolean isProvided(String path);  // 是否出现
  Object getValue(String path);     // 按路径读值
  Class<?> type();                  // 源类型，供字段注解解析与反射使用
}
```

内置实现：

| 访问器 | 源 | presence 判定 |
|---|---|---|
| `JavaBeanAccessor` | JavaBean / POJO / record | 两态，`isProvided` 恒返回 `true` |
| `MapAccessor` | `Map<String, Object>` 负载 | 三态，按 key 是否存在判定 |

`JavaBeanAccessor` 对普通对象走字段反射、对 record 走组件 accessor；构造时校验源形状，Map、数组、标量等不具备可解析形状的对象在声明期即抛 `BeanMapException` 并给出可读提示，不静默退化。record 组件 accessor 用于写方向的 `DtoReflection` 取值；读方向 `BeanReader.mapAll()` 按 public getter 查找，见「边界与约束」。

`MapAccessor` 的第一个参数是**描述字段的 DTO 类型**（供字段声明、注解规则与覆盖度断言解析），第二个参数是本次负载；点号路径按 Map 层级逐段下钻：

```java
Map<String, Object> payload = Map.of("name", "Ada", "profile", Map.of("userNo", "u1"));
SourceAccessor source = new MapAccessor(UserDto.class, payload);

BeanMapper.of(user, source)
        .requireAnyProvided()
        .mapAll()
        .apply();
```

要接入其它源形态（如 JSON 节点），自行实现 `SourceAccessor`。

读方向（`BeanReader`）无条件全量拷贝，不适用三态判据。

## Quick Start

示例中的 `source` 为 `SourceAccessor`；以普通 DTO 作为负载、按两态全量写入时传 `new JavaBeanAccessor(dto)`。

### 写方向：更新已有 domain

```java
BeanMapResource<User> result = BeanMapper.of(user, source)
        .requireAnyProvided()
        .excludes(request::getTenantId)
        .mapAll()
        .apply();
```

`BeanMapper.of(domain, source)` 的第一个参数是**待写入**的领域对象，第二个是**负载**（`SourceAccessor`）。`apply()` 就地写入 `domain`（不是副本）并返回 `BeanMapResource`。`requireAnyProvided()` 在写入任何字段之前判定，一个可改字段都没出现时以 `304` 中断。

带修饰符的完整声明：

```java
BeanMapResource<User> result = BeanMapper.of(user, source)
        .requireAnyProvided()
        .excludes(request::getTenantId, request::getCreatedAt)
        .requires(request::getName)
        .map(request::getEmail, User::setEmail).ignoreBlank().notClearable("邮箱不允许清空")
        .map(request::getNickname, User::setNickname).nullable()
        .map(request::getAge, User::setAge).withDefault(0)
        .apply();
```

声明顺序即执行顺序；修饰符作用于最近一次 `map(...)` 声明的字段。属性名取自 DTO 侧取值方法引用，domain 侧 setter 可用已绑定引用（`user::setEmail`）或未绑定引用（`User::setEmail`）。

### 读方向：domain → 响应 DTO

```java
UserDto dto = BeanReader.read(user, new UserDto())
        .map("nickname", User::getName, UserDto::setNickname)
        .mapAll()
        .apply();
```

`BeanReader.read(source, target)` 的第一个参数是**值的来源**，第二个是**待装配目标**，与 `BeanMapper.of` 的参数顺序相反。`map(...)` 声明重命名 / 转换 / 忽略，`mapAll()` 补齐其余同名属性。

## 核心 API

| 类 / 接口 | 作用 |
|---|---|
| `BeanMapper<P>` | 写方向入口，`of(domain, source)` 开启一次三态写入 |
| `PartialChain<P, R>` | `BeanMapper` / `BeanField` / `NestedPartial` 的公共基类，承载字段与子对象声明 |
| `BeanField<P, R, T>` | 字段句柄与处理管线，所有修饰符挂在这里 |
| `NestedPartial<Q, R>` | 子对象块，类型上没有 `apply()`，防止在块内提前结束整次写入 |
| `BeanMapResource<R>` | 一次写入的结果（写入了哪些字段、各列要落库的值） |
| `BeanReader<S, T>` | 读方向装配：同名直读 + 显式映射 |
| `BeanComposer<T>` | 多源合并：聚合多个源到同一目标，按冲突策略裁决同名字段 |
| `ConflictPolicy` | 多源合并的冲突裁决策略 |
| `BeanMapConfiguration` | 编程式默认策略集合，经 `withConfiguration(...)` 按入口传入 |
| `SourceAccessor` / `JavaBeanAccessor` / `MapAccessor` | 源对象访问抽象与 JavaBean、Map 两种内置实现 |
| `DtoReflection`、`LambdaNames` | 属性路径反射读取、方法引用解析属性名 |
| `BeanMapCoverage` | 覆盖度断言（由测试调用） |
| `BeanMapException` / `BeanMapAbortException` | 声明期错误 / 策略中断 |
| `AbortCode` | 策略中断语义码，携带等价整型状态码与默认提示 |
| `AbortHandler` | 中断处理扩展点，决定一次中断要抛出的异常 |
| `BeanMapProcessor` | APT 处理器，生成 `*BeanMapImpl` |

## 字段声明与修饰符

| 声明 | 语义 |
|---|---|
| `map(getter, setter)` | 显式声明一个字段，属性名由取值方法引用解析 |
| `map(getter, setter, converter)` | 两侧类型不同，转换器必填；另有 `BiFunction<S, SourceAccessor, T>` 重载可拿到负载做多字段联动 |
| `map(String, setter)` | 字符串属性名形态，用于点号路径等无法绑定取值引用的场合 |
| `map(getter)` | 同名直写且需要修饰符，domain 侧同名 setter 自动配对 |
| `map(getter...)` | 批量白名单同名直写，只有列出的属性可写 |
| `mapAll()` | 整份 DTO 除 `@BeanUnmapped` 与 `excludes(...)` 外全部同名直写（**黑名单语义**，新增 DTO 字段会自动获得写入能力） |
| `requires(getter...)` / `notClearables(getter...)` | 批量同名直写 + 必填 / 不可清空 |
| `excludes(getter...)` | 入口级封禁：恒不写入、不计入「有没有输入」，`mapAll()` 跳过；同一入口再声明这些字段即报错 |
| `sub(dtoGetter, domainGetter[, factory])` | 声明嵌套子对象块；子对象源值必须实现 `SourceAccessor` |
| `anyProvided()` | 本链声明的字段里是否有任一在负载中出现 |
| `requireAnyProvided()` | 写入级断言，无任何输入时以 `304` 中断 |
| `withConversionService(cs)` | 链首声明，指定本次写入使用的 `ConversionService`；默认 Spring 共享实例 |
| `withAbortHandler(handler)` | 链首声明，指定本次写入的中断处理扩展点；默认抛 `BeanMapAbortException` |
| `withInstantiationStrategy(s)` | 链首声明，指定元素级映射的实例化策略 |
| `withConfiguration(config)` | 链首声明，一次应用类型转换器、中断扩展点、实例化策略与冲突策略 |
| `element(...)` / `ignoreNullElement()` | 字段级声明，逐元素转换容器值并控制 null 元素 |
| `apply()` | 按声明顺序执行，返回 `BeanMapResource<R>` |

字段修饰符：

| 类别 | 方法 |
|---|---|
| 按值跳过 | `ignoreValue`、`ignoreLessThan`、`ignoreGreaterThan`、`ignoreBetween`、`ignoreBefore`、`ignoreAfter`、`ignoreIf`、`ignoreBlank`、`ignoreEmpty` |
| 值处理 | `trim`、`withConverter`、`withDefault` |
| 约束 | `required`、`notClearable`、`nullable` |

处理管线：缺省分流 → `ignore*` → `withDefault` / `notClearable` → `trim` → 转换 → `required` → 写入。`withDefault` 与 `notClearable` 互斥；按值判定的修饰符作用于转换前的 DTO 侧原值，`required(...)` 的谓词作用于转换后的值。显式 `null` 不经过 `withConverter` 与 `element(...)`，按 `nullable()` / `notClearable()` 的规则落为 null。字段声明了 `required` 且命中 `ignore*` 时跳过写入并以 `REQUIRED`（400）中断。

DTO 注解可作为修饰符的默认值，生效优先级为：**链上显式调用 > 字段注解 > 类注解 > 默认关闭**。

## 类型转换

字段两侧类型不同时，若未用 `withConverter(...)` 显式给出转换器，库按 setter 参数类型做隐式转换。转换器由本次写入作用域提供，默认使用 Spring `DefaultConversionService` 的共享实例。

替换或扩展转换规则时，在链首传入自定义的 `ConversionService`：

```java
GenericConversionService conversion = new GenericConversionService();
DefaultConversionService.addDefaultConverters(conversion);
conversion.addConverter(String.class, Locale.class, Locale::forLanguageTag);

BeanMapper.of(user, source)
        .withConversionService(conversion)
        .mapAll()
        .apply();
```

`withConversionService(...)` 只作用于本次映射，不同入口可使用不同的转换规则。扩展规则走 Spring 原生的 `Converter` 注册机制，库不引入自有 SPI 接口，也不增加依赖。

## 集合元素级映射

`element(...)` 让集合 / 数组 / Map 按元素转换，而不是把整个容器交给字段级 converter：

```java
BeanMapper.of(domain, source)
        .map(dto::getNames, Domain::setNames).element(v -> "[" + v + "]").ignoreNullElement()
        .apply();
```

| 声明 | 作用 |
|---|---|
| `element(Function<Object, Object>)` | 逐元素转换；Iterable / Set 保持容器语义，Map 转换 value 并保留 key，数组按 List 语义返回（目标 setter 需能接收 `List`） |
| `element(Class<S>, Class<T>)` | 逐元素映射：优先走链上 `ConversionService`，无法转换时按 `InstantiationStrategy` 创建目标实例并做同名直读 |
| `ignoreNullElement()` | 跳过容器中的 null 元素；未声明时保留 |
| `withInstantiationStrategy(...)` | 链首注入实例化策略，默认反射调用无参构造器 |

两侧元素类型不同时，`map(getter, setter)` 的类型约束要求两侧一致，需改用字符串属性名重载：

```java
.map("items", Domain::setItems).element(Src.class, Dst.class)
```

`InstantiationStrategy` 的默认实现走无参构造器，record / 不可变目标由调用方按入口注入自定义实现。库不提供元素映射器注册表，转换规则由链上声明或 `ConversionService` 决定。

## 多源合并

`BeanComposer` 把多个源产出的同名属性聚合进一个目标对象。每个 `from(...)` 源先产出局部结果，`apply()` 汇总后按冲突策略裁决同名字段，再统一写入；目标上没有被任何源产出的属性保持原值。

```java
import static cn.labzen.beanmap.ConflictPolicy.lastWins;

UserView view = BeanComposer.compose(new UserView())
        .from(new JavaBeanAccessor(user))
        .from(new JavaBeanAccessor(profile))
        .from(new MapAccessor(ExtraDto.class, extra), "remark")
        .excludes("internalNote")
        .conflict(lastWins())
        .apply();
```

| 声明 | 作用 |
|---|---|
| `compose(target)` | 开启一次合并，`apply()` 就地写入并返回同一个目标实例 |
| `from(source)` | 该源产出「源类型字段 ∩ 目标类型字段」中所有出现过的属性 |
| `from(source, properties...)` | 限定该源只产出给定的目标属性；属性不存在或与 `excludes(...)` 冲突时，在 `apply()` 汇总阶段抛 `BeanMapException` |
| `excludes(properties...)` | 禁止任何源写入这些目标属性 |
| `conflict(policy)` | 同名字段的冲突裁决策略，默认 `lastWins()` |
| `withConversionService(cs)` / `withAbortHandler(h)` | 本次合并的类型转换器与中断扩展点 |
| `apply()` | 汇总、裁决、写入 |

| 策略 | 行为 |
|---|---|
| `lastWins()` | 保留最后声明的源产出的值（默认） |
| `firstWins()` | 保留最先声明的源产出的值 |
| `throwOnConflict()` | 以 `FIELD_CONFLICT`（409）中断，消息列出参与写入的源序号 |

JavaBean / POJO 源按两态处理（字段视为全部出现）；`MapAccessor` 等三态源只产出 `isProvided(...)` 为 `true` 的属性，未出现的属性不参与合并，也不参与冲突判定。写入按 setter 参数类型做隐式转换，无法转换时以 `TYPE_MISMATCH` 中断；目标字段标 `@BeanUnmapped` 时不参与合并。

多源合并只裁决字段整体值，不做子字段级跨源合并，也不做聚合覆盖度断言；覆盖度仍由 `BeanMapCoverage.verify(...)` 在测试期校验。

## 写入结果

| 方法 | 作用 |
|---|---|
| `domain()` | 本次写入作用的根 domain 实例（就地写入） |
| `anyWritten()` / `anyChanged()` | 是否写入了字段 / 是否写入了与原值不同的值 |
| `changedProperties()` / `changed(getter)` | 值真的变了的 domain 属性，供审计与联动 |
| `willWrite(getter)` | 该列本次要不要写（含「写成 NULL」） |
| `written(getter)` | 该列本次要落库的值（已过 `trim` / `withConverter`） |
| `ifWillWrite(getter, sink)` | 落库侧推荐用法，一次表达「写不写」与「写什么值」 |

`written(...)` 用 `null` 兼作「不写该列」的信号，因此要把某列写成 `NULL` 必须在该字段上声明 `nullable()`，否则抛 `BeanMapException`。落库侧统一用 `ifWillWrite(...)` 判空。

## 注解

| 注解 | 位置 | 作用 |
|---|---|---|
| `@BeanUnmapped` | DTO 字段 | 永不参与写入，覆盖度断言中作为「已做过决定」的归宿 |
| `@BeanClearable` | DTO 类 / 字段 | 允许被显式 `null` 清空，即 `nullable()` 的默认规则；`value = false` 可从类级默认中豁免 |
| `@BeanUnmappedBlank` | DTO 类 / 字段 | String 字段空白串视为没传，即 `ignoreBlank()` 的默认规则 |
| `@BeanMap` | 接口 | 标记映射接口，由 APT 生成实现 |
| `@BeanTarget` | 方法参数 | 在 `updateDomain` 中标出被写入的领域对象参数 |
| `@BeanMapField` / `@BeanMapFields` | 方法 | 读方向单字段映射：`source` / `target` / `ignore` / `converter` |

## APT 生成

```java
@BeanMap
public interface UserBeanMap {
  User toDomain(UserDto dto);
  void updateDomain(UserDto dto, @BeanTarget User domain);
  void copyDto(UserDto source, UserDto target);
  UserDto toDto(User domain);
}
```

编译期在同包生成 `UserBeanMapImpl`（默认 `spring` 模式带 `@Component`），方法体委托给编程式 API：

| 方法形态 | 识别方式 | 生成逻辑 |
|---|---|---|
| `toDomain(Dto)` | `toDomain` 前缀 + 单参 + 有返回值 | `new` 出 domain 后 `BeanMapper.of(...).mapAll().apply()` |
| `updateDomain(..., @BeanTarget Domain)` | 双参、返回 `void`、其中一个参数带 `@BeanTarget` | 对传入 domain 就地 `mapAll()`；参数顺序按接口声明保持 |
| `copyDto(Src, Tgt)` | 方法名 `copyDto` + 双参 + `void` | `BeanReader.read(src, tgt).mapAll().apply()`，第二参数类型按接口声明 |
| 读方向（单参 + 有返回值） | 兜底分支：不匹配 `toDomain` 的单参有返回值方法，不区分 `toDto` / `toChain` | `BeanReader.read(...)`，按 `@BeanMapField` 声明显式映射后 `mapAll()` |

`static` / `default` 方法不参与生成。处理器不读取 domain / DTO 的字段，只解析映射接口的方法签名，因此不依赖 Lombok 的字段生成顺序。未识别的方法形态生成抛 `UnsupportedOperationException` 的方法体；处理器内部生成失败以编译期 `ERROR` 诊断报告。

处理器经 `META-INF/services/javax.annotation.processing.Processor` 注册。本库构建时通过 `<proc>none</proc>` 关闭注解处理（处理器类自身尚未生成），下游以 jar 依赖本库时正常触发代码生成。

生成类的注册方式由编译器选项 `-Abeanmap.registry` 控制：

| 取值 | 生成结果 | 适用场景 |
|---|---|---|
| `spring`（默认，含未传） | 生成类带 `@Component` | Spring 项目，由组件扫描注册 |
| `spi` | 生成不带注册注解的普通 `public` 类 | 非 Spring 项目，由使用方按自己的容器注册 |

`spi` 模式不提供全局注册表，也不做 `ServiceLoader` 自动加载；使用方自行实例化或注册生成的 `*Impl`。Maven 配置：

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <compilerArgs>
      <arg>-Abeanmap.registry=spi</arg>
    </compilerArgs>
  </configuration>
</plugin>
```

未知取值会告警并按 `spring` 处理；生成代码中的库包名与注解类型由处理器常量统一提供。

## 覆盖度断言

`BeanMapCoverage.verify(PartialChain<?, ?>... writes)` 比对「请求 DTO 的字段」与「各入口链上声明的可写字段 ∪ `@BeanUnmapped` ∪ 各入口 `excludes(...)`」。每个 DTO 字段必须有归宿，否则断言失败；它同时校验请求 DTO 一律使用包装类型（primitive 无法表达显式 `null`）。

该断言属于代码问题而非运行期问题，由域的**测试**显式调用，运行期不做此项校验。

## 异常与策略中断

| 异常 | 场景 |
|---|---|
| `BeanMapException` | 声明期校验失败、反射读写失败、结果查询非法；支持 `{}` 占位符格式化 |
| `BeanMapAbortException` | 策略中断（`requireAnyProvided`、`required`、`notClearable`、类型转换失败、多源冲突等） |

`AbortCode` 枚举给出中断语义码及等价的整型状态码：

| 语义码 | 整型码 | 场景 |
|---|---|---|
| `NO_CHANGE` | 304 | 声明的字段一个都没在负载里出现 |
| `REQUIRED` | 400 | 必填字段没有可用值 |
| `NOT_CLEARABLE` | 400 | 不可清空字段被显式传 null |
| `TYPE_MISMATCH` | 400 | 值无法转换或写入目标类型 |
| `FIELD_CONFLICT` | 409 | `throwOnConflict()` 下同一字段被多处源写入 |

`BeanMapAbortException#getAbortCode()` 返回语义码，`getCode()` 返回等价整型码。库本身不依赖任何 Web 框架，整型码由上层（如 `cn.labzen:web`）映射为 HTTP 状态。

中断行为由链首注入的 `AbortHandler` 决定：

```java
BeanMapper.of(user, source)
        .withAbortHandler((code, message) -> new MyBusinessException(code, message))
        .requireAnyProvided()
        .mapAll()
        .apply();
```

`AbortHandler` 返回本次中断要抛出的异常，由库在中断点执行 `throw`；默认实现为 `AbortHandler.THROWING`，返回 `BeanMapAbortException`，返回空时回退到默认异常。作用域为单次写入，与 `withConversionService(...)` 一致。

## 编程式配置

`BeanMapConfiguration` 把一次映射的默认策略收在一个显式构造的对象里：类型转换器、中断扩展点、实例化策略与多源冲突策略。配置对象由调用方创建、可复用于多条链，经 `withConfiguration(...)` 按入口传入；库不读取 `labzen.yml` 等配置文件，不维护全局默认状态，也不做 `ServiceLoader` 式自动加载。

```java
BeanMapConfiguration config = BeanMapConfiguration.builder()
        .conversionService(conversion)
        .abortHandler((code, message) -> new MyBusinessException(code, message))
        .instantiation(type -> myFactory.create(type))
        .conflict(ConflictPolicy.firstWins())
        .build();

BeanMapper.of(user, source).withConfiguration(config).mapAll().apply();

BeanComposer.compose(view)
        .withConfiguration(config)
        .from(new JavaBeanAccessor(user))
        .apply();
```

| 配置项 | 类型 | 默认值 | 作用链 |
|---|---|---|---|
| `conversionService` | `ConversionService` | Spring `DefaultConversionService` 共享实例 | `BeanMapper` / `BeanComposer` |
| `abortHandler` | `AbortHandler` | `AbortHandler.THROWING` | `BeanMapper` / `BeanComposer` |
| `instantiation` | `InstantiationStrategy` | 无参构造器 | `BeanMapper` |
| `conflict` | `ConflictPolicy` | `lastWins()` | `BeanComposer` |

`BeanMapConfiguration.defaults()` 返回库内默认策略集合。`withConfiguration(...)` 与各 `withXxx(...)` / `conflict(...)` 按调用顺序生效，后声明者覆盖先声明者；读方向 `BeanReader` 不做隐式转换与策略中断，不使用该配置。

## 边界与约束

- 字段取值与写入走反射，转换走链上的 `ConversionService`（默认 Spring 共享实例，可按入口替换），运行时慢于生成代码；高吞吐批量映射需评估。
- `sub` 不做环检测，自引用嵌套会栈溢出。
- 子对象块的源值必须实现 `SourceAccessor`，否则声明期抛 `BeanMapException`。
- 读方向 `mapAll()` 会跳过两侧类型不一致、源对象没有 public `getXxx` / `isXxx` 的属性（含 record 裸 accessor）、以及目标字段上的 `@BeanUnmapped`；被跳过的字段需显式 `map(...)` 或保持不写。
- 配置文件驱动（如 `labzen.yml`）与 `ServiceLoader` 式自动加载不在当前能力范围内；默认策略由 `BeanMapConfiguration` 按入口显式传入。
- 库内自带 JUnit 5 测试套件，`mvn test` 执行且测试失败即中止构建；覆盖度断言 `BeanMapCoverage.verify(...)` 由使用方的测试显式调用。

## 版本与发布

`cn.labzen:bean-map:1.1.1` 已发布至 Maven Central，对应本仓库当前源码能力；`1.0.0` 为首个发布版本。Central 版本不可覆盖，后续发布需提升版本号。parent 为 `cn.labzen:parent:21.3`（统一 import `labzen-bom:2.3`），JDK 21，Apache License 2.0。源码仓库：`git@github.com:labzen/bean-map.git`。版本记录与各版本升级注意事项见 `CHANGELOG.md`。
