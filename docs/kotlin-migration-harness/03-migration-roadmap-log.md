# 模块三：迁移计划路线图与提交日志

> **设计目的**：按依赖拓扑逐批推进迁移，忠实记录每一次提交的**迁移文件、更改函数与签名、遇到的问题（关联 `R-xxx` / `NR-xxx`）以及四层验收结果**。所有数据均以真实终端输出和 Git 提交为准，不事后美化，不合并不同批次的验证结论。

---

## 一、迁移路线图总览

| 批次 | 目标范围 | 目标文件数 | 状态 | Commit | 本地单测（56项） | Lint（基线 0E/37W） | 命中/新增风险 |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Batch 0** | 基线确认 + Kotlin 插件接入（Groovy） | 0（仅构建） | `DONE` | `d3dcf25` | 56/56 PASS | PASS（0 Error） | `NR-001` → `R-EVOLVED-01` |
| **Batch 1** | 依赖末端纯逻辑工具类（5 个） | 5 | `DONE` | `aba4458` | 56/56 PASS | PASS（0 Error） | `NR-002` → `R-EVOLVED-02` |
| **Batch 2** | 契约类 + ContentProvider（2 个） | 2 | `DONE` | `a038b77` | 56/56 PASS | PASS（0 Error） | `NR-003` → `R-EVOLVED-03` |
| **Batch 3** | 导出异步任务 + 轻量兼容 Activity（3 个） | 3 | `DONE` | `e7ce6da` | 56/56 PASS | 0 Error（增量未导出） | 复审暴露 `NR-004`/`008`/`009` |
| **Batch 1–3 复审** | 字节码/语义/覆盖率深度审计与纠偏 | 5 修复 + 1 测试 | `DONE` | `3089648` | **61/61 PASS** | 0 Error（增量未导出） | 实锤 `NR-004`～`NR-009` → `R-EVOLVED-04`～`09` |
| **Batch 4a** | 核心列表页 `NotesList`（1 个） | 1 | `DONE` | `6eda74a` | **65/65 PASS** | 0 Error（增量未导出） | 0 新增故障；前置命中 `R-EVOLVED-04`/`05`/`06`/`09` |
| **Batch 4b** | 核心编辑页 `NoteEditor`（1 个） | 1 | `DONE` | `15e7724` | **69/69 PASS** | **PASS（0E / 37W）** | `NR-010` → `R-EVOLVED-10`（顺带关闭 3 批 `R-LIFECYCLE-03`） |
| **Batch 5 (镜像对照 A/B)** | 收口清账 `NR-008` + 方案 A/B 双分支镜像对照与四层验收 | A: 2 收敛 / B: 6 收敛 + 12 测试迁 Kotlin | `DONE` | A:`e9ac7ec` / B:`503cfba` | **双分支 69/69 PASS** | **双分支 0E / 37W** | 方案 B 命中 `NR-011` → `R-EVOLVED-11` |

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

