param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'C:\Users\sanki\.cache\codex-jdks\temurin17\jdk-17.0.19+10'
$gradle = 'C:\Users\sanki\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat'
Push-Location $projectRoot
try {
 $arguments = @('--no-daemon', '--console=plain', 'assembleDebug', 'testDebugUnitTest', 'lintDebug')
 if ($Offline) { $arguments += '--offline' }
 & $gradle @arguments
 if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
} finally { Pop-Location }
