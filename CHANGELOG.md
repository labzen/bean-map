# Changelog

`cn.labzen:bean-map` 的版本记录，遵循语义化版本。当前公开 API 与能力边界见 `README.md`，各版本之间的升级注意事项见文末「升级说明」。

## 版本策略

- `MAJOR` 承载不兼容变更，`MINOR` 承载向后兼容的功能增量，`PATCH` 承载缺陷修复、测试与文档。
- `1.1.0` 中 `BeanMapAbortException` 的构造器变更是不兼容变更；按语义化版本由 `MAJOR` 承载。该版本已发布至 Maven Central 且不可覆盖，处置方式是在本文件的「升级说明」中给出迁移方式，不追加 `(int, String)` 兼容构造器：整型状态码与 `AbortCode` 语义码是两套契约，`getCode()` 已满足整型码需求，追加旧构造器会让中断异常的构造入口重新分叉。
- 不兼容变更在对应版本的「不兼容变更」与文末「升级说明」两处同时标注并给出迁移方式；版本号按项目发布决策确定（`1.2.0` 的新增公开注解 / 类、`1.3.0` 的包结构重组均以 `MINOR` 发布）。

## [1.4.0] - 2026-11-09

> 已发布至 Maven Central（2026-11-09）；本版本为向后兼容的功能增量，升级方式见文末「1.3.0 → 1.4.0」。

写方向 APT 生成代码支持源对象自带三态语义。

### 新增

- `BeanMapProcessor` 为 `TO_DOMAIN` / `UPDATE_DOMAIN` 生成源访问器选择：源对象实现 `SourceAccessor` 时直接透传，由源对象判定字段是否出现；其余来源包一层 `JavaBeanAccessor`，字段视为全部出现。生成类按需引入 `cn.labzen.beanmap.accessor.SourceAccessor`。
- 消费方可以让请求 DTO 实现 `SourceAccessor`，使其字段出现记录直接驱动三态写入，无需为每个映射接口手写实现。

### 兼容性

- 公开 API 无变更，二进制兼容；普通 JavaBean 源仍走 `JavaBeanAccessor`，行为不变。

### 修复

- `@BeanMapField` 未指定 `converter` 时，APT 不再把编译期的 `void` 类型当作转换器输出 `new void()`。

### 测试

- APT 生成用例断言写方向的源访问器选择与 `SourceAccessor` import，并覆盖默认 `converter` 的生成结果。

## [1.3.0] - 2026-11-08

> 已发布至 Maven Central（2026-11-08），`V1.3.0` tag 与 `main` 同步；本版本为不兼容变更，升级方式见文末「1.2.0 → 1.3.0」。

按功能性重组包结构，类名、方法签名与行为不变，仅包路径变化。

### 不兼容变更

所有类型的全限定名发生变化（含包内可见类型），使用方需按所在包调整 import：

| 所在包 | 类 |
|---|---|
| `cn.labzen.beanmap.core` | `BeanMapper`、`BeanReader`、`BeanComposer`、`BeanMapConfiguration`、`BeanMapCoverage`、`PartialChain`、`BeanField`、`NestedPartial`、`PartialState`、`BeanMapResource`、`LambdaNames`、`BeanGetter`、`BeanSetter` |
| `cn.labzen.beanmap.accessor` | `SourceAccessor`、`JavaBeanAccessor`、`MapAccessor`、`DtoReflection` |
| `cn.labzen.beanmap.spi` | `AbortCode`、`AbortHandler`、`InstantiationStrategy`、`ConflictPolicy` |
| `cn.labzen.beanmap.exception` | `BeanMapException`、`BeanMapAbortException` |
| `cn.labzen.beanmap.annotation` | `BeanMap`、`BeanOp`、`MappingType`、`BeanMapField`、`BeanMapFields`、`BeanTarget`（原有 `@BeanUnmapped` / `@BeanClearable` / `@BeanUnmappedBlank` 路径不变） |
| `cn.labzen.beanmap.processor` | `BeanMapProcessor` |

`META-INF/services/javax.annotation.processing.Processor` 注册的处理器类为 `cn.labzen.beanmap.processor.BeanMapProcessor`；APT 生成代码的 import 分别指向 `cn.labzen.beanmap.core.BeanMapper`、`cn.labzen.beanmap.core.BeanReader` 与 `cn.labzen.beanmap.accessor.JavaBeanAccessor`。根包 `cn.labzen.beanmap` 仅保留 `package-info.java`。

### 测试

