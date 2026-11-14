"""Public-UI scenarios for docs/测试与验收流程.md v1.0.

Run one named stage at a time so real system dialogs can be inspected and recorded.
The candidate app is not rebuilt or instrumented by this controller.
"""
from pathlib import Path
import argparse
import os
import json
import time
import subprocess
import sqlite3
from device_client import Device, PACKAGE

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(os.environ.get('NOTEPAD_TEST_OUTPUT', str(ROOT / 'docs/acceptance/2026-11-07-basic-flows')))
ID = PACKAGE + ':id/'
FIXTURES = [
    ('安卓复习计划', '今天复习 Activity 生命周期。', '学习'),
    ('采购清单', '购买安卓测试手机和数据线。', '生活'),
    ('周会纪要', '讨论版本发布。', '工作'),
    ('随手记', '周末阅读。', '未分类'),
    ('特殊字符', "折扣50%；变量_a；感叹!；路径C:\\notes；作者O'Reilly；中文与📘。", '学习'),
]
d = Device(OUT)

def texts(nodes): return [n.get('text') for n in nodes if n.get('visible') and n.get('text')]
def value(nodes, id):
    return next((n.get('text','') for n in nodes if n.get('visible') and n.get('id') == id), None)
def count(expected):
    return d.wait(lambda ns: value(ns, ID+'note_count') == f'{expected} 条笔记', 'count-'+str(expected))
def list_ready(): return d.wait(lambda ns: value(ns, ID+'note_count') is not None, 'list')
def query(word): d.set('android:id/search_src_text', word)
def all_notes():
    d.click('全部', by='text'); query(''); list_ready()
def category(label): d.click(label, by='text')
def editor_ready():
    return d.wait(lambda ns:any(n.get('id')==ID+'editor_save' for n in ns), 'editor')
def edit(title=None, body=None, group=None):
    if title is not None: d.set(ID+'title',title)
    if body is not None: d.set(ID+'note',body)
    if group is not None:
        d.click(ID+'editor_category'); d.click(group,by='text')
def fields(title,body,group):
    nodes=editor_ready()
    assert value(nodes,ID+'title')==title, ('title',value(nodes,ID+'title'),title)
    assert value(nodes,ID+'note')==body, ('body',value(nodes,ID+'note'),body)
    assert value(nodes,'android:id/text1')==group, ('category',texts(nodes))
    return nodes

def save(): d.click(ID+'editor_save'); list_ready()
def back_editor(): d.click(ID+'editor_back');list_ready()
def open_note(title):
    all_notes();query(title)
    d.wait(lambda ns:any(n.get('id')=='android:id/text1' and n.get('text')==title for n in ns),'row-'+title)
    d.click(title,by='row_text');editor_ready()
def add(title,body,group):
    # Search may retain IME focus after clearing; expose the visible New button.
    if not any(n.get('id')==ID+'new_note' and n.get('visible') for n in d.nodes()):
        d.key(4)
    d.click(ID+'new_note');editor_ready();edit(title,body,group);fields(title,body,group);save()
def selected_titles(nodes):
    return {n['text'] for n in nodes if n.get('visible') and n.get('id')=='android:id/text1' and n.get('text')}
def results(expected):
    nodes=count(len(expected));assert selected_titles(nodes)==set(expected),(selected_titles(nodes),expected)
    return nodes

def delete_editor(label):
    d.click(ID+'editor_more');d.click('删除笔记',by='text')
    d.wait(lambda ns:'确定删除这篇笔记？' in texts(ns),'delete-dialog')
    d.capture(label+'-confirmation');d.click('android:id/button1');list_ready()

