# 模块一：可能会遇到的风险与验收对照表

> **使用方式**：每个批次修改完成后、提交之前，必须对照本表中与当前批次相关的风险项逐一检查，并在下方[批次验收打卡表](#批次验收打卡表)中记录检查结果。若迁移过程中出现本表未覆盖的新问题，请先登记至 [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md)，再将共性规律晋升回本表。

---

## 一、构建与环境边界风险（`R-BUILD`）

| 编号 | 风险描述 | 典型触发点 / 涉及文件 | 验收检查标准（Pass Criteria） |
| :--- | :--- | :--- | :--- |
| `R-BUILD-01` | 混淆 AGP 8.x 与 AGP 9.0 的 Kotlin 插件配置方式 | `build.gradle`、`app/build.gradle` | AGP 8.12.2 必须通过 `buildscript` 引入兼容的 `kotlin-gradle-plugin` 并在 `:app` 显式应用 `org.jetbrains.kotlin.android` |
| `R-BUILD-02` | Agent“顺手现代化”改动构建语言、依赖或 SDK 配置 | `build.gradle`、`app/build.gradle`、`settings.gradle` | 保持 Groovy DSL，不新增 AndroidX/Compose/Coroutines/Room 依赖，不改动 `minSdk 11` / `targetSdk 34` / `compileSdk 35` |
| `R-BUILD-03` | Kotlin `jvmTarget` 与 Java 编译目标版本不一致导致构建报错 | `app/build.gradle` | `compileOptions` 与 `kotlinOptions.jvmTarget` 一致，且 `./gradlew assembleDebug testDebugUnitTest` 无版本不匹配错误 |

---

## 二、字节码、可见性与反射契约风险（`R-BYTECODE`）

| 编号 | 风险描述 | 典型触发点 / 涉及文件 | 验收检查标准（Pass Criteria） |
| :--- | :--- | :--- | :--- |
| `R-BYTECODE-01` | Java 包级可见（package-private）转为 Kotlin `internal` 后发生方法名修饰（`$app_debug`），破坏未迁移的 Java 调用方与测试 | `NoteCategory`、`NoteSearch`、`NoteText`、`NoteTimestamp`、`NoteExport`、`NoteExportTask`、`NotePadProvider.getOpenHelperForTest`、`NoteEditor.showExportProgress/showExportResult` | 混编阶段凡是被 Java 源码或 Java 测试直接访问的类/成员，均保证 Java 侧可无改动编译通过（避免 `internal` 名字修饰破坏调用） |
| `R-BYTECODE-02` | 工具类转为 Kotlin `object` 后静态方法缺少 `@JvmStatic`，导致 Java 侧无法以 `ClassName.method()` 调用 | `NoteCategory`、`NoteSearch`、`NoteText`、`NoteTimestamp`、`NoteExport` | `object` 内供 Java 调用的静态方法均标注 `@JvmStatic`；现有 Java 测试无需改为 `.INSTANCE.xxx()` 即可通过 |
| `R-BYTECODE-03` | 契约类 `NotePad.Notes` 中常量未正确暴露为 JVM 静态字段，导致 Java 侧访问字段失败 | `NotePad.java` → `NotePad.kt`（`AUTHORITY`、`CONTENT_URI`、`CONTENT_ID_URI_BASE`、`LIVE_FOLDER_URI` 等） | `String`/`Int` 常量使用 `const val`；`Uri` 等引用类型常量使用 `@JvmField val`；Java 侧直接字段读取无报错 |
| `R-BYTECODE-04` | XML `android:onClick="onClickOk"` 反射签名被破坏（如改可见性、加默认参数或签名不匹配） | `TitleEditor.kt`、`app/src/main/res/layout/title_editor.xml` | 保留 `public fun onClickOk(v: View?)`（或 `View`），无默认参数且无重命名；点击确定按钮可正常关闭弹窗 |
| `R-BYTECODE-05` | `NotePadProvider.DatabaseHelper` 被误改为 `inner class` 或私有类，导致外部/测试无法直接实例化 | `NotePadProvider.kt`、`NoteDatabaseMigrationTest.java`、`NotePadProviderTest.java` | `DatabaseHelper` 为普通嵌套类（非 `inner`），构造函数对同包 Java 测试可见，`NoteDatabaseMigrationTest` 通过 |
| `R-BYTECODE-06` | 若迁移 JUnit 测试类，未指定 `@Rule` 注解目标导致贴在私有字段上触发 JUnit 运行时异常 | `app/src/test` / `app/src/androidTest`（仅当迁移测试文件时适用） | 所有 JUnit `@Rule` 使用 `@get:Rule` 或 `@JvmField @Rule` |

---

## 三、跨语言空安全边界风险（`R-NULL`）

| 编号 | 风险描述 | 典型触发点 / 涉及文件 | 验收检查标准（Pass Criteria） |
| :--- | :--- | :--- | :--- |
| `R-NULL-01` | 工具方法原本允许接收 `null` 参数，迁移后误写为非空类型导致 Java 传 `null` 时触发 `NullPointerException` | `NoteSearch.normalize/selection/arguments`、`NoteTimestamp.format(Long?)`、`NoteCategory.indexOf/requireValid/label`、`NoteText.preview` | 所有显式支持 `null` 输入的签名保持可空类型（`String?`、`Long?`），对应边界单测全通过 |
| `R-NULL-02` | `NoteExport.write` 将 `OutputStream` 误写为非空，导致传入 `null` 时抛 `NullPointerException` 而非原契约 `IOException` | `NoteExport.write(OutputStream?, String)`、`NoteExportTest.java` | 保持 `stream: OutputStream?`，当 `stream == null` 时显式抛出 `IOException` |
| `R-NULL-03` | `ContentProvider` 覆写方法参数与 `context` / `Cursor.getString()` 平台类型处理过于乐观 | `NotePadProvider.kt`（`query`/`insert`/`update`/`delete`/`openTypedAssetFile`） | `projection`、`selection`、`selectionArgs`、`sortOrder`、`initialValues`、`where`、`whereArgs` 均正确声明可空；不随意使用无守卫 `!!` |
| `R-NULL-04` | Android SDK 平台类型（`Intent.getData()`、`Bundle`、`ClipboardManager.getPrimaryClip()`、`getLastNonConfigurationInstance()`）空指针 | `NotesList.kt`、`NoteEditor.kt`、`TitleEditor.kt`、`NotesLiveFolder.kt` | 遵循“宁可偏向可空”策略，采用安全调用 `?.` 与显式判空，不引入新的 `!!` 崩溃面 |

---

## 四、Android 生命周期、内存与版本兼容风险（`R-LIFECYCLE`）

| 编号 | 风险描述 | 典型触发点 / 涉及文件 | 验收检查标准（Pass Criteria） |
| :--- | :--- | :--- | :--- |
| `R-LIFECYCLE-01` | `NoteEditor.onCreate` 在打开笔记失败时提前 `finish(); return;`，若字段使用 `lateinit var` 且在 `onPause`/`onSaveInstanceState`/`onDestroy` 被访问会抛 `UninitializedPropertyAccessException` | `NoteEditor.kt`（`mUri`、`mOriginalTitle/Body/Category`、`mSavedTitle/Body/Category`、`mTitle/mText/mCategory/mStatus`） | 失败早退前未初始化的成员不使用裸 `lateinit var`（或确保所有生命周期回调严格以 `mInitialized` 守卫并在单测中验证无效 URI 早退） |
| `R-LIFECYCLE-02` | `NotesList.NotesLoader` 被误写成 `inner class`，导致 Loader 持有旧 Activity 引用发生内存泄漏 | `NotesList.kt` | `NotesLoader` 保持 `private class NotesLoader`（非 `inner`），仅持有 `applicationContext` |
| `R-LIFECYCLE-03` | `NoteExportTask` 跨屏幕旋转状态保留机制被破坏或产生强引用泄漏 | `NoteExportTask.kt`、`NoteEditor.kt`（`onRetainNonConfigurationInstance`、`attach`/`detach`） | 保留 `WeakReference<NoteEditor>`，旋转重建时正确 `detach()` / `attach()`，导出完成结果仅投递一次 |
| `R-LIFECYCLE-04` | `NoteEditor.BackNavigation` 的 `@TargetApi(33)` 类隔离被合并进 `NoteEditor` 主体，破坏 API < 33 兼容性 | `NoteEditor.kt`（`OnBackInvokedDispatcher` / `OnBackInvokedCallback`） | 保留独立的 `@TargetApi(33)` 嵌套类/对象封装，且仅在 `Build.VERSION.SDK_INT >= 33` 分支调用 |
| `R-LIFECYCLE-05` | `TitleEditor` 的 `managedQuery` 返回可空 `Cursor?`，若未判空或未调用 `moveToFirst()` 判断空游标会导致崩溃 | `TitleEditor.kt` | `mCursor` 声明为 `Cursor?`，`onResume` / `onPause` 严格保留空检查 |

---

## 五、核心业务契约与回归风险（`R-BEHAVIOR`）

| 编号 | 风险描述 | 典型触发点 / 涉及文件 | 验收检查标准（Pass Criteria） |
| :--- | :--- | :--- | :--- |
| `R-BEHAVIOR-01` | `NotePadProvider.update` 条件拼接（`IS NOT ?`）或 `notifyChange` 条件走样，导致无改动保存改写 `modified` 时间 | `NotePadProvider.kt`、`NotePadProviderBehaviorTest.java` | 未显式传 `modified` 且内容无变化时 `update` 返回 0、不更新 `modified`、不触发 `notifyChange` |
| `R-BEHAVIOR-02` | SQL `LIKE ... ESCAPE '!'` 转义、分类 `AND` 括号优先级、或 Unicode 码点截断在改写为 Kotlin 标准库函数时语义漂移 | `NoteSearch.kt`、`NoteText.kt`、`NoteExport.kt` | 特殊字符 `%` / `_` / `!` / `\` 与 Emoji 代理对截断行为与原 Java 100% 一致 |
| `R-BEHAVIOR-03` | `NoteEditor` 剪贴板单次消费（含防止 SQLite 复用刚删除行 ID）与保存失败后拦截返回/恢复草稿/显式另存为新笔记状态机受损 | `NoteEditor.kt`、`NoteEditorBehaviorTest.java`、`NoteEditorSaveFailureTest.java`、`NotePasteLifecycleTest.java` | 上述 3 个测试类全部通过，失败返回不退出页面，配置重建后三字段草稿与失败态保留 |
| `R-BEHAVIOR-04` | 数据库 `v2 → v3` 原地增量升级或未知版本拒绝策略被破坏 | `NotePadProvider.kt`、`NoteDatabaseMigrationTest.java` | `v2 → v3` 保留旧数据并填充 `uncategorized`；未知旧版本抛 `SQLException` 且不销毁旧表 |
| `R-BEHAVIOR-05` | 迁移引入新的 Android Lint Error/Fatal 或显著新增 Warning | 全工程 `./gradlew lintDebug` | 保持 `0 Error / 0 Fatal`，记录 Warning 数与基线（37）的差异并逐项解释新增项 |

---

## 批次验收打卡表

> 状态约定：
> - `PENDING`（待检）
> - `PASS`（已有明确测试/`javap`/实机证据证明通过）
> - `TRIGGERED`（实操或复审中命中缺陷，已按 `NR-xxx` 修复并补证）
> - `UNVERIFIED`（仅通过静态阅读或编译，缺少运行时/单测/精确指标实证，严禁冒充 `PASS`）
> - `N/A`（本批不涉及）

| 风险编号 | Batch 0 | Batch 1 | Batch 2 | Batch 3 | Batch 4a | Batch 4b | Batch 5 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `R-BUILD-01` | TRIGGERED (`NR-001`) | N/A | N/A | N/A | N/A | N/A | PASS |
| `R-BUILD-02` | PASS | PASS | PASS | PASS | PASS | PASS | PASS (A/B 双分支) |
| `R-BUILD-03` | PASS | TRIGGERED (`NR-002`) | N/A | N/A | N/A | N/A | PASS (A/B 双分支) |
| `R-BYTECODE-01` | N/A | TRIGGERED (`NR-008`) | TRIGGERED (`NR-008`) | TRIGGERED (`NR-004`/`008`) | N/A | PASS (`internal` 收敛) | PASS (A局部/B全量收敛) |
| `R-BYTECODE-02` | N/A | PASS | N/A | N/A | N/A | N/A | PASS (A保留/B移除对照) |
| `R-BYTECODE-03` | N/A | N/A | PASS (`javap`) | N/A | N/A | N/A | PASS (A/B 均保留) |
| `R-BYTECODE-04` | N/A | N/A | N/A | TRIGGERED (`NR-009`→补测通过) | N/A | N/A | PASS (69/69 复跑) |
| `R-BYTECODE-05` | N/A | N/A | PASS (`javap`+单测) | N/A | N/A | N/A | PASS (A公开/B internal) |
| `R-BYTECODE-06` | N/A | N/A | N/A | N/A | N/A | N/A | N/A (本仓测试无 `@Rule`) |
| `R-NULL-01` | N/A | TRIGGERED (`NR-005`) | N/A | N/A | N/A | N/A | PASS |
| `R-NULL-02` | N/A | PASS | N/A | N/A | N/A | N/A | PASS |
| `R-NULL-03` | N/A | N/A | TRIGGERED (`NR-007`) | N/A | N/A | N/A | PASS |
| `R-NULL-04` | N/A | N/A | N/A | PASS (补测 `NotesLiveFolder`) | PASS (`javap -c`+补测) | PASS (`javap -c`+单测) | PASS |
| `R-LIFECYCLE-01` | N/A | N/A | N/A | N/A | N/A | PASS (早退全周期补测) | PASS |
| `R-LIFECYCLE-02` | N/A | N/A | N/A | N/A | PASS (`javap`+反射单测) | N/A | PASS |
| `R-LIFECYCLE-03` | N/A | N/A | N/A | UNVERIFIED (已于 4b 补测关闭) | N/A | PASS (旋转重绑单测) | PASS |
| `R-LIFECYCLE-04` | N/A | N/A | N/A | N/A | N/A | PASS (`javap -v` 0 引用) | PASS |
| `R-LIFECYCLE-05` | N/A | N/A | N/A | PASS | N/A | N/A | PASS |
| `R-BEHAVIOR-01` | N/A | N/A | PASS | N/A | N/A | N/A | PASS |
| `R-BEHAVIOR-02` | N/A | TRIGGERED (`NR-006`) | N/A | N/A | PASS (`trim` 补测通过) | PASS (`isEmpty` `trim` 补测) | PASS |
| `R-BEHAVIOR-03` | N/A | N/A | N/A | N/A | N/A | PASS (全套单测) | PASS |
| `R-BEHAVIOR-04` | N/A | N/A | PASS | N/A | N/A | N/A | PASS |
| `R-BEHAVIOR-05` | UNVERIFIED (仅证0E) | UNVERIFIED (仅证0E) | UNVERIFIED (仅证0E) | UNVERIFIED (仅证0E) | UNVERIFIED (仅证0E) | TRIGGERED (`NR-010`→0E/37W) | PASS (A/B 均 0E/37W) |

---

## 从模块二晋升的新增检查项（动态追加区）

> 当 [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md) 中出现具有复用价值的新风险时，按 `R-EVOLVED-01`、`R-EVOLVED-02` 递增编号追加至此表，供后续批次及未来项目复用。

| 编号 | 来源（`NR-xxx`） | 新增检查规则 | 适用场景 | 验收标准 |
| :--- | :---: | :--- | :--- | :--- |
| `R-EVOLVED-01` | `NR-001` | 引入 Kotlin Gradle Plugin 时须同时核对官方 `KGP × AGP × Gradle` 兼容性矩阵与本地 Gradle 缓存/代理环境，避免盲取最新稳定版导致下载受阻或跨大版本不兼容 | Batch 0 及任何构建插件版本变更 | 所选 KGP 版本落在官方兼容矩阵内，且在当前机器网络/缓存条件下可完成 `Gradle Sync`、`assembleDebug` 与 `testDebugUnitTest` |
| `R-EVOLVED-02` | `NR-002` | 警惕 Batch 0“无 `.kt` 源码时的空接假绿”：没有 `.kt` 文件时 Gradle 会因 `NO-SOURCE` 跳过 `compileDebugKotlin` 并延迟解析 `kotlinBuildToolsApiClasspath`（含 `kotlin-compiler-embeddable`） | 任何老项目首次接入 Kotlin 构建链路验收 | 只有当至少 1 个真实 `.kt` 文件完成 `compileDebugKotlin` 编译，才能认定 Kotlin 编译工具链已完整就绪 |
| `R-EVOLVED-03` | `NR-003` | 在将 `connectedDebugAndroidTest` 纳入 L3 验收前，须先在纯 Java 基线上核验 `compileDebugAndroidTestJavaWithJavac` 是否可用，防止把老项目遗留的 `android.test.*` 缺失误判为 Kotlin 迁移退化 | 任何升级过 `compileSdk >= 28` 的老 Android 工程迁移基线检查 | 明确记录 `androidTest` 在旧基线下的可编译状态；不可编译时在 Harness 中改列实际可用的设备验证链路，不越界修改 `useLibrary` |
| `R-EVOLVED-04` | `NR-004` | **覆写可见性隐式收窄检查**：Kotlin `override fun` 默认继承父类方法可见性（如 `Activity.onCreate` 的 `protected`），而不是默认 `public`；若原 Java 显式放宽为 `public`，Kotlin 必须显式写 `public override fun` | 所有 `Activity` / `ContentProvider` / 子类覆写方法（Batch 3、4a、4b） | 用 `javap -p` 对照原 Java 方法修饰符，确保不存在 `public → protected` 的静默收窄 |
| `R-EVOLVED-05` | `NR-005` | **Java 容忍 `null` 的操作转 Kotlin 非空参数反噬检查**：原 Java 中字符串拼接（`a + b`）、`Objects.equals`、`Map.get` 等本不抛 NPE 的参数，迁入 Kotlin 若写为非空类型会因 `Intrinsics.checkNotNullParameter` 制造新 NPE | 所有仍可能被 Java 调用或无 `@NonNull` 契约的参数 | 对原 Java 不抛 NPE 的入参一律声明为可空类型，并补充传 `null` 回归单测 |
| `R-EVOLVED-06` | `NR-006` | **标准库同名方法语义漂移检查**：Kotlin `String.trim()`（`Char.isWhitespace()`）与 Java `String.trim()`（`<= '\u0020'`）在 ASCII 控制字符（如 `\u0000`）和 Unicode 全角空格（`\u3000`）上行为不同 | 所有调用 `trim()` / `replace` / `split` / `toLowerCase` 等标准库同名方法的迁移类 | 严格等价迁移时使用 `trim { it <= ' ' }`，并补充控制字符与全角空格对照单测 |
| `R-EVOLVED-07` | `NR-007` | **异常类型漂移与 `open`/`final` 可继承性检查**：(1) 禁止随意用 `?: DefaultValue()` 静默改变原 Java 在 `null` 处的异常类型；(2) 检查原 Java 非 `final` 公共组件类在 Kotlin 变 `final` 后是否影响外部继承或测试替身 | `ContentProvider`、`Activity` 及所有公开类 | 用 `javap -p` 检查 `final` 修饰变更并评估影响；异常契约通过边界单测锁定 |
| `R-EVOLVED-08` | `NR-008` | **包级私有 → `public` 可见性扩张追踪**：混编期因规避 `internal` 名字修饰（`$app_debug`）而被迫放宽为 `public` 的内部类与测试后门（如 `getOpenHelperForTest()`），必须登记清账，不得永久遗忘 | Batch 1–4 所有原 package-private 类及测试钩子，Batch 5 收口复核 | 在 Batch 5 逐一复核临时 `public` 列表：确认哪些在纯 Kotlin 后应收窄为 `internal`，哪些保留给 Java 测试并记录原因 |
| `R-EVOLVED-09` | `NR-009` | **反“橡皮图章式 PASS”验收红线**：现有测试全绿绝不等于当前风险项被覆盖。凡是没有直接单测、`javap` 或设备运行证据的检查项，严禁标为 `PASS`，必须标为 `UNVERIFIED` 并给出补测计划 | 全部批次验收打卡 | 每个 `PASS` 标记都能指明对应的具体测试用例名、字节码输出或实机日志；0 覆盖项必须诚实标 `UNVERIFIED` |
| `R-EVOLVED-10` | `NR-010` | **低 `minSdk` 禁止盲目将 Java `try (Cursor)` 替换为 Kotlin `Closeable.use {}`**：`android.database.Cursor` 自 API 16 起才实现 `java.io.Closeable`；在 `minSdk < 16` 项目中调用 `kotlin.io.use {}` 会生成 `Closeable` 隐式强转并触发 Lint `[NewApi]` Error（且 IDE 反而会误推荐 `use()`） | 所有 `minSdk < 16` 项目中含 `Cursor`、`SQLiteDatabase`、`AssetFileDescriptor` 等晚于 API 1 实现 `Closeable` 的资源类 | 改用基于原生 `close()` 的 `try/finally` 或自定义 `useCompat` 内联扩展；以 `lintDebug` 0 Error 与 `javap -v` 常量池 0 `Closeable` 引用验收 |
| `R-EVOLVED-11` | `NR-011` | **测试迁移时的 `protected` 同包可见性语义差**：Java `protected` 允许同包测试直接调用（如 `onActivityResult`、`onListItemClick`），而 Kotlin `protected` 仅允许子类访问、禁止同包调用 | 将同包 Java 单元测试迁移为 Kotlin（如方案 B） | 预先评估测试中直接调用生产类 `protected` 生命周期回调的点，选择反射调用、Robolectric Shadow 或显式 `internal` 测试入口，避免为了迁测试擅自把生产 `protected` 方法改成 `public` |
