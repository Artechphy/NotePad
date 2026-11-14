"""Selected v1.0 alternate flows on an isolated emulator, through public UI.

The failures group uses the exported Provider only to remove/restore a synthetic
source as controlled fault injection. All user editing and retry actions use UI.
"""
import argparse
import os
import re
import time
import run_basic_flows as t

d, ID = t.d, t.ID


def case(name, action):
    selected = os.environ.get('NOTEPAD_CASES', '')
    if selected and name not in selected.split(','):
        return
    try:
        detail = action()
        d.capture(name)
        d.record(name, 'PASS', detail, [name+'.png', name+'.json'])
    except Exception as error:
        label='failure-'+name+'-'+str(time.time_ns())
        d.capture(label)
        d.record(name, 'FAIL', str(error), [label+'.png', label+'.json'])
        raise


def new():
    t.all_notes()
    if not any(n.get('visible') and n.get('id') == ID+'new_note' for n in d.nodes()):
        d.key(4)
    d.click(ID+'new_note')
    t.editor_ready()


def cleanup(title):
    t.open_note(title)
    t.delete_editor('cleanup-'+title)
    t.all_notes()
    t.count(5)


def blank(nodes, key):
    node = next(n for n in nodes if n.get('id') == ID+key and n.get('visible'))
    # Accessibility exposes an empty EditText's hint as text; saved data is checked separately.
    return node.get('text') in ['', '标题', '开始记录…', '开始记录…\n', None]


def dismiss_ime():
    if 'mInputShown=true' in d.shell('dumpsys','input_method'):
        d.key(4)


def recreate():
    dismiss_ime()
    before = d.shell('dumpsys', 'activity', 'top')
    token = re.search(r'context: com\.example\.android\.notepad\.(?:NoteEditor|NotesList)@(\w+)', before)
    assert token, 'Cannot establish Activity instance before rotation'
    d.shell('settings', 'put', 'system', 'accelerometer_rotation', '0')
    d.shell('settings', 'put', 'system', 'user_rotation', '1')
    time.sleep(1)
    after = d.shell('dumpsys', 'activity', 'top')
    other = re.search(r'context: com\.example\.android\.notepad\.(?:NoteEditor|NotesList)@(\w+)', after)
    assert other and other.group(1) != token.group(1), 'Rotation did not recreate the Activity'
    dismiss_ime()


def portrait():
    d.shell('settings', 'put', 'system', 'user_rotation', '0')
    time.sleep(.8)


def cancel_picker():
    # DocumentsUI may traverse a folder or hide its IME before cancelling.
    for _ in range(6):
        d.key(4)
        if any(n.get('id')==ID+'editor_save' and n.get('visible') for n in d.nodes()):
            return t.editor_ready()
    raise AssertionError('Cannot cancel the system document picker')


