# NotePad 期中实验：UI 美化验收与下一阶段交接文档

> **交接日期**：2026-11-23  
> **当前分支**：`feat/ui-polish`（切自 `exp/kotlin-migration-pure-kotlin-tests` 提交 `366e23f`）  
> **本阶段状态**：基于 `openai/apps-sdk-ui` 的 UI 美化与深色模式已完成代码落地、本地测试/Lint 验收与 6 张离线视觉渲染图用户签认；**模拟器自动化截图工作流与 Phase 2 `README.md` 重写明确划归下一阶段**。

---

## 一、目标与已锁定口径（不可随意推翻）

本项工作总目标是完成 **满分（20/20）期中作业答辩交付**，涵盖高辨识度 UI 升级、真实模拟器实拍素材与最终 `README.md` 编写。已与用户对齐并锁定的关键决策如下：

1. **分支与技术栈口径**：
   - 主开发分支为 **`feat/ui-polish`**（继承自纯 Kotlin 方案 B 分支 `exp/kotlin-migration-pure-kotlin-tests`），保留 `main`（纯 Java 基线 `b15fd9f`）及方案 A/B 双镜像对照分支不受污染；
   - 技术栈坚持 **Kotlin 2.3.21 + 原生 XML Views + SQLite / ContentProvider + CursorLoader + 0 第三方生产库依赖**，保持 `minSdk 11`、`targetSdk 34`、`compileSdk 35`、`AGP 8.12.2`、`Gradle 8.13`。
2. **期中评分细则映射口径（最终 `README.md` 必须严格遵循）**：
   - **基本功能（10 分）**：
     1. 笔记列表显示时间戳与智能更新（5 分）；
     2. 笔记标题/正文参数化实时搜索（5 分）。
   - **核心扩展 Top 2（8 分，重点展开）**：
     1. **笔记分类管理与搜索组合筛选**（含 SQLite `v2 → v3` 无损增量升级）；
     2. **全局 UI 现代化美化与完整深色模式**（参考 OpenAI 官方开源 `openai/apps-sdk-ui` 设计系统，含顶栏一键深/浅色切换与 `EmptyMessage` 空态）。
   - **额外加分项（明确呈现，不可遗漏）**：
     1. 单篇笔记 UTF-8 文本安全导出（SAF `ACTION_CREATE_DOCUMENT` + 后台 `AsyncTask` + `WeakReference` + 跨旋转状态保留）；
     2. 高可靠自动保存、异常容错恢复与现代 Android 适配（保存失败拦截返回/草稿恢复/“另存为新笔记”、API 33+ 预测性返回 `@TargetApi(33) BackNavigation`、Android 10+ 剪贴板焦点门控粘贴、`Cursor.useCompat` 守护 `minSdk 11`）；
     3. 工程质量体系（6 类→12 模块高内聚重构、70 项 JUnit 4 + Robolectric 自动化单测、全量渐进式 Java→Kotlin 迁移与字节码核验）。
   - **文档规范**：`README.md` 采用“评分对照索引表 → 基本功能 → 扩展功能（Top 2 + 额外加分项）→ 技术架构与代码规范（含简短**设计边界说明**与 OpenAI MIT 图标开源署名）→ 构建与运行指南”。
3. **当前根目录 `README.md` 状态**：
   - 根目录 [README.md](../README.md) 仍停留在旧版 Java 基线 `d0726f6`，**尚未修改**，须在下一阶段完成模拟器截图后再执行 Phase 2 统一重写。

---

## 二、本阶段已完成设计、实现与改动清单

### 2.1 核心设计落地（参考 `openai/apps-sdk-ui`，MIT License）
- **三层色彩与双模体系**：
  - 浅色模式：次级画布 `#F9F9F9` + 纯白卡片 `#FFFFFF`（8% 透明黑发丝边 `#140D0D0D`）+ 碳黑主字 `#0D0D0D` + 柔和实心黑主按钮 `#181818`；
  - 深色模式：ChatGPT 灵魂中性炭灰画布 `#212121` + 抬升卡片 `#303030`（按 `variables-primitive.css` 官方规则取消深色抬升层描边）+ 柔和实心白主按钮 `#F3F3F3`；
  - 三级文字层级：`ink`（主）/ `muted`（次）/ `tertiary` `#8F8F8F`（时间戳、计数、提示）；
  - API 分层主题：`values` / `values-v21` / `values-v23`（浅色状态栏图标）/ `values-v27`（浅色导航栏图标）与 `values-night` / `values-night-v21`（深色状态栏/导航栏及原生深色 `AlertDialog` / `PopupMenu` / `Spinner`），`TitleEditor` 同步接入 `AppDialogTheme`。
