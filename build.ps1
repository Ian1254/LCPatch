$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = Join-Path $PSScriptRoot '..\lc_modern\build-tools\jdk\jdk-17.0.20.1+1'
$env:ANDROID_HOME = Join-Path $PSScriptRoot '..\lc_modern\build-tools\sdk'
$nativeBuild = Join-Path $PSScriptRoot 'native-refactor\build.ps1'
& $nativeBuild
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Remove-Item -LiteralPath (Join-Path $PSScriptRoot 'app\src\main\jniLibs\arm64-v8a\libbypass.so') -Force -ErrorAction SilentlyContinue
$gradle = Join-Path $PSScriptRoot '..\lc_modern\build-tools\gradle9\bin\gradle.bat'
& $gradle --no-daemon :app:assembleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
