# 模块二：新增风险与修复方式对照表（Harness 演进台账）

> **设计目的**：模块一记录的是“动手前靠经验和分析预判到的风险”，而本模块专门捕捉**实战中新冒出来的意外失败、边缘报错、测试退化或 Agent 误操作**，并将“现象 → 根因 → 修复方式 → Harness 演进动作”结构化固化下来。
>
> **演进闭环**：每当本表新增一条 `NR-xxx` 记录后，评估其是否具备共性；若是，则将其转化为可检查条款晋升到 [01-risk-checklist.md](01-risk-checklist.md) 的“动态追加区”，并视情况补充新的回归单测或更新 Agent 提示词约束。

---

## 一、新增风险与修复对照总表

| 新增编号 | 发现批次 | 涉及文件 / 函数 | 真实报错 / 异常现象 | 根因分类 | 修复方式（核心要点） | Harness 演进动作（规则 / 单测 / 提示词） | 晋升至模块一 |
| :--- | :---: | :--- | :--- | :---: | :--- | :--- | :---: |
| `NR-001` | Batch 0 | `build.gradle`（Kotlin Gradle Plugin 版本） | 引入最新稳定版 `2.4.21` 时 Gradle Sync 超时，随后构建报错 `Connect to 127.0.0.1:65533 failed: Connection refused` | `构建/插件` | 交叉核对官方兼容矩阵与本地 `~/.gradle/caches`，改用本地已缓存且官方完整支持 `AGP 8.12.2 + Gradle 8.13` 的 `2.3.21`（不修改用户全局 `~/.gradle/gradle.properties`） | 提炼版本选型前先查“官方兼容矩阵 + 本地缓存/代理”规则，避免盲取 latest 阻断构建 | `R-EVOLVED-01` |
| `NR-002` | Batch 1 | `:app:compileDebugKotlin`（首次编译 `.kt` 源码） | Batch 0 空接构建已通过，但 Batch 1 放入首个 `.kt` 文件后编译失败：`Could not resolve ':app:kotlinBuildToolsApiClasspath'`（缺失 `kotlin-compiler-embeddable-2.3.21.jar` 等 7 个编译器依赖） | `构建/插件` | 使用单次命令行参数 `-Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyPort=` 绕过失效本机代理完成 `2.3.21` 编译器依赖拉取与本地缓存，后续常规构建不再需要参数 | 揭示“无 `.kt` 文件时 Batch 0 通过属于延迟解析假绿”，晋升编译器工具链真实落盘验收条款 | `R-EVOLVED-02` |
| `NR-003` | Batch 2 | `app/src/androidTest`（`NotePadProviderTest.java`、`NotePadActivityTest.java`） | 尝试执行 `compileDebugAndroidTestJavaWithJavac` 核验 `androidTest` 混编兼容时报错：`程序包 android.test 不存在`（找不到 `ProviderTestCase2`、`ActivityInstrumentationTestCase2`、`MockContentResolver`），但对 `NotePad`/`NotePadProvider` 引用 0 报错 | `测试框架/Robolectric` | 确认属于旧项目升级至 `compileSdk 35` 后未配置 `useLibrary 'android.test.*'` 的历史基线状态（非本次 Kotlin 迁移引入）；按边界约束不擅自改动 `app/build.gradle` 依赖，改为如实修正 Harness L3 说明 | 修正 Harness `README.md` L3 验收描述，避免把“跑不了历史 `connectedDebugAndroidTest`”误判为迁移退化，晋升 `R-EVOLVED-03` | `R-EVOLVED-03` |
| `NR-004` | Batch 1–3 深度复审（原 Batch 3） | `TitleEditor.kt` → `onCreate(Bundle?)` | `javap -p TitleEditor.class` 暴露出 `protected void onCreate(android.os.Bundle)`，而原 `TitleEditor.java` 显式声明为 `public void onCreate`——Kotlin `override fun` 静默收窄了方法可见性 | `字节码/互操作` | 显式改为 `public override fun onCreate(savedInstanceState: Bundle?)`，并用 `javap -p` 与反射单测验证 `Modifier.isPublic(...)` | 将 `javap -p` 字节码比对纳入每批标准闭环，新增反射回归单测，晋升 `R-EVOLVED-04` | `R-EVOLVED-04` |
| `NR-005` | Batch 1–3 深度复审（原 Batch 1） | `NoteExport.kt` → `document(...)` | 原 Java 使用字符串 `+` 拼接容忍 `null` 而不抛异常；迁移后将 4 个参数写成非空 `String`，Kotlin 插入 `Intrinsics.checkNotNullParameter`，在 Java 传 `null` 时制造原本不存在的 `NullPointerException` | `空安全/平台类型` | 将 4 个参数放宽为 `String?`（`document(title: String?, body: String?, category: String?, time: String?)`） | 补充 `noteExportDocumentToleratesNullPartsLikeJavaStringConcat` 回归单测，晋升 `R-EVOLVED-05` | `R-EVOLVED-05` |
| `NR-006` | Batch 1–3 深度复审（原 Batch 1） | `NoteSearch.kt#normalize`、`NoteText.kt#preview/title`、`NoteExport.kt#filename` | 原 Java `String.trim()` 裁剪 `<= '\u0020'`（含 `\u0000` 等控制字符、不含 `\u3000`）；迁移后直接改调 Kotlin `String.trim()`（`Char.isWhitespace()`），导致 `\u0000` 不再被裁剪且 `\u3000` 行为静默改变，现有 56 项单测完全未拦截 | `行为/语义漂移` | 全部改用 `trim { it <= ' ' }` 严格对齐 Java `String.trim()` 语义 | 补充 `trimMatchesJavaStringTrimSemanticsForControlCharsAndFullWidthSpaces` 回归单测，晋升 `R-EVOLVED-06` | `R-EVOLVED-06` |
| `NR-007` | Batch 1–3 深度复审（原 Batch 2/3） | `NotePadProvider.kt#update` 及 `NotePadProvider`/`TitleEditor`/`NotesLiveFolder` 类修饰符 | (1) `update` 随手写成 `values ?: ContentValues()`，使传 `null` 时从原 Java 的 `NullPointerException` 漂移为 SQLite 的 `IllegalArgumentException: Empty values`；(2) `javap -p` 显示原非 `final` 公开组件类全部静默变为 `public final class` | `行为/语义漂移` / `字节码/互操作` | `update` 恢复显式抛 `NullPointerException` 保持异常契约一致；记录组件类默认 `final` 的可继承性收窄（当前仓库无子类继承，暂不盲目全加 `open`） | 补充 `notePadProviderUpdateWithNullValuesPreservesNullPointerException` 回归单测，晋升 `R-EVOLVED-07` | `R-EVOLVED-07` |
| `NR-008` | Batch 1–3 深度复审（原 Batch 1–3） | 6 个工具/任务类 + `DatabaseHelper` + `getOpenHelperForTest()` + `NOTE_STREAM_TYPES` | `javap -p` 实证：为规避 Kotlin `internal` 方法名修饰（`$app_debug`）对 Java 混编的破坏，将原本 package-private 的 6 个类与测试后门 `getOpenHelperForTest()` 全部静默扩张为 `public` API | `字节码/互操作` | 登记“混编期临时 `public` 清账清单”，待 Batch 5 根据是否保留 Java 测试统一决定收敛为 `internal` 还是显式保留文档说明 | 在模块一新增“临时可见性扩张清账”两阶段检查规则，晋升 `R-EVOLVED-08` | `R-EVOLVED-08` |
| `NR-009` | Batch 1–3 深度复审（Harness 自身缺陷） | `01-risk-checklist.md`（Batch 0–3 打卡表） | 现有 56 项单测从未点击 `TitleEditor` 的 `R.id.ok`（`android:onClick` 0 覆盖）、从未执行 `NotesLiveFolder`、从未测试 `NoteExportTask` 旋转重绑，且未核对 Lint 精确 Warning 增量，却被直接标成了 `PASS` | `Agent 越界改写` / 验收失真 | 引入 `UNVERIFIED` 状态并降级所有缺证项；同时在 `KotlinMigrationParityTest` 中补齐 `TitleEditor` XML 点击反射与 `NotesLiveFolder` 实测覆盖 | 修改 `README.md` 闭环协议与打卡状态定义，设立“反橡皮图章式 PASS”红线，晋升 `R-EVOLVED-09` | `R-EVOLVED-09` |
| `NR-010` | Batch 4b | `NoteEditor.kt` → `onCreate` / `saveNote` / `pasteNote` / `onActivityResult`（4 处 `Cursor?.use {}`） | `:app:lintDebug` 直接失败：`Lint found 4 errors, 37 warnings, 10 hints` — `Error: Implicit cast from Cursor to Closeable requires API level 16 (current min is 11) [NewApi]`（而编译与 API 34 单测完全不报错） | `Lint/静态检查` / `行为/语义漂移` | 编写基于原生 `Cursor.close()`（API 1）的 `private inline fun <R> Cursor.useCompat(...)` 替换全部 4 处 `kotlin.io.use {}`（无 `Closeable` 强转，也不引入 API 19 `addSuppressed`） | `lintDebug` 恢复 0 Error（同时实证全仓 Warning 精准维持基线 37 条）；`javap -v` 验证 0 `Closeable` 引用；晋升 `R-EVOLVED-10` | `R-EVOLVED-10` |

