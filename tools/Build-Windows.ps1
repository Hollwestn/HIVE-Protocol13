param([string]$RuntimePath = '', [switch]$SkipVerify, [switch]$Resume, [switch]$AppImageOnly, [string]$BuildId = (Get-Date -Format 'yyyyMMdd-HHmmss'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$appVersion = ([xml](Get-Content -Raw -LiteralPath 'pom.xml')).project.version
$mainJar = 'game-client-' + $appVersion + '.jar'
if (-not $env:JAVA_HOME) { throw 'Укажите JAVA_HOME на JDK 25' }
$packager = Join-Path $env:JAVA_HOME 'bin/jpackage.exe'
if (-not (Test-Path -LiteralPath $packager)) { throw 'jpackage.exe не найден в JAVA_HOME' }
if (-not $RuntimePath) {
    $bundledRuntime = Get-ChildItem -LiteralPath '.runtime' -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
    if (-not $bundledRuntime) { throw 'Передайте -RuntimePath с путём к OpenJDK JRE 25 (Windows x64)' }
    $RuntimePath = $bundledRuntime.FullName
}
$RuntimePath = (Resolve-Path -LiteralPath $RuntimePath).Path
if (-not $SkipVerify) { & .\mvnw.cmd -B verify; if ($LASTEXITCODE -ne 0) { throw 'Maven verify завершился ошибкой' } }
$staleDependencies = Get-ChildItem -LiteralPath 'game-client/target/lib' -File | Where-Object { $_.Name -match '^(game-server|shared-model)-' -and $_.Name -notin @(('game-server-'+$appVersion+'.jar'),('shared-model-'+$appVersion+'.jar')) }
if ($staleDependencies) { throw 'В target/lib есть JAR старой версии. Выполните mvnw clean verify перед упаковкой.' }
if ($BuildId -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Недопустимый BuildId' }
$buildRoot = Join-Path $projectRoot ('release/build-' + $BuildId)
if ((Test-Path -LiteralPath $buildRoot) -and -not $Resume) { throw 'Этот каталог сборки уже существует. Выберите другой BuildId или -Resume для продолжения упаковки.' }
$staging = Join-Path $buildRoot 'staging'
$imageRoot = Join-Path $buildRoot 'app-image'
$installers = Join-Path $buildRoot 'installers'
if (-not $Resume) {
New-Item -ItemType Directory -Path $staging,$imageRoot,$installers | Out-Null
Copy-Item -LiteralPath (Join-Path 'game-client/target' $mainJar) -Destination $staging
Copy-Item -LiteralPath 'game-client/target/lib' -Destination $staging -Recurse
$docs = Join-Path $staging 'docs'
New-Item -ItemType Directory -Path $docs | Out-Null
Get-ChildItem -LiteralPath $projectRoot -Filter '*.md' | Where-Object { $_.Name -notin @('MASTER_PROMPT_CODEX.md','REFERENCE_GUIDE.md') } | Copy-Item -Destination $staging
foreach ($name in @('STATUS.md','QA_0_7_0_RU.md','ART_PROVENANCE.md','RELEASE_0_7_0_RU.md')) {
    $sourceDoc=Join-Path 'docs' $name
    if (Test-Path -LiteralPath $sourceDoc) { Copy-Item -LiteralPath $sourceDoc -Destination $docs }
}
if (Test-Path -LiteralPath 'docs/guide') { Copy-Item -LiteralPath 'docs/guide' -Destination (Join-Path $docs 'guide') -Recurse }
if (Test-Path -LiteralPath 'docs/qa') { Copy-Item -LiteralPath 'docs/qa' -Destination (Join-Path $docs 'qa') -Recurse }
$common = @('--name','HIVE-Protocol13','--app-version',$appVersion,'--vendor','HIVE Protocol13','--description',('HIVE Protocol13 technical alpha '+$appVersion))
& $packager '--type' 'app-image' '--input' $staging '--dest' $imageRoot '--main-jar' $mainJar '--main-class' 'ru.hive13.client.Launcher' '--runtime-image' $RuntimePath '--icon' (Join-Path $projectRoot 'assets/brand/hive-icon.ico') '--java-options' '--enable-native-access=ALL-UNNAMED' @common
if ($LASTEXITCODE -ne 0) { throw 'Не удалось создать app-image' }
}
$image = Join-Path $imageRoot 'HIVE-Protocol13'
if (-not (Test-Path -LiteralPath (Join-Path $image 'HIVE-Protocol13.exe'))) { throw 'Нет app-image для продолжения' }
if ($AppImageOnly) { Write-Output ('APP_IMAGE=' + $image); return }
$common = @('--name','HIVE-Protocol13','--app-version',$appVersion,'--vendor','HIVE Protocol13','--description',('HIVE Protocol13 technical alpha '+$appVersion))
foreach ($kind in @('exe','msi')) {
    if (-not (Get-ChildItem -LiteralPath $installers -Filter ('*.'+$kind))) {
    & $packager '--type' $kind '--app-image' $image '--dest' $installers '--win-per-user-install' '--win-dir-chooser' '--win-menu' '--win-menu-group' 'HIVE Protocol13' '--win-shortcut' '--temp' (Join-Path $buildRoot ('wix-'+$kind)) '--verbose' @common
    if ($LASTEXITCODE -ne 0) { throw ('Не удалось создать ' + $kind) }
    }
    $artifact = Get-ChildItem -LiteralPath $installers -Filter ('*.'+$kind) | Select-Object -First 1
    Copy-Item -LiteralPath $artifact.FullName -Destination ('release/HIVE-Protocol13-Setup.'+$kind) -Force
}
Compress-Archive -LiteralPath $image -DestinationPath 'release/HIVE-Protocol13-Portable.zip' -Force
Get-FileHash -LiteralPath 'release/HIVE-Protocol13-Setup.exe','release/HIVE-Protocol13-Setup.msi','release/HIVE-Protocol13-Portable.zip' -Algorithm SHA256 | ForEach-Object { $_.Hash.ToLowerInvariant() + '  ' + (Split-Path $_.Path -Leaf) } | Set-Content -Encoding UTF8 'release/SHA256SUMS.txt'
Write-Output ('APP_IMAGE=' + $image)