### Batch 1–3 深度复审与纠偏（字节码 / 语义 / 覆盖率审计）
- **状态**：`DONE`
- **Commit**：`3089648`
- **审计动因**：Batch 1–3 表面上“全部 56 项单测一次通过”，但逐行比对原 Java、执行全量 `javap -p` 字节码检查及核查测试覆盖率后，揪出 **6 类真实被忽略的隐性退化与验收失真（`NR-004`～`NR-009`）**。
- **实际修改文件与函数清单**：
  1. `TitleEditor.kt` → `public override fun onCreate(savedInstanceState: Bundle?)`：修正 Kotlin `override` 隐式继承 `Activity.onCreate` 的 `protected` 可见性导致的静默收窄（`NR-004`）。
  2. `NoteExport.kt` → `document(title: String?, body: String?, category: String?, time: String?)` & `filename`：修复非空参数在 Java 传 `null` 时触发新 NPE 的退化（`NR-005`）；将 `.trim()` 改为 `.trim { it <= ' ' }` 对齐 Java 语义（`NR-006`）。
  3. `NoteSearch.kt` → `normalize(keyword: String?)`：`.trim()` → `.trim { it <= ' ' }`（`NR-006`）。
  4. `NoteText.kt` → `preview(text: String?)` & `title(entered: String, body: String?)`：`.trim()` → `.trim { it <= ' ' }`（`NR-006`）。
  5. `NotePadProvider.kt` → `update(...)`：将 `values ?: ContentValues()` 改回显式抛 `NullPointerException`，恢复原异常契约（`NR-007`）。
  6. 新增 `app/src/test/java/com/example/android/notepad/KotlinMigrationParityTest.java`（5 项定向回归单测）：覆盖 `TitleEditor.onCreate` 公开可见性 + `R.id.ok` XML `android:onClick` 反射点击、`NoteExport.document` 全 `null` 入参、`\u0000`/`\u3000` `trim` 等价性、`NotePadProvider.update(uri, null, ...)` NPE 契约、`NotesLiveFolder` 支持/不支持 Action 双分支（关闭 `NR-004`～`NR-007` 及 `NR-009` 部分 0 覆盖盲区）。
- **验证证据与仍保留的诚实盲区**：
  - `javap -p TitleEditor.class` 实证 `onCreate` 已恢复为 `public void onCreate(android.os.Bundle);`。
  - `assembleDebug testDebugUnitTest lintDebug` 全部通过（`:app:testDebugUnitTest` **61 passed, 0 skipped, 0 failed**）。
  - 仍保持 `UNVERIFIED` 的真实盲区：`NoteExportTask` 跨屏幕旋转重绑（`R-LIFECYCLE-03`）、`NotePadProvider.openTypedAssetFile/writeDataToPipe` 流式导出、以及 `lintDebug` 具体 Warning 增量条数；同时登记 `NR-008`（6 个工具/任务类与 `getOpenHelperForTest()` 临时 `public` 扩张）待 Batch 5 清账。

---

### Batch 4a：核心列表页 `NotesList`
- **状态**：`DONE`
- **Commit**：`6eda74a`
- **计划迁移文件（1 个）**：
  1. `NotesList.java` → `NotesList.kt`
     - 关键类/函数：`onCreate`、`updateSearch`、`addCategoryFilter`、`updateCategorySelection`、`reloadNotes`、`createNote`、`notesUri`、`onSaveInstanceState`、`onCreateLoader`、`NotesLoader`、`onLoadFinished`、`onLoaderReset`、`onCreateOptionsMenu`、`onPrepareOptionsMenu`、`onOptionsItemSelected`、`onCreateContextMenu`、`onContextItemSelected`、`onListItemClick`