def create_cases():
    def empty_complete():
        new(); t.edit(title='  ', body=' \n ')
        d.click(ID+'editor_save'); nodes=t.editor_ready()
        assert any(n.get('id')==ID+'editor_save' for n in nodes)
        assert t.value(nodes,ID+'save_status')!='已保存'
        d.capture('UC01-A01-stayed'); t.back_editor();t.all_notes();t.count(5)
        return '空白标题/正文点完成仍在编辑页；返回清理，未保存空记录。'
    case('UC01-A01', empty_complete)
    def discard():
        for group in [None, '工作']:
            new()
            if group:t.edit(group=group)
            t.back_editor(); t.all_notes();t.count(5)
        d.stop_app();d.launch();t.list_ready();t.all_notes();t.count(5)
        return '全空和仅改变分类两种草稿返回均清理；重启仍5条。'
    case('UC01-A02', discard)
    def title_only():
        new();t.edit(title='仅标题笔记',group='生活');t.save();t.open_note('仅标题笔记')
        row=next(r for r in t.db_snapshot('UC01-A03-db') if r['title']=='仅标题笔记')
        assert row['note']=='' and row['category']=='life'
        assert t.value(d.nodes(), ID+'title')=='仅标题笔记'
        d.capture('UC01-A03-reopened');t.delete_editor('UC01-A03-cleanup');t.all_notes();t.count(5)
        return '仅标题保存重开，正文为空且生活分类保留；清理后5条。'
    case('UC01-A03', title_only)
    def body_only():
        new();t.edit(body='第一行\n第二行');t.save();t.open_note('第一行 第二行')
        t.fields('第一行 第二行','第一行\n第二行','未分类');d.capture('UC01-A04-reopened')
        t.delete_editor('UC01-A04-cleanup');t.all_notes();t.count(5)
        return '只填正文保留两行，自动标题正确，保存重开一致。'
    case('UC01-A04', body_only)
    def filtered():
        t.all_notes();t.category('工作');t.query('发布')
        d.click(ID+'list_more');d.click('新建笔记',by='text');t.editor_ready()
        assert t.value(d.nodes(),'android:id/text1')=='工作'
        t.edit(title='筛选内新建',body='不含原搜索词');t.save();t.results(['周会纪要'])
        t.query('');t.results(['周会纪要','筛选内新建']);cleanup('筛选内新建')
        return '工作+发布筛选中新建继承工作；保存后被关键词过滤，清空后可找到。'
    case('UC01-A05', filtered)
    def back_save():
        for system in [False,True]:
            new();t.edit(title='返回保存样本',body='系统返回' if system else '页面返回')
            if system:
                d.key(4)
                if any(n.get('id')==ID+'editor_save' for n in d.nodes()):d.key(4)
                t.list_ready()
            else:t.back_editor()
            t.open_note('返回保存样本');t.fields('返回保存样本','系统返回' if system else '页面返回','未分类')
            t.delete_editor('UC01-A06-cleanup');t.all_notes();t.count(5)
        return '页面返回及系统返回各自保存非空新笔记，重开一致且无重复。'
    case('UC01-A06', back_save)
    def rebuilt():
        new();t.edit(title='重建草稿',body='重建保留正文📘',group='生活');recreate()
        t.fields('重建草稿','重建保留正文📘','生活');d.capture('UC01-A07-landscape');portrait()
        t.fields('重建草稿','重建保留正文📘','生活');t.save();t.all_notes();t.count(6);cleanup('重建草稿')
        return '核对Activity实例变化后，横竖屏重建保留标题/正文/分类，最终仅新增1条。'
    case('UC01-A07', rebuilt)
    def duplicate():
        new();t.edit(title='安卓复习计划',body='重名独立正文',group='工作');t.save();t.all_notes();t.count(6)
        rows=t.db_snapshot('UC01-A08-db');dups=[r for r in rows if r['title']=='安卓复习计划']
        assert len(dups)==2 and {r['note'] for r in dups}=={'重名独立正文',t.FIXTURES[0][1]}
        t.category('工作');t.query('安卓复习计划');d.click('安卓复习计划',by='row_text');t.fields('安卓复习计划','重名独立正文','工作')
        t.delete_editor('UC01-A08-cleanup');t.all_notes();t.count(5)
        return '相同标题保存为独立记录，原笔记未覆盖，仅清理新增记录。'
    case('UC01-A08', duplicate)


