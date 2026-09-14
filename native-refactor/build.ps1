$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path $PSScriptRoot -Parent
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdkRoot) {
    throw 'Set ANDROID_SDK_ROOT (or ANDROID_HOME) to an Android SDK containing NDK 27.2.12479018.'
}
$ndk = Join-Path $sdkRoot 'ndk\27.2.12479018'
if (-not (Test-Path -LiteralPath $ndk)) {
    throw "Android NDK 27.2.12479018 was not found at $ndk."
}
$cmake = (Get-Command cmake -ErrorAction Stop).Source
$ninja = (Get-Command ninja -ErrorAction Stop).Source
$build = Join-Path $PSScriptRoot 'build-arm64'
$output = Join-Path $projectRoot 'app\src\main\jniLibs\arm64-v8a\liblcpatch_core.so'

& $cmake -S $PSScriptRoot -B $build -G Ninja `
    "-DCMAKE_TOOLCHAIN_FILE=$(Join-Path $ndk 'build\cmake\android.toolchain.cmake')" `
    '-DANDROID_ABI=arm64-v8a' '-DANDROID_PLATFORM=android-28' `
    '-DCMAKE_BUILD_TYPE=Release' "-DCMAKE_MAKE_PROGRAM=$ninja"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $cmake --build $build --target lcpatch_core --parallel
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

New-Item -ItemType Directory -Path (Split-Path $output -Parent) -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $build 'liblcpatch_core.so') -Destination $output -Force
& (Join-Path $ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe') --strip-all $output
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Get-FileHash -LiteralPath $output -Algorithm SHA256