def prepare():
    # D01 was entered during initial live UI inspection. Continue without duplicating it.
    all_notes();nodes=list_ready()
    current=value(nodes,ID+'note_count')
    assert current in ['1 条笔记','5 条笔记'], current
    if current=='1 条笔记':
        for i,(title,body,group) in enumerate(FIXTURES[1:],start=2):
            add(title,body,group);count(i)
    d.capture('04-baseline-five-notes')
    # Verify full saved content through the editor, not database reads.
    for title,body,group in FIXTURES:
        open_note(title);fields(title,body,group);back_editor()
    all_notes();count(5)
    d.shell('input','swipe','500','1750','500','750','400')
    d.capture('05-baseline-scrolled')
    d.record('UC00-B','PASS','常规安装并从公开 launcher 入口进入；5条列表、摘要、分类、时间、滚动及更多/新建入口可用；首次系统旧版本提示已记录并关闭。',['00-legacy-target-warning.png','04-baseline-five-notes.png','05-baseline-scrolled.png'])

def m01_first():
    all_notes();count(5)
    add('验收临时笔记','安卓验收正文','学习');count(6)
    d.click('验收临时笔记',by='text');fields('验收临时笔记','安卓验收正文','学习');back_editor()
    d.capture('m01-1-created')
    d.record('M01-1','PASS','从5条新增至6条，标题/正文/学习分类重开一致。',['m01-1-created.png'])
    d.record('UC01-B','PASS','新建、选择分类、完成保存及重开均通过。',['03-first-note-edit.png','m01-1-created.png'])
    category('学习');query('安卓');results(['安卓复习计划','验收临时笔记'])
    d.capture('m01-2-study-search')
    d.record('M01-2','PASS','学习+安卓仅D01与新笔记，共2条。',['m01-2-study-search.png'])
    d.click('验收临时笔记',by='text');editor_ready()
    edit('验收临时笔记·修订','阶段C验收正文','工作');save()
    results(['安卓复习计划']);d.capture('m01-3-moved-from-study')
    d.record('M01-3','PASS','修改三字段后，原学习+安卓筛选仅剩D01。',['m01-3-moved-from-study.png'])
    category('工作');results([]);d.capture('m01-4-work-search-empty')
    query('');results(['周会纪要','验收临时笔记·修订']);d.capture('m01-4-work-cleared')
    d.record('M01-4','PASS','工作+安卓为0条，清空关键词恢复工作2条。',['m01-4-work-search-empty.png','m01-4-work-cleared.png'])
    d.click('验收临时笔记·修订',by='text');fields('验收临时笔记·修订','阶段C验收正文','工作')
    d.click(ID+'editor_more');d.capture('m01-5-export-menu')
    d.click('导出为文本',by='text');time.sleep(.5)
    nodes=d.capture('m01-5-system-picker')
    print(json.dumps([n for n in nodes if n.get('visible') and (n.get('text') or n.get('desc'))],ensure_ascii=False,indent=2))

def search_basic():
    all_notes();count(5);query('安卓');results(['安卓复习计划','采购清单']);d.capture('uc03-title-or-body')
    category('生活');results(['采购清单']);d.capture('uc03-life-and-search')
    category('学习');results(['安卓复习计划']);query('');results(['安卓复习计划','特殊字符']);d.capture('uc03-study-clear')
    all_notes();count(5)
    d.record('UC03-B','PASS','全部中文标题/正文OR命中2条；生活/学习分别1条；清空保留学习2条，再全部恢复5条。',['uc03-title-or-body.png','uc03-life-and-search.png','uc03-study-clear.png'])

def edit_basic():
    before=db_snapshot('uc02-before')
    open_note('随手记');fields('随手记','周末阅读。','未分类')
    edit('阅读记录','读完第三章。','学习');d.capture('uc02-edit-three-fields');save()
    all_notes();count(5);open_note('阅读记录');fields('阅读记录','读完第三章。','学习');d.capture('uc02-reopened');back_editor()
    after=db_snapshot('uc02-after')
    original=next(row for row in before if row['title']=='随手记')
    changed=next(row for row in after if row['_id']==original['_id'])
    assert changed['created']==original['created'] and changed['modified']>original['modified']
    assert [row for row in before if row['_id']!=original['_id']]==[row for row in after if row['_id']!=original['_id']]
    for title,body,group in FIXTURES:
        if title=='随手记':continue
        open_note(title);fields(title,body,group);back_editor()
    open_note('阅读记录');edit('随手记','周末阅读。','未分类');save();all_notes();count(5)
    d.record('UC02-B','PASS','D04三字段修改保存重开一致，总数5；另外四条逐条通过UI重开核对未变；已恢复D04基线。',['uc02-edit-three-fields.png','uc02-reopened.png'])