- **选择依据**：主入口列表页，涉及 `CursorLoader`、动态分类按钮、`SearchView`、`PopupMenu` 及上下文菜单，单独提交便于定位 UI 回归。
- **重点核对风险**：`R-BUILD-02`、`R-NULL-04`、`R-LIFECYCLE-02`、`R-BEHAVIOR-05`、`R-EVOLVED-04`～`R-EVOLVED-09`
- **实际更改函数与签名调整**：
  - `NotesLoader`：保持为普通嵌套类 `private class NotesLoader`（非 `inner`），构造函数仅接收 `applicationContext` 等参数，`loadInBackground(): Cursor?` 保留异常兜底返回 `null`（满足 `R-LIFECYCLE-02`）。
  - 平台类型空安全（`R-NULL-04`、`R-EVOLVED-05`）：抽离 `private fun notesUri(): Uri = intent?.data ?: NotePad.Notes.CONTENT_URI` 避免多处隐式 `!!`；`onCreateContextMenu(menu: ContextMenu?, view: View?, menuInfo: ContextMenuInfo?)` 显式声明全可空参数，防止原 Java 未使用的 `view` 参数因 Kotlin 插入 `checkNotNullParameter` 制造新 NPE；`onLoadFinished`/`onLoaderReset`/`onListItemClick` 保持可空参数；`onPrepareOptionsMenu` 保持 `hasWindowFocus() && clipboard?.hasPrimaryClip() == true` 短路顺序与 `arrayOfNulls<MenuItem>(1)`。
  - 标准库等价性（`R-EVOLVED-06`）：`SimpleCursorAdapter.ViewBinder` 中标题空白判断直接采用 `title == null || title.trim { it <= ' ' }.isEmpty()`，前置避免 `\u0000`/`\u3000` 语义漂移。
  - 覆写可见性与 `final` 核对（`R-EVOLVED-04`、`R-EVOLVED-07`）：全部 11 个覆写方法（3 个 `protected` + 8 个 `public`）字节码修饰符与原 Java 100% 一致；`NotesList` 默认变为 `public final class`（全仓无子类继承）；`onCreateContextMenu`/`onContextItemSelected` 保持 `ClassCastException` 捕获日志及 `null` 抛 NPE 契约。
  - 补齐覆盖率缺口（`R-EVOLVED-09`）：在 `KotlinMigrationParityTest.java` 新增 4 项 `NotesList` 定向回归单测（`notesListLoaderIsStaticNonInnerAndShowsErrorOnNullCursor`、`notesListViewBinderMatchesJavaTrimAndEmptyPreviewSemantics`、`notesListItemClickSupportsEditPickAndGetContent`、`notesListOptionsAndContextMenuHandleCopyDeleteAndBadMenuInfo`）。
- **遇到的问题与处理**：
  - 本批 **0 新增意外故障**——Batch 1–3 深度复审沉淀下来的 `R-EVOLVED-04`/`05`/`06`/`09` 在编码前与 `javap` 验收中直接发挥了前置拦截作用：
    1. 前置按 `R-EVOLVED-06` 写了 `trim { it <= ' ' }`，没有在 `ViewBinder` 标题判空处重蹈 `NR-006` 覆辙；
    2. 前置按 `R-EVOLVED-05` 将 `onCreateContextMenu` 改为全可空参数，并用 `javap -c` 确认 0 个多余 `checkNotNullParameter`；
    3. 按 `R-EVOLVED-09` 主动盘点出原有单测对 `NotesList` 存在 7 处未覆盖分支，并用 4 项新单测打穿其中核心路径。
- **验证证据与盲区**：
  - **L1 构建**：`NotesList.kt` 静态分析 0 Error；`assembleDebug` 通过。
  - **L1.5 字节码核验**：
    - `javap -p NotesList.class` 对照原 Java 基线：全部 11 个覆写方法可见性与签名 100% 一致；无新增 `NR-008` 可见性扩张项。
    - `javap -p 'NotesList$NotesLoader.class'` 确认构造函数为 `(Context, Uri, String, String)`，无 `this$0` 外部引用。
    - `javap -c -p NotesList.class` 核对 `Intrinsics.checkNotNullParameter`，确认 `onCreateContextMenu` / `onLoadFinished` / `onLoaderReset` / `onListItemClick` 均为 0 隐式非空断言。
  - **L2 本地单测**：`assembleDebug testDebugUnitTest lintDebug` 全部通过（`:app:testDebugUnitTest` **65 passed, 0 skipped, 0 failed**，含原 `NotesListTest` 7 项、`NotePasteLifecycleTest#listMenuDoesNotReadClipboardWhileWindowIsUnfocused`、`NoteUiPreviewTest` 及新增 4 项 `NotesList` parity 测试）。
  - **仍保留的诚实盲区**：
    1. `list_more` 弹出 `PopupMenu` 交互、`onCreateContextMenu` 带真实 `ContextMenu` 充气菜单与 header 渲染，以及真机/模拟器上的实际触摸/长按交互尚未执行；
    2. `NotesLoader.loadInBackground()` 在后台线程真实抛出 `RuntimeException` 走 `catch` 返回 `null` 的路径未直接注入模拟（已测下游 `onLoadFinished(null, null)` 展示错误文案与 0 计数）；
    3. `lintDebug` 确认 0 Error/Fatal，具体 Warning 条数与基线（37）的精确增量仍保持 `UNVERIFIED`。