- **语义微胶囊与组件规范**：
  - 4 类笔记角标（`study` 蓝 / `work` 紫 / `life` 橘 / `uncategorized` 灰）与编辑页保存状态微标（`saved` 绿 / `editing` 橘 / `error` 红 / `neutral` 灰）统一采用 `6dp` 微圆角 + `soft-alpha` 半透明底色；
  - 胶囊按钮与筛选器应用 `1.33x` 水平留白比例，顶栏移除冗余投影（`stateListAnimator="@null"`）并做光学边缘对齐；
  - 编辑页采用去镀铬顶栏（移除顶栏下横贯分割线）+ 胶囊化 `Spinner`；
  - 列表空态基于既有 `@android:id/empty` 通过 `drawableTop` 落地 OpenAI `EmptyMessage` 徽章（`44dp` 圆角 `10dp` 软底 + `Notepad` 图标），不改动 ID 契约。
- **原厂 MIT 矢量图标与一键主题切换**：
  - 移植 6 个 OpenAI 官方 SVG 路径（保留 MIT 版权注释头，兼容 `minSdk 11` 无 `evenOdd` 依赖）：`ComposeEditSquare`（FAB）、`DarkMode`、`LightMode`、`ArrowLeft`、`DotsVertical`、`Notepad`；
  - 列表顶栏内置 `R.id.theme_toggle`（API 31+ 通过 `@TargetApi(31) NightModeToggle` 调用原生 `UiModeManager.setApplicationNightMode`，低版本自动隐藏并跟随系统）。
- **中文标题字重校准**：
  - 为 `HeadingLg` / `HeadingEditor` / `HeadingCard` / `BadgeLabel` / `TonalButton` 补齐 `textStyle="bold"`，避免 Android CJK 字体在 `sans-serif-medium` 下退化为 Regular 400。

### 2.2 改动文件完整清单（当前在工作区，尚未 `git commit`）

| 分类 | 文件路径（相对仓库根目录） | 变更类型 |
| :--- | :--- | :---: |
| **Kotlin 源码** | `app/src/main/java/com/example/android/notepad/NoteCategory.kt` | 修改（新增 4 分类徽章背景/文字色映射） |
| | `app/src/main/java/com/example/android/notepad/NotesList.kt` | 修改（绑定分类徽章配色 + API 31+ 一键深/浅色切换） |
| | `app/src/main/java/com/example/android/notepad/NoteEditor.kt` | 修改（动态保存状态微标 + 自定义胶囊 Spinner 布局） |
| **清单与测试** | `app/src/main/AndroidManifest.xml` | 修改（`TitleEditor` 切换至 `@style/AppDialogTheme`） |
| | `app/src/test/java/com/example/android/notepad/NoteUiPreviewTest.kt` | 修改（扩展渲染 Light/Dark 列表、编辑页与空态共 6 张图） |
| **色彩与主题** | `app/src/main/res/values/colors.xml`、`values/strings.xml`、`values/styles.xml`、`values-v21/styles.xml`、`color/category_tab_text.xml` | 修改 |
| | `app/src/main/res/values-night/colors.xml`、`values-night/styles.xml`、`values-night-v21/styles.xml`、`values-v23/styles.xml`、`values-v27/styles.xml` | 新增 |
| **布局文件** | `app/src/main/res/layout/notes_list.xml`、`noteslist_item.xml`、`category_filter.xml`、`note_editor.xml` | 修改 |
| | `app/src/main/res/layout/spinner_category_item.xml`、`spinner_category_dropdown.xml` | 新增 |
| **Drawable 与图标** | `app/src/main/res/drawable/button_plain.xml`、`category_tab.xml`、`new_note_background.xml`、`search_background.xml` | 修改 |
| | `app/src/main/res/drawable/card_item_background.xml`、`tonal_button.xml`、`spinner_capsule.xml`、`empty_icon_badge.xml`、`category_badge_{uncategorized,study,life,work}.xml`、`status_badge_{neutral,editing,saved,error}.xml`、`ic_{add,arrow_back,more_vert,dark_mode,light_mode,notepad}_24.xml` | 新增 |
| | `app/src/main/res/drawable-v21/button_plain.xml`、`card_item_background.xml`、`category_tab.xml`、`new_note_background.xml`、`tonal_button.xml`、`ic_notepad_24.xml` | 新增 |