> **根因分类建议枚举**：`构建/插件`、`字节码/互操作`、`空安全/平台类型`、`生命周期/并发`、`行为/语义漂移`、`测试框架/Robolectric`、`Lint/静态检查`、`Agent 越界改写`。

---

## 二、Harness 演进决策规则

遇到未预期问题时，按以下 4 步决定如何升级 Harness：

1. **分类定性**：判断问题是一次性笔误，还是可复现的系统性陷阱（例如特定 API、特定插件版本、特定语言互操作模式）。
2. **最小修复**：在不突破迁移边界的前提下，给出可验证的最小修复，并保留修复前后的报错与 diff 片段。
3. **补强防线**（至少落地一项）：
   * **检查表晋升**：抽象为一条可判定的 `R-EVOLVED-xx` 检查项写入模块一。
   * **自动化护栏**：若现有单测未能第一时间拦住该问题，补充针对性单元测试或静态检查，防止后续批次复发。
   * **约束话术**：若是 Agent 倾向性导致的越界（如擅改签名/私自简化逻辑/橡皮图章式标 `PASS`），更新 `README.md` 中的边界声明或闭环协议。
4. **博客素材标记**：若该案例具备典型教学价值，在详情卡片中标记 `博客素材：推荐收录`，方便直接写入实践章节。

