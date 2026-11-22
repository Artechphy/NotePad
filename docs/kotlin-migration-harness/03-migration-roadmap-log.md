# 模块三：迁移计划路线图与提交日志

> **设计目的**：按依赖拓扑逐批推进迁移，忠实记录每一次提交的**迁移文件、更改函数与签名、遇到的问题（关联 `R-xxx` / `NR-xxx`）以及四层验收结果**。所有数据均以真实终端输出和 Git 提交为准，不事后美化，不合并不同批次的验证结论。

---

## 一、迁移路线图总览

| 批次 | 目标范围 | 目标文件数 | 状态 | Commit | 本地单测（56项） | Lint（基线 0E/37W） | 命中/新增风险 |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Batch 0** | 基线确认 + Kotlin 插件接入（Groovy） | 0（仅构建） | `DONE` | `d3dcf25` | 56/56 PASS | PASS（0 Error） | `NR-001` → `R-EVOLVED-01` |
| **Batch 1** | 依赖末端纯逻辑工具类（5 个） | 5 | `DONE` | `aba4458` | 56/56 PASS | PASS（0 Error） | `NR-002` → `R-EVOLVED-02` |
| **Batch 2** | 契约类 + ContentProvider（2 个） | 2 | `DONE` | `a038b77` | 56/56 PASS | PASS（0 Error） | `NR-003` → `R-EVOLVED-03` |
| **Batch 3** | 导出异步任务 + 轻量兼容 Activity（3 个） | 3 | `DONE` | `e7ce6da` | 56/56 PASS | PASS（0 Error） | 无新增（预期风险全拦截） |
| **Batch 4a** | 核心列表页 `NotesList`（1 个） | 1 | `PLANNED` | — | — | — | — |
| **Batch 4b** | 核心编辑页 `NoteEditor`（1 个） | 1 | `PLANNED` | — | — | — | — |
| **Batch 5** | 收口复核 / 可选测试迁移与四层验收 | 待定 | `PLANNED` | — | — | — | — |

---

## 二、分批次详细计划与提交日志

### Batch 0：基线验证与 Kotlin 构建接入
- **状态**：`DONE`
- **Commit**：`d3dcf25`（前置 Harness 提交：`e75a006`）
- **计划改动文件**：
  - `build.gradle`
  - `app/build.gradle`
- **计划变更内容**：
  - 切出独立实验分支 `exp/kotlin-migration`，验证纯 Java 基线。
  - 引入与 `AGP 8.12.2` / `Gradle 8.13` 兼容的 Kotlin Gradle Plugin（显式 `org.jetbrains.kotlin.android`），统一 `jvmTarget`，保持 Groovy DSL 不变。
- **重点核对风险**：`R-BUILD-01`、`R-BUILD-02`、`R-BUILD-03`、`R-BEHAVIOR-05`
- **实际变更明细**：
  - `build.gradle`：在 `buildscript.dependencies` 中新增 `classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21'`。
  - `app/build.gradle`：应用 `apply plugin: 'org.jetbrains.kotlin.android'`；显式声明 `compileOptions`（`JavaVersion.VERSION_1_8`）与 Kotlin 2.x `kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }`；完整保留 Groovy DSL 与原 SDK/依赖配置。
- **遇到的问题与处理**：
  - 触发新增风险 `NR-001`（详见 [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md)）：初选最新稳定版 `2.4.21` 时受本机 `~/.gradle/gradle.properties` 失效代理（`127.0.0.1:65533`）阻断；经核对官方兼容矩阵与本地缓存，改用官方支持 `AGP 8.12.2 + Gradle 8.13` 且本地已缓存的 `2.3.21`，已晋升为 `R-EVOLVED-01`。
- **验证证据与盲区**：
  - 纯 Java 基线（改动前）：`assembleDebug testDebugUnitTest lintDebug` 全部通过（`:app:testDebugUnitTest` 56 passed, 0 skipped, 0 failed）。
  - Kotlin 接入后（`d3dcf25`）：`assembleDebug testDebugUnitTest lintDebug` 全部通过（`:app:testDebugUnitTest` 56 passed, 0 skipped, 0 failed）；Android Studio `Gradle Sync` 通过。
  - 盲区：本批尚未包含 `.kt` 源文件编译与设备测试；`lintDebug` 确认 0 Error/Fatal，具体 Warning 条目增量因构建报告目录受工作区访问限制暂未逐行导出比对。

---

