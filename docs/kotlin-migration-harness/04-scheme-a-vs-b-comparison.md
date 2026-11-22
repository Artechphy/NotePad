# 模块四：Batch 5 镜像对照报告（方案 A vs 方案 B）

> **设计目的**：在全部 12 / 12 个生产类完成 Kotlin 迁移后，团队通常会面临一个核心收口抉择：**是否保留 Java 单元测试作为跨语言互操作黑盒护栏（方案 A），还是将测试一并迁入 Kotlin 以彻底收敛 `NR-008` 临时 `public` API（方案 B）？**
> 本项目通过两个精准分叉的 Git 镜像分支完整落地了两种方案，提供可直接 `git diff` 与 `javap -p` 复现的工程证据。

---

## 一、双分支镜像拓扑与复现命令

```text
5b34c63 (Batch 4b 完成：12/12 生产类 100% Kotlin)
   │
   └── e9ac7ec  [分支 exp/kotlin-migration]  方案 A：保留 12 个 Java 单测 + 局部收敛 internal/private
         │
         └── 503cfba  [分支 exp/kotlin-migration-pure-kotlin-tests]  方案 B：12 个单测全迁 Kotlin + 全量收敛 internal
```

* 查看方案 A 与方案 B 的全部代码差异（不含文档）：
  ```bash
  git diff e9ac7ec..503cfba -- app/src/
  ```
* 查看仅生产代码的可见性与注解差异（6 个文件）：
  ```bash
  git diff e9ac7ec..503cfba -- app/src/main/
  ```

---

## 二、`NR-008` 可见性与 JVM 字节码（`javap -p`）镜像对照

| 符号 | 原 Java 基线 | 方案 A（`e9ac7ec`，保留 Java 测试） | 方案 B（`503cfba`，纯 Kotlin 测试） | 差异解析 |
| :--- | :--- | :--- | :--- | :--- |
| `NoteCategory` | package-private `final class` + `static` | `public final class` + `public static final`（`@JvmStatic`） | `internal object` → JVM 无 `@JvmStatic`，方法变为 `INSTANCE` 实例方法 `public final` | 方案 A 为 Java 测试保留静态调用与公开性；方案 B 在 Kotlin 元数据层面封闭模块边界 |
| `NoteSearch` | package-private `final class` + 重载 `static` | `public final class` + `public static final` + 2 个 `@JvmOverloads` 重载 | `internal object` → 移除 `@JvmStatic`/`@JvmOverloads`，JVM 减少 2 个重载方法 | 方案 B 消除为 Java 服务的桥接重载 |
| `NoteText` | package-private `final class` + `static` | `public final class` + `public static final`（`@JvmStatic`） | `internal object`（移除 `@JvmStatic`） | 同上 |
| `NoteTimestamp` | package-private `final class` + `static` | `public final class` + `public static final`（`@JvmStatic`） | `internal object`（移除 `@JvmStatic`） | 同上 |
| `NoteExport` | package-private `final class` + `static` | `public final class` + `public static final`（`@JvmStatic`） | `internal object`（移除 `@JvmStatic`，保留 `@Throws`） | 同上 |
| `NoteExportTask` | package-private `final class` | **`internal class`** + `attach$app_debug` / `detach$app_debug` | **`internal class`** + `attach$app_debug` / `detach$app_debug` | 实证：即使方案 A 的 Java 测试执行了 `instanceof NoteExportTask`，因 `internal class` 在 JVM 仍编译为 `public final class`，也可提前收敛 |
| `NoteEditor.showExportProgress` / `showExportResult` | package-private 方法 | **`internal fun`** → `showExportProgress$app_debug` / `showExportResult$app_debug` | **`internal fun`** → `showExportProgress$app_debug` / `showExportResult$app_debug` | Batch 4b 唯一调用方已为 Kotlin，两种方案均实现 0 债务收敛 |
| `NotePadProvider.NOTE_STREAM_TYPES` | package-private `static final` | **`private static final`**（移除 `@JvmField`） | **`private static final`**（移除 `@JvmField`） | 全仓仅 `NotePadProvider` 内部自用，两种方案均收紧为 `private` |
| `NotePadProvider.DatabaseHelper` | package-private `static class` | `public final class`（保留供 `NoteDatabaseMigrationTest.java` 实例化） | **`internal class`** | 方案 B 彻底收回数据库 Helper 的模块外可见性 |
| `NotePadProvider.getOpenHelperForTest()` | package-private 测试后门 | `public final`（保留供 7 个 Java 单测关闭连接） | **`internal fun`** → `getOpenHelperForTest$app_debug()` | 方案 B 在字节码层面对外部调用方实施名称修饰防护，杜绝测试后门泄漏 |

> **关键发现**：公开契约类 `NotePad`（含 `const val` 与 `@JvmField` `Uri`）以及 AndroidManifest 注册的 5 个系统组件（`NotePadProvider`、`NotesList`、`NoteEditor`、`TitleEditor`、`NotesLiveFolder`）在两种方案中均保持 `public`。

---

## 三、综合维度对比（实测数据）