---

## 三、新增风险详情卡片（按发生顺序追加）

### NR-001：盲选最新 KGP 版本遭遇本机失效代理阻断与 AGP 兼容性校验
- **发现批次**：Batch 0
- **涉及位置**：`build.gradle` → `buildscript.dependencies`
- **发现手段**：`Gradle Sync` 超时（3m）+ `assembleDebug` 依赖解析报错
- **真实报错 / 现象**：
  ```text
  Could not resolve org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21.
  > Got socket exception during request. It might be caused by SSL misconfiguration
    > Connect to 127.0.0.1:65533 [/127.0.0.1] failed: Connection refused
  ```
- **根因分析**：
  1. 兼容性层面：通过官方文档核查，`AGP 8.12.2 + Gradle 8.13` 需使用 `KGP 2.3.0+`（旧版 `2.2.x` 官方上限仅至 `AGP 8.11.1`，强行搭配 `AGP 8.12` 存在 `NoSuchMethodError` 风险）。
  2. 环境层面：本机全局 `~/.gradle/gradle.properties` 中残留了 `127.0.0.1:65533` 代理配置，当前端口未开启，导致访问未缓存的新版本 `2.4.21` 必然失败；而本机 `~/.gradle/caches` 已缓存 `2.3.21`（同时满足 `KGP 2.3.x` 对 `AGP 8.12.2 + Gradle 8.13` 的官方支持范围）。
