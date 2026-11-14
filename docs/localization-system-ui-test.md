# Localization expansion and 柔光玻璃 / Liquid Acrylic test build

This branch produces a signed Actions artifact only. It does not merge main,
create a tag, publish a Release, or remove earlier test releases. Device testing
is intentionally left to the user.

## Translation changes

- Applying a Root pack creates an explicit resource-name-to-JSON allowlist.
  Ambiguous basenames are excluded; qualified resource names remain available.
- A narrow `UnityEngine.TextAsset.get_text` hook covers built-in Unity text
  assets whose names match that allowlist. Its bootstrap observes actual
  Resources/AssetBundle/TextAsset internal-call resolution on an attached game
  thread. It does not hook `il2cpp_runtime_invoke`, call managed APIs from the
  startup worker, use fixed game offsets, or replace verification results.
- Unity's thread-safe `Object.CurrentThreadIsMainThread` predicate gates the
  name getter and replacement. Background-thread readers retain original text.
- The runtime checks instance/static flags, parameter count, return type and
  executable address ranges before installing the getter. Missing APIs or
  unsupported signatures leave the existing loader intact and produce logs.
- The normal file loader and beta.5 verification guard remain in place.
- Root settings include **補齊 PC 版翻譯**, enabled by default. On apply it
  downloads the current LLC official ZIP and fills missing files, missing
  ID-addressed records and untranslated display fields. Existing Chinese
  wording, resource order, IDs and non-text fields are retained. Format
  parameters and markup must match before a present text field is replaced.
- This is not a claim of 100% translation. Text not translated upstream,
  ID-less structures, images and byte-based readers still need separate work.
  Suspected remaining text is a heuristic, not a game-wide coverage percentage.
- A network failure retains the selected pack and displays incomplete PC
  comparison status on the overview. A merge/validation failure stops apply
  before the game directory is exchanged. Reports are included in diagnostics.

Known example IDs in LLC's `LoginUIText.json`: `switch_account`,
`switch_allcacheclear`, `loginui_loading_battlehint`. `FileDownloadDesc.json`
contains download-screen world introductions. These examples have source
translations; the actual effect in Android 1.119.0 has not been verified.

After installing this APK, **reapply the pack once**, then restart the game so
the allowlist and PC supplements are present. Installing the APK alone does
not rewrite an already applied translation.

Non-root mode keeps the existing JP/KR replacement approach; this native
TextAsset expansion and PC supplementation require Root/LSPosed mode.

## test.7 corrections and validation

- The reported `ov0` apply failure mapped to `PcTranslationSupplementKt`.
  Its Java-compatible regex contained an unescaped closing brace rejected by
  Android ICU. The literal is corrected and CI compiles the actual patterns
  with ICU before building. Apply errors now retain their underlying cause.
- Official LLC 2026111301 and the corresponding Android builder archive were
  compared directly: the named title/loading resources already contain Chinese.
  The real-archive integration test leaves a complete pack unchanged and repairs
  deliberately removed files/rows/fields. See the archive audit below.
- The TextAsset bootstrap now follows a validated ARM64 branch veneer to the
  shared internal-call resolver. Unity-generated calls bypass the public export
  previously hooked. Evidence comes from retained 1.117.1 artifacts and Unity
  source; this does not establish runtime coverage in game 1.119.0.
- Hook trampolines are published atomically, and bootstrap retries occur on a
  separate bounded worker. Main-thread restrictions remain in effect.
- The interface was rebuilt with shared overview/settings/log components,
  consistent icons, full-width log cards, grouped rows and a collapsing title.
  Floating navigation intentionally overlaps scrolling content, with bottom
  scroll space so the last item remains reachable.
- Local validation: ARM64 native build; ICU pattern compilation; Release build
  and lint; 56 executed unit tests with no failures (one separate test skipped);
  11 Compose renders covering both materials, dark/light, logs, large text and
  landscape. These are desktop renders, not physical-device verification.

## Interface changes

**設定 → 介面與顯示 → 系統介面風格** switches between **柔光玻璃** and
**Liquid Acrylic**. The selection persists across restarts. Both styles control
light/dark colors, card geometry, preference rows, switches, selection dialogs,
buttons, top-level titles and bottom navigation. Functional state and settings
are shared between the styles.

