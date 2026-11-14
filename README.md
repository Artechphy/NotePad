# NotePad Android 期中实验

基于教师指定NotePad示例扩展的本地记事本，使用Java、XML Views、SQLite和ContentProvider。已实现两项必做功能，以及界面美化、笔记分类和单篇文本导出。

## 当前版本

当前功能与验证基线为提交 `d0726f6`（2026-11-14）。全量56项本地测试与debug构建通过；Lint为0 Error、0 Fatal、37 Warning。保存失败后的输入恢复缺口已修复，Android 14上的完整演示主线、相关回归及真实旧库覆盖升级通过。

原10项特殊分支、其他Android版本、实体真机和用户／教师人工验收仍未补齐。已有测试是Agent执行结果，不等同于整个人工验收完成。

| 题目范围 | 当前实现 |
| --- | --- |
| 必做 时间戳 | 列表显示最近修改时间，按设备时区格式化 |
| 必做 查询 | 同一关键词匹配标题或正文，可与分类组合 |
| 附加 界面美化 | 简洁列表、正文摘要、统一编辑页、空状态与保存反馈 |
| 附加 笔记分类 | 未分类、学习、生活、工作，支持保存和筛选 |
| 附加 文本导出 | 系统文件选择器保存单篇UTF-8文本 |

文本导出不包含导入恢复或云同步。当前实现保留Java/XML；Kotlin、Compose、Room和Material 3迁移属于历史提案。

## 文档入口

- [文档总览](docs/README.md)：当前维护文档、课程报告与历史记录的索引。
- [项目说明](docs/项目说明.md)：功能、模块职责、数据结构和行为边界。
- [构建运行与演示](docs/构建运行与演示.md)：已验证环境、命令和七步演示路径。
- [测试与交付状态](docs/测试与交付状态.md)：版本对应的实际结果和剩余项。
- [提交说明](docs/提交说明.md)：材料位置和提交前核对事项。
- [独立实验报告](docs/实验报告/NotePad期中实验报告.docx) · [Markdown正文](docs/实验报告/NotePad期中实验报告.md)。

## 构建与安装

Android Studio应打开含 `settings.gradle` 的本目录。当前使用AGP 8.12.2、Gradle 8.13、compileSdk 35、targetSdk 34、minSdk 11；本机验证使用JDK 21。

已设置正确JDK和SDK路径后执行：

```bash
bash ./gradlew --offline --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

`--offline` 适用于依赖已缓存的环境。首次下载、具体本机路径、Windows用法和安装步骤见[构建运行与演示](docs/构建运行与演示.md)。当前网络使用系统TUN路由，不设置旧HTTP／SOCKS显式代理。

可复核的冻结APK为[修复候选](docs/acceptance/2026-11-14-save-recovery-fix/artifacts/candidate.apk)，SHA-256：

```text
9a08c629f01fbb4aa08c8cae749f905b059984f61f81a7684b901b81c128ffbb
```

重新构建的默认输出为 `app/build/outputs/apk/debug/app-debug.apk`。文件名相同不保证字节与冻结候选相同；引用证据时应核对指纹。

## 数据和来源

应用ID为 `com.example.android.notepad`，Provider authority为 `com.google.provider.NotePad`。数据库位于应用私有目录 `databases/note_pad.db`，版本为3，已验证v2→v3增量升级。

覆盖安装保留数据需要相同应用ID和签名。卸载或清除应用数据会删除本地笔记，不能用作“保留旧数据升级”的步骤。

题目位于仓库上层 `题目/期中实验_NotePad记事本应用.pdf`，课程入口为[任务清单](../任务清单.md)。单独克隆本仓库不会取得上层题目及接续文件。

教师指定示例为 [fjnu-cse/NotePad](https://github.com/fjnu-cse/NotePad)，原源码的Android Open Source Project Apache-2.0版权头继续保留。[历史索引](docs/history/README.md)保存旧提案和阶段记录，历史配置与旧代理命令不作为当前指引。