---

### Batch 4b：核心编辑页 `NoteEditor`
- **状态**：`DONE`
- **Commit**：`15e7724`
- **计划迁移文件（1 个）**：
  1. `NoteEditor.java` → `NoteEditor.kt`（全项目最后一个生产 Java 文件，至此 12 / 12 生产类 100% 迁移完成）
     - 关键类/函数：`onCreate`、`onWindowFocusChanged`、`category`、`isEmpty`、`updateStatus`、`saveNote`、`saveAndFinish`、`leaveEditor`、`onBackPressed`、`onPause`、`onSaveInstanceState`、`showActions`、`onCreateOptionsMenu`、`onPrepareOptionsMenu`、`onOptionsItemSelected`、`removeNote`、`saveAsNewNote`、`revertNote`、`pasteNote`、`exportNote`、`onActivityResult`、`showExportProgress`、`showExportResult`、`onRetainNonConfigurationInstance`、`onDestroy`、`BackNavigation`、`useCompat`
- **选择依据**：全项目状态机最复杂、风险最高的页面，放在业务代码最后单独迁移，充分依托 `NoteEditorBehaviorTest`、`NoteEditorSaveFailureTest`、`NotePasteLifecycleTest` 护航。
- **重点核对风险**：`R-BYTECODE-01`、`R-NULL-04`、`R-LIFECYCLE-01`、`R-LIFECYCLE-03`、`R-LIFECYCLE-04`、`R-BEHAVIOR-03`、`R-BEHAVIOR-05`、`R-EVOLVED-04`～`R-EVOLVED-10`
- **实际更改函数与签名调整**：
  - **早退安全初始化（`R-LIFECYCLE-01`）**：仅在 `setContentView` 后立即赋值的 4 个 View 字段使用 `lateinit var`；`try` 块中才赋值的 `mUri` 声明为可空 `Uri? = null`，6 个原始/已保存快照字符串默认初始化为空串 `""`，彻底杜绝 `onCreate` 异常早退（`finish(); return;`）后生命周期回调触发 `UninitializedPropertyAccessException`。
  - **可见性收敛（`R-BYTECODE-01`、`R-EVOLVED-08`）**：因调用方 `NoteExportTask.kt` 已先期迁入 Kotlin 且无 Java 测试引用，直接将 `showExportProgress()` 与 `showExportResult(success: Boolean)` 收敛为 `internal fun`，0 新增 `NR-008` 临时 `public` 债务。
  - **API 33 类隔离（`R-LIFECYCLE-04`）**：保留 `@TargetApi(33) private object BackNavigation` 独立类封装 `OnBackInvokedDispatcher` / `OnBackInvokedCallback`，仅在 `Build.VERSION.SDK_INT >= 33` 分支调用。
  - **API 11 `Cursor` 兼容（`NR-010`）**：新增 `private inline fun <R> Cursor.useCompat(...)` 替代 4 处 `kotlin.io.use {}`，避免对 `Cursor` 的 `Closeable`（API 16+）隐式强转。
  - **标准库等价（`R-EVOLVED-06`）**：`isEmpty()` 前置采用 `.trim { it <= ' ' }.isEmpty()`，严格对齐 Java `String.trim()` 对 `\u0000` 控制字符与 `\u3000` 全角空格的判定。
  - **定向补测与关闭遗留盲区（`R-EVOLVED-09`）**：在 `KotlinMigrationParityTest.java` 新增 4 项回归测试：
    1. `noteEditorEarlyExitOnInvalidUriSurvivesLifecycleWithoutLateinitCrash`（实证关闭 `R-LIFECYCLE-01`）；
    2. `noteExportTaskRetainsAcrossConfigurationChangeAndDeliversResultOnce`（使用 `controller.configurationChange(...)` 实测屏幕旋转时 `NoteExportTask` 保留、后台完成后向新实例投递结果、以及二次旋转不重复弹 Toast，**顺带将 Batch 3 遗留的 `R-LIFECYCLE-03` 从 `UNVERIFIED` 关闭为 `PASS`**）；
    3. `noteEditorEmptyCheckMatchesJavaTrimSemanticsForControlAndFullWidthSpaces`（实证 `isEmpty()` 符合 `R-EVOLVED-06`）；
    4. `noteEditorMenuDeleteDialogAndNewNoteRevertRemoveDraft`（补齐确认删除弹窗与新建撤销草稿路径）。
