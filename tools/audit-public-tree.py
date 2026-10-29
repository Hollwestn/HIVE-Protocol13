"""Audit the Git index without printing potentially sensitive file contents."""
from pathlib import Path
import subprocess,re,json
root=Path(__file__).resolve().parents[1]
files=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode('utf-8').split('\0')
files=[p for p in files if p]
forbidden=[];secrets=[];large=[];total=0
patterns=[rb'gh[pousr]_[A-Za-z0-9]{25,}',rb'github_pat_[A-Za-z0-9_]{30,}',rb'sk-proj-[A-Za-z0-9_-]{20,}',rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----']
for name in files:
    p=root/name;data=p.read_bytes();total+=len(data)
    if any(part in name.split('/') for part in ['target','release','artifacts','.runtime','references','saves']):forbidden.append(name)
    if p.name in ['MASTER_PROMPT_CODEX.md','settings.properties'] or p.suffix in ['.auth','.pfx','.p12','.key','.pem']:forbidden.append(name)
    if name.startswith('game-client/src/main/resources/assets/reference/'):forbidden.append(name)
    if name.endswith(('github-publish.py','github-account-check.py')):forbidden.append(name)
    if len(data)>50_000_000:large.append(name)
    if any(re.search(pattern,data) for pattern in patterns):secrets.append(name)
report={'files':len(files),'bytes':total,'forbiddenPaths':forbidden,'credentialPatternFiles':secrets,'over50MB':large}
print(json.dumps(report,ensure_ascii=False))
if forbidden or secrets or large:raise SystemExit(1)