### Batch 1：依赖末端纯逻辑工具类
- **状态**：`DONE`
- **Commit**：`aba4458`
- **计划迁移文件（5 个）**：
  1. `NoteTimestamp.java` → `NoteTimestamp.kt`
     - 关键函数：`format(millis: Long?, timeZone: TimeZone, locale: Locale): String?`
  2. `NoteText.java` → `NoteText.kt`
     - 关键函数：`truncate(text: String, maxCodePoints: Int)`、`preview(text: String?)`、`title(entered: String, body: String?)`
  3. `NoteCategory.java` → `NoteCategory.kt`
     - 关键常量/函数：`DEFAULT`、`indexOf(id: String?)`、`requireValid(id: String?)`、`idAt(position: Int)`、`label(context: Context, id: String?)`、`labels(context: Context)`
  4. `NoteSearch.java` → `NoteSearch.kt`
     - 关键函数：`normalize(keyword: String?)`、`selection(keyword: String?, category: String? = null)`、`arguments(keyword: String?, category: String? = null)`
  5. `NoteExport.java` → `NoteExport.kt`
     - 关键函数：`filename(title: String)`、`document(title, body, category, time)`、`write(stream: OutputStream?, document: String)`
- **选择依据**：处于依赖图最末端，无 Android 组件生命周期，且均有独立单元测试覆盖；保留 Java 测试不动可真实检验混编互操作。
- **重点核对风险**：`R-BYTECODE-01`、`R-BYTECODE-02`、`R-NULL-01`、`R-NULL-02`、`R-BEHAVIOR-02`、`R-BEHAVIOR-05`
- **实际更改函数与签名调整**：
  - 5 个 `final class` 统一迁移为 `object`，全部供 Java 调用的方法标注 `@JvmStatic`，避免 `internal` 名称修饰和 `.INSTANCE` 调用破坏（满足 `R-BYTECODE-01`、`R-BYTECODE-02`）。
  - `NoteCategory.DEFAULT` 使用 `const val` 暴露为 JVM 静态常量字段；`NoteSearch.selection` 与 `arguments` 使用 `@JvmStatic @JvmOverloads` 合并单参/双参重载。
  - 空安全与异常契约：`NoteTimestamp.format` 保持 `Long?`；`NoteSearch` / `NoteCategory` / `NoteText.preview` 保持可空 `String?`；`NoteText.title` 将 `body` 放宽为 `String?`；`NoteExport.write` 保持 `stream: OutputStream?` 并额外标注 `@Throws(IOException::class)` 以保留 Java 受检异常契约，字符集采用全 API 兼容的 `Charsets.UTF_8`（规避 `StandardCharsets` 的 API 19 限制）与 `.use {}` 自动关闭流。
  - 正则预编译：`NoteText.WHITESPACE`、`NoteExport.INVALID_FILENAME_CHARS` / `EDGE_DOTS_OR_SPACES` 抽为 `private val Regex` 常量。
- **遇到的问题与处理**：
  - 触发新增风险 `NR-002`（详见 [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md)）：首次编译 `.kt` 时才触发 `:app:kotlinBuildToolsApiClasspath` 懒解析并撞上失效代理；通过单次清空代理参数拉取 `2.3.21` 编译器依赖入本地缓存后，常规无参构建恢复全绿，并晋升为 `R-EVOLVED-02`。
- **验证证据与盲区**：
  - 5 个新 `.kt` 文件静态分析 0 Error。
  - 常规无参命令 `assembleDebug testDebugUnitTest lintDebug` 通过；`:app:testDebugUnitTest --rerun-tasks` 全部 **56 passed, 0 skipped, 0 failed**（包含未改动的全部 Java 混编单测：`NoteTimestampTest`、`NoteTextTest`、`NoteSearchTest`、`NoteExportTest` 及所有调用方组件测试）。
  - 盲区：本批尚未执行真机/模拟器测试；`NoteSearch`/`NoteText` 改为 Kotlin `trim()` 后会额外裁剪 Unicode 空白（如全角空格），对现有 ASCII 用例全兼容且更符合中文场景，但未额外覆盖特殊 Unicode 控制字符样本。

---

### Batch 2：契约类与数据访问层
- **状态**：`DONE`
- **Commit**：`a038b77`
- **计划迁移文件（2 个）**：
  1. `NotePad.java` → `NotePad.kt`
     - 关键成员：`AUTHORITY`、`Notes` 表名/列名/MIME/排序常量（`const val`）、URI 常量（`@JvmField val`）
  2. `NotePadProvider.java` → `NotePadProvider.kt`
     - 关键类/函数：`DatabaseHelper`（`onCreate`、`onUpgrade`）、`onCreate`、`query`、`getType`、`getStreamTypes`、`openTypedAssetFile`、`writeDataToPipe`、`insert`、`delete`、`update`、`getOpenHelperForTest`