def editor_cases():
    def unchanged():
        before=t.db_snapshot('UC02-A01-before')
        t.open_note('随手记');t.back_editor();t.open_note('随手记');t.save()
        assert t.db_snapshot('UC02-A01-after')==before
        return '无改动返回及完成均未改任何字段，包括毫秒修改时间。'
    case('UC02-A01',unchanged)
    def single():
        for changes,title,body,group in [({'title':'单改标题'},'单改标题','周末阅读。','未分类'),({'body':'单改正文'},'随手记','单改正文','未分类'),({'group':'学习'},'随手记','周末阅读。','学习')]:
            before=t.db_snapshot('UC02-A02-before-'+next(iter(changes)))
            old=next(r for r in before if r['title']=='随手记')
            t.open_note('随手记');t.edit(**changes);t.save();t.open_note(title);t.fields(title,body,group);t.back_editor()
            after=t.db_snapshot('UC02-A02-after-'+next(iter(changes)));row=next(r for r in after if r['_id']==old['_id'])
            assert row['created']==old['created'] and row['modified']>old['modified']
            assert [r for r in before if r['_id']!=old['_id']]==[r for r in after if r['_id']!=old['_id']]
            t.open_note(title);t.edit(title='随手记',body='周末阅读。',group='未分类');t.save()
        return '标题、正文、分类分别单独改变；重开正确，创建时间不变，修改时间更新，其他记录不变。'
    case('UC02-A02',single)
    def revert():
        t.open_note('随手记');t.edit(title='撤销标题',body='撤销正文',group='工作')
        d.click(ID+'editor_more');d.click('撤销本次编辑',by='text');t.list_ready();t.open_note('随手记');t.fields('随手记','周末阅读。','未分类');t.back_editor();t.all_notes();t.count(5)
        return '三字段修改后撤销，恢复本次进入时内容和分类，总数不变。'
    case('UC02-A03',revert)
    def revert_new():
        new();t.edit(title='撤销新草稿',body='不应保留');d.click(ID+'editor_more');d.click('撤销本次编辑',by='text');t.list_ready();t.all_notes();t.count(5)
        return '非空新草稿撤销后删除，仅保留5条基线。'
    case('UC02-A04',revert_new)
    def clear_body():
        t.open_note('随手记');t.edit(body='');t.save();t.open_note('随手记')
        row=next(r for r in t.db_snapshot('UC02-A05-db') if r['title']=='随手记')
        assert row['note']=='' and row['category']=='uncategorized'
        d.capture('UC02-A05-reopened');t.edit(body='周末阅读。');t.save();t.all_notes();t.count(5)
        return '已有笔记正文清空后仍可重开，标题及分类保留；已恢复正文。'
    case('UC02-A05',clear_body)
    def autosave():
        for mode in ['back','background','restart']:
            t.open_note('随手记');t.edit(body='自动保存-'+mode)
            if mode=='background':
                d.key(3);d.launch()
                if not any(n.get('id')==ID+'editor_save' for n in d.nodes()):t.open_note('随手记')
                t.editor_ready();t.fields('随手记','自动保存-'+mode,'未分类');t.back_editor()
            else:t.back_editor()
            if mode=='restart':d.stop_app();d.launch();t.list_ready()
            t.open_note('随手记');t.fields('随手记','自动保存-'+mode,'未分类');t.edit(body='周末阅读。');t.save();t.all_notes();t.count(5)
        return '返回、切后台回前台、先保存后结束进程重启分别核验，内容持久化且无重复。'
    case('UC02-A06',autosave)


def search_cases():
    def spaces():
        t.all_notes();t.query(' 安卓 ');t.results(['安卓复习计划','采购清单'])
        t.category('学习');t.query(' \n ');t.results(['安卓复习计划','特殊字符']);t.all_notes()
        return '关键词两侧空白被忽略；纯空白恢复当前分类内全部记录。'
    case('UC03-A01',spaces)
    def no_results():
        t.all_notes();t.query('不存在的关键词');nodes=t.results([])
        assert '没有匹配的笔记' in t.texts(nodes);t.query('');t.count(5)
        return '无匹配显示0条和明确提示，清空恢复5条。'
    case('UC03-A02',no_results)
    def empty_category():
        t.open_note('周会纪要');t.edit(group='未分类');t.save();t.all_notes();t.category('工作');nodes=t.results([])
        assert '这个分类还没有笔记' in t.texts(nodes);d.capture('UC03-A03-empty')
        t.open_note('周会纪要');t.edit(group='工作');t.save();t.all_notes();t.count(5)
        return '先移出唯一工作笔记，工作分类确为空时显示独立空提示；随后恢复。'
    case('UC03-A03',empty_category)
    def literals():
        t.all_notes()
        for word in ['%','_','!','\\',"O'Reilly",'📘']:
            t.query(word);t.results(['特殊字符'])
        t.query("' OR 1=1 --");t.results([]);t.all_notes()
        return '6种特殊字符均只命中D05；SQL样式字符串为0条，未扩大结果。'
    case('UC03-A04',literals)
    def rapid():
        t.all_notes()
        for word in ['安卓','采购','随手']:t.query(word)
        t.results(['随手记']);time.sleep(1);t.results(['随手记']);t.all_notes()
        return '连续更新关键词后最终仅D04，等待后未被旧查询覆盖。'
    case('UC03-A05',rapid)
    def edited_results():
        for field in ['body','group']:
            t.all_notes();t.category('生活');t.query('安卓');t.results(['采购清单']);d.click('采购清单',by='row_text');t.editor_ready()
            t.edit(**({'body':'购买手机和数据线。'} if field=='body' else {'group':'工作'}));t.save();t.results([])
            t.open_note('采购清单');t.fields('采购清单','购买手机和数据线。' if field=='body' else t.FIXTURES[1][1],'生活' if field=='body' else '工作')
            t.edit(body=t.FIXTURES[1][1],group='生活');t.save();t.all_notes();t.count(5)
        return '从生活+安卓结果分别改正文去词和改分类，保存后移出；清除条件仍可找到，恢复基线。'
    case('UC03-A06',edited_results)
    def list_rebuilt():
        t.all_notes();t.category('生活');t.query('安卓');t.results(['采购清单']);recreate()
        assert t.value(d.nodes(),'android:id/search_src_text')=='安卓';t.results(['采购清单']);d.capture('UC03-A07-landscape');portrait()
        t.results(['采购清单']);t.query('');t.results(['采购清单']);t.all_notes()
        return '实际Activity重建后关键词、生活分类和结果保留；清空仍只见生活记录。'
    case('UC03-A07',list_rebuilt)