- **修复方式**：
  - 修复前：`classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21'`
  - 修复后：`classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21'`（保持用户全局配置不被越权篡改）
- **验证证据**：`Gradle Sync` 成功，`assembleDebug testDebugUnitTest lintDebug` 通过（56 passed, 0 skipped, 0 failed）。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-01）`
  - [ ] 是否补充回归测试：`否`（构建配置由 L1 `assembleDebug` 与 `Gradle Sync` 覆盖）
  - [ ] 是否更新边界/提示词约束：`否`
- **博客素材价值**：`推荐收录`

### NR-002：Batch 0“空接假绿”——首个 `.kt` 文件才触发 `kotlinBuildToolsApiClasspath` 懒解析
- **发现批次**：Batch 1
- **涉及位置**：`:app:compileDebugKotlin` → `ClasspathEntrySnapshotTransform` / `kotlinBuildToolsApiClasspath`
- **发现手段**：Batch 1 首次运行 `assembleDebug testDebugUnitTest lintDebug`
- **真实报错 / 现象**：
  ```text
  Execution failed for task ':app:compileDebugKotlin'.
  > Could not resolve all files for configuration ':app:kotlinBuildToolsApiClasspath'.
     > Could not download kotlin-compiler-embeddable-2.3.21.jar ...
     > Connect to 127.0.0.1:65533 [/127.0.0.1] failed: Connection refused
  ```
- **根因分析**：Gradle 与 KGP 2.x 采用懒解析策略。Batch 0 仓库中尚无 `.kt` 文件，`:app:compileDebugKotlin` 因 `NO-SOURCE` 直接跳过，并未实际解析 `kotlin-compiler-embeddable` 与 `kotlin-build-tools-impl`；当 Batch 1 引入首个 `.kt` 文件后，编译器依赖解析才真正发生并撞上失效代理。
- **修复方式**：
  - 单次执行 `assembleDebug testDebugUnitTest lintDebug -Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyPort=` 拉取 `2.3.21` 编译器依赖入本地缓存，随后去掉所有代理覆盖参数复跑常规构建通过。
- **验证证据**：常规无参构建与 `:app:testDebugUnitTest --rerun-tasks` 全部通过（56 passed, 0 skipped, 0 failed）。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-02）`
  - [ ] 是否补充回归测试：`否`
  - [ ] 是否更新边界/提示词约束：`否`
- **博客素材价值**：`推荐收录`

### NR-003：历史遗留 `androidTest` 在 `compileSdk 35` 下无法编译（暴露 Harness L3 假前提）
- **发现批次**：Batch 2
- **涉及位置**：`app/src/androidTest/java/com/example/android/notepad/NotePadProviderTest.java`、`NotePadActivityTest.java`
- **发现手段**：主动运行 `:app:compileDebugAndroidTestJavaWithJavac`
- **真实报错 / 现象**：
  ```text
  NotePadProviderTest.java:26: 错误: 程序包android.test不存在
  import android.test.ProviderTestCase2;
  ```
