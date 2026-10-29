"""Validate and hash Windows release artifacts after packaging and committing sources."""
from pathlib import Path
from datetime import datetime,timezone
import json,hashlib,zipfile,subprocess,shutil

root=Path(__file__).resolve().parents[1]
release=root/'release';version='0.7.0';build='alpha070-publish-01'
app=release/('build-'+build)/'app-image/HIVE-Protocol13/app'
def sha(path):return hashlib.file_digest(path.open('rb'),'sha256').hexdigest()
tested=json.loads((root/'docs/qa/0.7.0/tested-jars.json').read_text())
for name,digest in tested.items():
    file=app/name if name.startswith('game-client-') else app/'lib'/name
    assert sha(file)==digest,name
with zipfile.ZipFile(app/'game-client-0.7.0.jar') as jar:
    assert not any(n.startswith('assets/reference/') for n in jar.namelist())
    assert all('assets/brand/'+n in jar.namelist() for n in ['hive-icon.png','menu-background-v1.png','portraits-v1.png'])
pdf=root/'output/pdf/HIVE_Protocol13_Guide_RU.pdf'
assert sha(pdf)==sha(app/'docs/guide/guide.pdf')
with zipfile.ZipFile(release/'HIVE-Protocol13-Portable.zip') as archive:
    assert archive.testzip() is None
    names=archive.namelist()
    for suffix in ['HIVE-Protocol13.exe','app/docs/guide/index.html','app/docs/guide/guide.pdf','runtime/bin/java.exe']:
        assert 'HIVE-Protocol13/'+suffix in names,suffix
    assert not any(n.endswith(('.auth','settings.properties')) or '/assets/reference/' in n for n in names)
    embedded=archive.read('HIVE-Protocol13/app/docs/guide/guide.pdf')
    assert hashlib.sha256(embedded).hexdigest()==sha(pdf)
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
files=[release/'HIVE-Protocol13-Setup.exe',release/'HIVE-Protocol13-Setup.msi',release/'HIVE-Protocol13-Portable.zip',pdf]
artifacts=[{'file':p.name,'bytes':p.stat().st_size,'sha256':sha(p)} for p in files]
manifest={'name':'HIVE: PROTOCOL 13','version':version,'status':'public technical alpha; original full specification incomplete','buildId':build,'platform':'Windows x64','createdUtc':datetime.now(timezone.utc).isoformat(),'sourceRepository':'https://github.com/Hollwestn/HIVE-Protocol13','sourceCommit':commit,'networkProtocol':5,'testedJars':tested,'artifacts':artifacts,'verification':{'automatedTests':157,'failures':0,'freshSixActExodusRuns':{'trio':True,'medicAndScout':True},'alternativeFinalsFromChapterFiveCheckpoint':['bastion','cleansing','symbiosis','protocol','awakening'],'packagedNativeMinute':True,'installerPayloadVerified':True,'actualInstallUninstallVerified':False,'threePhysicalPcsVerified':False,'sixToTenHoursVerified':False},'remainingScope':'docs/STATUS.md','codeSigned':False}
destination=release/'RELEASE_MANIFEST.json'
destination.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
lines=[a['sha256']+'  '+a['file'] for a in artifacts]
lines.append(sha(destination)+'  '+destination.name)
(release/'SHA256SUMS.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
shutil.copy2(pdf,release/pdf.name)
print(json.dumps({'commit':commit,'artifacts':artifacts,'portableIntegrity':True,'guideMatchesPackaged':True},ensure_ascii=False))
