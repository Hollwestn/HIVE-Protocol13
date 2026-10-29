"""Render the project's original vector emblem into Windows icon sizes."""
from pathlib import Path
from PIL import Image,ImageDraw
import shutil,json

root=Path(__file__).resolve().parents[1];out=root/'assets/brand';out.mkdir(parents=True,exist_ok=True)
svg='''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160"><rect width="160" height="160" rx="24" fill="#06110e"/><path d="M80 14 137 47V113L80 146 23 113V47Z" fill="#0a2018" stroke="#6ebc9e" stroke-width="5"/><path d="M80 25 127 53V107L80 135 33 107V53Z" fill="none" stroke="#244d3d" stroke-width="2"/><g fill="#a6d7be"><rect x="47" y="53" width="14" height="56" rx="3"/><rect x="73" y="42" width="14" height="78" rx="3"/><rect x="99" y="53" width="14" height="56" rx="3"/></g><path d="M61 79H99" stroke="#a6d7be" stroke-width="9"/><path d="m120 118 10-6v12l-10 6Z" fill="#b64a42"/></svg>'''
(out/'hive-icon.svg').write_text(svg,encoding='utf-8')
scale=4;image=Image.new('RGBA',(640,640));d=ImageDraw.Draw(image)
def pts(points):return [(x*scale,y*scale) for x,y in points]
d.rounded_rectangle((0,0,639,639),radius=96,fill='#06110e')
hexagon=[(80,14),(137,47),(137,113),(80,146),(23,113),(23,47)]
d.polygon(pts(hexagon),fill='#0a2018');d.line(pts(hexagon+[hexagon[0]]),fill='#6ebc9e',width=20,joint='curve')
inside=[(80,25),(127,53),(127,107),(80,135),(33,107),(33,53)]
d.line(pts(inside+[inside[0]]),fill='#244d3d',width=8,joint='curve')
for x,y,h in [(47,53,56),(73,42,78),(99,53,56)]:d.rounded_rectangle((x*4,y*4,(x+14)*4,(y+h)*4),radius=12,fill='#a6d7be')
d.line(pts([(61,79),(99,79)]),fill='#a6d7be',width=36)
d.polygon(pts([(120,118),(130,112),(130,124),(120,130)]),fill='#b64a42')
icon=image.resize((512,512),Image.Resampling.LANCZOS);icon.save(out/'hive-icon.png');icon.save(out/'hive-icon.ico',sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
target=root/'game-client/src/main/resources/assets/brand';target.mkdir(parents=True,exist_ok=True)
for name in ['hive-icon.png','menu-background-v1.png','portraits-v1.png']:shutil.copy2(out/name,target/name)
print(json.dumps({'icon':str(out/'hive-icon.ico'),'assets':{n:list(Image.open(target/n).size) for n in ['hive-icon.png','menu-background-v1.png','portraits-v1.png']}},ensure_ascii=False))