- **选择依据**：全应用共享数据契约与持久化核心，向下仅依赖 Batch 1 的 `NoteCategory`，向上被所有 Activity 与 Provider 测试依赖。
- **重点核对风险**：`R-BYTECODE-01`、`R-BYTECODE-03`、`R-BYTECODE-05`、`R-NULL-03`、`R-BEHAVIOR-01`、`R-BEHAVIOR-04`、`R-BEHAVIOR-05`
- **实际更改函数与签名调整**：
  - `NotePad.kt`：转为 `object NotePad` 与嵌套 `object Notes : BaseColumns`。针对 Kotlin 不继承 Java 接口静态常量的特性，在 `Notes` 内显式转发 `const val _ID = BaseColumns._ID` 与 `_COUNT`；`Uri` 静态常量全部标注 `@JvmField`，保留 `CONTENT_ID_URI_PATTERN` 原始路径语义。
  - `NotePadProvider.kt`：`DatabaseHelper` 保持公开普通嵌套类（非 `inner`），`getOpenHelperForTest()` 保持 `public` 避免 `$app_debug` 名称修饰；`NOTE_STREAM_TYPES` 标注 `@JvmField`；`openTypedAssetFile` 标注 `@Throws(FileNotFoundException::class)`。
  - 数组不变性处理：在 `update()` 中将 `whereArgs: Array<String>?` 映射为局部 `Array<String?>?`，避免因 Kotlin `Array` 不变性导致追加可空列值 `incomingValues.getAsString(column)` 时编译失败，完整保留 `IS NOT ?` 变更检测与 `AND (...)` 括号优先级逻辑。
- **遇到的问题与处理**：
  - 触发新增风险 `NR-003`（详见 [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md)）：尝试跑 `:app:compileDebugAndroidTestJavaWithJavac` 检验历史 `androidTest` 时，暴露出旧基线在 `compileSdk 35` 下缺少 `android.test.*` 的既有限制（对 `NotePad`/`NotePadProvider` 引用 0 报错）。已修正 `README.md` L3 说明并晋升 `R-EVOLVED-03`。
- **验证证据与盲区**：
  - `NotePad.kt` 与 `NotePadProvider.kt` 静态分析 0 Error。
  - `assembleDebug testDebugUnitTest lintDebug` 通过；`:app:testDebugUnitTest --rerun-tasks` 全部 **56 passed, 0 skipped, 0 failed**（包含 `NoteDatabaseMigrationTest` 真实 `v2 → v3` 文件升级与未知版本拒升测试、`NotePadProviderBehaviorTest` 全 12 项行为回归）。
  - 盲区：`openTypedAssetFile` / `writeDataToPipe` 流式读取链路原有验证位于 `androidTest`（见 `NR-003`），当前未在真机/模拟器或新增本地单测中覆盖。

---

### Batch 3：异步导出任务与兼容型轻量 Activity
- **状态**：`DONE`
- **Commit**：`e7ce6da`
- **计划迁移文件（3 个）**：
  1. `NoteExportTask.java` → `NoteExportTask.kt`
     - 关键函数：`attach`、`detach`、`doInBackground`、`onPostExecute`、`deliver`
  2. `NotesLiveFolder.java` → `NotesLiveFolder.kt`
     - 关键函数：`onCreate`
  3. `TitleEditor.java` → `TitleEditor.kt`
     - 关键常量/函数：`EDIT_TITLE_ACTION`、`onCreate`、`onResume`、`onPause`、`onClickOk`
- **选择依据**：生命周期较简单、与其他页面耦合低，适合提前验证 `AsyncTask`、`WeakReference` 及 XML `android:onClick` 反射契约。
- **重点核对风险**：`R-BYTECODE-01`、`R-BYTECODE-04`、`R-NULL-04`、`R-LIFECYCLE-03`、`R-LIFECYCLE-05`、`R-BEHAVIOR-05`
- **实际更改函数与签名调整**：
  - `NoteExportTask.kt`：保持类与 `attach`/`detach` 为 `public`（供尚未迁移的 Java `NoteEditor` 直接调用，满足 `R-BYTECODE-01`）；`AsyncTask<Uri?, Void?, Exception?>` 的 `doInBackground(vararg uris: Uri?)` 增加空 URI 防御，保留 `WeakReference<NoteEditor?>` 与单次投递标志（满足 `R-LIFECYCLE-03`）。
  - `NotesLiveFolder.kt`：保留旧版 `LiveFolders` 兼容逻辑，`intent?.action` 采用安全调用（满足 `R-NULL-04`）。
  - `TitleEditor.kt`：`EDIT_TITLE_ACTION` 以 `companion object` + `const val` 暴露，保证 `NoteEditorBehaviorTest` 零改动编译；`mCursor`/`mText`/`mUri` 全部可空（满足 `R-LIFECYCLE-05`）；`onClickOk(v: View?)` 保持公开无默认参数单参签名以匹配 XML `android:onClick="onClickOk"` 反射调用（满足 `R-BYTECODE-04`）。