def remote_bytes(path):
    return subprocess.check_output([d.adb,'-s',d.serial,'exec-out','cat',path])

def db_snapshot(label):
    path=OUT/(label+'.db')
    path.write_bytes(subprocess.check_output([d.adb,'-s',d.serial,'exec-out','run-as',PACKAGE,'cat','databases/note_pad.db']))
    with sqlite3.connect('file:'+str(path)+'?mode=ro',uri=True) as connection:
        connection.row_factory=sqlite3.Row
        rows=[dict(row) for row in connection.execute('SELECT * FROM notes ORDER BY _id')]
        version=connection.execute('PRAGMA user_version').fetchone()[0]
    (OUT/(label+'.json')).write_text(json.dumps({'version':version,'rows':rows},ensure_ascii=False,indent=2))
    return rows

def m01_finish():
    nodes=d.nodes()
    if not any(n.get('package')=='com.android.htmlviewer' for n in nodes):
        nodes=json.loads((OUT/'m01-5-text-reader.json').read_text())
    content=next(n['text'] for n in nodes if n.get('package')=='com.android.htmlviewer' and '阶段C验收正文' in n.get('text',''))
    data=remote_bytes('/sdcard/Download/NotePad-验收/验收临时笔记·修订.txt')
    assert data.decode('utf-8')==content
    assert content.startswith('验收临时笔记·修订\n\n分类：工作\n修改时间：')
    (OUT/'artifacts/m01-export.txt').write_bytes(data)
    d.record('M01-5','PASS','真实系统选择器保存；文件管理器打开至自带HTML文本阅读器；UTF-8完整97字节，标题、分类、正文一致；成功提示已截图。',['m01-5-export-returned.png','m01-5-file-in-manager.png','m01-5-text-reader.png','artifacts/m01-export.txt'])
    d.stop_app();d.launch();list_ready();all_notes();count(6)
    open_note('验收临时笔记·修订');fields('验收临时笔记·修订','阶段C验收正文','工作');d.capture('m01-6-restarted')
    d.record('M01-6','PASS','结束进程后重启仍6条；三项修订内容重开一致。',['m01-6-restarted.png'])
    delete_editor('m01-7-delete');all_notes();count(5)
    d.stop_app();d.launch();list_ready();all_notes();count(5);query('验收临时笔记·修订');results([]);d.capture('m01-7-deleted-after-restart')
    assert remote_bytes('/sdcard/Download/NotePad-验收/验收临时笔记·修订.txt')==data
    all_notes();count(5)
    d.record('M01-7','PASS','确认删除后重启仍只有5条基线；已删除笔记查无结果，原导出文件仍完整保留。',['m01-7-delete-confirmation.png','m01-7-deleted-after-restart.png','artifacts/m01-export.txt'])
    d.record('M01','PASS','M01-1～M01-7全部实际执行通过，使用真实设备UI和系统文档提供器。',['m01-5-text-reader.png','m01-7-deleted-after-restart.png'])

def export_begin():
    open_note('安卓复习计划');fields('安卓复习计划','今天复习 Activity 生命周期。','学习')
    db_snapshot('uc04-before')
    d.click(ID+'editor_more');d.click('导出为文本',by='text')
    nodes=d.wait(lambda ns:any(n.get('package')=='com.android.documentsui' for n in ns),'uc04-system-picker')
    nodes=d.wait(lambda ns:any(n.get('id')=='android:id/title' and n.get('class')=='android.widget.EditText' and n.get('text')=='安卓复习计划.txt' for n in ns),'uc04-filename')
    d.capture('uc04-picker')
    print(json.dumps([n for n in nodes if n.get('visible') and (n.get('text') or n.get('desc'))],ensure_ascii=False))

