param([string[]]$GameArgs = @())
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
$appVersion = ([xml](Get-Content -Raw -LiteralPath 'pom.xml')).project.version
& $javaExecutable '--enable-native-access=ALL-UNNAMED' '-jar' ('game-client/target/game-client-'+$appVersion+'.jar') @GameArgs
exit $LASTEXITCODE
