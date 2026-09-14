$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '..\lc_modern\build-tools'
$ndk = Join-Path $toolRoot 'sdk\ndk\27.2.12479018'
$cmake = Join-Path $projectRoot 'runtime\cmake-python\cmake\data\bin\cmake.exe'
$ninja = Join-Path $projectRoot 'runtime\cmake-python\bin\ninja.exe'
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