def export_cases():
    def picker():
        d.click(ID+'editor_more');d.click('导出为文本',by='text')
        ns=d.wait(lambda ns:any(n.get('package')=='com.android.documentsui' for n in ns),'alternate-picker')
        if not any(n.get('id')=='com.android.documentsui:id/breadcrumb_text' and n.get('text')=='NotePad-验收' for n in ns):
            d.wait(lambda ns:any(n.get('text')=='NotePad-验收' for n in ns),'alternate-folder')
            d.click('NotePad-验收',by='text')
        return d.wait(lambda ns:any(n.get('class')=='android.widget.EditText' and n.get('id')=='android:id/title' for n in ns),'alternate-filename')
    def cancel():
        t.open_note('随手记');t.edit(body='取消导出前的新正文');picker();cancel_picker()
        t.fields('随手记','取消导出前的新正文','未分类');t.back_editor();t.open_note('随手记');t.fields('随手记','取消导出前的新正文','未分类')
        picker();d.capture('UC04-A01-can-export-again');cancel_picker();t.edit(body='周末阅读。');t.save()
        return '取消真实选择器返回编辑页，导出前保存的新正文保留，且可再次导出。'
    case('UC04-A01',cancel)
    def empty():
        new();d.click(ID+'editor_more');d.click('导出为文本',by='text');nodes=t.editor_ready()
        assert not any(n.get('package')=='com.android.documentsui' for n in nodes)
        d.capture('UC04-A02-no-picker');t.back_editor();t.all_notes();t.count(5)
        return '全空新草稿导出留在编辑页，未打开选择器，返回清理。'
    case('UC04-A02',empty)
    def pending():
        new();t.edit(title='导出未完成编辑',body='导出中文📘新正文',group='生活');picker();d.capture('UC04-A03-picker')
        before=t.db_snapshot('UC04-A03-before-write');row=next(r for r in before if r['title']=='导出未完成编辑')
        assert row['note']=='导出中文📘新正文' and row['category']=='life'
        assert any(n.get('text')=='NotePad-验收' for n in d.nodes()),'Expected isolated export folder'
        d.click('android:id/button1');t.editor_ready()
        data=t.remote_bytes('/sdcard/Download/NotePad-验收/导出未完成编辑.txt')
        text=data.decode('utf-8');assert text.startswith('导出未完成编辑\n\n分类：生活\n') and text.endswith('\n\n导出中文📘新正文\n')
        assert t.db_snapshot('UC04-A03-after-write')==before
        (t.OUT/'artifacts/alternate-pending-export.txt').write_bytes(data)
        t.back_editor();cleanup('导出未完成编辑')
        return '未点完成直接导出：先保存三字段，真实写出UTF-8文件；文件写入未再次改变源修改时间。'
    case('UC04-A03',pending)
    def filenames():
        new();t.edit(title='课程/复习\\计划',body='文件名边界');nodes=picker()
        assert any(n.get('class')=='android.widget.EditText' and n.get('text')=='课程_复习_计划.txt' for n in nodes)
        d.capture('UC04-A04-sanitized');cancel_picker();t.edit(title='长中文📘'*50);nodes=picker()
        name=next(n['text'] for n in nodes if n.get('id')=='android:id/title' and n.get('class')=='android.widget.EditText')
        assert name.endswith('.txt') and '\ufffd' not in name and name.encode('utf-8').decode('utf-8')==name
        d.capture('UC04-A04-unicode');cancel_picker();t.delete_editor('UC04-A04-cleanup');t.all_notes();t.count(5)
        return '真实选择器建议名清理路径分隔符；长中文/emoji建议名保持完整Unicode。'
    case('UC04-A04',filenames)


