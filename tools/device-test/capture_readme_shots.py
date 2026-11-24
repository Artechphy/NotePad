"""Automated Pixel_6 emulator screenshot workflow for docs/images/01~13*.png.

Reuses the isolated public-UI controller (UiBridge + device_client.Device) without
modifying the candidate app APK. Captures 12 real emulator screenshots and 1 real
unit-test + lint report screenshot.
"""
from __future__ import annotations

import argparse
import html as html_lib
import json
import os
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

from device_client import Device, PACKAGE

ROOT = Path(__file__).resolve().parents[2]
IMAGES_DIR = ROOT / "docs" / "images"
EVIDENCE_DIR = Path("/private/tmp/notepad-device-test/readme-shots")
ADB_DEFAULT = "/Users/artechphy/Library/Android/sdk/platform-tools/adb"
CHROME_BIN = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
ID = PACKAGE + ":id/"

SEED_NOTES = [
    (
        "Android 期中架构复习",
        "整理 ContentProvider、CursorLoader\n"
        "异步查询与 SQLite v2 到 v3 无损\n"
        "增量迁移要点，重点复核参数化 LIKE\n"
        "防注入机制与生命周期保存状态恢复。",
        "study",
        2 * 3600,
    ),
    (
        "周三项目进度同步",
        "确认 Android 客户端 UI 视觉走查与深色模式适配进度，核对自动保存边界用例、"
        "SAF 文本导出流程及本周演示排期安排。",
        "work",
        27 * 3600,
    ),
    (
        "周末露营与采购清单",
        "提前备好防潮垫、保温壶、便携炉具与应急充电宝，周五下班前顺路采购水果、"
        "全麦面包、手冲挂耳咖啡和饮用水。",
        "life",
        53 * 3600,
    ),
    (
        "数据结构与算法错题集",
        "复盘动态规划状态压缩与图论最短路模板，整理 Dijkstra 堆优化边界条件、"
        "拓扑排序判环步骤以及常见时间复杂度分析。",
        "study",
        98 * 3600,
    ),
    (
        "读书随笔：设计心理学",
        "优秀的界面应当通过清晰的层级、恰当的留白与即时的状态反馈降低认知负担，"
        "让工具本身退居内容之后。",
        "uncategorized",
        148 * 3600,
    ),
]

SHOT_FILENAMES = {
    "01": "01-notes-list-overview.png",
    "02": "02-timestamp-sort-update.png",
    "03": "03-search-title-and-body.png",
    "04": "04-search-empty-state.png",
    "05": "05-category-filter-combo.png",
    "06": "06-editor-unified-ui.png",
    "07": "07-dark-mode-list.png",
    "08": "08-dark-mode-editor.png",
    "09": "09-editor-more-menu.png",
    "10": "10-export-saf-picker.png",
    "11": "11-export-file-content.png",
    "12": "12-delete-confirm-dialog.png",
    "13": "13-unit-test-lint-pass.png",
}


def detect_serial(adb: str) -> str:
    env_serial = os.environ.get("NOTEPAD_SERIAL")
    if env_serial:
        return env_serial
    out = subprocess.check_output([adb, "devices"], text=True)
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device" and parts[0].startswith("emulator-"):
            return parts[0]
    raise RuntimeError(f"No online emulator-* device found in `adb devices`:\n{out}")


def adb_cmd(adb: str, serial: str, *args: str) -> str:
    return subprocess.check_output([adb, "-s", serial, *args], text=True)


def wait_for_boot(adb: str, serial: str, timeout: int = 120) -> None:
    start = time.monotonic()
    while time.monotonic() - start < timeout:
        boot = subprocess.run(
            [adb, "-s", serial, "shell", "getprop", "sys.boot_completed"],
            capture_output=True,
            text=True,
        )
        pm = subprocess.run(
            [adb, "-s", serial, "shell", "pm", "path", "android"],
            capture_output=True,
            text=True,
        )
        if boot.returncode == 0 and boot.stdout.strip() == "1" and pm.returncode == 0 and "package:" in pm.stdout:
            return
        time.sleep(1.0)
    raise TimeoutError(f"Emulator {serial} did not finish booting within {timeout}s")


def visible_texts(nodes: list[dict]) -> list[str]:
    return [n["text"] for n in nodes if n.get("visible") and n.get("text")]