> **工作区提醒**：`git status` 中除上述 UI 美化文件外，还存在本任务开始前已有的 `docs/实验报告/migrating_legacy_android_java_to_kotlin_with_agent (2).md`（已 staged）与 `docs/实验报告/NotePad_Java迁移Kotlin与Agent工程化实战教程.md`（未跟踪）及 `docs/.DS_Store`。下一阶段提交 UI 改动时请**仅暂存 `app/` 与本文档**，避免将无关报告草稿混入 UI 提交。

---

## 三、本阶段验证结果与诚实边界

| 证据层级 | 验证项与命令 | 结果 | 说明 |
| :--- | :--- | :---: | :--- |
| **技术构建** | `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/artechphy/Library/Android/sdk bash ./gradlew --offline --no-daemon :app:assembleDebug --console=plain` | **PASS** | 0 第三方生产依赖，保留 `minSdk 11` |
| **本地单测** | 同环境执行 `:app:testDebugUnitTest` | **70 / 70 PASS** | 0 failures / 0 errors / 0 skipped（原 69 项全过 + 新增深色/空态渲染） |
| **静态检查** | 同环境执行 `:app:lintDebug` | **PASS（0 Errors）** | 0 Error / 38 Warnings / 10 Hints；0 `[NewApi]`、0 `[VectorRaster]` |
| **视觉与用户验收** | `app/build/outputs/ui-preview/*.png`（6 张 Android Native View 离线渲染图） | **用户验收 PASS** | 已覆盖浅/深色列表、浅/深色编辑页、浅/深色空状态 |
| **真实模拟器运行** | 新版 UI 在 Android Studio 模拟器上的实机运行与交互 | **待下一阶段执行** | 尚未在真实模拟器上核验顶栏 `theme_toggle` 实机切换、系统状态栏/导航栏亮暗图标及真机截图 |

---

## 四、下一阶段任务规划（Next Stage）

### 阶段 1：提交 `feat/ui-polish` 基线（前置清理）
- 将本阶段通过验收的 `app/` UI 美化与深色模式改动打成一个干净原子提交（不混入 `docs/实验报告/` 的无关草稿）。

### 阶段 2：Antigravity + Android Studio 模拟器自动化截图工作流（核心待办）

复用仓库既有的公开 UI 测试框架 [tools/device-test/UiBridge.java](../tools/device-test/UiBridge.java) 与 [tools/device-test/device_client.py](../tools/device-test/device_client.py)（通过 Android `UiAutomation` 原生支持中文 UTF-8 注入、按 `resource-id`/文本精准点击及无损 `screencap`），编写可复跑脚本（建议路径：`tools/device-test/capture_readme_shots.py`），按 5 步闭环产出全套高颜值答辩截图至 **`docs/images/`**：

1. **Stage 0｜环境预检与安装**：
   - `adb devices -l` 确认模拟器在线（或拉起专用 AVD 并等待 `sys.boot_completed=1`）；
   - 安装最新 `app/build/outputs/apk/debug/app-debug.apk`；
   - 编译/安装 `UiBridge`（`python3 tools/device-test/build_bridge.py`），启动 `am instrument -w dev.notepad.check/.UiBridge` 并执行 `adb forward tcp:18765 tcp:18765`。
2. **Stage 1｜演示环境净化与种子数据**：
   - `adb shell pm clear com.example.android.notepad` 清空历史脏数据；
   - 开启 System UI Demo Mode（`settings put global sysui_demo_allowed 1` + 固定 `10:00` 满电满信号 0 通知）净化状态栏；
   - 通过公开 `ContentProvider`（`adb shell content insert`）预埋 4~5 条自然中文演示笔记：覆盖 4 个分类（学习/工作/生活/未分类）、带自然时间梯度、长正文（展示 2 行摘要省略号）、且关键词（如 `"Android"`）同时分布在一条笔记的标题和另一条笔记的正文中。
3. **Stage 2｜剧本驱动实拍并输出至 `docs/images/`（13 张精确清单）**：