柔光玻璃 (based on HyperOS 4) uses rounded glass-edged surfaces, floating pill navigation and
larger titles. Liquid Acrylic (based on ColorOS 17) uses compact grouped surfaces, pill action buttons,
bottom-aligned selection dialogs and its own control geometry. These are
Compose interpretations, not OEM framework components or a claim of exact
pixel matching on every OEM version.

Official version references:

- https://www.mi.com/global/hyperos/
- https://www.coloros.com/version/coloros17/

## User test focus

1. Reapply a Root pack and check the PC comparison summary.
2. Check title-screen account/cache buttons, initial loading text and in-game
   loading tips. Share diagnostics if these remain in the original language.
3. Check that normal resource updates still work and restarting does not cause
   repeated downloads. Preserve the test log if the game fails to launch.
4. Switch both interface styles in light/dark mode; check scrolling, selection
   dialogs, toggles and navigation, including larger system font settings.

Diagnostic keys: `core.textasset.ready`, `core.textasset.redirect`,
`core.textasset.unavailable`, `translation.textasset.index`,
`translation.pc.supplement`, `translation.pc.supplement.failed`.

## PC archive audit

# PC pack audit and Android apply failure

This is an offline source/resource audit. No device was connected or tested.

## Android-only initialization failure

The test.6 local release mapping maps `com.lcpatch.PcTranslationSupplementKt`
to `ov0`, the class identifier in the user's apply-failure screenshot.
That file initializes its regular expressions before any comparison runs.

Its old placeholder expression, `\{[^{}]+}|</?[^>]+>`, compiles under the host
JVM's `java.util.regex`, but ICU rejects it as `U_REGEX_RULE_SYNTAX` at offset
9. Android uses ICU for regex parsing. Calling the host ICU C API reproduced
this syntax failure. Escaping the closing brace fixes it:
`\{[^{}]+\}|</?[^>]+>`.

This provides a concrete platform-specific explanation for successful JVM
tests followed by Android class-initialization/apply failure. The screenshot
does not contain the original exception stack; the complete on-device cause
chain is therefore not independently available. The corrected expression and
the other two literal expressions now pass the ICU check.

Run the inline ICU validation step from `.github/actions/android-build/action.yml` on Ubuntu with libicu installed,
or Python on Windows 10+. This check is necessary in addition to JVM tests.
Apply-stage failures now record the full cause chain and identify the PC
processing stage before any game-directory exchange.

## Compared public resources

Both archives were downloaded from the published release assets:

