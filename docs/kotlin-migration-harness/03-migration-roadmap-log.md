# 模块三：迁移计划路线图与提交日志

> **设计目的**：按依赖拓扑逐批推进迁移，忠实记录每一次提交的**迁移文件、更改函数与签名、遇到的问题（关联 `R-xxx` / `NR-xxx`）以及四层验收结果**。所有数据均以真实终端输出和 Git 提交为准，不事后美化，不合并不同批次的验证结论。

---

## 一、迁移路线图总览

| 批次 | 目标范围 | 目标文件数 | 状态 | Commit | 本地单测（56项） | Lint（基线 0E/37W） | 命中/新增风险 |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Batch 0** | 基线确认 + Kotlin 插件接入（Groovy） | 0（仅构建） | `DONE` | `d3dcf25` | 56/56 PASS | PASS（0 Error） | `NR-001` → `R-EVOLVED-01` |
| **Batch 1** | 依赖末端纯逻辑工具类（5 个） | 5 | `PLANNED` | — | — | — | — |
| **Batch 2** | 契约类 + ContentProvider（2 个） | 2 | `PLANNED` | — | — | — | — |
| **Batch 3** | 导出异步任务 + 轻量兼容 Activity（3 个） | 3 | `PLANNED` | — | — | — | — |
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
- **状态**：`PLANNED`
- **Commit**：`待填写`
- **计划迁移文件（5 个）**：
  1. `NoteTimestamp.java` → `NoteTimestamp.kt`
     - 关键函数：`format(millis: Long?, timeZone: TimeZone, locale: Locale): String?`
  2. `NoteText.java` → `NoteText.kt`
     - 关键函数：`truncate(text: String, maxCodePoints: Int)`、`preview(text: String?)`、`title(entered: String, body: String)`
  3. `NoteCategory.java` → `NoteCategory.kt`
     - 关键常量/函数：`DEFAULT`、`indexOf(id: String?)`、`requireValid(id: String?)`、`idAt(position: Int)`、`label(context: Context, id: String?)`、`labels(context: Context)`
  4. `NoteSearch.java` → `NoteSearch.kt`
     - 关键函数：`normalize(keyword: String?)`、`selection(keyword: String?)`、`selection(keyword: String?, category: String?)`、`arguments(...)`
  5. `NoteExport.java` → `NoteExport.kt`
     - 关键函数：`filename(title: String)`、`document(title, body, category, time)`、`write(stream: OutputStream?, document: String)`
- **选择依据**：处于依赖图最末端，无 Android 组件生命周期，且均有独立单元测试覆盖；保留 Java 测试不动可真实检验混编互操作。
- **重点核对风险**：`R-BYTECODE-01`、`R-BYTECODE-02`、`R-NULL-01`、`R-NULL-02`、`R-BEHAVIOR-02`、`R-BEHAVIOR-05`
- **实际更改函数与签名调整**：_（执行后填写）_
- **遇到的问题与处理**：_（执行后填写，关联 `R-xxx` / `NR-xxx`）_
- **验证证据与盲区**：_（执行后填写）_

---

### Batch 2：契约类与数据访问层
- **状态**：`PLANNED`
- **Commit**：`待填写`
- **计划迁移文件（2 个）**：
  1. `NotePad.java` → `NotePad.kt`
     - 关键成员：`AUTHORITY`、`Notes` 表名/列名/MIME/排序常量（`const val`）、URI 常量（`@JvmField val`）
  2. `NotePadProvider.java` → `NotePadProvider.kt`
     - 关键类/函数：`DatabaseHelper`（`onCreate`、`onUpgrade`）、`onCreate`、`query`、`getType`、`getStreamTypes`、`openTypedAssetFile`、`writeDataToPipe`、`insert`、`delete`、`update`、`getOpenHelperForTest`
- **选择依据**：全应用共享数据契约与持久化核心，向下仅依赖 Batch 1 的 `NoteCategory`，向上被所有 Activity 与 Provider 测试依赖。
- **重点核对风险**：`R-BYTECODE-01`、`R-BYTECODE-03`、`R-BYTECODE-05`、`R-NULL-03`、`R-BEHAVIOR-01`、`R-BEHAVIOR-04`、`R-BEHAVIOR-05`
- **实际更改函数与签名调整**：_（执行后填写）_
- **遇到的问题与处理**：_（执行后填写，关联 `R-xxx` / `NR-xxx`）_
- **验证证据与盲区**：_（执行后填写）_

---

### Batch 3：异步导出任务与兼容型轻量 Activity
- **状态**：`PLANNED`
- **Commit**：`待填写`
- **计划迁移文件（3 个）**：
  1. `NoteExportTask.java` → `NoteExportTask.kt`
     - 关键函数：`attach`、`detach`、`doInBackground`、`onPostExecute`、`deliver`
  2. `NotesLiveFolder.java` → `NotesLiveFolder.kt`
     - 关键函数：`onCreate`
  3. `TitleEditor.java` → `TitleEditor.kt`
     - 关键常量/函数：`EDIT_TITLE_ACTION`、`onCreate`、`onResume`、`onPause`、`onClickOk`
- **选择依据**：生命周期较简单、与其他页面耦合低，适合提前验证 `AsyncTask`、`WeakReference` 及 XML `android:onClick` 反射契约。
- **重点核对风险**：`R-BYTECODE-01`、`R-BYTECODE-04`、`R-NULL-04`、`R-LIFECYCLE-03`、`R-LIFECYCLE-05`、`R-BEHAVIOR-05`
- **实际更改函数与签名调整**：_（执行后填写）_
- **遇到的问题与处理**：_（执行后填写，关联 `R-xxx` / `NR-xxx`）_
- **验证证据与盲区**：_（执行后填写）_

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
