# 模块二：新增风险与修复方式对照表（Harness 演进台账）

> **设计目的**：模块一记录的是“动手前靠经验和分析预判到的风险”，而本模块专门捕捉**实战中新冒出来的意外失败、边缘报错、测试退化或 Agent 误操作**，并将“现象 → 根因 → 修复方式 → Harness 演进动作”结构化固化下来。
>
> **演进闭环**：每当本表新增一条 `NR-xxx` 记录后，评估其是否具备共性；若是，则将其转化为可检查条款晋升到 [01-risk-checklist.md](01-risk-checklist.md) 的“动态追加区”，并视情况补充新的回归单测或更新 Agent 提示词约束。

---

## 一、新增风险与修复对照总表

| 新增编号 | 发现批次 | 涉及文件 / 函数 | 真实报错 / 异常现象 | 根因分类 | 修复方式（核心要点） | Harness 演进动作（规则 / 单测 / 提示词） | 晋升至模块一 |
| :--- | :---: | :--- | :--- | :---: | :--- | :--- | :---: |
| `NR-001` | Batch 0 | `build.gradle`（Kotlin Gradle Plugin 版本） | 引入最新稳定版 `2.4.21` 时 Gradle Sync 超时，随后构建报错 `Connect to 127.0.0.1:65533 failed: Connection refused` | `构建/插件` | 交叉核对官方兼容矩阵与本地 `~/.gradle/caches`，改用本地已缓存且官方完整支持 `AGP 8.12.2 + Gradle 8.13` 的 `2.3.21`（不修改用户全局 `~/.gradle/gradle.properties`） | 提炼版本选型前先查“官方兼容矩阵 + 本地缓存/代理”规则，避免盲取 latest 阻断构建 | `R-EVOLVED-01` |

> **根因分类建议枚举**：`构建/插件`、`字节码/互操作`、`空安全/平台类型`、`生命周期/并发`、`行为/语义漂移`、`测试框架/Robolectric`、`Lint/静态检查`、`Agent 越界改写`。

---

## 二、Harness 演进决策规则

遇到未预期问题时，按以下 4 步决定如何升级 Harness：

1. **分类定性**：判断问题是一次性笔误，还是可复现的系统性陷阱（例如特定 API、特定插件版本、特定语言互操作模式）。
2. **最小修复**：在不突破迁移边界的前提下，给出可验证的最小修复，并保留修复前后的报错与 diff 片段。
3. **补强防线**（至少落地一项）：
   * **检查表晋升**：抽象为一条可判定的 `R-EVOLVED-xx` 检查项写入模块一。
   * **自动化护栏**：若现有 56 项单测未能第一时间拦住该问题，补充针对性单元测试或静态检查，防止后续批次复发。
   * **约束话术**：若是 Agent 倾向性导致的越界（如擅改签名/私自简化逻辑），更新 `README.md` 中的边界声明或批前提示词。
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
- **博客素材价值**：`推荐收录`（生动说明“连写第一行 `.kt` 之前，Agent 盲选 latest 版本就可能翻车，而兼容性矩阵 + 缓存自检能立刻化险为夷”）

---

> 每次新增 `NR-xxx` 时，复制下方模板并据实填写：

```markdown
### NR-002：<简短标题>
- **发现批次**：Batch X
- **涉及位置**：`path/to/File.kt` → `functionName(...)`
- **发现手段**：`assembleDebug` / `testDebugUnitTest` / `lintDebug` / `connectedDebugAndroidTest` / 人工走查
- **真实报错 / 现象**：
  ```text
  <粘贴关键错误栈或失败测试名>
  ```
- **根因分析**：<为什么编译通过或失败，底层 Java/Kotlin/Android 机制是什么>
- **修复方式**：
  - 修复前：`<简明代码或签名>`
  - 修复后：`<简明代码或签名>`
- **验证证据**：`<修复后执行的命令及通过结果>`
- **Harness 演进动作**：
  - [ ] 是否晋升到模块一：`否` / `是（R-EVOLVED-xx）`
  - [ ] 是否补充回归测试：`否` / `是（TestClass#testMethod）`
  - [ ] 是否更新边界/提示词约束：`否` / `是（说明）`
- **博客素材价值**：`推荐收录` / `归档备查`
```