- **根因分析**：AOSP 早期样例的 `androidTest` 继承了已从 Android SDK 28+ 默认编译路径移除的 `android.test.*`，且原 `app/build.gradle` 未配置 `useLibrary 'android.test.runner'`。对 `NotePad`/`NotePadProvider` 的引用为 0 报错。
- **修复方式**：严守迁移边界不擅改 `app/build.gradle`，在 Harness `README.md` 如实修正 L3 验证说明。
- **验证证据**：`assembleDebug testDebugUnitTest lintDebug` 全部通过。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-03）`
  - [ ] 是否补充回归测试：`否`
  - [x] 是否更新边界/提示词约束：`是（更新 README.md L3 说明）`
- **博客素材价值**：`推荐收录`

### NR-004：`override fun` 隐式收窄原 Java `public` 方法可见性（`javap` 字节码实锤）
- **发现批次**：Batch 1–3 深度复审（引入于 Batch 3 `e7ce6da`）
- **涉及位置**：`app/src/main/java/com/example/android/notepad/TitleEditor.kt` → `onCreate(Bundle?)`
- **发现手段**：`javap -p app/build/tmp/kotlin-classes/debug/com/example/android/notepad/TitleEditor.class`
- **真实报错 / 现象**：
  ```text
  // 修复前 javap -p TitleEditor.class:
  protected void onCreate(android.os.Bundle);
  // 原 TitleEditor.java:
  public void onCreate(Bundle savedInstanceState)
  ```
- **根因分析**：开发者常误以为 Kotlin 不写修饰符一律默认 `public`，但对于 `override` 成员，Kotlin 会**继承父类被覆写成员的可见性**。`Activity.onCreate` 是 `protected`，因此去掉 `public` 后的 `override fun onCreate` 被编译器静默收窄回了 `protected`，破坏了原本对外的二进制与源码可见性契约。
- **修复方式**：
  - 修复前：`override fun onCreate(savedInstanceState: Bundle?)`
  - 修复后：`public override fun onCreate(savedInstanceState: Bundle?)`
- **验证证据**：修复后 `javap -p TitleEditor.class` 输出 `public void onCreate(android.os.Bundle);`；新增 `KotlinMigrationParityTest#titleEditorOnCreateRemainsPublicAndXmlOnClickFinishesActivity` 反射验证通过（`3089648`）。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-04）`
  - [x] 是否补充回归测试：`是（KotlinMigrationParityTest#titleEditorOnCreateRemainsPublicAndXmlOnClickFinishesActivity）`
  - [x] 是否更新边界/提示词约束：`是（将 javap -p 字节码比对写入 README.md L1.5 闭环）`
- **博客素材价值**：`推荐收录`（极佳的“编译全绿、56 项单测全绿，却只有 `javap` 才能揪出的字节码语义差”案例）

### NR-005：Java 字符串拼接容忍 `null` → Kotlin 非空参数反噬引发隐式 NPE
- **发现批次**：Batch 1–3 深度复审（引入于 Batch 1 `aba4458`）
- **涉及位置**：`app/src/main/java/com/example/android/notepad/NoteExport.kt` → `document(...)`
- **发现手段**：逐行对照原 `NoteExport.java` 语义与空安全边界审计
- **真实报错 / 现象**：原 Java 中 `title + "\n\n分类：" + category + ...` 对任一 `null` 参数均安全输出 `"null"`，绝不抛出异常；迁移后将 4 个参数写成非空 `String`，Kotlin 编译器会在方法入口自动插入 `Intrinsics.checkNotNullParameter`，Java 侧只要传入 `null` 就会立即抛 `NullPointerException`。
- **根因分析**：迁移时仅按“常规业务不会传 `null`”的直觉写了非空 `String`，违背了博客强调的跨语言边界“宁可偏向可空”原则。
- **修复方式**：
  - 修复前：`fun document(title: String, body: String, category: String, time: String): String`
  - 修复后：`fun document(title: String?, body: String?, category: String?, time: String?): String`
- **验证证据**：新增 `KotlinMigrationParityTest#noteExportDocumentToleratesNullPartsLikeJavaStringConcat` 通过（`3089648`）。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-05）`
  - [x] 是否补充回归测试：`是（KotlinMigrationParityTest#noteExportDocumentToleratesNullPartsLikeJavaStringConcat）`
  - [ ] 是否更新边界/提示词约束：`否`
- **博客素材价值**：`推荐收录`（直接落地博客第 1.2 节“原本 Java 时代不存在的 NPE”）

### NR-006：Java `String.trim()` 与 Kotlin `CharSequence.trim()` 的字符集语义静默漂移
- **发现批次**：Batch 1–3 深度复审（引入于 Batch 1 `aba4458`）
- **涉及位置**：`NoteSearch.kt#normalize`、`NoteText.kt#preview` / `#title`、`NoteExport.kt#filename`
- **发现手段**：逐行标准库语义比对审计
- **真实报错 / 现象**：
  - Java `String.trim()` 裁剪所有码值 `<= '\u0020'` 的字符（含 `\u0000` NUL、`\u0007` BEL 等 ASCII 控制字符，**不含**全角空格 `\u3000`）。
  - Kotlin `CharSequence.trim()` 裁剪 `Char.isWhitespace()`（**不含** `\u0000` / `\u0007`，却**包含**全角空格 `\u3000`）。
  - 迁移后 `NoteSearch.normalize("\u0000")` 从原本的 `""`（恢复全量列表）变成了 `"\u0000"`（按控制符搜索为空），`NoteText.title("\u0000", "正文")` 从原本回退正文变成了保留控制符标题；原有 56 项测试全未覆盖控制字符而全部放行。
