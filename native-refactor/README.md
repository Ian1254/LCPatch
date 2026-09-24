# LCPatch open-source native core

This directory contains the source and build definition for the packaged
`liblcpatch_core.so`. The release APK contains no legacy `libbypass.so` binary.

The core keeps only:

- isolated read-only `open`, `openat` and `fopen` redirects in the PLT/GOT of
  `libunity.so` and `libil2cpp.so` for game requests below
  `Localize/<language>/`, with per-file fallback to the original game asset;
- a Unity font hook with verified profiles for Limbus 1.113.1 and 1.115.0 that reads the game cache
  `ChineseFont.ttf`;
- ELF, GNU Build ID, entry and accessor signature guards;
- a strict dynamic locator for unknown Unity builds with unique accessor, caller and entry plus a separate entry fingerprint;
- bounded TrueType loading and structured logcat messages.

Translation files are synchronized by the app with root before game launch. A
Localize redirect is still required because the game normally opens its bundled
`files/Assets/Resources_moved/Localize/<language>` path rather than the cache
directory. The redirect changes access only when the matching cache file
exists and otherwise falls back to the original game file. Inline libc hooks
are excluded; only the two game libraries' import slots are changed.
Process-wide signal, timer,
exit, abort, wait, ptrace and AppSealing patches are not included.

The Dobby source is pinned at commit `5dfc8546954ce3b3198132ab13fddb89ee92cdd7`
under `app/src/main/cpp/third_party/Dobby` and remains Apache-2.0 licensed.
The vendored commit carries a small Android/NDK 27 compatibility patch: an ELF
GOT relocation in the ARM64 bridge, removal of a stale include, correction of
stale `RuntimeModule` field names, and removal of an include cycle in the
memory-readability helper. `SymbolResolver` is disabled because this core uses
`dlsym` only for exported libc symbols.

Install Java 17 or newer, Android SDK with NDK 27.2.12479018, CMake and Ninja.
Set `ANDROID_SDK_ROOT`, then run `build.ps1` from the repository root to build
the native core and unsigned release APK. Run `native-refactor/build.ps1` alone
to configure, compile, strip and copy the ARM64 library into the Android source
set. The 1.113.1 and 1.115.0 profiles require matching GNU Build ID note,
executable offsets and exact entry/accessor signatures. The 1.115.0 profile
was checked against the ARM64 Unity library in the game's APK; an on-device
font swap remains to be confirmed. Unknown builds use strict dynamic
verification: one matching accessor, one structurally valid caller, one
function start in an executable segment, and the known entry fingerprint.
Only the ADRP page immediate at entry +20 is masked; its opcode and register
remain checked. Any mismatch disables the font hook. This can accommodate
address-only drift with the same implementation, not arbitrary future Unity
changes. Read `core.unity.verified` or `core.unity.dynamic_verified`, followed
by `core.font.hooked` and `core.font.swap`, to confirm each stage in the native
log. `core.unity.unsupported` includes the failure reason.
