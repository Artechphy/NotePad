# 阶段 C：Simplenote 方向的界面、分类与导出

> 以下为阶段 C 完成时的记录。后续已实际执行 M01 与基本流，发现复制粘贴失败；最新结果见[设备执行记录](acceptance/2026-11-07-basic-flows/执行结果.md)。本页的未执行状态是当时状态。

日期：2026-11-07。依据：用户选择 Simplenote，并要求完成阶段 C。此前阶段 A/B 的代码与本地验证沿用，本次未提交、发布或操作用户已有笔记数据库。

## 已实现

### 界面与编辑体验

- 白底、蓝色主操作、灰色辅助文字和细分隔线，列表展示标题、两行正文摘要、修改时间和分类。
- 顶部显示当前结果数量，提供搜索、分类筛选及更多操作；右下角为新建入口。
- 标题、分类、正文在同一编辑页面完成，保留返回自动保存，提供明确保存状态、完成按钮和删除确认。
- 标题为空时从正文生成简短标题；支持仅有标题的笔记。离开全空的新笔记时清理草稿，已有笔记不会因正文变空而被自动删除。
- 撤销本次编辑会一起恢复标题、正文和分类；页面重建复用同一草稿，保留输入与分类。
- 保留剪贴板粘贴、单独标题编辑及原 Provider/URI 入口。应用继续使用 Java/XML 与平台组件，未引入 Kotlin、Compose 或 Material 依赖。

### 笔记分类

- 每篇笔记一个分类，固定为“未分类／学习／生活／工作”，数据库保存稳定 ID。
- 分类与标题/正文搜索组合，清空关键词仍保留当前分类；重建列表保留关键词与分类。
- 在分类中点击新建会默认使用当前分类；空分类有独立提示。
- SQLite 数据库版本从 2 升至 3，仅增加带默认值的 category 列，旧笔记归为未分类；保留原有 ID、标题、正文和创建/修改时间。
- 未知旧版本升级会明确失败并保留数据，不再删表重建；未提供更早版本样本，不宣称兼容所有旧版本。
- Provider 拒绝未知或空分类；仅分类实际变化也会更新修改时间。

### 文本导出

- 编辑页“更多 → 导出为文本”，先保存当前笔记，再调用系统文件保存界面。
- 导出 UTF-8 文本，包含标题、分类、修改时间和正文；建议文件名清理路径字符并保留 Unicode 字符边界。
- 文件写入在后台执行，处理取消、无可用选择器、空返回、写入失败和关闭失败；失败不报告成功。
- 导出任务在 Activity 配置重建时保留，释放旧页面引用；导出本身不改源笔记时间。
- 系统文件选择器要求 API 19 或更高；低版本禁用该入口。未申请广泛存储权限。
- 本功能为单篇文本导出，不承诺批量恢复或云同步。

## 实际检查结果

- `:app:testDebugUnitTest`：**42 项执行，0 失败、0 错误、0 跳过**，其中 1 项生成并检查原生布局渲染。
- `:app:assembleDebug`：**PASS**，生成阶段 C debug APK。
- `git diff --check`：**PASS**。
- Android Lint：**FAIL（遗留配置）**，最终 1 项 Fatal、37 项 Warning。唯一 Fatal 为原工程 `targetSdk 23` 触发的 `ExpiredTargetSdkVersion`；未压制、未新增 baseline。新增布局方向和内部跳转错误均已修复，查询对象的页面引用风险也已修复。警告包括旧图标、RTL/文本资源与组件导出声明等，完整清单见 `app/build/reports/lint-results-debug.html`。
- 设备安装、启动及系统文件选择器实际交互：**NOT_RUN**。前一阶段独立模拟器启动崩溃，本轮未重复设备测试。
- 用户/教师人工验收：**NOT_RUN**。

| 测试类 | 用例数 | 范围 |
| --- | --- | --- |
| NoteTimestampTest | 2 | 固定时间、跨日和缺失值 |
| NoteSearchTest | 3 | 空输入、绑定参数和字符转义 |
| NoteTextTest | 3 | 标题生成、摘要与 Unicode 截断 |
| NoteExportTest | 3 | UTF-8、文件名、写入与关闭错误 |
| NotePadProviderBehaviorTest | 12 | 搜索、修改时间、分类组合和非法分类 |
| NoteDatabaseMigrationTest | 2 | 真实隔离 v2 数据库文件升级、未知版本保护 |
| NotesListTest | 7 | 列表绑定、筛选、搜索、重建与新建分类继承 |
| NoteEditorBehaviorTest | 9 | 编辑保存、草稿重建、分类持久化、撤销、导出入口/取消/输出及失败反馈 |
| NoteUiPreviewTest | 1 | 390 × 844 dp 原生布局渲染 |

导出组件测试为系统保存界面核验 Intent，并为选择后的目标 URI 注册内存输出流；它验证应用侧流程，不等同于真实文档提供器或设备验收。数据库升级测试使用隔离 SQLite 文件，未迁移用户真实数据。

## 视觉检查

以下由实际 Android XML/View 在 Robolectric 原生图形环境中渲染，使用示例内容；并非模拟器或真机运行截图。已查看两张预览，标题、摘要、分类、正文与操作入口没有观察到重叠或裁切。

![笔记列表示例](design/simplenote-list-preview.png)

![编辑页示例](design/simplenote-editor-preview.png)

可编辑来源是 `app/src/main/res/layout/`、主题资源与 `app/src/main/java/`；`NoteUiPreviewTest` 可重建预览。

## 失败及修正

1. 首次资源编译发现 `saveFromParentEnabled` 不属于可用 XML 属性，改用 View 的公开方法设置后编译通过。
2. 新增测试使用 Robolectric 的重载 `Shadows.shadowOf` 时触发 compileSdk 23 缺少较新平台类型，改为显式类型的 `Shadow.extract`，未为此提高应用 SDK。
3. 第一轮扩展测试 42 项中导出异步测试 1 项失败：PAUSED 模式不支持旧调度器刷新方法。该测试采用显式 LEGACY 调度后，保持原断言，聚焦 9 项和完整 42 项均通过。
4. 首次离线 Lint 因 lint-gradle 未缓存而未执行，经核验指定代理后补全依赖继续检查。首次报告 6 项错误及 40 项警告；修复分类布局方向、应用内显式跳转及静态 Loader 引用等问题后，测试与 APK 任务再次通过，Lint 最终仅剩上述 targetSdk Fatal 及 37 项警告。组合验证命令整体以非零退出是由该 Lint Fatal 导致，不能标记为所有检查通过。

## 复现与产物

本机命令与 JAVA_HOME 问题的说明沿用[阶段一二记录](阶段一二验证记录.md)。在工程根目录运行：

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/Users/artechphy/Library/Android/sdk \
  bash ./gradlew --offline :app:testDebugUnitTest :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。

测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。

首次依赖下载沿用户指定的 `http://127.0.0.1:65533` 代理；本次官方资料通过内置网页工具直接访问，该工具不支持配置此代理。

应用 ID、Provider authority、compileSdk 23、minSdk 11 和 targetSdk 23 均保留。现代系统的常规安装兼容性仍需阶段 D 核验。

## 参考

- [Simplenote](https://simplenote.com/)：整体简洁方向。
- [Simplenote 官方帮助](https://simplenote.com/help/)：标签组织与导出交互参考。本次采用单分类和单篇文本导出。
- [Android 文档保存指南](https://developer.android.com/training/data-storage/shared/documents-files)：系统保存选择器。
- [SQLiteOpenHelper](https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper)：升级入口。