- **根因分析**：同名 API（`.trim()`）在不同语言标准库中的边界定义不同，单纯依赖肉眼“语法等价”和常规 ASCII 测试集无法察觉。
- **修复方式**：
  - 修复前：`.trim()`
  - 修复后：`.trim { it <= ' ' }`（与 Java `String.trim()` 严格语义等价）
- **验证证据**：新增 `KotlinMigrationParityTest#trimMatchesJavaStringTrimSemanticsForControlCharsAndFullWidthSpaces` 通过（`3089648`）。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-06）`
  - [x] 是否补充回归测试：`是（KotlinMigrationParityTest#trimMatchesJavaStringTrimSemanticsForControlCharsAndFullWidthSpaces）`
  - [ ] 是否更新边界/提示词约束：`否`
- **博客素材价值**：`推荐收录`（非常硬核且容易被 99% 开发者和 Agent 忽略的 Java→Kotlin 标准库陷阱）

### NR-007：随手加默认值改变异常契约 + Kotlin 默认 `final class` 收窄可继承性
- **发现批次**：Batch 1–3 深度复审（引入于 Batch 2 `a038b77` 与 Batch 3 `e7ce6da`）
- **涉及位置**：`NotePadProvider.kt#update`、`NotePadProvider` / `TitleEditor` / `NotesLiveFolder` 类声明
- **发现手段**：逐行异常路径核对 + `javap -p` 字节码检查
- **真实报错 / 现象**：
  1. `NotePadProvider.update(uri, null, ...)` 在原 Java 会直接抛 `NullPointerException`；迁移时随手写了 `values ?: ContentValues()`，导致空 `ContentValues` 传入 SQLite 后改抛 `IllegalArgumentException: Empty values`。
  2. `javap -p` 显示原本非 `final` 的 `NotePadProvider`、`TitleEditor`、`NotesLiveFolder` 全部变为 `public final class`。
- **根因分析**：Agent 在消除平台类型可空警告时倾向于用 `?: 空对象` 兜底，无意改写异常契约；同时 Kotlin 类默认 `final` 会改变 JVM 字节码的可继承性。
- **修复方式**：
  - `NotePadProvider.update` 改为 `val incomingValues = values ?: throw NullPointerException("ContentValues must not be null")`，恢复原异常契约。
  - 经核查当前仓库无任何代码或测试继承上述组件类，保留 `final` 但在 Harness 明确记录该字节码变更。
- **验证证据**：新增 `KotlinMigrationParityTest#notePadProviderUpdateWithNullValuesPreservesNullPointerException` 通过（`3089648`）。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-07）`
  - [x] 是否补充回归测试：`是（KotlinMigrationParityTest#notePadProviderUpdateWithNullValuesPreservesNullPointerException）`
  - [ ] 是否更新边界/提示词约束：`否`
- **博客素材价值**：`推荐收录`