- **遇到的问题与处理**：
  - 触发新增风险 **`NR-010`**（详见 [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md)）：初版将原 Java 4 处 `try (Cursor row = ...)` 惯性改为 Kotlin `?.use {}`，被 `:app:lintDebug` 拦截（`Lint found 4 errors, 37 warnings, 10 hints` — `Implicit cast from Cursor to Closeable requires API level 16 (current min is 11) [NewApi]`）。改用基于原生 `Cursor.close()` 的 `useCompat` 修复，并晋升为 `R-EVOLVED-10`；该次报错同时也意外给出了全仓 Lint Warning 的精确计数（`37 warnings`，与基线 37 增量严格为 0）。
  - 编写旋转测试时还观察到一个 Robolectric 与 Android 原生生命周期细节：`ActivityController.recreate()` 不模拟 `onRetainNonConfigurationInstance` 传递，须用 `configurationChange(newConfig)`；此外 `onRestoreInstanceState` 发生在 `onCreate` 之后会触发 `TextWatcher.onTextChanged -> updateStatus()`，这与原 Java 行为一致，未擅自改写业务逻辑。
- **验证证据与盲区**：
  - **L1 构建与静态检查**：`NoteEditor.kt` 静态分析 0 Error；`assembleDebug testDebugUnitTest lintDebug` 全部通过（`lintDebug` 0 Error / 37 Warnings，增量 0）。
  - **L1.5 字节码核验**：
    - `javap -p NoteEditor.class` 确认全部 11 个覆写方法可见性与原 Java 100% 一致，`showExportProgress$app_debug` / `showExportResult$app_debug` 正确为 `internal`；
    - `javap -v NoteEditor.class` 确认常量池中 `OnBackInvoked*` 与 `Closeable*` 引用均为 **0**（API 33 隔离与 API 11 兼容性字节码铁证），`OnBackInvoked*` 仅存在于 `NoteEditor$BackNavigation.class`；
    - `javap -c -p NoteEditor.class` 确认 `onCreate`、`onActivityResult` 为 0 `checkNotNullParameter`。
  - **L2 本地单测**：`:app:testDebugUnitTest` **69 passed, 0 skipped, 0 failed**（原 56 + `KotlinMigrationParityTest` 13 项，全套 `NoteEditorBehaviorTest`、`NoteEditorSaveFailureTest`、`NotePasteLifecycleTest`、`NoteUiPreviewTest` 全绿）。
  - **仍保留的诚实盲区**：
    1. 未在真实 Android 设备 / 模拟器上执行 API 33+ 预测性返回手势（`BackNavigation` 回调真正触发 `leaveEditor()`）与 API 11–15 低版本实机验证；
    2. `Build.VERSION.SDK_INT < 19` 分支的 `exportNote()` 提示与 `ActivityNotFoundException` 兜底路径未在 `@Config(sdk = ...)` 多 SDK 矩阵下执行。

---