def deletion_cases():
    def cancel():
        t.open_note('周会纪要');t.edit(body='取消删除仍保留输入');d.click(ID+'editor_more');d.click('删除笔记',by='text');d.click('android:id/button2')
        t.fields('周会纪要','取消删除仍保留输入','工作');d.capture('UC05-A01-kept-input');t.edit(body=t.FIXTURES[2][1]);t.save();t.all_notes();t.count(5)
        return '取消删除留在编辑页，输入可见且笔记未删除；已恢复基线正文。'
    case('UC05-A01',cancel)
    def long_delete():
        t.open_note('周会纪要');t.back_editor();d.long('周会纪要',by='row_text');d.click('删除笔记',by='text');t.list_ready();t.all_notes();t.count(4)
        assert not any(n.get('text')=='确定删除这篇笔记？' for n in d.nodes())
        d.capture('UC05-A02-direct-delete');t.add(*t.FIXTURES[2]);t.all_notes();t.count(5)
        return '现状回归：列表长按删除直接生效且无确认；恢复的仅为合成D03。'
    case('UC05-A02',long_delete)
    def duplicate():
        new();t.edit(title='重名笔记',body='待删除记录',group='工作');t.save()
        new();t.edit(title='重名笔记',body='保留记录',group='生活');t.save();t.all_notes();t.count(7)
        t.category('工作');t.query('重名笔记');d.click('重名笔记',by='row_text');t.editor_ready();t.delete_editor('UC05-A03-target')
        t.all_notes();t.query('重名笔记');t.results(['重名笔记']);d.click('重名笔记',by='row_text');t.fields('重名笔记','保留记录','生活');d.capture('UC05-A03-survivor')
        t.delete_editor('UC05-A03-cleanup');t.all_notes();t.count(5)
        return '同名两条只删除所选工作笔记；另一条生活笔记正文完整，随后清理。'
    case('UC05-A03',duplicate)


def failures():
    def inject_source_removal():
        new();t.edit(title='保存失败样本',body='原有正文');t.save();t.open_note('保存失败样本');t.edit(body='尚未保存的新输入')
        row=next(r for r in t.db_snapshot('failure-source-before') if r['title']=='保存失败样本')
        uri='content://com.google.provider.NotePad/notes/'+str(row['_id'])
        d.shell('content','delete','--uri',uri)
        assert not any(r['_id']==row['_id'] for r in t.db_snapshot('failure-source-removed'))
        return row
    def manual():
        row=inject_source_removal();d.click(ID+'editor_save');t.editor_ready();t.fields('保存失败样本','尚未保存的新输入','未分类')
        assert t.value(d.nodes(),ID+'save_status')=='保存失败，请重试'
        d.capture('UC02-A07-failed-stayed')
        # Restore the removed synthetic source to make retry possible, not to simulate a user save.
        args=['content','insert','--uri','content://com.google.provider.NotePad/notes']
        for key,val in row.items():args += ['--bind',key+(':'+('l' if isinstance(val,int) else 's')+':')+str(val)]
        d.shell(*args);d.click(ID+'editor_save');t.list_ready();t.open_note('保存失败样本');t.fields('保存失败样本','尚未保存的新输入','未分类')
        t.delete_editor('UC02-A07-cleanup');t.all_notes();t.count(5)
        return '受控删除合成来源导致保存前读取失败：手动完成留在编辑页且保留输入；恢复同ID来源后UI重试成功。此为来源消失条件，不代表全部I/O故障。'
    case('UC02-A07',manual)
    def automatic():
        inject_source_removal();d.click(ID+'editor_back');t.list_ready();t.all_notes();t.count(5)
        d.capture('UC02-A08-left-after-failure');t.query('保存失败样本');t.results([])
        raise AssertionError('受控移除来源后点返回，自动保存失败但页面仍退出；无保留或恢复尚未保存输入的入口，GAP01复现。')
    case('UC02-A08',automatic)


