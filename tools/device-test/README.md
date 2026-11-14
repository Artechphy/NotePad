# Android 公开 UI 测试工具

本工具用于 `docs/测试与验收流程.md` v1.0。它没有依赖被测应用的 Java 类或内部函数。

最新执行：[2026-11-14结果](../../docs/acceptance/2026-11-14-final-candidate/执行结果.md)。主线和基本流通过，UC02-A08受控故障场景失败；原始驱动问题及未覆盖项均在记录中保留。

- `UiBridge.java`：独立包 `dev.notepad.check` 中的 Instrumentation，通过 Android UiAutomation 读取实际 UI、设置文本。候选 APK 不嵌入测试代码。
- `device_client.py`：以当前可见控件的边界执行系统 `input tap/swipe/keyevent`，记录 UI 快照和命令；支持 UTF-8 文本输入与设备截图。
- `run_basic_flows.py`：按阶段执行准备、M01、分项基本流和真实版本升级。阶段有明确数据前提，不是可随意重复的无状态命令。
- `run_uc06_text.py`：从 5 条基线通过原生文本选择复制，检查普通文本粘贴、换行/表情保存及焦点恢复后不覆盖编辑；仅清理自己的两个临时笔记。
- `run_alternate_flows.py`：复用 v1.0 预期，分组执行常用新建、编辑、搜索、导出、删除、剪贴板及可读性备用流。`failures` 组通过公开 Provider 删除/恢复一条合成来源，受控触发保存前读取失败；编辑及重试仍从 UI 操作。该条件不代表磁盘满或全部 I/O 故障。
- `build_bridge.py`：使用本机 JDK/Android SDK 编译独立控制器；在临时目录生成专用测试签名，不读取或更换应用签名。

本次执行结果与所有已观察的失败历史见 `docs/acceptance/2026-11-07-basic-flows/执行结果.md`。准备失败、M01 恢复、文件选择器交互等人工编排动作也保留在该目录日志中，不能只看脚本退出码推定整轮通过。

## 本机准备

使用全新、仅含合成数据的测试实例。重启、创建、删除、剪贴板和导出会改变这个实例；不要将脚本指向个人设备或个人笔记库。设备就绪后安装冻结候选与控制器，并转发端口：

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/Users/artechphy/Library/Android/sdk \
  python3 tools/device-test/build_bridge.py

adb -s emulator-5580 install /private/tmp/notepad-device-test/bridge/bridge.apk
adb -s emulator-5580 forward tcp:18765 tcp:18765
adb -s emulator-5580 shell am instrument -w dev.notepad.check/.UiBridge
```

最后一条在独立终端保持运行。`ANDROID_HOME`、`JAVA_HOME`、`NOTEPAD_ADB` 可按本机路径设置；不修改全局配置。首次弹出的系统兼容提示和通知权限提示应先记录并正常处理，再断言应用列表已就绪。

## 阶段执行

```bash
PYTHONPATH=tools/device-test python3 tools/device-test/run_basic_flows.py prepare
PYTHONPATH=tools/device-test python3 tools/device-test/run_basic_flows.py m01-first
```

`prepare` 从既定基线准备/核对；本次最初 D01 在观察编辑页时手工建立。`m01-first` 停在真实系统保存界面，需根据实际 UI 创建/选中 `NotePad-验收` 文件夹、保存，并用设备文件管理器打开导出文本。实际动作应通过客户端记录，不能在宿主直接生成一个文本冒充成功。

确认设备阅读器中的文本后运行 `m01-finish`，它核对真实文件字节并继续进程重启、确认删除与文件保留。其余阶段为 `search-basic`、`edit-basic`、`export-begin`/`export-save`、`copy-basic`、`delete-basic`。各阶段先满足流程文档对应的数据前提。

升级在另一个空实例上先安装 `artifacts/v2_baseline.apk`，从其公开 UI 新建 U01/U02，执行 `migration-old`；再使用 `adb install -r artifacts/candidate.apk` 覆盖，执行 `migration-new`。`migration-old` 的第一张编辑页需先从旧应用新建入口打开。U01/U02 准备后不得卸载或清数据。用 `NOTEPAD_SERIAL=emulator-5582` 选择升级实例，转发端口也必须切换到该实例。

记录目录默认指向首次基本流目录；新一轮复测必须用 `NOTEPAD_TEST_OUTPUT` 指定新目录并绑定新的 APK 指纹，避免覆盖原始证据。例如：

```bash
NOTEPAD_TEST_OUTPUT="$PWD/docs/acceptance/2026-11-07-uc06-fix" \
  PYTHONPATH=tools/device-test python3 tools/device-test/run_basic_flows.py copy-basic
NOTEPAD_TEST_OUTPUT="$PWD/docs/acceptance/2026-11-07-uc06-fix" \
  PYTHONPATH=tools/device-test python3 tools/device-test/run_uc06_text.py
```

`db_snapshot` 仅只读导出隔离测试库，用于核对 ID、毫秒时间和版本的技术补证；所有业务变更仍从 UI 完成。UC06 修复的版本、断言与失败历史另见 `docs/acceptance/2026-11-07-uc06-fix/执行结果.md`。

结束后客户端 `rpc('stop')` 停止控制器，移除端口转发并关闭本轮临时模拟器。不得停止其他用户设备、ADB 服务或不属于本轮的实例。

## 备用流分组

在完整五条基线、竖屏和正常字号下执行；导出组还要求已从系统选择器创建并使用 `Downloads/NotePad-验收`。每组只清理本组临时笔记，失败即停止，保留现场后先判断产品、驱动或数据问题。

```bash
NOTEPAD_TEST_OUTPUT="$PWD/docs/acceptance/2026-11-14-final-candidate" \
  PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools/device-test \
  python3 tools/device-test/run_alternate_flows.py create
```

可选组：`create`、`editor`、`search`、`export`、`delete`、`clipboard`、`readability`、`failures`。其中 `failures` 是明确改变合成来源的故障注入，须仅用于专用实例；不能指向个人数据。恢复方案不满足 UC02-A08 预期时，应输出 FAIL 和非零退出，不能把现状改写成通过条件。

`NOTEPAD_CASES=UC01-A07,UC01-A08` 可筛选一组中的编号，适用于恢复基线后的聚焦续测；它不会自动修复前次失败留下的数据或界面。时间异常组也会通过公开Provider仅修改合成样本的时间字段，并用只读副本核验注入值。
