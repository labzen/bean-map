![LOGO](http://r7jiu5wkl.hd-bkt.clouddn.com/images/2022/02/19/16-34-57-167.png)

# Labzen BeanMap

![Labzen BeanMap](https://img.shields.io/badge/Labzen-BeanMap-green)
![Maven Central](https://img.shields.io/maven-central/v/cn.labzen/bean-map)
![GitHub](https://img.shields.io/github/license/labzen/bean-map)
![JDK](https://img.shields.io/badge/JDK-21-blue)

通用对象映射基础库：三态部分更新、两态全量读写、覆盖度断言、入口级字段封禁。字段声明基于方法引用，属性名在编译期校验，避免字符串拼写错误导致的静默漏写。

## Installation

Maven（JDK 21+）：

```xml
<dependency>
  <groupId>cn.labzen</groupId>
  <artifactId>bean-map</artifactId>
  <version>1.0.0</version>
</dependency>
```

依赖 `cn.labzen:spring`、`cn.labzen:tool`、`cn.labzen:logger`；字段隐式类型转换使用 Spring `ConversionService`（`spring-core`）。使用 APT 生成时，生成的 `*Impl` 类带 `@Component`，消费方需提供 `spring-context`。

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

内置实现 `JavaBeanAccessor` 对任意 JavaBean / POJO 反射读值，是**两态实现**（`isProvided` 恒返回 `true`），即所有字段都视为「出现」。需要区分「没传」与「传了 null」时，由调用方实现 `SourceAccessor`（例如基于 JSON 节点或请求负载的 key 集合）。

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
| `SourceAccessor` / `JavaBeanAccessor` | 源对象访问抽象与默认两态实现 |
| `DtoReflection`、`LambdaNames` | 属性路径反射读取、方法引用解析属性名 |
| `BeanMapCoverage` | 覆盖度断言（由测试调用） |
| `BeanMapException` / `BeanMapAbortException` | 声明期错误 / 策略中断 |
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
| `apply()` | 按声明顺序执行，返回 `BeanMapResource<R>` |

字段修饰符：

| 类别 | 方法 |
|---|---|
| 按值跳过 | `ignoreValue`、`ignoreLessThan`、`ignoreGreaterThan`、`ignoreBetween`、`ignoreBefore`、`ignoreAfter`、`ignoreIf`、`ignoreBlank`、`ignoreEmpty` |
| 值处理 | `trim`、`withConverter`、`withDefault` |
| 约束 | `required`、`notClearable`、`nullable` |

处理管线：缺省分流 → `ignore*` → `withDefault` / `notClearable` → `trim` → 转换 → `required` → 写入。`withDefault` 与 `notClearable` 互斥；按值判定的修饰符作用于转换前的 DTO 侧原值，`required(...)` 的谓词作用于转换后的值。

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

编译期在同包生成 `UserBeanMapImpl`（带 `@Component`），方法体委托给编程式 API：

| 方法形态 | 识别方式 | 生成逻辑 |
|---|---|---|
| `toDomain(Dto)` | `toDomain` 前缀 + 单参 + 有返回值 | `new` 出 domain 后 `BeanMapper.of(...).mapAll().apply()` |
| `updateDomain(Dto, @BeanTarget Domain)` | 参数带 `@BeanTarget` 且返回 `void` | 对传入 domain 就地 `mapAll()` |
| `copyDto(Src, Tgt)` | 方法名 `copyDto` + 双参 + `void` | `BeanReader.read(...).mapAll().apply()` |
| `toDto(Domain)` / `toChain(Domain)` | 单参 + 有返回值 | `BeanReader.read(...)`，按 `@BeanMapField` 声明显式映射后 `mapAll()` |

处理器不读取 domain / DTO 的字段，只解析映射接口的方法签名，因此不依赖 Lombok 的字段生成顺序。未识别的方法形态生成抛 `UnsupportedOperationException` 的方法体。

处理器经 `META-INF/services/javax.annotation.processing.Processor` 注册。本库构建时通过 `<proc>none</proc>` 关闭注解处理（处理器类自身尚未生成），下游以 jar 依赖本库时正常触发代码生成。

## 覆盖度断言

`BeanMapCoverage.verify(PartialChain<?, ?>... writes)` 比对「请求 DTO 的字段」与「各入口链上声明的可写字段 ∪ `@BeanUnmapped` ∪ 各入口 `excludes(...)`」。每个 DTO 字段必须有归宿，否则断言失败；它同时校验请求 DTO 一律使用包装类型（primitive 无法表达显式 `null`）。

该断言属于代码问题而非运行期问题，由域的**测试**显式调用，运行期不做此项校验。

## 异常

| 异常 | 场景 |
|---|---|
| `BeanMapException` | 声明期校验失败、反射读写失败、结果查询非法；支持 `{}` 占位符格式化 |
| `BeanMapAbortException` | 策略中断（`requireAnyProvided`、`required`、`notClearable` 等），`getCode()` 提供整型语义码（`304` / `400`） |

库本身不依赖任何 Web 框架，语义码由上层（如 `cn.labzen:web`）映射为 HTTP 状态。

## 边界与约束

- 字段取值与写入走反射，转换走链上的 `ConversionService`（默认 Spring 共享实例，可按入口替换），运行时慢于生成代码；高吞吐批量映射需评估。
- `sub` 不做环检测，自引用嵌套会栈溢出。
- 子对象块的源值必须实现 `SourceAccessor`，否则声明期抛 `BeanMapException`。
- 读方向 `mapAll()` 会跳过两侧类型不一致的属性，这类字段需显式 `map(...)`。
- record / Map 源、集合元素级映射、多源合并、SPI 扩展点、`labzen.yml` 配置均不在 1.0.0 能力范围内。
- 1.0.0 未附带测试用例，使用方需自行编写覆盖度断言测试。

## 版本与发布

`cn.labzen:bean-map:1.0.0` 已发布至 Maven Central；本仓库当前源码另包含链级类型转换器注入点，发布前需提升版本号。parent 为 `cn.labzen:parent:21.3`（统一 import `labzen-bom:2.3`），JDK 21，Apache License 2.0。源码仓库：`git@github.com:labzen/bean-map.git`。