### NR-008：为规避 `internal` 名称修饰引发的系统性“可见性扩张”（Visibility Widening）
- **发现批次**：Batch 1–3 深度复审（横跨 Batch 1–3）
- **涉及位置**：`NoteCategory`、`NoteSearch`、`NoteText`、`NoteTimestamp`、`NoteExport`、`NoteExportTask`、`NotePadProvider.DatabaseHelper`、`NotePadProvider.getOpenHelperForTest()`、`NotePadProvider.NOTE_STREAM_TYPES`
- **发现手段**：`javap -p` 全量扫描已迁移 `.class`
- **真实报错 / 现象**：原本在 Java 中严格限制在包内可见（package-private）的 6 个工具/任务类及 `getOpenHelperForTest()` 测试后门，在字节码中全部变成了 `public`。
- **根因分析**：Kotlin 没有 package-private；而 `internal` 会把 JVM 方法名修饰为 `xxx$app_debug`，直接破坏尚未迁移的同包 Java 调用方与 Java 单测。混编阶段为了兼容性全部改成了 `public`，若缺乏清账机制，会导致内部实现与测试后门永久泄漏为公开 API。
- **修复方式**：在 Harness 中建立两阶段治理——混编期显式登记“临时 `public` 清账表”；在 Batch 5 收口时根据是否保留 Java 测试，逐项决定收窄为 `internal` 还是保留并说明理由。
- **验证证据**：`javap -p` 已列出完整清单，纳入 Batch 5 收口核查项。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-08）`
  - [ ] 是否补充回归测试：`否`
  - [ ] 是否更新边界/提示词约束：`否`
- **博客素材价值**：`推荐收录`

### NR-009：Harness 自身验收失真——把 0 测试覆盖项与不完整指标误标为 `PASS`
- **发现批次**：Batch 1–3 深度复审（审计 `01-risk-checklist.md`）
- **涉及位置**：`01-risk-checklist.md` 中 `R-BYTECODE-04`、`R-LIFECYCLE-03`、`R-BEHAVIOR-05` 及 `NotesLiveFolder` 覆盖情况
- **发现手段**：检索 `app/src/test` 全量测试用例，交叉比对打卡表与实际执行路径
- **真实报错 / 现象**：
  - `R-BYTECODE-04`（`title_editor.xml` 的 `android:onClick="onClickOk"` 反射）被标为 `PASS`，但原 56 项单测从未点击过 `R.id.ok`。
  - `NotesLiveFolder` 迁移后被标为已验，但原 56 项单测对其覆盖率为 **0**。
  - `R-LIFECYCLE-03`（`NoteExportTask` 跨旋转重绑）仅有普通成功/失败测试，未覆盖旋转重建，却标为 `PASS`。
  - `R-BEHAVIOR-05`（Lint Warning 增量）仅确认了 0 Error，并未核对具体 Warning 是否从 37 条上涨，却标为 `PASS`。
- **根因分析**：典型的 Agent 验收惯性——“我写代码时注意到了 + 原有 56 项测试全绿 = 顺手把所有相关检查项勾成 PASS”，混淆了“未报错”与“已实证覆盖”。
- **修复方式**：
  1. 在 `01-risk-checklist.md` 增设 `UNVERIFIED` 状态，将 `R-LIFECYCLE-03`（Batch 3 旋转重绑）与 `R-BEHAVIOR-05`（Lint 精确增量）诚实降级为 `UNVERIFIED`。
  2. 立即在 `KotlinMigrationParityTest` 中补写 `TitleEditor` 点击 `R.id.ok` 反射测试与 `NotesLiveFolder` 双分支测试，用真实执行证据关闭这两处 0 覆盖盲区。
- **验证证据**：`assembleDebug testDebugUnitTest lintDebug` 全部通过（**61 passed, 0 skipped, 0 failed**，`3089648`）。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-09）`
  - [x] 是否补充回归测试：`是（KotlinMigrationParityTest 新增 5 项定向回归单测）`
  - [x] 是否更新边界/提示词约束：`是（更新 README.md 闭环第 4 步与打卡表状态定义）`
- **博客素材价值**：`推荐收录`（最能体现 Harness 价值的核心反思：Harness 不是写完就一劳永逸的“打勾表单”，而是用来戳穿“假 PASS”的照妖镜）