- APT 生成用例补充生成代码 import 包路径断言；测试总数保持 50，`mvn -o -B clean verify` 全绿。

### 兼容性说明

- Java 通配符 import 不递归子包，单个 `import cn.labzen.beanmap.*;` 无法引入各功能子包的类型，需按上表分别引入。
- 13 个写核心相关类因包内可见性互访保持同一包（`.core`），封装不变。
- 本版本按项目决策以 `MINOR` 发布；包路径变更属不兼容变更，迁移方式见文末「升级说明」。

## [1.2.0] - 2026-11-08

功能版本，对应路线图阶段 12（`@BeanOp` 显式映射形态声明）。

### 新增

- `@BeanOp` 注解与 `MappingType` 枚举：在 `@BeanMap` 接口方法上显式声明映射形态（`TO_DOMAIN` / `UPDATE_DOMAIN` / `COPY_DTO` / `TO_DTO` / `TO_CHAIN`），方法名不再参与分派。
- `BeanMapProcessor` 双轨分派：有注解按形态生成并校验签名，形态与签名不匹配以编译期 `ERROR` 报告；无注解按签名回退并产生编译告警。
- `TO_DOMAIN` 生成体使用专属局部变量名，避免与接口参数同名时生成无法编译的代码。

### 测试

- 新增 6 个 APT 用例：五形态各一、注解优先于方法名、回退告警、非法签名编译错误、`spi` 注册模式、无 `@BeanTarget` 的编辑形态；测试总数 50。

### 文档

- `README.md` 与 `CHANGELOG.md` 说明显式分派与回退判据；设计草案同步阶段 12 完成状态与落地清单。

### 兼容性

- 公开 API 仅新增注解与枚举；未标注的方法保持原有按签名生成行为，仅新增编译告警。

## [1.1.1] - 2026-11-08

缺陷修复与测试补齐，公开 API 无变更。

### 修复

- 字段声明 `required` 且命中 `ignore*` 跳过规则时仍执行必填判定，避免绕过 `REQUIRED` 中断。
- 读方向 `BeanReader.mapAll()` 跳过目标字段上的 `@BeanUnmapped`，与写方向语义一致。
- APT 生成跳过 `static` / `default` 方法；按方法真实签名生成参数列表；`updateDomain` 保持声明参数顺序，`copyDto` 使用第二参数真实类型，`toDomain` 要求单参且有返回值。
- APT 生成读取 `@BeanMapField#converter` 类型时处理 `MirroredTypeException`；处理器生成失败以编译期 `ERROR` 诊断报告。

### 测试与构建

- 新增 7 个 JUnit 5 测试类、44 个用例，覆盖三态与修饰符管线、`SourceAccessor`（含 record / Map / 形状 fail-fast）、集合元素级映射、多源合并三种冲突策略、编程式配置、覆盖度断言与 APT 生成。
- `maven-surefire-plugin` 显式设置 `testFailureIgnore=false`，测试失败即中止构建。

### 文档

- `README.md` 按源码行为复核显式 null 的转换路径、数组元素映射返回 `List`、白名单校验时机、APT 识别方式、读方向 `mapAll()` 边界与测试套件说明。
- 设计草案同步阶段 10 完成状态、落地清单与阶段 12 评估结论。

## [1.1.0] - 2026-11-08

功能版本，对应路线图阶段 3–9 与阶段 11。

### 新增

- `AbortCode` 语义码与 `BeanMapAbortException#getAbortCode()`；`AbortHandler` 链级中断扩展点。
- `BeanMapper.withConversionService(...)` 链级类型转换器注入，默认使用 Spring `DefaultConversionService` 共享实例。
- APT 生成类注册方式由 `-Abeanmap.registry=spring|spi` 控制，缺省为 `spring`。
- 集合元素级映射 `element(...)` / `ignoreNullElement()` 与 `InstantiationStrategy` 实例化策略。
- 多源合并 `BeanComposer` 与 `ConflictPolicy` 的 `lastWins()` / `firstWins()` / `throwOnConflict()`。
- 编程式配置 `BeanMapConfiguration`（`withConfiguration(...)`），不读取配置文件、不做 `ServiceLoader` 自动加载。
- record 组件 accessor、`MapAccessor` 三态 Map 源与 `JavaBeanAccessor` 的源形状 fail-fast。
- `README.md` 完整说明坐标、公开 API、注解、APT 生成、覆盖度断言与边界约束。

### 不兼容变更