- [LLC 2026111301](https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany/releases/tag/2026111301)
- [Builder LLC-2026111301](https://github.com/Ian1254/LCPatch-Localization-Builder/releases/tag/LLC-2026111301)

SHA-256:

| Archive | SHA-256 |
| --- | --- |
| `official.zip` | `e63782a048bcdde282dab5b0b3d1cd0a060c364d9cc2e16143189864d6e14665` |
| `builder.zip` | `0ed3a1fea577206acc2e1c329e43f6a3c31b47876ed93c34c7746aeadd8e1deb` |

The official archive contains 2,384 JSON files under its LLC language root;
the builder contains 2,383. The sole excluded file is `Info/version.json`,
which is package metadata. The 890 semantic differences are story resources
with builder-added speaker/title data, rather than missing game text files.

These resources have identical parsed contents in both archives:

| Resource | Rows |
| --- | ---: |
| `LoginUIText.json` | 92 |
| `FileDownloadDesc.json` | 9 |
| `BattleHint.json` | 35 |
| `BattleHint_AbnorBattle.json` | 7 |
| `BattleHint_NormalBattle.json` | 4 |

The account/cache button and loading-hint translations are already in those
resources. Comparing the same complete builder release to this official PC
release should change no text. A zero-change result cannot establish whether
the Android game loaded the translation.

## Additional source fixes

- Recognize display fields actually present in the source schema, including
  `dlg`, `simpleDesc`, `messageDesc`, `flavor` and `goalDescription1` through
  `goalDescription8`. Continue excluding IDs, model references and color data.
- Match an existing literal basename before adding a second variant. In
  particular, the builder preserves `kr_settings-ui-donttranslate` in
  `JP_kr_settings-ui-donttranslate.json`, while older import normalization
  may produce `JP_settings-ui-donttranslate.json`. This does not guess or
  change the game's canonical resource path.
- Count exact upstream matches as alignment, rather than calling Latin
  names such as `E.G.O` missing translations. Alignment is not a game-wide
  translation coverage percentage.

## Real-archive regression

Set `LCPATCH_PACK_FIXTURES` to a directory containing the two archives named
`official.zip` and `builder.zip`, then run `PcPackArchiveIntegrationTest`.
The test otherwise skips, so the project does not need to redistribute
translation assets or download them during ordinary unit tests.

The real-archive run passed with:

- Full builder versus official pack: zero added files, records and fields.
- Deliberately removed `FileDownloadDesc`, removed `switch_account`, and
  replaced the first battle hint with untranslated text: one restored file,
  one restored record and one repaired field.
- All five resource names above present in the generated native allowlist.

This verifies pack preparation and indexing only. Game resource selection,
native hook coverage, and the visual result in Android 1.119.0 remain distinct
runtime questions.

## Historical bootstrap evidence (not current device verification)

```json
{
  "kind": "offline historical native evidence, not a live-device test",
  "identified_game_version": "1.117.1",
  "metadata_sha256": "478f9ea1906191d2b3c8e77476f5a422cc09364391138872dd15ae3fa1907f2d",
  "original_libil2cpp_sha256": "fbfc653607f48c2abfe4330eab784867104b80d2ff60a91684bb18bfa09ce097",
  "retained_code_sha256": "8c632e4b37b14ea69ed112355ba8d5836a833119dedc62a778ff6e8315908dda",
  "retained_code_identity_check": {
    "profile_entry": "0x52e2a58",
    "matched_bytes": 32,
    "signature": "ff4301d1fe5f02a9f65703a9f44f04a9b7cd0290169502f0f403022ae84a7139"
  },
  "public_export": {
    "name": "il2cpp_resolve_icall",
    "address": "0x4570b7c",
    "instruction": "0x14010ccd",
    "tail_branch_target": "0x45b3eb0"
  },
  "generated_textasset_call_paths": [
    {
      "text_asset_callsite": "0x9cab7b0",
      "codegen_resolver": "0x4569a08",
      "codegen_callsite": "0x4569a10",
      "shared_resolver": "0x45b3eb0"
    }
  ],
  "conclusion": "Generated TextAsset icall wrappers bypass the public export and call the shared resolver implementation. An observer on only the export does not observe these generated calls.",
  "source_corroboration": [
    "Unity 6000.6.7f1 libil2cpp/codegen/il2cpp-codegen.cpp:157-164: il2cpp_codegen_resolve_icall calls vm::InternalCalls::Resolve directly.",
    "Unity 6000.6.7f1 libil2cpp/il2cpp-api.cpp:170-172: public il2cpp_resolve_icall calls InternalCalls::Resolve."
  ],
  "fix": "Select only an ABI-preserving AArch64 B veneer target within the same executable/readable ELF image; reject an unverified public-body fallback. Retry zero/protected code on a bounded native worker without blocking font setup.",
  "validation": {
    "arm64_native_build": "passed",
    "host_native_tests": "passed with MSVC /W4 /WX; veneer bounds, cycles, BL/conditional rejection and protected-code retry included"
  },
  "limitations": [
    "The user report also showed translation.install_failed ov0; that apply failure is the primary confirmed reason the new pack was not installed.",
    "Android 1.119.0 was not supplied for offline native comparison, and no device was accessed or tested.",
    "The existing main-thread TextAsset/name safety gate remains. Background-thread readers retain original assets.",
    "Installing a common-resolver observer after an icall has already been cached may still miss the first asset; new trigger/lookup/thread-skip logs distinguish this from missing resources."
  ]
}

```

The interface fixtures share the existing PC supplement test source file, which is also a screenshot source directory. Production components share SystemUi.kt; native branch decoding shares text_asset_paths.h.