| 序号 | 输出文件名（`docs/images/`） | 截取界面状态 | 对应证明评分项 |
| :---: | :--- | :--- | :--- |
| 1 | `01-notes-list-overview.png` | 浅色主列表全景（4~5 条中文演示笔记、4 类彩色微胶囊、时间戳、搜索栏、FAB、顶栏月亮按钮） | 基本功能 1（时间戳）+ Top 1（分类展示）+ Top 2（UI 美化） |
| 2 | `02-timestamp-sort-update.png` | 经真实 UI 编辑旧笔记并保存返回列表后：该笔记置顶且修改时间刷新，其余笔记时间不变 | 基本功能 1（倒序排序 + 智能时间戳仅变更更新） |
| 3 | `03-search-title-and-body.png` | 搜索框输入 `"Android"`（收起键盘），同时命中标题与正文匹配笔记，计数同步更新 | 基本功能 2（标题 + 正文实时搜索） |
| 4 | `04-search-empty-state.png` | 搜索不存在关键词（收起键盘），居中展示 OpenAI `EmptyMessage` 徽章空态与 `"没有匹配的笔记"` | 基本功能 2（搜索空态）+ Top 2（空态 UI 美化） |
| 5 | `05-category-filter-combo.png` | 选中 `"学习"` 实心胶囊 + 叠加关键词搜索的组合筛选状态 | Top 1（分类筛选 + 搜索组合查询） |
| 6 | `06-editor-unified-ui.png` | 浅色统一编辑页（去镀铬顶栏、保存状态微胶囊、`"完成"` 胶囊按钮、标题、胶囊 `Spinner`、正文） | Top 1（分类编辑）+ Top 2（统一编辑页 UI） |
| 7 | `07-dark-mode-list.png` | 点击顶栏月亮图标切换至深色模式后的列表页全景（`#212121` 画布 + `#303030` 无边框卡片 + 太阳图标） | Top 2（深色模式 + 一键切换） |
| 8 | `08-dark-mode-editor.png` | 深色模式下的统一编辑页（含深色状态栏/导航栏与反转 `"完成"` 按钮） | Top 2（全应用深色一致性） |
| 9 | `09-editor-more-menu.png` | 编辑页点击 `⋮` 展开的溢出菜单（展示 `"导出为文本"` / `"撤销本次编辑"` / `"删除笔记"`） | 额外加分 1（文本导出入口）+ 额外加分 2 |
| 10 | `10-export-saf-picker.png` | 点击 `"导出为文本"` 拉起的系统 DocumentsUI 保存界面（自动预填清洗后的 `<标题>.txt`） | 额外加分 1（SAF 文本导出交互） |
| 11 | `11-export-file-content.png` | 文件管理器/阅读器打开导出的 UTF-8 `.txt` 文件（清晰显示标题、分类、修改时间与正文） | 额外加分 1（UTF-8 导出文件内容验证） |
| 12 | `12-delete-confirm-dialog.png` | 编辑页点击 `"删除笔记"` 弹出的二次确认对话框（浅色或深色原生主题） | Top 2 / 额外加分 2（防误删交互优化） |
| 13 | `13-unit-test-lint-pass.png` | 终端或 HTML 报告截图：70/70 单元测试 PASS + Lint 0 Error | 代码规范与工程验证证据 |

4. **Stage 3｜Antigravity 视觉自动质检**：
   - 脚本执行完毕后，由 Agent 用 `read_file` 逐张查看 `docs/images/01~12*.png`，核验无软键盘遮挡、文字无截断、状态栏整洁、深色模式 `PopupMenu`/`AlertDialog`/`Spinner` 无发白错位；发现瑕疵仅重跑对应单张。
5. **Stage 4｜现场清理**：
   - 退出 Demo Mode、调用 `rpc('stop')` 停止 `UiBridge`、移除 `adb forward` 端口转发。

### 阶段 3：Phase 2 — 编写最终满分 `README.md`
- 严格对照已落盘的 `docs/images/*.png` 与 `feat/ui-polish` 真实源码，重写根目录 `README.md`；
- 明确标注 `openai/apps-sdk-ui`（MIT License）设计参考与图标署名；
- 最终经用户确认后，按需将 `feat/ui-polish` 合并至 `main` 作为课程最终提交版本。

---

## 五、下一轮快速接续提示词（可直接复用）

```text
请先阅读 docs/UI美化验收与下一阶段交接文档.md 和 tools/device-test/README.md。
当前在 feat/ui-polish 分支，UI 美化已验收通过。请按交接文档执行下一阶段：
1. 先将本次 UI 美化改动干净提交到 feat/ui-polish（不要混入 docs/实验报告/ 里的无关草稿）；
2. 实现并运行 Android Studio 模拟器自动化截图工作流，生成 docs/images/ 下全套截图并逐张视觉质检；
3. 截图确认后，进入 Phase 2 编写最终满分 README.md。
```