### Batch 5：收口复核、方案 A vs B 镜像对照与四层最终验收
- **状态**：`DONE`
- **双分支镜像 Commit**：
  - **方案 A（`exp/kotlin-migration`，推荐作混编护栏基准）**：`e9ac7ec` — `refactor(batch-5a): reconcile internal visibility for Scheme A (Java test guardrail)`
  - **方案 B（`exp/kotlin-migration-pure-kotlin-tests`，纯 Kotlin 测试对照镜像）**：`503cfba` — `refactor(batch-5b): migrate unit tests to Kotlin and tighten internal visibility (Scheme B)`
- **实际变更明细**：
  - **方案 A（`e9ac7ec`）**：
    1. `NoteExportTask.kt`：收敛为 `internal class NoteExportTask` 及 `internal fun attach` / `detach`（实证 Java 测试的 `instanceof NoteExportTask` 仍可零改动通过编译）；
    2. `NotePadProvider.kt`：`NOTE_STREAM_TYPES` 由 `@JvmField val` 收敛为 `private val`；
    3. 保留全部 12 个 Java 单元测试（69 项）作为持久的 Java→Kotlin 互操作黑盒护栏，并显式登记 7 项因 Java 测试保留 `public` 的符号清账理由。
  - **方案 B（`503cfba`，基于 `e9ac7ec` 切出）**：
    1. 将 `NoteCategory`、`NoteSearch`、`NoteText`、`NoteTimestamp`、`NoteExport` 全部收敛为 `internal object` 并移除冗余 `@JvmStatic` / `@JvmOverloads`；
    2. 将 `NotePadProvider.DatabaseHelper` 与 `NotePadProvider.getOpenHelperForTest()` 收敛为 `internal`（字节码变为 `getOpenHelperForTest$app_debug()`，彻底关闭测试后门泄漏）；
    3. 将 `app/src/test` 全部 12 个测试文件（69 项用例）1:1 迁移为 Kotlin（移除 12 个 `.java` 测试文件），完整落地并验证 Robolectric `@Implements(ClipboardManager::class)` 自定义 Shadow 的 Kotlin 写法。
- **遇到的问题与处理**：
  - 方案 B 触发新增风险 **`NR-011`**（详见 [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md)）：Java `protected` 同包可见，而 Kotlin `protected` 仅子类可见，导致 `NoteEditorBehaviorTest.kt` 和 `KotlinMigrationParityTest.kt` 无法直接裸调 `onActivityResult` 与 `onListItemClick`。采用测试侧反射封装，保住生产类 `protected` 契约不被反向破坏，并晋升为 `R-EVOLVED-11`。
  - 完整镜像对照与选型矩阵已独立沉淀至 [04-scheme-a-vs-b-comparison.md](04-scheme-a-vs-b-comparison.md)。