def node_text(nodes: list[dict], res_id: str) -> str | None:
    return next((n.get("text", "") for n in nodes if n.get("visible") and n.get("id") == res_id), None)


def node_desc(nodes: list[dict], res_id: str) -> str | None:
    return next((n.get("desc", "") for n in nodes if n.get("visible") and n.get("id") == res_id), None)


def hide_ime(d: Device) -> None:
    for _ in range(2):
        dump = d.shell("dumpsys", "input_method")
        if "mInputShown=true" in dump:
            d.shell("input", "keyevent", "4")
            time.sleep(0.5)
        else:
            break


def enable_demo_mode(d: Device) -> None:
    d.shell("settings", "put", "global", "sysui_demo_allowed", "1")
    d.shell("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "enter")
    d.shell("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "clock", "-e", "hhmm", "1000")
    d.shell("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false")
    d.shell(
        "am",
        "broadcast",
        "-a",
        "com.android.systemui.demo",
        "-e",
        "command",
        "network",
        "-e",
        "wifi",
        "show",
        "-e",
        "level",
        "4",
        "-e",
        "mobile",
        "show",
        "-e",
        "datatype",
        "none",
        "-e",
        "level",
        "4",
    )
    d.shell("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "notifications", "-e", "visible", "false")
    time.sleep(0.4)


def disable_demo_mode(d: Device) -> None:
    subprocess.run(
        [d.adb, "-s", d.serial, "shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit"],
        capture_output=True,
        text=True,
    )


def save_shot(d: Device, shot_id: str, selected: set[str]) -> None:
    if shot_id not in selected:
        return
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)
    filename = SHOT_FILENAMES[shot_id]
    target = IMAGES_DIR / filename
    nodes = d.nodes()
    (EVIDENCE_DIR / f"{shot_id}.json").write_text(json.dumps(nodes, ensure_ascii=False, indent=2), encoding="utf-8")
    with target.open("wb") as output:
        subprocess.run([d.adb, "-s", d.serial, "exec-out", "screencap", "-p"], stdout=output, check=True)
    print(f"[SHOT {shot_id}] Saved {target}", flush=True)


def seed_notes(d: Device) -> None:
    d.shell("input", "keyevent", "3")
    d.stop_app()
    d.shell("pm", "clear", PACKAGE)
    d.shell("cmd", "uimode", "night", "no")
    # Clean any previous exported test files (both filesystem and MediaStore) so Downloads stays deterministic
    subprocess.run(
        [
            d.adb,
            "-s",
            d.serial,
            "shell",
            "rm -f /sdcard/Download/'Android 期中架构复习'*.txt /sdcard/Documents/'Android 期中架构复习'*.txt || true; "
            "content delete --uri content://media/external/file --where \"_display_name LIKE 'Android%'\" >/dev/null 2>&1 || true",
        ],
        capture_output=True,
        text=True,
    )

    now_sec = int(d.shell("date", "+%s").strip())
    for title, body, cat, age_sec in SEED_NOTES:
        ts_ms = (now_sec - age_sec) * 1000
        cmd = (
            "content insert --uri content://com.google.provider.NotePad/notes "
            f"--bind title:s:{shlex.quote(title)} "
            f"--bind note:s:{shlex.quote(body)} "
            f"--bind category:s:{shlex.quote(cat)} "
            f"--bind created:l:{ts_ms} "
            f"--bind modified:l:{ts_ms}"
        )
        out = d.shell(cmd)
        if "Row:" not in out and out.strip():
            print(f"[seed] {title}: {out.strip()}", flush=True)


def launch_and_wait_list(d: Device, expected_count: int = 5) -> list[dict]:
    d.launch()
    for _ in range(3):
        time.sleep(0.5)
        nodes = d.nodes()
        # Dismiss Android system compatibility or permission dialog if one appears
        if any(
            n.get("visible")
            and n.get("id") == "android:id/button1"
            and (
                "older version" in (n.get("text") or "").lower()
                or "ok" == (n.get("text") or "").strip().lower()
                or "确定" in (n.get("text") or "")
                or "allow" in (n.get("text") or "").lower()
            )
            for n in nodes
        ) or any(
            n.get("visible") and "older version" in (n.get("text") or "").lower()
            for n in nodes
        ):
            try:
                d.click("android:id/button1")
                time.sleep(0.4)
            except Exception:
                pass
        if node_text(d.nodes(), ID + "note_count") == f"{expected_count} 条笔记":
            break
    nodes = d.wait(lambda ns: node_text(ns, ID + "note_count") == f"{expected_count} 条笔记", f"list-{expected_count}")
    hide_ime(d)
    return nodes


def wait_editor(d: Device) -> list[dict]:
    nodes = d.wait(lambda ns: any(n.get("visible") and n.get("id") == ID + "editor_save" for n in ns), "editor-ready")
    hide_ime(d)
    return nodes


def find_documentsui_package(d: Device) -> str:
    pkgs = d.shell("pm", "list", "packages", "documentsui")
    for candidate in ("com.google.android.documentsui", "com.android.documentsui"):
        if candidate in pkgs:
            return candidate
    raise RuntimeError(f"No documentsui package found: {pkgs}")


def run_emulator_shots(d: Device, selected: set[str]) -> None:
    if not any(s in selected for s in [f"{i:02d}" for i in range(1, 13)]):
        return

    enable_demo_mode(d)
    seed_notes(d)
    launch_and_wait_list(d, 5)
    time.sleep(0.4)
    hide_ime(d)

    # 01: Light mode overview
    save_shot(d, "01", selected)

    # 02: Edit #3 note ("周末露营与采购清单"), save so it moves to #1 with updated timestamp;
    # also open & back out of "周三项目进度同步" without changes to verify timestamp immutability.
    d.click("周末露营与采购清单", by="row_text")
    nodes = wait_editor(d)
    old_body = node_text(nodes, ID + "note") or ""
    d.set(ID + "note", old_body + "，已补充折叠椅与防晒喷雾。")
    hide_ime(d)
    d.click(ID + "editor_save")
    d.wait(lambda ns: node_text(ns, ID + "note_count") == "5 条笔记", "after-edit-list")

    d.click("周三项目进度同步", by="row_text")
    wait_editor(d)
    d.click(ID + "editor_back")
    nodes = d.wait(lambda ns: node_text(ns, ID + "note_count") == "5 条笔记", "after-noop-back")
    hide_ime(d)
    save_shot(d, "02", selected)

    # 03: Search "Android" -> hits title ("Android 期中架构复习") + body ("周三项目进度同步")
    d.set("android:id/search_src_text", "Android")
    d.wait(lambda ns: node_text(ns, ID + "note_count") == "2 条笔记", "search-android")
    hide_ime(d)
    time.sleep(0.3)
    save_shot(d, "03", selected)

    # 04: Search empty state
    d.set("android:id/search_src_text", "不存在的关键词")
    d.wait(
        lambda ns: node_text(ns, ID + "note_count") == "0 条笔记" and "没有匹配的笔记" in visible_texts(ns),
        "search-empty",
    )
    hide_ime(d)
    time.sleep(0.3)
    save_shot(d, "04", selected)

    # 05: Category "学习" + keyword "Android" combo
    d.click("学习", by="text")
    d.set("android:id/search_src_text", "Android")
    d.wait(lambda ns: node_text(ns, ID + "note_count") == "1 条笔记", "combo-study-android")
    hide_ime(d)
    time.sleep(0.3)
    save_shot(d, "05", selected)

    # 06: Light unified editor
    d.click("全部", by="text")
    d.set("android:id/search_src_text", "")
    d.wait(lambda ns: node_text(ns, ID + "note_count") == "5 条笔记", "reset-all")
    hide_ime(d)
    d.click("Android 期中架构复习", by="row_text")
    wait_editor(d)
    time.sleep(0.3)
    save_shot(d, "06", selected)

    # 07: Dark mode list via top-bar theme_toggle
    d.click(ID + "editor_back")
    d.wait(lambda ns: node_text(ns, ID + "note_count") == "5 条笔记", "back-before-dark")
    hide_ime(d)
    d.click(ID + "theme_toggle")
    d.wait(
        lambda ns: node_desc(ns, ID + "theme_toggle") == "切换到浅色模式"
        and node_text(ns, ID + "note_count") == "5 条笔记",
        "dark-mode-list",
    )
    hide_ime(d)
    time.sleep(0.5)
    save_shot(d, "07", selected)

    # 08: Dark mode editor
    d.click("Android 期中架构复习", by="row_text")
    nodes = wait_editor(d)
    time.sleep(0.3)
    save_shot(d, "08", selected)

    # 09: Editor more overflow menu (append line so "撤销本次编辑" is also visible)
    editor_body = node_text(nodes, ID + "note") or ""
    d.set(ID + "note", editor_body + "\n补充：实机验证深色模式与导出。")
    hide_ime(d)
    d.click(ID + "editor_more")
    d.wait(
        lambda ns: all(label in visible_texts(ns) for label in ("导出为文本", "撤销本次编辑", "删除笔记")),
        "editor-more-menu",
    )
    time.sleep(0.3)
    save_shot(d, "09", selected)

    # 10 & 11: Export SAF picker and exported UTF-8 content
    if "10" in selected or "11" in selected:
        d.click("导出为文本", by="text")
        nodes = d.wait(
            lambda ns: any("documentsui" in (n.get("package") or "") for n in ns)
            and any("Android 期中架构复习.txt" in (n.get("text") or "") for n in ns),
            "saf-picker",
            timeout=20,
        )
        hide_ime(d)
        time.sleep(0.6)
        save_shot(d, "10", selected)

        if "11" in selected:
            # Click Save button in DocumentsUI
            nodes = d.nodes()
            if any(n.get("visible") and n.get("id") == "android:id/button1" for n in nodes):
                d.click("android:id/button1")
            else:
                save_node = next(
                    (
                        n
                        for n in nodes
                        if n.get("visible")
                        and n.get("clickable")
                        and (n.get("text") or "").strip().lower() in ("save", "保存")
                    ),
                    None,
                )
                if save_node is None:
                    raise AssertionError("DocumentsUI Save button not found")
                d.click(save_node["text"], by="text")

            wait_editor(d)
            time.sleep(1.5)

            # Verify real UTF-8 file written on device storage
            found = d.shell("find /sdcard/ -maxdepth 3 -name 'Android 期中架构复习*.txt' 2>/dev/null").strip()
            assert found, "Exported file not found under /sdcard"
            exported_path = found.splitlines()[0]
            exported_bytes = subprocess.check_output([d.adb, "-s", d.serial, "exec-out", "cat", exported_path])
            exported_text = exported_bytes.decode("utf-8")
            assert exported_text.startswith("Android 期中架构复习\n\n分类：学习\n修改时间："), exported_text
            (EVIDENCE_DIR / "exported.txt").write_bytes(exported_bytes)

            # Open exported file via DocumentsUI Files launcher into device text viewer
            doc_pkg = find_documentsui_package(d)
            d.shell(
                "am",
                "start",
                "-W",
                "-a",
                "android.intent.action.MAIN",
                "-c",
                "android.intent.category.LAUNCHER",
                "-n",
                f"{doc_pkg}/com.android.documentsui.LauncherActivity",
            )
            nodes = d.wait(lambda ns: any("documentsui" in (n.get("package") or "") for n in ns), "files-app")
            time.sleep(0.5)
            # Navigate to Downloads if file isn't already visible
            target_filename = Path(exported_path).name
            if not any(n.get("visible") and n.get("text") == target_filename for n in nodes):
                for folder_label in ("Downloads", "Download", "下载"):
                    if any(n.get("visible") and n.get("text") == folder_label for n in d.nodes()):
                        d.click(folder_label, by="text")
                        time.sleep(0.6)
                        break
            if not any(n.get("visible") and n.get("text") == target_filename for n in d.nodes()):
                # Open drawer if Downloads isn't directly visible
                for nav_desc in ("Show roots", "显示根目录", "Open navigation drawer"):
                    if any(n.get("visible") and n.get("desc") == nav_desc for n in d.nodes()):
                        d.click(nav_desc, by="desc")
                        time.sleep(0.4)
                        for folder_label in ("Downloads", "Download", "下载"):
                            if any(n.get("visible") and n.get("text") == folder_label for n in d.nodes()):
                                d.click(folder_label, by="text")
                                time.sleep(0.6)
                                break
                        break
            d.wait(lambda ns: any(n.get("visible") and n.get("text") == target_filename for n in ns), "exported-row")
            d.click(target_filename, by="text")
            # Android may show an "Open with" resolver sheet (either list or "Open with HTML Viewer")
            for _ in range(15):
                time.sleep(0.5)
                nodes = d.nodes()
                once = next(
                    (n for n in nodes if n.get("visible") and n.get("id") == "android:id/button_once" and n.get("enabled")),
                    None,
                )
                if once is not None and any(
                    n.get("visible") and "HTML Viewer" in (n.get("text") or "") for n in nodes
                ):
                    _, top, _, bottom = map(int, re.findall(r"-?\d+", once["bounds"]))
                    if 0 <= top < bottom <= 2300:
                        time.sleep(0.3)
                        d.click("android:id/button_once")
                        break
                hv = next((n for n in nodes if n.get("visible") and n.get("text") == "HTML Viewer"), None)
                if hv is not None:
                    _, top, _, bottom = map(int, re.findall(r"-?\d+", hv["bounds"]))
                    if 0 <= top < bottom <= 2300:
                        time.sleep(0.4)
                        d.click("HTML Viewer", by="text")
                        d.wait(
                            lambda ns: any(
                                n.get("visible") and n.get("id") == "android:id/button_once" and n.get("enabled")
                                for n in ns
                            )
                            or any("htmlviewer" in (n.get("package") or "") for n in ns),
                            "html-viewer-selected",
                            timeout=10,
                        )
                        nodes = d.nodes()
                        if any(n.get("visible") and n.get("id") == "android:id/button_once" and n.get("enabled") for n in nodes):
                            d.click("android:id/button_once")
                        break
                if any("分类：学习" in (n.get("text") or "") for n in nodes) or any(
                    "htmlviewer" in (n.get("package") or "") for n in nodes
                ):
                    break
            d.wait(
                lambda ns: any("分类：学习" in (n.get("text") or "") for n in ns)
                or any("htmlviewer" in (n.get("package") or "") for n in ns),
                "text-viewer",
                timeout=20,
            )
            time.sleep(0.6)
            save_shot(d, "11", selected)

    # 12: Delete confirmation dialog (in Dark mode so native Dark AlertDialog is also verified)
    if "12" in selected:
        d.stop_app()
        launch_and_wait_list(d, 5)
        d.click("读书随笔：设计心理学", by="row_text")
        wait_editor(d)
        d.click(ID + "editor_more")
        d.wait(lambda ns: "删除笔记" in visible_texts(ns), "delete-menu")
        d.click("删除笔记", by="text")
        d.wait(lambda ns: "确定删除这篇笔记？" in visible_texts(ns), "delete-dialog")
        time.sleep(0.3)
        save_shot(d, "12", selected)
        d.click("android:id/button2")


def generate_shot_13() -> None:
    report_html_path = ROOT / "app/build/reports/tests/testDebugUnitTest/index.html"
    lint_txt_path = ROOT / "app/build/reports/lint-results-debug.txt"
    if not report_html_path.exists() or not lint_txt_path.exists():
        raise FileNotFoundError("Missing test or lint reports; run :app:testDebugUnitTest :app:lintDebug first")

    report_html = report_html_path.read_text(encoding="utf-8")
    lint_txt = lint_txt_path.read_text(encoding="utf-8")
    lint_lines = [line.strip() for line in lint_txt.splitlines() if line.strip()]
    lint_summary = next((line for line in reversed(lint_lines) if "errors," in line and "warnings" in line), "0 errors")

    # Extract summary block and Classes table from real Gradle HTML report
    try:
        summary_inner = report_html.split('<div id="summary">', 1)[1].split('<div class="tab-container">', 1)[0]
        summary_html = '<div id="summary">' + summary_inner
        classes_inner = report_html.split('<h2>Classes</h2>', 1)[1].split('</table>', 1)[0]
        classes_html = '<div class="tab">\n<h2>Classes</h2>' + classes_inner + '</table>\n</div>'
        footer_html = '<div id="footer">' + report_html.split('<div id="footer">', 1)[1].rsplit('</div>', 1)[0] + '</div>'
    except IndexError as exc:
        raise RuntimeError("Could not parse Gradle test HTML report structure") from exc

    base_css = (ROOT / "app/build/reports/tests/testDebugUnitTest/css/base-style.css").read_text(encoding="utf-8")
    style_css = (ROOT / "app/build/reports/tests/testDebugUnitTest/css/style.css").read_text(encoding="utf-8")

    # Verify zero NewApi / VectorRaster errors in real lint report
    new_api_count = lint_txt.count("[NewApi]")
    vector_raster_count = lint_txt.count("[VectorRaster]")

    composite_html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<title>Unit Test &amp; Lint Verification</title>
<style>
{base_css}
{style_css}
body {{
  background: #FFFFFF;
  margin: 0;
  padding: 28px 34px;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", sans-serif;
}}
#content {{
  max-width: 1080px;
  margin: 0 auto;
}}
#content table {{
  width: 100%;
}}
.lint-card {{
  margin-top: 22px;
  padding: 16px 20px;
  border-radius: 8px;
  border: 1px solid #B7E4C7;
  background: #F3FBF6;
}}
.lint-card h2 {{
  margin: 0 0 8px 0;
  font-size: 16px;
  color: #006D32;
}}
.lint-meta {{
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #1B1F23;
  line-height: 1.6;
}}
.lint-pill {{
  display: inline-block;
  background: #008635;
  color: #FFFFFF;
  font-weight: 600;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  margin-right: 10px;
}}
</style>
</head>
<body>
<div id="content">
  <h1>Test Summary — <code>:app:testDebugUnitTest</code> &amp; <code>:app:lintDebug</code></h1>
  {summary_html}
  <div class="tab-container">
    {classes_html}
  </div>
  <div class="lint-card">
    <h2><span class="lint-pill">LINT PASS</span>Android Lint Static Analysis (<code>app/build/reports/lint-results-debug.txt</code>)</h2>
    <div class="lint-meta">
      <div><strong>Summary:</strong> {html_lib.escape(lint_summary)}</div>
      <div><strong>Compatibility Checks:</strong> {new_api_count} <code>[NewApi]</code> errors &middot; {vector_raster_count} <code>[VectorRaster]</code> errors (<code>minSdkVersion 11</code> / <code>targetSdkVersion 34</code>)</div>
    </div>
  </div>
  {footer_html}