- **遇到的问题与处理**：
  - 本批未触发新增风险（预期风险 `R-BYTECODE-01`/`04`、`R-NULL-04`、`R-LIFECYCLE-03`/`05` 在批前编码阶段已全部前置拦截）。
- **验证证据与盲区**：
  - 3 个新 `.kt` 文件静态分析 0 Error。
  - `assembleDebug testDebugUnitTest lintDebug` 全部通过（`:app:testDebugUnitTest` **56 passed, 0 skipped, 0 failed**，含 `NoteEditorBehaviorTest` 中 `TitleEditor` 标题弹窗修改并更新时间戳用例）。
  - 盲区：`NotesLiveFolder` 与 `TitleEditor` XML `onClickOk` 反射点击路径未在真机/模拟器走查；`NoteExportTask` 跨旋转重绑需待真机/模拟器或后续定向测试进一步覆盖。

---

### Batch 4a：核心列表页 `NotesList`
- **状态**：`PLANNED`
- **Commit**：`待填写`
- **计划迁移文件（1 个）**：
  1. `NotesList.java` → `NotesList.kt`
     - 关键类/函数：`onCreate`、`updateSearch`、`addCategoryFilter`、`updateCategorySelection`、`reloadNotes`、`createNote`、`onSaveInstanceState`、`onCreateLoader`、`NotesLoader`、`onLoadFinished`、`onLoaderReset`、`onCreateOptionsMenu`、`onPrepareOptionsMenu`、`onOptionsItemSelected`、`onCreateContextMenu`、`onContextItemSelected`、`onListItemClick`
- **选择依据**：主入口列表页，涉及 `CursorLoader`、动态分类按钮、`SearchView`、`PopupMenu` 及上下文菜单，单独提交便于定位 UI 回归。
- **重点核对风险**：`R-NULL-04`、`R-LIFECYCLE-02`、`R-BEHAVIOR-05`
- **实际更改函数与签名调整**：_（执行后填写）_
- **遇到的问题与处理**：_（执行后填写，关联 `R-xxx` / `NR-xxx`）_
- **验证证据与盲区**：_（执行后填写）_

---

### Batch 4b：核心编辑页 `NoteEditor`
- **状态**：`PLANNED`
- **Commit**：`待填写`
- **计划迁移文件（1 个）**：
  1. `NoteEditor.java` → `NoteEditor.kt`
     - 关键类/函数：`onCreate`、`onWindowFocusChanged`、`updateStatus`、`saveNote`、`saveAndFinish`、`leaveEditor`、`onBackPressed`、`onPause`、`onSaveInstanceState`、`showActions`、`onCreateOptionsMenu`、`onPrepareOptionsMenu`、`onOptionsItemSelected`、`removeNote`、`saveAsNewNote`、`revertNote`、`pasteNote`、`exportNote`、`onActivityResult`、`showExportProgress`、`showExportResult`、`onRetainNonConfigurationInstance`、`onDestroy`、`BackNavigation`
- **选择依据**：全项目状态机最复杂、风险最高的页面，放在业务代码最后单独迁移，充分依托 `NoteEditorBehaviorTest`、`NoteEditorSaveFailureTest`、`NotePasteLifecycleTest` 护航。
- **重点核对风险**：`R-BYTECODE-01`、`R-NULL-04`、`R-LIFECYCLE-01`、`R-LIFECYCLE-03`、`R-LIFECYCLE-04`、`R-BEHAVIOR-03`、`R-BEHAVIOR-05`
- **实际更改函数与签名调整**：_（执行后填写）_
- **遇到的问题与处理**：_（执行后填写，关联 `R-xxx` / `NR-xxx`）_
- **验证证据与盲区**：_（执行后填写）_

---

### Batch 5：收口复核、可选测试迁移与四层验收
- **状态**：`PLANNED`
- **Commit**：`待填写`
- **收口核查项**：
  1. 确认是否保留 Java 测试作为混编黑盒护栏，或挑拣/全量迁移 `app/src/test`（若迁移测试则重点核对 `R-BYTECODE-06`）。
  2. 汇总全部 4 层验收结果：
     - L1：`./gradlew assembleDebug`
     - L2：`./gradlew testDebugUnitTest`（56/56）
     - 静态检查：`./gradlew lintDebug`（对比基线 0 Error / 37 Warning）
     - L3：`./gradlew connectedDebugAndroidTest`（标注是否具备设备环境）
     - L4：人工走查记录与明确未验证盲区声明
  3. 复盘模块二（`02-new-risks-and-fixes.md`）中的新增风险与演进成果，提炼博客实践结论。
