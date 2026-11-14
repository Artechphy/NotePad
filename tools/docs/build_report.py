"""Build the course report from its maintained Markdown source.

Requires python-docx and Pillow. Only the report's small Markdown subset is
supported: headings, paragraphs, tables, lists, code, page and figure markers.
"""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import re
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'docs/实验报告/NotePad期中实验报告.md'
OUTPUT = SOURCE.with_suffix('.docx')


def font(run, size=None, bold=None, east='Songti SC', latin='Arial'):
    run.font.name = latin
    run._element.get_or_add_rPr().rFonts.set(qn('w:eastAsia'), east)
    run.font.color.rgb = RGBColor(0, 0, 0)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold


def inline(paragraph, text, size=12, bold=False):
    for piece in re.split(r'(`[^`]+`)', text):
        code = piece.startswith('`') and piece.endswith('`')
        run = paragraph.add_run(piece[1:-1] if code else piece)
        font(run, 9.5 if code else size, bold, latin='Menlo' if code else 'Arial')


def configure(doc):
    section = doc.sections[0]
    section.page_width, section.page_height = Cm(21), Cm(29.7)
    section.top_margin, section.bottom_margin = Cm(2.0), Cm(2.0)
    section.left_margin, section.right_margin = Cm(2.3), Cm(2.3)
    section.footer_distance = Cm(.9)
    for grid in list(section._sectPr.findall(qn('w:docGrid'))):
        section._sectPr.remove(grid)
    for name in ['Normal', 'Title', 'Heading 1', 'Heading 2', 'Caption']:
        style = doc.styles[name]
        style.font.name = 'Arial'
        style._element.get_or_add_rPr().rFonts.set(qn('w:eastAsia'), 'Songti SC')
        style.font.color.rgb = RGBColor(0, 0, 0)
        for attr in ['asciiTheme', 'hAnsiTheme', 'eastAsiaTheme', 'cstheme']:
            style._element.rPr.rFonts.attrib.pop(qn('w:' + attr), None)
        ppr = style._element.find(qn('w:pPr'))
        if ppr is not None:
            for border in list(ppr.findall(qn('w:pBdr'))):
                ppr.remove(border)
        if ppr is None:
            ppr = style._element.get_or_add_pPr()
        snap = OxmlElement('w:snapToGrid')
        snap.set(qn('w:val'), '0')
        ppr.append(snap)
    normal = doc.styles['Normal'].paragraph_format
    normal.line_spacing, normal.space_after = Pt(18), Pt(6)
    normal.first_line_indent = Cm(.8)
    normal.widow_control = True
    for name, size in [('Title', 18), ('Heading 1', 14), ('Heading 2', 12)]:
        style = doc.styles[name]
        style.font.size, style.font.bold = Pt(size), True
        style._element.rPr.rFonts.set(qn('w:eastAsia'), 'Heiti SC')
        style.paragraph_format.first_line_indent = Cm(0)
        style.paragraph_format.space_before = Pt(0 if name != 'Heading 2' else 8)
        style.paragraph_format.space_after = Pt(8)
        style.paragraph_format.keep_with_next = True
        style.paragraph_format.line_spacing = Pt(25 if name == 'Title' else 21 if name == 'Heading 1' else 18)
    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    footer.paragraph_format.first_line_indent = Cm(0)
    font(footer.add_run('第 '), 9)
    field = OxmlElement('w:fldSimple')
    field.set(qn('w:instr'), 'PAGE')
    footer._p.append(field)
    font(footer.add_run(' 页'), 9)
    props = doc.core_properties
    props.title = 'NotePad 记事本应用扩展实验报告'
    props.subject = 'Android期中实验 功能扩展与验证'
    props.author = ''
    props.last_modified_by = ''
    props.comments = ''
    props.keywords = ''
    props.created = datetime.now(timezone.utc)
    props.modified = props.created