- `BeanMapAbortException` 的构造器接收 `AbortCode`；`getCode()` 仍提供等价整型状态码。`AbortHandler` 的实现按 `AbortCode` 接收语义码。
- APT 生成类在默认模式下带 `@Component`，消费方需提供 `spring-context`；非 Spring 消费方使用 `-Abeanmap.registry=spi`。

## [1.0.0] - 2026-11-07

首个发布版本。坐标 `cn.labzen:bean-map:1.0.0`，JDK 21，强依赖 Spring（`spring-core`）。

- 从 paragon 抽离 17 个类与 3 个注解，独立 Maven 坐标、独立 git 仓库与 Central 发布链。
- 两态全量读写与三态部分更新、方法引用字段声明、字段修饰符管线、覆盖度断言、入口级字段封禁。
- `SourceAccessor` 抽象与 `JavaBeanAccessor` 默认实现；`BeanMapProcessor` 生成 `*BeanMapImpl`。

---

## 升级说明

### 1.0.0 → 1.1.0

- 把依赖坐标中的版本改为 `1.1.0`（或由依赖管理统一锁定）。
- `BeanMapAbortException` 的构造器接收 `AbortCode` 枚举值；直接按整型码构造该异常的代码改用 `AbortCode`。自定义 `AbortHandler` 的实现按 `AbortCode` 接收语义码，需要整型码时用 `getCode()`。
- 非 Spring 消费方在 `maven-compiler-plugin` 的 `compilerArgs` 中配置 `-Abeanmap.registry=spi`，生成不带注册注解的普通 `public` 类。
- `1.1.0` 起 APT 生成类默认带 `@Component`，Spring 消费方需提供 `spring-context` 并启用组件扫描。

### 1.1.0 → 1.1.1

二进制兼容，直接替换版本号即可。以下为缺陷修复带来的行为变化，若业务此前依赖旧行为需确认：

- 字段同时声明 `required` 与 `ignore*` 时，命中跳过规则会以 `REQUIRED`（400）中断。
- 读方向 `mapAll()` 不再尝试写入标了 `@BeanUnmapped` 的目标字段；这类字段需显式 `map(...)` 或保持不写。
- APT 不再为 `static` / `default` 方法生成 `@Override` stub，生成方法的参数列表与接口声明完全一致。
- 构建侧 `testFailureIgnore=false` 只影响本库自身的构建；作为依赖引入不受影响。

### 1.1.1 → 1.2.0

二进制兼容，直接替换版本号即可。新增 `@BeanOp` / `MappingType` 用于显式声明映射形态：

- 既有 `@BeanMap` 接口无需改动：未标注 `@BeanOp` 的方法按原签名判据生成，仅新增编译告警。
- 新接口推荐在每个方法上标注 `@BeanOp(MappingType.XXX)`；标注后方法名不再参与分派，可自由命名并在同一接口声明多个同形态方法。
- 形态与签名不匹配时编译期报错（如 `COPY_DTO` 要求双参且返回 `void`）；用法与回退判据见 `README.md` 的「APT 生成」。
- 补 `@BeanOp` 可消除回退告警；告警不影响生成结果。

### 1.2.0 → 1.3.0

包路径变化，类名、方法签名与行为不变。按以下顺序调整：

- 把依赖坐标中的版本改为 `1.3.0`（或由依赖管理统一锁定）。
- 按 `1.3.0` 的「不兼容变更」迁移表调整 import：`cn.labzen.beanmap.core` 承载映射引擎与链式 API，`cn.labzen.beanmap.accessor` 承载源访问器，`cn.labzen.beanmap.spi` 承载策略类型，`cn.labzen.beanmap.exception` 承载异常，`cn.labzen.beanmap.annotation` 承载全部注解，`cn.labzen.beanmap.processor` 承载 APT 处理器。
- 使用 `import cn.labzen.beanmap.*;` 的代码需按实际引用的类型分别引入对应子包；Java 通配符 import 不递归子包。
- 显式指定 APT 处理器类名的构建配置（如 `-processor` 参数）使用 `cn.labzen.beanmap.processor.BeanMapProcessor`；通过 `META-INF/services` 自动发现处理器的构建无需调整。
- 由 APT 生成的 `*Impl` 源码中的 import 由处理器按功能包输出，重新编译即可。

### 1.3.0 → 1.4.0

二进制兼容，直接替换版本号即可。请求 DTO 实现 `SourceAccessor` 后，APT 生成的 `TO_DOMAIN` / `UPDATE_DOMAIN` 方法直接使用该 DTO 的 `isProvided(...)` 判定字段是否出现；普通 JavaBean 源仍由 `JavaBeanAccessor` 承载，行为不变。
