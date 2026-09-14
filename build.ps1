$ErrorActionPreference = 'Stop'

if (-not $env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
}
if (-not $env:ANDROID_SDK_ROOT) {
    throw 'Set ANDROID_SDK_ROOT (or ANDROID_HOME) to an Android SDK containing NDK 27.2.12479018.'
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java 17 or newer must be available on PATH.'
}

$nativeBuild = Join-Path $PSScriptRoot 'native-refactor\build.ps1'
& $nativeBuild
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Remove-Item -LiteralPath (Join-Path $PSScriptRoot 'app\src\main\jniLibs\arm64-v8a\libbypass.so') -Force -ErrorAction SilentlyContinue
$gradle = Join-Path $PSScriptRoot 'gradlew.bat'
& $gradle --no-daemon :app:assembleRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