| 对比维度 | 方案 A（`exp/kotlin-migration`） | 方案 B（`exp/kotlin-migration-pure-kotlin-tests`） |
| :--- | :--- | :--- |
| **生产源码语言** | 12 / 12 文件全 Kotlin（0 `.java`） | 12 / 12 文件全 Kotlin（0 `.java`） |
| **单元测试语言** | 12 / 12 文件全 Java（1,690+ 行） | 12 / 12 文件全 Kotlin（~1,980 行，含反射辅助封装） |
| **JVM 跨语言互操作护栏** | **强**：69 项 Java 单测在每次 CI 持续验证 `@JvmStatic`、`@JvmField`、可空参数与异常契约 | **弱**：纯 Kotlin 单测无法检验 Java 调用方缺少 `@JvmStatic` 或遭遇 `$app_debug` 修饰的问题 |
| **`NR-008` 可见性收敛度** | 局部收敛（3/10 收敛：2 `internal` + 1 `private`；7 项因 Java 测试保留 `public`） | **100% 收敛**（全部 10 项原 package-private 符号均收窄为 `internal` / `private`） |
| **测试迁移新暴露的语言差异** | 无（保留原 Java 测试零改动成本） | 命中 **`NR-011`**：Java `protected` 同包可见 vs Kotlin `protected` 仅子类可见，导致 `onActivityResult`、`onListItemClick` 不能直接裸调，须改用反射或新增 `internal` 测试入口 |
| **Robolectric Shadow 兼容性** | 原 Java `@Implements(ClipboardManager.class)` 稳定运行 | Kotlin `@Implements(ClipboardManager::class)` + `companion object` 状态字段实测通过（`NotePasteLifecycleTest.kt` 7/7 PASS） |
| **JUnit `@Rule` (`R-BYTECODE-06`)** | `N/A`（测试仍为 Java） | `N/A`（本仓 12 个测试类均未使用 `@Rule`，未伪造 PASS） |
| **自动化验证结果** | `assembleDebug` PASS；`:app:testDebugUnitTest --rerun-tasks` **69/69 PASS**；`lintDebug` **0E / 37W PASS** | `assembleDebug` PASS；`:app:testDebugUnitTest --rerun-tasks` **69/69 PASS**；`lintDebug` **0E / 37W PASS** |

---

## 四、给工程团队与博客读者的选型建议

1. **优先选方案 A（保留 Java 测试护栏）的场景**：
   * 当前模块仍被其他未迁移的 Java 业务模块、SDK 调用方或历史 `androidTest` 消费；
   * 处于增量迁移中期，需要零成本的跨语言二进制/源码兼容性回归防线；
   * 老项目测试大量依赖 Java“同包访问 `protected` 生命周期回调”（如 `onActivityResult`、`onListItemClick`、`onSaveInstanceState`），不想为了迁测试引入反射样板或破坏原方法可见性。
2. **优先选方案 B（纯 Kotlin 测试 + 全量 `internal` 收敛）的场景**：
   * 当前为独立 App 模块（无外部 Java 消费者）且团队已决定彻底告别 Java；
   * 希望彻底消除 `NR-008` 造成的公开 API 膨胀与测试后门（如 `getOpenHelperForTest()`）泄漏，且希望清理冗余的 `@JvmStatic` / `@JvmOverloads` 样板注解。

---

## 五、四层最终验收汇总与诚实盲区声明（双分支一致）

| 验证层级 | 验证命令 / 手段 | 最终结果 | 说明 |
| :--- | :--- | :---: | :--- |
| **L1 构建检查** | `assembleDebug` | **PASS** | 保持 Groovy DSL、AGP 8.12.2、Gradle 8.13、minSdk 11、0 新增第三方库 |
| **L1.5 字节码核验** | `javap -p` / `javap -v` / `javap -c` | **PASS** | 全量 12 个生产类已核验可见性、`BackNavigation` API 33 常量池隔离（0 引用）、0 `Closeable`（API 11 安全）、隐式 `checkNotNullParameter` 审计 |
| **L2 本地单测** | `:app:testDebugUnitTest --rerun-tasks` | **PASS（69/69）** | 原 11 类 56 项 + Harness 补测 13 项，双分支全部 0 skipped / 0 failed |
| **静态检查** | `lintDebug` | **PASS（0E / 37W）** | 与纯 Java 基线（0 Error / 37 Warnings）增量严格为 0 |
| **L3 仪器/设备测试** | `tools/device-test` / `androidTest` | **UNVERIFIED（未连设备）** | 历史 `app/src/androidTest` 在 `compileSdk 35` 基线下缺少 `android.test.*`（`NR-003`）；当前会话未连接真机/模拟器 |
| **L4 人工走查** | 真机/模拟器核心路径走查 | **UNVERIFIED（未连设备）** | 以下盲区诚实保留待真机验收：① API 33+ 预测性返回手势回调；② API 11–15 低版本实机运行；③ `NotePadProvider.openTypedAssetFile/writeDataToPipe` 流式导出；④ `list_more` `PopupMenu` 与长按上下文菜单真实手势交互 |