def clipboard_cases():
    def missing_source():
        new();t.edit(title='失效复制来源',body='不得复制到其他笔记',group='工作');t.save()
        t.open_note('失效复制来源');t.back_editor();d.long('失效复制来源',by='row_text');d.click('复制',by='text')
        t.open_note('失效复制来源');t.delete_editor('UC06-A03-remove-source');t.all_notes();t.count(5)
        before=t.db_snapshot('UC06-A03-before-paste');d.click(ID+'list_more');d.click('粘贴',by='text');t.editor_ready()
        assert t.value(d.nodes(),ID+'title')=='标题'
        d.capture('UC06-A03-empty-draft');t.back_editor();t.all_notes();t.count(5)
        assert t.db_snapshot('UC06-A03-after-paste')==before
        return '真实复制后从UI删除来源，再粘贴未读到其他记录；返回清理空草稿，5条基线逐字段不变。'
    case('UC06-A03',missing_source)


def readability():
    def missing_time():
        new();t.edit(title='时间缺失样本',body='受控时间字段');t.save()
        row=next(r for r in t.db_snapshot('UC00-A04-initial') if r['title']=='时间缺失样本')
        uri='content://com.google.provider.NotePad/notes/'+str(row['_id'])
        t.all_notes();t.query('时间缺失样本')
        for kind,value in [('l','0'),('l','-1'),('n','')]:
            d.shell('content','update','--uri',uri,'--bind','modified:'+kind+':'+value)
            actual=next(r for r in t.db_snapshot('UC00-A04-injected-'+kind+value) if r['_id']==row['_id'])
            assert actual['modified']==(None if kind=='n' else int(value)),actual
            d.wait(lambda ns:'时间未知' in t.texts(ns),'unknown-time-'+kind+value)
            d.capture('UC00-A04-'+kind+value)
        t.open_note('安卓复习计划');t.fields(*t.FIXTURES[0]);t.back_editor();cleanup('时间缺失样本')
        return '仅对合成样本受控注入modified=0、-1、NULL，列表均显示时间未知；其他笔记仍可重开；已清理样本。'
    case('UC00-A04',missing_time)
    def long_text():
        title='长标题中文📘'*25
        body='\n'.join('第'+str(i)+'行：长正文中文与表情📘，完整保留。' for i in range(1,101))
        new();t.edit(title=title,body=body,group='学习');t.save();t.open_note(title);t.fields(title,body,'学习')
        d.click(ID+'note');d.shell('input','swipe','500','1700','500','650','400')
        assert any(n.get('id')==ID+'editor_save' and n.get('visible') for n in d.nodes())
        d.capture('UC00-A05-long-with-keyboard');d.shell('settings','put','system','font_scale','1.3');time.sleep(1)
        assert t.value(d.nodes(),ID+'note')==body
        assert any(n.get('id')==ID+'editor_save' and n.get('visible') for n in d.nodes())
        # Title/category may correctly be above the scroll viewport; reopen to inspect them.
        t.back_editor();t.open_note(title);t.fields(title,body,'学习')
        d.capture('UC00-A05-font130');d.shell('settings','put','system','font_scale','1.0');time.sleep(1)
        t.delete_editor('UC00-A05-cleanup');t.all_notes();t.count(5)
        return '1080×2340/420dpi下长标题及100行正文保存重开完整；键盘、滚动及130%字号时完成入口可见。仅代表已测尺寸与字号。'
    case('UC00-A05',long_text)


GROUPS={'create':create_cases,'editor':editor_cases,'search':search_cases,'export':export_cases,'delete':deletion_cases,'failures':failures,'clipboard':clipboard_cases,'readability':readability}
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('group',choices=GROUPS)
    GROUPS[parser.parse_args().group]()