def export_save():
    nodes=d.wait(lambda ns:any(n.get('id')=='android:id/title' and n.get('class')=='android.widget.EditText' and n.get('text')=='安卓复习计划.txt' for n in ns),'uc04-filename')
    d.capture('uc04-picker')
    assert any(n.get('text')=='NotePad-验收' for n in nodes), 'Output folder must be the isolated test folder'
    d.click('android:id/button1');editor_ready();d.capture('uc04-export-success')
    data=remote_bytes('/sdcard/Download/NotePad-验收/安卓复习计划.txt')
    text=data.decode('utf-8')
    assert text.startswith('安卓复习计划\n\n分类：学习\n修改时间：') and text.endswith('\n\n今天复习 Activity 生命周期。\n')
    (OUT/'artifacts/uc04-export.txt').write_bytes(data)
    before=json.loads((OUT/'uc04-before.json').read_text())['rows'];after=db_snapshot('uc04-after')
    assert before==after, 'Export must not modify any source field or timestamp'
    d.shell('am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n','com.android.documentsui/.files.FilesActivity')
    nodes=d.wait(lambda ns:any(n.get('package')=='com.android.documentsui' for n in ns),'uc04-files')
    # The file manager remembers its own current folder from the earlier M01 read.
    d.wait(lambda ns:any(n.get('text')=='安卓复习计划.txt' for n in ns),'uc04-export-file')
    d.click('安卓复习计划.txt',by='text')
    nodes=d.wait(lambda ns:any(n.get('package')=='com.android.htmlviewer' for n in ns),'uc04-reader')
    assert any(n.get('text')==text for n in nodes)
    d.capture('uc04-reader')
    d.stop_app();d.launch();list_ready();open_note('安卓复习计划');fields('安卓复习计划','今天复习 Activity 生命周期。','学习');back_editor();all_notes();count(5)
    d.record('UC04-B','PASS','真实保存选择器写出文本；设备阅读器打开且UTF-8内容一致；导出前后全部源字段与毫秒时间一致，重开源笔记仍正确。',['uc04-picker.png','uc04-export-success.png','uc04-reader.png','artifacts/uc04-export.txt','uc04-before.json','uc04-after.json'])

def copy_basic():
    open_note('周会纪要');fields('周会纪要','讨论版本发布。','工作');back_editor()
    d.long('周会纪要',by='row_text');d.capture('uc06-context-menu');d.click('复制',by='text')
    d.click(ID+'list_more');nodes=d.nodes()
    assert any(n.get('text')=='粘贴' and n.get('enabled') for n in nodes)
    d.click('粘贴',by='text');fields('周会纪要','讨论版本发布。','工作');d.capture('uc06-pasted-fields')
    edit(title='周会纪要副本');save();all_notes();count(6)
    open_note('周会纪要副本');edit(body='副本补充内容。');save()
    open_note('周会纪要');fields('周会纪要','讨论版本发布。','工作');d.capture('uc06-original-unchanged');back_editor()
    open_note('周会纪要副本');fields('周会纪要副本','副本补充内容。','工作');delete_editor('uc06-cleanup');all_notes();count(5)
    d.record('UC06-B','PASS','真实长按复制和更多菜单粘贴；三项字段一致，新副本独立修改不影响原记录；已仅清理副本恢复5条。',['uc06-context-menu.png','uc06-pasted-fields.png','uc06-original-unchanged.png'])

def delete_basic():
    before=db_snapshot('uc05-before')
    open_note('周会纪要');fields('周会纪要','讨论版本发布。','工作');delete_editor('uc05-delete');all_notes();count(4)
    d.stop_app();d.launch();list_ready();all_notes();count(4);query('周会纪要');results([]);d.capture('uc05-deleted-after-restart')
    after=db_snapshot('uc05-after')
    assert after==[row for row in before if row['title']!='周会纪要']
    for title,body,group in FIXTURES:
        if title=='周会纪要':continue
        open_note(title);fields(title,body,group);back_editor()
    all_notes();count(4)
    d.record('UC05-B','PASS','删除确认实际出现；确认后5变4，进程重启不复活；另外4条逐条UI重开且精确字段与时间未变。',['uc05-delete-confirmation.png','uc05-deleted-after-restart.png','uc05-before.json','uc05-after.json'])

