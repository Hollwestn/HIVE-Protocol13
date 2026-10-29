param([string]$Msi = 'release/HIVE-Protocol13-Setup.msi')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$msiPath = (Resolve-Path -LiteralPath $Msi).Path
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase($msiPath,0)
$view = $database.OpenView('SELECT `Value` FROM `Property` WHERE `Property` = ''ProductCode''')
$view.Execute()
$record = $view.Fetch()
$productCode = $record.StringData(1)
if ($productCode -notmatch '^\{[0-9A-Fa-f-]{36}\}$') { throw 'Invalid MSI product code' }
$upgradeView=$database.OpenView('SELECT `Value` FROM `Property` WHERE `Property` = ''UpgradeCode''')
$upgradeView.Execute()
$upgradeRecord=$upgradeView.Fetch()
$upgradeCode=$upgradeRecord.StringData(1)
$relatedProducts=@($installer.RelatedProducts($upgradeCode) | Where-Object { $_ })
if ($relatedProducts.Count -gt 0) { throw 'A related user installation already exists; refusing to upgrade or downgrade it.' }
$registryPaths = @(('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\'+$productCode),('HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\'+$productCode))
foreach ($registryPath in $registryPaths) { if (Test-Path -LiteralPath $registryPath) { throw 'A user installation already exists; refusing to change it.' } }
$testRoot = Join-Path $projectRoot ('artifacts/installer-smoke-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
$installTarget = [IO.Path]::GetFullPath((Join-Path $testRoot 'installed-game'))
if (-not $installTarget.StartsWith($projectRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe test installation path' }
New-Item -ItemType Directory -Path $testRoot | Out-Null
$capture = Join-Path $testRoot 'capture'
$report = [ordered]@{productCode=$productCode;testRoot=$testRoot;registeredBefore=$false;installExitCode=$null;launcherExitCode=$null;uninstallExitCode=$null;qaVerified=$false;exeRemaining=$null}
$installed = $false
try {
    $argsInstall = '/i "'+$msiPath+'" /qn /norestart INSTALLDIR="'+$installTarget+'" /L*V "'+(Join-Path $testRoot 'install.log')+'"'
    $installation = Start-Process -FilePath 'msiexec.exe' -ArgumentList $argsInstall -WindowStyle Hidden -PassThru -Wait
    $report.installExitCode=$installation.ExitCode
    if ($installation.ExitCode -notin @(0,3010)) { throw ('Installation failed: '+$installation.ExitCode) }
    $installed=$true
    $launcher = Join-Path $installTarget 'HIVE-Protocol13.exe'
    if (-not (Test-Path -LiteralPath $launcher)) { throw 'Installed launcher missing' }
    $env:JAVA_HOME=''
    $env:PATH='C:\Windows\System32;C:\Windows'
    $env:JAVA_TOOL_OPTIONS='-Dhive.mute=true -Dhive.dataDir="'+(Join-Path $testRoot 'data')+'"'
    $argsGame='--qa=true --record=true --width=1536 --height=864 --capture="'+$capture+'"'
    $game=Start-Process -FilePath $launcher -ArgumentList $argsGame -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -Wait
    $report.launcherExitCode=$game.ExitCode
    if ($game.ExitCode -ne 0) { throw ('Installed launcher failed: '+$game.ExitCode) }
    $qa=Get-Content -Raw -LiteralPath (Join-Path $capture 'qa-result.json') | ConvertFrom-Json
    if (-not $qa.b4Sealed -or $qa.cycle -ne 2 -or $qa.parts -ne 10 -or $qa.independentTcpClients -ne 3 -or -not $qa.worldFrozenDuringVote -or @($qa.lastSevenSecondsMovement | Where-Object {$_ -gt 1}).Count -ne 3) { throw 'Installed game QA assertions failed' }
    $report.qaVerified=$true
    $report.qa=$qa
} finally {
    if ($installed) {
        $argsUninstall='/x "'+$msiPath+'" /qn /norestart /L*V "'+(Join-Path $testRoot 'uninstall.log')+'"'
        $uninstall=Start-Process -FilePath 'msiexec.exe' -ArgumentList $argsUninstall -WindowStyle Hidden -PassThru -Wait
        $report.uninstallExitCode=$uninstall.ExitCode
    }
    $report.exeRemaining=Test-Path -LiteralPath (Join-Path $installTarget 'HIVE-Protocol13.exe')
    $report.productStateAfter=$installer.ProductState($productCode)
    $report | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $testRoot 'result.json')
    $report | ConvertTo-Json -Depth 8
}
if ($report.uninstallExitCode -notin @(0,3010) -or $report.exeRemaining -or $report.productStateAfter -ne -1) { throw 'Uninstall verification failed; inspect report' }
