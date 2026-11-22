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

> 状态约定：`PENDING`（待检） / `PASS`（通过） / `TRIGGERED`（命中并已修复，需关联 `NR-xxx` 或日志说明） / `N/A`（本批不涉及）

| 风险编号 | Batch 0 | Batch 1 | Batch 2 | Batch 3 | Batch 4a | Batch 4b | Batch 5 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `R-BUILD-01` | PASS | N/A | N/A | N/A | N/A | N/A | PENDING |
| `R-BUILD-02` | PASS | PASS | PENDING | PENDING | PENDING | PENDING | PENDING |
| `R-BUILD-03` | PASS | N/A | N/A | N/A | N/A | N/A | PENDING |
| `R-BYTECODE-01` | N/A | PASS | PENDING | PENDING | N/A | PENDING | PENDING |
| `R-BYTECODE-02` | N/A | PASS | N/A | N/A | N/A | N/A | PENDING |
| `R-BYTECODE-03` | N/A | N/A | PENDING | N/A | N/A | N/A | PENDING |
| `R-BYTECODE-04` | N/A | N/A | N/A | PENDING | N/A | N/A | PENDING |
| `R-BYTECODE-05` | N/A | N/A | PENDING | N/A | N/A | N/A | PENDING |
| `R-BYTECODE-06` | N/A | N/A | N/A | N/A | N/A | N/A | PENDING |
| `R-NULL-01` | N/A | PASS | N/A | N/A | N/A | N/A | PENDING |
| `R-NULL-02` | N/A | PASS | N/A | N/A | N/A | N/A | PENDING |
| `R-NULL-03` | N/A | N/A | PENDING | N/A | N/A | N/A | PENDING |
| `R-NULL-04` | N/A | N/A | N/A | PENDING | PENDING | PENDING | PENDING |
| `R-LIFECYCLE-01` | N/A | N/A | N/A | N/A | N/A | PENDING | PENDING |
| `R-LIFECYCLE-02` | N/A | N/A | N/A | N/A | PENDING | N/A | PENDING |
| `R-LIFECYCLE-03` | N/A | N/A | N/A | PENDING | N/A | PENDING | PENDING |
| `R-LIFECYCLE-04` | N/A | N/A | N/A | N/A | N/A | PENDING | PENDING |
| `R-LIFECYCLE-05` | N/A | N/A | N/A | PENDING | N/A | N/A | PENDING |
| `R-BEHAVIOR-01` | N/A | N/A | PENDING | N/A | N/A | N/A | PENDING |
| `R-BEHAVIOR-02` | N/A | PASS | N/A | N/A | N/A | N/A | PENDING |
| `R-BEHAVIOR-03` | N/A | N/A | N/A | N/A | N/A | PENDING | PENDING |
| `R-BEHAVIOR-04` | N/A | N/A | PENDING | N/A | N/A | N/A | PENDING |
| `R-BEHAVIOR-05` | PASS | PASS | PENDING | PENDING | PENDING | PENDING | PENDING |

---

## 从模块二晋升的新增检查项（动态追加区）

> 当 [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md) 中出现具有复用价值的新风险时，按 `R-EVOLVED-01`、`R-EVOLVED-02` 递增编号追加至此表，供后续批次及未来项目复用。

| 编号 | 来源（`NR-xxx`） | 新增检查规则 | 适用场景 | 验收标准 |
| :--- | :---: | :--- | :--- | :--- |
| `R-EVOLVED-01` | `NR-001` | 引入 Kotlin Gradle Plugin 时须同时核对官方 `KGP × AGP × Gradle` 兼容性矩阵与本地 Gradle 缓存/代理环境，避免盲取最新稳定版导致下载受阻或跨大版本不兼容 | Batch 0 及任何构建插件版本变更 | 所选 KGP 版本落在官方兼容矩阵内，且在当前机器网络/缓存条件下可完成 `Gradle Sync`、`assembleDebug` 与 `testDebugUnitTest` |
| `R-EVOLVED-02` | `NR-002` | 警惕 Batch 0“无 `.kt` 源码时的空接假绿”：没有 `.kt` 文件时 Gradle 会因 `NO-SOURCE` 跳过 `compileDebugKotlin` 并延迟解析 `kotlinBuildToolsApiClasspath`（含 `kotlin-compiler-embeddable`） | 任何老项目首次接入 Kotlin 构建链路验收 | 只有当至少 1 个真实 `.kt` 文件完成 `compileDebugKotlin` 编译，才能认定 Kotlin 编译工具链已完整就绪 |
