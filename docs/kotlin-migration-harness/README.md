# NotePad Java → Kotlin 迁移 Harness

本目录用于约束、验收并持续演进 NotePad 项目的渐进式 Kotlin 迁移实践。核心理念源自《当 Android Studio 已经能把 Java 转成 Kotlin，为什么用 Agent 迁移老项目还是会翻车？》中的三项原则：**先锁基线与边界、按依赖风险分批、用分层证据闭环验收**。

## 模块组成

| 模块 | 文件 | 核心用途 |
| :--- | :--- | :--- |
| **模块一：风险与验收对照表** | [01-risk-checklist.md](01-risk-checklist.md) | 预先沉淀已知风险（构建、字节码/可见性/反射、跨语言空安全、生命周期、核心业务契约），每批验收时逐项核对 |
| **模块二：新增风险与修复演进表** | [02-new-risks-and-fixes.md](02-new-risks-and-fixes.md) | 记录迁移实操中新暴露的未预期问题、真实报错、修复方式与规则沉淀，驱动 Harness 自身持续演进 |
| **模块三：迁移路线图与提交日志** | [03-migration-roadmap-log.md](03-migration-roadmap-log.md) | 规划并追踪每次提交迁移的文件、变更函数/签名、遇到的问题与四层验证证据，可直接作为博客实战素材 |

## 不可突破的迁移边界（Invariants）

1. **构建脚本语言不变**：保留 Groovy DSL（`build.gradle`），不改为 `.gradle.kts`。
2. **AGP/SDK 版本不变**：保持 `AGP 8.12.2`、`Gradle 8.13`、`compileSdk 35`、`targetSdk 34`、`minSdk 11`，采用显式 `org.jetbrains.kotlin.android` 插件（不套用 AGP 9.0 内置 Kotlin 写法）。
3. **架构与 UI 栈不变**：保留传统 XML 布局、`ListActivity`、`CursorLoader`、`ContentProvider`、`SQLiteOpenHelper`、`AsyncTask`；不顺手引入 Compose、ViewBinding、Room、ViewModel 或协程。
4. **行为严格等价**：不顺手修改已有业务规则或已知交互差异（例如列表长按删除无确认框与编辑页确认删除的差异保持原样）。
5. **诚实验收原则**：严禁仅凭编译通过宣称完成，必须区分已执行的构建/本地单测/Lint/设备验证与未覆盖盲区。

## 每批次标准执行闭环

```text
1. 批前检查：阅读模块三当前批次范围 + 查阅模块一对应风险项（R-xxx）
       ↓
2. 增量迁移：仅迁移当前批次文件，严格守住边界，保持每批原子提交
       ↓
3. 自动化验证：运行 ./gradlew assembleDebug testDebugUnitTest lintDebug
       ↓
4. 逐项验收：对照模块一风险清单逐条核验；若发现新问题，立即修复并登记到模块二（NR-xxx）
       ↓
5. Harness 演进：将模块二中具有共性的新风险提炼为新检查项，反向晋升补充到模块一
       ↓
6. 沉淀日志：在模块三补全本批次 Commit、迁移文件、函数变更清单、问题链接与实测证据
```

## 标准验证命令

| 层级 | 命令 | 作用 |
| :--- | :--- | :--- |
| **L1 构建检查** | `./gradlew assembleDebug` | 验证 Java/Kotlin 混编、Kotlin 插件配置与资源打包 |
| **L2 本地单测** | `./gradlew testDebugUnitTest` | 运行 11 个测试类 / 56 项 JUnit + Robolectric 回归测试 |
| **静态检查** | `./gradlew lintDebug` | 对比基线（0 Error/Fatal，37 Warning），检查是否引入新告警 |
| **L3 仪器 / 设备测试** | 项目已有 `tools/device-test` 设备验收脚本（注：历史遗留 `app/src/androidTest` 依赖已从 compileSdk 35 移除的 `android.test.*`，当前基线不可直接跑 `connectedDebugAndroidTest`，详见 `NR-003`） | 需已连接模拟器/真机；验证真实进程下 Provider 与 Activity 行为 |
| **L4 人工走查** | 按核心路径实机/模拟器走查 | 覆盖屏幕旋转、剪贴板、导出、返回键等自动化盲区 |
