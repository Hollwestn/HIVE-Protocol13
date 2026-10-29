"""Compare WiX dark's extracted MSI files with app-image, without installing."""
from pathlib import Path
import json,hashlib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
work=root/'artifacts/release070/msi-content'
tree=ET.parse(work/'package.wxs');ns={'w':'http://schemas.microsoft.com/wix/2006/wi'}
product=tree.getroot().find('w:Product',ns)
assert product.attrib['Version']=='0.7.0'
directory=product.find('.//w:Directory[@Id="INSTALLDIR"]',ns)
image=root/'release/build-alpha070-publish-01/app-image/HIVE-Protocol13'
checked=[];markers=[]
def sha(p):return hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def visit(node,relative):
    for component in node.findall('w:Component',ns):
        for file in component.findall('w:File',ns):
            dest=relative/file.attrib['Name'];source=root/file.attrib['Source']
            assert source.resolve().is_relative_to(work.resolve())
            if dest.as_posix()=='app/.package':
                assert source.read_text(encoding='utf-8').strip()=='HIVE-Protocol13'
                markers.append(dest.as_posix());continue
            assert sha(source)==sha(image/dest),str(dest)
            checked.append(dest.as_posix())
    for child in node.findall('w:Directory',ns):visit(child,relative/child.attrib['Name'])
visit(directory,Path())
expected={p.relative_to(image).as_posix() for p in image.rglob('*') if p.is_file()}-{'app/.jpackage.xml'}
assert set(checked)==expected
report={'passed':True,'msiVersion':product.attrib['Version'],'packagedFiles':len(checked)+len(markers),'matchedApplicationFiles':len(checked),'everyApplicationFileMatchesTestedAppImage':True,'installerMarkersVerified':markers,'guideIncluded':'app/docs/guide/guide.pdf' in checked,'launcherIncluded':'HIVE-Protocol13.exe' in checked,'nativeAppImageMinutePassed':True,'actualInstallUninstallVerified':False,'reason':'A related installation already exists; installation check stopped before making changes. MSI payload extracted with WiX dark and compared without installing.','codeSigned':False}
path=root/'docs/qa/0.7.0/installer-payload.json';path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