### NR-010：Java `try (Cursor)` 惯性改为 Kotlin `Cursor.use {}` 在 `minSdk 11` 触发 `[NewApi]` 致命报错
- **发现批次**：Batch 4b
- **涉及位置**：`app/src/main/java/com/example/android/notepad/NoteEditor.kt` → `onCreate`、`saveNote`、`pasteNote`、`onActivityResult`（共 4 处）
- **发现手段**：Android Studio 静态分析 + `:app:lintDebug` 构建阻断
- **真实报错 / 现象**：
  ```text
  Lint found 4 errors, 37 warnings, 10 hints. First failure:
  NoteEditor.kt:113: Error: Implicit cast from Cursor to Closeable requires API level 16 (current min is 11) [NewApi]
                  contentResolver.query(noteUri, null, null, null, null)?.use { row ->
                                                                          ~~~
  ```
- **根因分析**：
  1. `android.database.Cursor` 自带 `close()`（API 1），但直到 **Android API 16（Jelly Bean）** 才实现 `java.io.Closeable` 接口。
  2. Kotlin 标准库的 `kotlin.io.use {}` 扩展定义在 `Closeable?` 上，内联展开后会调用 `kotlin.io.CloseableKt.closeFinally(Closeable, Throwable)`，在字节码层面对 `Cursor` 进行向 `java.io.Closeable` 的隐式类型转换。在 API 11–15 设备上运行会触发运行时兼容性崩溃，且直接击穿 Android Lint `NewApi` Error。
  3. 极具迷惑性的是：`:app:assembleDebug` 编译与 Robolectric（`@Config(sdk = 34)`）单测对此完全无感、全部通过；且修复为 `try/finally { close() }` 后，Kotlin IDE 检查甚至还会反向给出 `Warning: try-finally can be replaced with 'use()'` 的误导建议！这正印证了“必须跑通 `lintDebug` 且不能盲从 IDE 自动建议”的工程价值。
- **修复方式**：
  - 修复前：`contentResolver.query(...)?.use { row -> ... }`（4 处）
  - 修复后：在 `NoteEditor.Companion` 中定义仅调用 API 1 `Cursor.close()` 的 `private inline fun <R> Cursor.useCompat(block: (Cursor) -> R): R`（内部 `try { block(this) } finally { close() }`），替换全部 4 处调用。
- **验证证据**：
  - 修复后 `assembleDebug testDebugUnitTest lintDebug` 全部通过（0 Error；结合故障时 Lint 汇总，全仓 Warning 精准维持基线 **37 warnings**，0 增量）；
  - `javap -v NoteEditor.class` 确认常量池中 `Closeable` 引用数为 **0**；
  - 全部 **69** 项本地单测通过（`15e7724`）。
- **Harness 演进动作**：
  - [x] 是否晋升到模块一：`是（R-EVOLVED-10）`
  - [ ] 是否补充回归测试：`否`（由 `:app:lintDebug` `[NewApi]` 与 `javap -v` 常量池检查覆盖）
  - [ ] 是否更新边界/提示词约束：`否`
- **博客素材价值**：`推荐收录`（极佳的“编译通过、API 34 单测全绿、连 IDE 都劝你用 `use()`，唯独 Android Lint 揪出 `minSdk 11` 隐式 `Closeable` 强转炸弹”的实战案例）

---

> 每次新增 `NR-xxx` 时，复制下方模板并据实填写：

```markdown
### NR-011：<简短标题>
- **发现批次**：Batch X
- **涉及位置**：`path/to/File.kt` → `functionName(...)`
- **发现手段**：`assembleDebug` / `javap -p` / `testDebugUnitTest` / `lintDebug` / 人工走查
- **真实报错 / 现象**：
  ```text
  <粘贴关键错误栈、javap 字节码或失败测试名>
  ```
- **根因分析**：<为什么编译通过或失败，底层 Java/Kotlin/Android 机制是什么>
- **修复方式**：
  - 修复前：`<简明代码或签名>`
  - 修复后：`<简明代码或签名>`
- **验证证据**：`<修复后执行的命令、javap 输出及通过结果>`
- **Harness 演进动作**：
  - [ ] 是否晋升到模块一：`否` / `是（R-EVOLVED-xx）`
  - [ ] 是否补充回归测试：`否` / `是（TestClass#testMethod）`
  - [ ] 是否更新边界/提示词约束：`否` / `是（说明）`
- **博客素材价值**：`推荐收录` / `归档备查`
```