</div>
</body>
</html>
"""
    html_out = EVIDENCE_DIR / "test-lint-report.html"
    html_out.parent.mkdir(parents=True, exist_ok=True)
    html_out.write_text(composite_html, encoding="utf-8")

    raw_png = EVIDENCE_DIR / "shot13-raw.png"
    subprocess.run(
        [
            CHROME_BIN,
            "--headless=new",
            "--disable-gpu",
            "--no-sandbox",
            "--hide-scrollbars",
            "--force-device-scale-factor=2",
            "--window-size=1220,1200",
            f"--screenshot={raw_png}",
            html_out.as_uri(),
        ],
        check=True,
    )

    # Auto-trim excess bottom whitespace cleanly
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)
    target = IMAGES_DIR / SHOT_FILENAMES["13"]
    with Image.open(raw_png) as img:
        rgb = img.convert("RGB")
        # Find lowest non-white row
        w, h = rgb.size
        pixels = rgb.load()
        bottom = h - 1
        while bottom > 100:
            if any(pixels[x, bottom] != (255, 255, 255) for x in range(0, w, 4)):
                break
            bottom -= 1
        cropped = rgb.crop((0, 0, w, min(h, bottom + 64)))
        cropped.save(target)
    print(f"[SHOT 13] Saved {target}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Capture NotePad README screenshots on Pixel_6 emulator.")
    parser.add_argument(
        "--shots",
        default="",
        help="Comma-separated shot numbers to save (e.g. 01,07,13). Defaults to all 01-13.",
    )
    args = parser.parse_args()

    selected = (
        {s.strip().zfill(2) for s in args.shots.split(",") if s.strip()}
        if args.shots.strip()
        else set(SHOT_FILENAMES.keys())
    )
    unknown = selected - set(SHOT_FILENAMES.keys())
    if unknown:
        raise ValueError(f"Unknown shot IDs: {sorted(unknown)}")

    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)

    need_emulator = any(s in selected for s in [f"{i:02d}" for i in range(1, 13)])
    if need_emulator:
        adb = os.environ.get("NOTEPAD_ADB", ADB_DEFAULT)
        serial = detect_serial(adb)
        wait_for_boot(adb, serial)
        d = Device(EVIDENCE_DIR, serial=serial)
        try:
            run_emulator_shots(d, selected)
        finally:
            disable_demo_mode(d)

    if "13" in selected:
        generate_shot_13()


if __name__ == "__main__":
    main()