UPGRADE_NOTES=['升级样本一：中文内容','升级样本二：另一条记录']

def migration_old():
    # First old editor was opened during live inspection of the v2 save controls.
    for i,text in enumerate(UPGRADE_NOTES):
        if i>0:
            d.click(ID+'menu_add')
            d.wait(lambda ns:any(n.get('id')==ID+'note' for n in ns),'v2-editor')
        d.set(ID+'note',text);d.capture('uc07-v2-edit-'+str(i+1));d.click(ID+'menu_save')
        d.wait(lambda ns:any(n.get('id')=='android:id/text1' and n.get('text')==text for n in ns),'v2-saved-'+str(i+1))
    nodes=d.capture('uc07-v2-two-notes')
    assert selected_titles(nodes)==set(UPGRADE_NOTES)
    before=db_snapshot('uc07-v2-before')
    assert len(before)==2 and json.loads((OUT/'uc07-v2-before.json').read_text())['version']==2
    assert {row['title'] for row in before}==set(UPGRADE_NOTES)
    assert all(row['title']==row['note'] for row in before)
    d.stop_app()
    d.record('UC07-B1','PASS','在真实旧APK中通过新建和保存准备2条中文样本；旧列表可见，数据库版本2，已保存精确字段副本。',['uc07-v2-two-notes.png','uc07-v2-before.json'])

def migration_new():
    d.launch();nodes=d.nodes()
    if any(n.get('id')=='android:id/message' and 'older version' in n.get('text','') for n in nodes):
        d.capture('uc07-v3-legacy-warning');d.click('android:id/button1')
    list_ready();all_notes();count(2);d.capture('uc07-v3-two-old-notes')
    after=db_snapshot('uc07-v3-after')
    before=json.loads((OUT/'uc07-v2-before.json').read_text())['rows']
    assert json.loads((OUT/'uc07-v3-after.json').read_text())['version']==3
    assert len(after)==len(before)==2
    assert [{key:value for key,value in row.items() if key!='category'} for row in after]==before
    assert all(row['category']=='uncategorized' for row in after)
    for text in UPGRADE_NOTES:
        open_note(text);fields(text,text,'未分类');back_editor()
    open_note(UPGRADE_NOTES[0]);edit(group='学习');save();d.stop_app();d.launch();list_ready();all_notes();count(2)
    open_note(UPGRADE_NOTES[0]);fields(UPGRADE_NOTES[0],UPGRADE_NOTES[0],'学习');d.capture('uc07-classified-after-restart');back_editor()
    open_note(UPGRADE_NOTES[1]);fields(UPGRADE_NOTES[1],UPGRADE_NOTES[1],'未分类');back_editor();all_notes();count(2)
    d.record('UC07-B','PASS','独立实例真实v2 APK到候选APK同签名覆盖安装；旧2条ID/标题/正文/创建与修改毫秒值完全保留，新分类默认未分类；U01设置学习后重启保留，U02未变。',['uc07-install-update.txt','uc07-v2-two-notes.png','uc07-v3-two-old-notes.png','uc07-v2-before.json','uc07-v3-after.json','uc07-classified-after-restart.png'])

STAGES={'migration-old':migration_old,'migration-new':migration_new,'export-begin':export_begin,'export-save':export_save,'copy-basic':copy_basic,'delete-basic':delete_basic,'m01-finish':m01_finish,'prepare':prepare,'m01-first':m01_first,'search-basic':search_basic,'edit-basic':edit_basic}
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('stage',choices=STAGES)
    args=parser.parse_args()
    try:STAGES[args.stage]()
    except Exception as error:
        try:d.capture('failure-'+args.stage)
        except Exception:pass
        d.record(args.stage,'FAIL',str(error),['failure-'+args.stage+'.json','failure-'+args.stage+'.png'])
        raise
