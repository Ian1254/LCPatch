# LCPatch open-source native core

This directory contains the source and build definition for the packaged
`liblcpatch_core.so`. The release APK contains no legacy `libbypass.so` binary.

The core keeps only:

- isolated read-only `open`, `openat` and `fopen` redirects in the PLT/GOT of
  `libunity.so` and `libil2cpp.so` for game requests below
  `Localize/<language>/`, with per-file fallback to the original game asset;
- a single verified Unity font hook reading the game cache `ChineseFont.ttf`;
- the verified Limbus 1.113.1 Unity font hook;
- ELF, GNU Build ID, entry and accessor signature guards;
- a unique-candidate structural locator for later Unity builds;
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

Run `native-refactor/build.ps1` from PowerShell to configure, compile, strip and
copy the ARM64 library into the Android source set. The known 1.113.1 build uses
the exact Build ID and instruction signatures. Later builds are accepted only
when the accessor signature and its validated caller produce one unique result;
zero or multiple candidates leave the font hook disabled.
