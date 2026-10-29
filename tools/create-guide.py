"""Build the same illustrated Russian guide as offline HTML and a paginated PDF.

Requires reportlab. Run from the repository root after capturing the real client.
Screenshot pixels are embedded unchanged; layout never paints fictitious game UI.
"""
from pathlib import Path
import json, html, shutil
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4, landscape
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.utils import ImageReader

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/guide'
DATA = ROOT / 'docs/guide-content.json'
FONT = ROOT / 'game-client/src/main/resources/assets/fonts'

def plain(value):
    return value.replace('\u2011', '-').replace('\u2013', '-').replace('\u2014', '-')

def main():
    pages = json.loads(DATA.read_text(encoding='utf-8'))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'images').mkdir(exist_ok=True)
    for p in pages:
        source = ROOT / p['source']
        if not source.is_file():
            source = OUT / 'images' / p['image']
        if not source.is_file():raise FileNotFoundError(source)
        target=OUT / 'images' / p['image']
        if source.resolve()!=target.resolve():shutil.copy2(source,target)
    render_html(pages)
    render_pdf(pages)

def render_html(pages):
    nav = ''.join(f'<a href="#p{i+1}">{i+1:02d} / {html.escape(p["title"])}</a>' for i,p in enumerate(pages))
    sections = []
    for i,p in enumerate(pages):
        paras = ''.join(f'<p>{html.escape(t)}</p>' for t in p['steps'])
        notes = ''.join(f'<p>{html.escape(t)}</p>' for t in p['notes'])
        sections.append(f'''<section id="p{i+1}"><div class="eyebrow">{i+1:02d} / {html.escape(p['tag'])}</div>
<h2>{html.escape(p['title'])}</h2><div class="layout"><figure><a href="images/{p['image']}" target="_blank" rel="noopener"><img src="images/{p['image']}" alt="{html.escape(p['caption'],quote=True)}" loading="lazy"></a><figcaption>{html.escape(p['caption'])}</figcaption><aside><h3>{html.escape(p['note_title'])}</h3>{notes}</aside></figure><div class="steps">{paras}</div></div></section>''')
    document = '''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>HIVE: PROTOCOL 13 - руководство 0.7.0</title><style>
:root{color-scheme:dark;--bg:#040c09;--panel:#0b1c15;--line:#315b49;--text:#d1e2d8;--muted:#8fb09e;--accent:#65ddac}*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:var(--bg);color:var(--text);font:17px/1.6 system-ui,sans-serif}a{color:var(--accent)}header{padding:48px max(24px,calc((100% - 1420px)/2));border-bottom:1px solid var(--line);background:linear-gradient(135deg,#133a29,#040c09)}h1{font-size:clamp(28px,5vw,62px);letter-spacing:.06em;margin:.1em 0}header p{max-width:850px;color:var(--muted)}.buttons{display:flex;gap:12px;flex-wrap:wrap}.buttons a{border:1px solid var(--line);padding:10px 18px;text-decoration:none}main{display:grid;grid-template-columns:240px 1fr;gap:36px;max-width:1480px;margin:auto;padding:32px 24px}nav{position:sticky;top:20px;align-self:start;max-height:90vh;overflow:auto}nav a{display:block;padding:7px 10px;text-decoration:none;font-size:14px;border-left:1px solid var(--line)}nav a:hover{background:var(--panel)}section{padding:0 0 48px;margin-bottom:40px;border-bottom:1px solid var(--line);scroll-margin-top:24px}h2{font-size:30px;line-height:1.2;margin:8px 0 24px}.eyebrow{color:var(--accent);font-size:12px;letter-spacing:.12em;text-transform:uppercase}.layout{display:grid;grid-template-columns:minmax(0,1.5fr) minmax(260px,1fr);gap:28px}figure{margin:0}img{display:block;width:100%;height:auto;border:1px solid var(--line)}figcaption{font-size:13px;color:var(--muted);margin-top:10px}aside{padding:16px 20px;background:var(--panel);border-left:3px solid var(--accent);margin-top:20px}aside p{font-size:15px;margin:6px 0}h3{font-size:16px;margin:0 0 8px}.steps p{margin:0 0 18px}footer{padding:30px;text-align:center;color:var(--muted);font-size:13px}@media(max-width:1100px){main{grid-template-columns:1fr}nav{position:static;max-height:240px}.layout{grid-template-columns:1fr}}@media print{nav,.buttons{display:none}main{display:block}body{background:white;color:black}section{break-before:page}aside{background:#eee;color:black}}
</style><header><div class="eyebrow">ПОЛЕВОЕ РУКОВОДСТВО / 29.10.2026 / АЛЬФА 0.7.0</div><h1>HIVE: PROTOCOL 13</h1><p>Подключение, управление и прохождение шести глав. Дальше есть сюжетные спойлеры и условия всех финальных маршрутов. Нажмите на снимок, чтобы открыть его в полном размере. Адреса лобби в примере не являются адресами вашей игры.</p><div class="buttons"><a href="guide.pdf">Скачать PDF</a><a href="https://github.com/Hollwestn/HIVE-Protocol13">Проект на GitHub</a><a href="https://github.com/Hollwestn/HIVE-Protocol13/releases/latest">Скачать игру</a></div></header><main><nav>''' + nav + '</nav><article>' + ''.join(sections) + '''</article></main><footer>Снимки работающего клиента 0.7.0. Ускорение используется только в проверках. Публичная альфа: полный объём первоначального ТЗ ещё не принят.</footer></html>'''
    (OUT / 'index.html').write_text(document, encoding='utf-8')

