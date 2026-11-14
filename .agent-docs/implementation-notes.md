# 实施说明：Kotlin、搜索与数据库迁移

派生自 [README](../README.md) 的 M1、R1、R2、E2、E3，适用于 2026-10-26 提案。以下为**未应用、未编译执行的参考片段**，不是当前工程配置；交互与范围以 README 为准。

## M1：构建配置

保留 Groovy，在根 `build.gradle` 中调整：

```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.13.2'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21'
    }
}
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

`app/build.gradle` 的关键增量如下；需与实际配置合并，不直接覆盖整文件：

```groovy
apply plugin: 'com.android.application'
apply plugin: 'org.jetbrains.kotlin.android'

android {
    namespace 'com.example.android.notepad'
    compileSdk 35
    defaultConfig {
        applicationId 'com.example.android.notepad'
        minSdk 23
        targetSdk 35
        testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
    }
    buildFeatures { viewBinding true }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation 'com.google.android.material:material:1.13.0'
    // 另加实际使用且已核对版本的 AppCompat、RecyclerView、
    // Lifecycle/ViewModel、协程、JUnit 与 AndroidX Test。
}
```

删除旧的固定 Build Tools 版本；补 `gradle.properties` 中 `android.useAndroidX=true`。无旧 support 依赖不默认启用 Jetifier。移除主 Manifest 的 package 属性，使用 Gradle namespace；逐项核对 Intent 入口并显式声明 exported。Runner 更改与测试源码迁移一起处理。

Gradle 保持现有 8.13。Gradle 运行 JDK、Java 编译目标、Kotlin JVM 目标分别核对；`jvmToolchain(17)` 需要真实可用的 JDK 17，本次只探测到 JDK 21，不能写成 JDK 17 已准备完成。最低 API 提升到 23 待设备范围确认。

## R2：参数化搜索

空关键词不增加 LIKE 条件，无分类筛选时传 null；分类落地前不传分类条件，不读取尚不存在的列。

```kotlin
data class NoteFilter(val selection: String?, val args: Array<String>?)

fun buildNoteFilter(keyword: String, category: String?): NoteFilter {
    val clauses = mutableListOf<String>()
    val args = mutableListOf<String>()
    val query = keyword.trim()
    if (query.isNotEmpty()) {
        val literal = query.replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
        clauses += "(title LIKE ? ESCAPE '!' OR note LIKE ? ESCAPE '!')"
        val pattern = "%$literal%"
        args += pattern
        args += pattern
    }
    if (category != null) {
        clauses += "category = ?"
        args += category
    }
    return if (clauses.isEmpty()) NoteFilter(null, null)
    else NoteFilter(clauses.joinToString(" AND "), args.toTypedArray())
}
```

关键词 `50%_!` 的两个参数均应为 `%50!%!_!!%`。`O'Reilly` 由参数绑定处理，无需手工拼接 SQL 引号。分类为 study 时必须是 `(标题匹配 OR 正文匹配) AND category = ?`，防止标题匹配绕过分类。

列名和排序只能来自固定代码/白名单，不接受用户拼接。列表至少读 `_id/title/note/modified`，默认排序：

```sql
modified DESC, _id DESC
```

ContentResolver 在后台查询，通过 `Cursor.use` 映射为不可变对象；ViewModel 防抖并保证最新结果优先。ContentObserver 响应 Provider 的 notifyChange，以当前筛选重查，随生命周期释放。

## R1 / E2：修改时间与增量升级

R1 使用已有时间列，不升级数据库。Provider 写入边界对比真实旧值，仅实际可变字段变化时更新 modified；新建同时初始化 created。撤销、旧入口与分类更新也遵循同一规则。

分类启用后，库版本 2→3；新建表增加同一字段，升级语句为：

```sql
ALTER TABLE notes
ADD COLUMN category TEXT NOT NULL DEFAULT 'uncategorized';
```

由 SQLiteOpenHelper 的升级事务承载，删除原 DROP TABLE 路径。同步列常量、Provider 投影映射、Note 模型、编辑保存与导出；仅改 SQL 不足以完成分类。

迁移测试建真实 v2 schema，写入固定样本，调用真实升级路径，核对版本、行数、旧列值与默认分类。没有更早版本样本时记录未知，不能捕获升级异常后重建库伪造成功。

## E3：导出

UI 使用 Activity Result `CreateDocument("text/plain")`，取消返回 null 时结束。后台调用 ContentResolver.openOutputStream，空流视为失败；UTF-8 Writer 正确关闭，关闭失败也不能提示成功。

若存在未保存输入，按确认后的规则保存成功，再导出已保存快照。输出标题、创建/修改时间、分类、正文，不写回源笔记。文件名清理路径分隔符和控制字符；TXT 不承诺导入恢复。

## P0 后的命令

以下在 NotePad 工程根目录执行，本轮没有运行。首次依赖下载需要网络与明确的仓库代理。本机外部 CLI 联网沿用 `http://127.0.0.1:65533`；Gradle JVM 代理需在用户本机配置，不提交凭据或机器路径，失败不静默直连。

```bash
bash ./gradlew --version
bash ./gradlew :app:assembleDebug
bash ./gradlew :app:testDebugUnitTest
```

构建与单测分开执行，零用例不能记为通过。Windows 用 gradlew.bat。debug APK 默认位于 `app/build/outputs/apk/debug/app-debug.apk`。

安装与设备/公开入口测试按明确执行确认安排，不通过混合命令自动启动。本次没有执行。
