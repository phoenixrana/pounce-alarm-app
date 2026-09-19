$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME='C:\Users\sanki\.cache\codex-jdks\temurin17\jdk-17.0.19+10'
Push-Location $projectRoot
try {
 & 'C:\Users\sanki\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat' --no-daemon --console=plain assembleRelease testDebugUnitTest lintRelease
 if($LASTEXITCODE -ne 0){throw 'Release checks failed'}
 New-Item -ItemType Directory -Force -Path releases | Out-Null
 Copy-Item -LiteralPath 'app\build\outputs\apk\release\app-release.apk' -Destination 'releases\Pounce-0.5.0.apk' -Force
 & 'C:\Users\sanki\.cache\android-sdk\build-tools\35.0.0\apksigner.bat' verify --verbose 'releases\Pounce-0.5.0.apk'
 if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
 Get-FileHash -Algorithm SHA256 -LiteralPath 'releases\Pounce-0.5.0.apk'
} finally {Pop-Location}