def render_pdf(pages):
    pdfmetrics.registerFont(TTFont('Hive',str(FONT/'RobotoCondensed-Regular.ttf')))
    pdfmetrics.registerFont(TTFont('HiveBold',str(FONT/'RobotoCondensed-Bold.ttf')))
    destination = ROOT/'output/pdf/HIVE_Protocol13_Guide_RU.pdf'
    destination.parent.mkdir(parents=True,exist_ok=True)
    c = canvas.Canvas(str(destination),pagesize=landscape(A4),pageCompression=1)
    c.setTitle('HIVE: PROTOCOL 13 | Руководство игрока 0.7.0')
    c.setAuthor('HIVE: PROTOCOL 13')
    width,height = landscape(A4)
    ink,muted,green = HexColor('#d1e2d8'),HexColor('#91b09f'),HexColor('#69ddb0')
    styles = {
        'body':ParagraphStyle('body',fontName='Hive',fontSize=11.5,leading=15.2,textColor=ink,spaceAfter=12),
        'note':ParagraphStyle('note',fontName='Hive',fontSize=10.8,leading=14,textColor=ink),
        'caption':ParagraphStyle('caption',fontName='Hive',fontSize=9,leading=11.5,textColor=muted),
    }
    def paragraph(text,x,y,w,kind):
        p=Paragraph(html.escape(plain(text)),styles[kind]);_,h=p.wrap(w,height)
        p.drawOn(c,x,y-h)
        return y-h
    for i,p in enumerate(pages):
        c.setFillColor(HexColor('#040c09'));c.rect(0,0,width,height,fill=1,stroke=0)
        c.setStrokeColor(HexColor('#315b49'));c.line(34,height-42,width-34,height-42)
        c.setFillColor(green);c.setFont('Hive',10)
        c.drawString(34,height-31,plain('HIVE / ПОЛЕВОЕ РУКОВОДСТВО / '+p['tag'].upper()))
        c.setFillColor(ink);c.setFont('HiveBold',26);c.drawString(34,height-78,plain(p['title']))
        imagepath=OUT/'images'/p['image']
        c.bookmarkPage('p'+str(i+1));c.addOutlineEntry(plain(p['title']),'p'+str(i+1),0)
        if i==0:
            cover_width=630
            c.drawImage(ImageReader(str(imagepath)),(width-cover_width)/2,145,width=cover_width,height=cover_width*9/16,mask='auto')
            y=paragraph(p['caption'],34,135,width-68,'caption')
            for note in p['notes']:y=paragraph(note,34,y-4,width-68,'note')
            if y<55:raise RuntimeError(f'Cover overflow: {y}')
            # Cover instructions live in the HTML and the following dedicated pages.
        else:
            x,top,iw=34,height-108,486
            ih=iw*9/16
            c.drawImage(ImageReader(str(imagepath)),x,top-ih,width=iw,height=ih,mask='auto')
            cy=paragraph(p['caption'],x,top-ih-10,iw,'caption')
            ny=cy-24
            c.setFillColor(green);c.setFont('HiveBold',12);c.drawString(x,ny,plain(p['note_title']))
            ny-=13
            for note in p['notes']:ny=paragraph(note,x,ny,iw,'note')-5
            if ny<55:raise RuntimeError(f'Notes overflow page {i+1}: {ny}')
            y=top
            for t in p['steps']:y=paragraph(t,545,y,width-579,'body')-11
            if y<55:raise RuntimeError(f'Body overflow page {i+1}: {y}')
        c.setStrokeColor(HexColor('#315b49'));c.line(34,41,width-34,41)
        c.setFont('Hive',9);c.setFillColor(muted)
        c.drawString(34,26,'АЛЬФА 0.7.0 / 29.10.2026 / СЮЖЕТНЫЕ СПОЙЛЕРЫ')
        c.drawRightString(width-34,26,f'{i+1:02d} / {len(pages):02d}')
        c.showPage()
    c.save()
    shutil.copy2(destination,OUT/'guide.pdf')
    print(json.dumps({'pages':len(pages),'pdf':str(destination),'bytes':destination.stat().st_size,'html':str(OUT/'index.html')},ensure_ascii=False))

if __name__=='__main__':main()
