"""UC06 plain-text and focus regression through public Android UI only.

Use NOTEPAD_TEST_OUTPUT to select a new evidence directory. Requires five baseline notes.
"""
import run_basic_flows as t


def run():
    d = t.d
    body = '外部文本\n第二行 📘'
    t.all_notes()
    t.count(5)
    # Clearing the search focuses its EditText; dismiss the IME to expose New note.
    if not any(n.get('id') == t.ID + 'new_note' and n.get('visible') for n in d.nodes()):
        d.key(4)
    # A regular EditText selection supplies a genuine text clipboard item.
    t.add('UC06文本来源', body, '未分类')
    t.open_note('UC06文本来源')
    d.click(t.ID + 'note')
    d.shell('input', 'keycombination', '113', '29')  # Ctrl+A
    d.shell('input', 'keycombination', '113', '31')  # Ctrl+C
    d.capture('uc06-text-copied')
    t.back_editor()
    d.click(t.ID + 'list_more')
    d.click('粘贴', by='text')
    nodes = t.editor_ready()
    assert t.value(nodes, t.ID + 'note') == body, t.texts(nodes)
    assert t.value(nodes, t.ID + 'title') == '标题', t.texts(nodes)  # Empty field hint.
    d.capture('uc06-text-pasted')
    # Menu focus loss/recovery must never trigger another paste over user input.
    edited = body + '\n手动补充'
    t.edit(body=edited)
    d.click(t.ID + 'editor_more')
    d.key(4)
    t.fields('标题', edited, '未分类')
    d.capture('uc06-focus-preserved-edit')
    t.save()
    # No title was entered: saving derives a readable title from the text.
    t.open_note('外部文本 第二行 📘 手动补充')
    t.fields('外部文本 第二行 📘 手动补充', edited, '未分类')
    d.capture('uc06-text-reopened')
    t.delete_editor('uc06-text-copy-cleanup')
    t.open_note('UC06文本来源')
    t.fields('UC06文本来源', body, '未分类')
    t.delete_editor('uc06-text-source-cleanup')
    t.all_notes()
    t.count(5)
    d.record('UC06-A02', 'PASS',
             '通过EditText真实复制纯文本；中文、换行和表情粘贴完整，未输入标题时保存生成可读标题，重开一致；来源未变，两个临时笔记已清理。',
             ['uc06-text-copied.png', 'uc06-text-pasted.png', 'uc06-text-reopened.png'])
    d.record('UC06-focus', 'PASS',
             '粘贴后补充正文，打开并关闭编辑菜单造成焦点切换，补充内容仍保留，未重复粘贴覆盖。',
             ['uc06-focus-preserved-edit.png'])


if __name__ == '__main__':
    try:
        run()
    except Exception as error:
        t.d.capture('failure-uc06-text')
        t.d.record('UC06-text-runner', 'FAIL', str(error), ['failure-uc06-text.png'])
        raise