def table(doc, rows):
    columns = len(rows[0])
    if columns == 2:
        widths = [4.7, 11.7]
    elif rows[0][0] == '类型':
        widths = [1.7, 5.0, 9.7]
    elif rows[0][0] == '字段':
        widths = [2.8, 4.8, 8.8]
    else:
        widths = [4.4, 5.0, 7.0]
    t = doc.add_table(rows=0, cols=columns)
    t.alignment, t.autofit = WD_TABLE_ALIGNMENT.CENTER, False
    for col, width in zip(t.columns, widths):
        col.width = Cm(width)
    borders = OxmlElement('w:tblBorders')
    for name in ['top', 'left', 'bottom', 'right', 'insideH', 'insideV']:
        e = OxmlElement('w:' + name)
        for key, value in [('val', 'single'), ('sz', '4'), ('color', 'D9D9D9')]:
            e.set(qn('w:' + key), value)
        borders.append(e)
    t._tbl.tblPr.append(borders)
    for i, row in enumerate(rows):
        cells = t.add_row().cells
        trpr = t.rows[-1]._tr.get_or_add_trPr()
        trpr.append(OxmlElement('w:cantSplit'))
        if i == 0:
            trpr.append(OxmlElement('w:tblHeader'))
        for j, (cell, text) in enumerate(zip(cells, row)):
            cell.width = Cm(widths[j])
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            p = cell.paragraphs[0]
            p.paragraph_format.first_line_indent = Cm(0)
            p.paragraph_format.space_after = Pt(1)
            p.paragraph_format.space_before = Pt(1)
            p.paragraph_format.line_spacing = Pt(14)
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER if i == 0 or j == 0 else WD_ALIGN_PARAGRAPH.LEFT
            inline(p, text, 10.5, i == 0)
            tcpr = cell._tc.get_or_add_tcPr()
            margins = OxmlElement('w:tcMar')
            for side, amount in [('top', '65'), ('bottom', '65'), ('left', '100'), ('right', '100')]:
                e = OxmlElement('w:' + side)
                e.set(qn('w:w'), amount)
                e.set(qn('w:type'), 'dxa')
                margins.append(e)
            tcpr.append(margins)
            if i == 0:
                shade = OxmlElement('w:shd')
                shade.set(qn('w:fill'), 'E7EDF4')
                tcpr.append(shade)
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after = Pt(1)
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.line_spacing = Pt(4)
    font(spacer.add_run(''), 3)


def figures(doc, line, source):
    parts = line.removeprefix('[[FIGURES|').removesuffix(']]').split('|')
    if len(parts) != 4:
        raise ValueError('Expected two figure paths and captions')
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.line_spacing = 1
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.keep_with_next = True
    for i in [0, 2]:
        if i:
            font(p.add_run('    '), 12)
        image = p.add_run().add_picture(str((source.parent / parts[i]).resolve()), width=Cm(6.8))
        image._inline.docPr.set('descr', parts[i+1])
    cap = doc.add_paragraph(style='Caption')
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.first_line_indent = Cm(0)
    cap.paragraph_format.space_after = Pt(6)
    inline(cap, parts[1] + '    ' + parts[3], 9)


def build(source, output):
    doc = Document()
    configure(doc)
    lines = source.read_text().splitlines()
    index = 0
    pending_page = False
    while index < len(lines):
        line = lines[index].strip()
        if not line:
            index += 1
            continue
        if line == '<!-- PAGE -->':
            pending_page = True
        elif line.startswith('[[FIGURES|'):
            figures(doc, line, source)
        elif line.startswith('# '):
            p = doc.add_paragraph(line[2:], style='Title')
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        elif line.startswith('## '):
            heading = doc.add_paragraph(line[3:], style='Heading 1')
            if pending_page:
                heading.paragraph_format.page_break_before = True
                pending_page = False
        elif line.startswith('### '):
            doc.add_paragraph(line[4:], style='Heading 2')
        elif line.startswith('```'):
            content = []
            index += 1
            while index < len(lines) and not lines[index].startswith('```'):
                content.append(lines[index])
                index += 1
            p = doc.add_paragraph()
            p.paragraph_format.first_line_indent = Cm(0)
            p.paragraph_format.left_indent = Cm(.3)
            p.paragraph_format.line_spacing = 1.15
            p.paragraph_format.keep_together = True
            font(p.add_run('\n'.join(content)), 9.5, latin='Menlo')
        elif line.startswith('|'):
            rows = []
            while index < len(lines) and lines[index].strip().startswith('|'):
                cells = [v.strip() for v in lines[index].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', c) for c in cells):
                    rows.append(cells)
                index += 1
            table(doc, rows)
            continue
        else:
            p = doc.add_paragraph()
            if re.match(r'^\d+\. ', line):
                p.paragraph_format.first_line_indent = Cm(0)
                p.paragraph_format.left_indent = Cm(.3)
                p.paragraph_format.space_after = Pt(4)
            inline(p, line)
        index += 1
    output.parent.mkdir(parents=True, exist_ok=True)
    doc.save(output)
    print(output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, default=SOURCE)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    args = parser.parse_args()
    build(args.source, args.output)