- **四层最终验收结果与盲区（双分支一致）**：
  - **L1 构建**：`assembleDebug` PASS（Groovy DSL / AGP 8.12.2 / Gradle 8.13 / minSdk 11 / 0 新增第三方库）。
  - **L1.5 字节码核验**：双分支均执行全量 `javap -p` 快照对比，完整验证了 `@JvmStatic` / `@JvmOverloads` / `$app_debug` 修饰差异与 `NoteEditor` API 33 / API 11 常量池隔离。
  - **L2 本地单测**：双分支均执行 `:app:testDebugUnitTest --rerun-tasks`，均获得 **69 passed, 0 skipped, 0 failed**。
  - **静态检查**：双分支 `lintDebug` 均通过（**0 Error / 37 Warnings**，较纯 Java 基线增量严格为 0）。
  - **L3 / L4 模拟器实机验收（`Pixel_6`，`emulator-5554`，Android 17 / API 37）**：
    - **方案 A（`8dc0e37`）与方案 B（`cb699e0`）均已完成对等全链路 9 项 E2E 实测 PASS**：
      1. **冷启动与空态渲染**：`NotesList` 正常展示 `"0 条笔记"`、5 个分类芯片、搜索栏、`list_more` 与 `+` 按钮；
      2. **新建、分类与 API 33+ 真实系统返回手势**：新建笔记选择 `"学习"` 分类（A：`Kotlin_Migration_Note` / B：`SchemeB_E2E_Note`），按系统返回键触发真实 `OnBackInvokedDispatcher` → `NoteEditor$BackNavigation`（双分支 Logcat 均实证 `WindowOnBackDispatcher` 注册了 `NoteEditor$BackNavigation$$ExternalSyntheticLambda0`）→ `leaveEditor()` 自动保存返回列表；
      3. **长按 `ContextMenu` + 真实剪贴板复制 + `list_more` `PopupMenu` + API 37 焦点门控粘贴**：长按弹出原生菜单（含动态 `CATEGORY_ALTERNATIVE` 项 `"Edit title"`），点击 `"复制"` 后打开 `list_more` 菜单点击已启用的 `"粘贴"`，成功在 Android 17 焦点门控下自动填充标题、正文与 `"学习"` 分类并保存；
      4. **分类筛选 + 正文检索 + 真实横竖屏旋转重建**：切换 `"工作"` 显示 `"这个分类还没有笔记"`；`"学习"` + 正文搜索（A：`API37` / B：`SchemeB_API37`）命中 2 条；真实旋转至横屏（`rotation=1`，新 Window 实例）后搜索词、选中分类与 2 条结果完整恢复；
      5. **真实端到端 SAF 导出（`DocumentsUI` + `NoteExportTask`）**：点击 `editor_more` → `"导出为文本"` 拉起系统 `com.google.android.documentsui`（A：`Kotlin_Migration_Note.txt` / B：`SchemeB_E2E_Note.txt`），点击 `SAVE` 后实测读回 `/sdcard/Download/*.txt`（A：99 字节 / B：90 字节 UTF-8，标题/分类/时间/正文格式完全吻合）；
      6. **`TitleEditor` 与 XML `android:onClick="onClickOk"` 实机反射**：从长按菜单 `"Edit title"` 进入对话框追加 `_Renamed`，点击 `"确定"` 正常反射提交并实时刷新列表；
      7. **双删除契约实证**：编辑页 `"删除笔记"` 弹出 `AlertDialog`（`"确定删除这篇笔记？"`）二次确认删除；列表长按菜单 `"删除笔记"` 无弹窗直接删除至 `"0 条笔记"`；
      8. **跨进程 `NotePadProvider` Binder IPC**：双分支均实测 `adb shell content query` 与 `content gettype`，正常返回全部列及 `vnd.android.cursor.{item,dir}/vnd.google.note`；
      9. **Logcat 体检**：双分支全程 `AndroidRuntime:E` 与全部业务 Tag（`NoteEditor`/`NotesList`/`NotePadProvider`/`TitleEditor`/`NoteExportTask`）`W+` 均为 **0 报错 / 0 崩溃**。
    - **仍诚实保留的 `UNVERIFIED` 盲区**：
      1. **API 11–15 低版本真机/模拟器实测**（当前环境仅有 API 37 镜像；API 11 兼容性仅由 `lintDebug` 0 `[NewApi]` Error 与 `javap -v` 0 `Closeable` 引用提供静态/字节码保证）；
      2. **`NotePadProvider.openTypedAssetFile` / `writeDataToPipe` 流式导出**：实测 `:app:connectedDebugAndroidTest` 再次确认历史 `app/src/androidTest` 在 `compileSdk 35` 基线下因缺少 `android.test.*` 无法编译（`NR-003`），且 `adb shell content read` 仅调用 `openFile()` 而非 `openTypedAssetFile()`，故该流式管道仍保持 `UNVERIFIED`；
      3. **`tools/device-test` Python 批量脚本**：本次采用实时 UI 树 + `adb` 交互逐项核验替代，未直接调用仓库内 Python 批量脚本。
