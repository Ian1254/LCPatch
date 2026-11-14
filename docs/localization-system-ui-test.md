# Localization expansion and 柔光玻璃 / Liquid Acrylic test build

This branch produces a signed Actions artifact only. It does not merge main,
create a tag, publish a Release, or remove earlier test releases. Device testing
is intentionally left to the user.

## test.10: bounded callback bootstrap and directional material edges

The supplied test.9 diagnostic confirms an index with3,840 entries, then a
self-signalled SIGKILL (exit-info status9) about285ms after shared-resolver
installation. No native crash stack or relevant tombstone was retained. This
establishes the exit and ordering, not its unique cause. Raw logs are not committed.

The shared InternalCalls::Resolve observer is removed. Existing font-load and
verified localization-read callbacks now attempt the targeted TextAsset getter
installation only on an already attached runtime thread and the Unity main thread.
The public resolver is called as an API; neither its export nor shared implementation
is patched. No worker polls domain_get: the locally installed Unity source shows
that domain_get can allocate a domain before initialization. Configuration is
published only after all setup-worker hook operations finish, including early exits,
so its Dobby installer cannot race the callback installer. Atomic installer ownership
continues to serialize callback attempts. TextAsset substitution also retains
original data when the verifier profile is unknown or its signature is not ready.

This change is a narrower startup route, not a confirmed device crash resolution.
A resource consumed before the first eligible callback is not retroactively reloaded.
The next diagnostic distinguishes waiting, callback trigger, getter installation,
lookup miss and successful replacement. Background readers retain original data.

Diagnostics now obtain historical game PIDs first and filter the retained logcat
ring by game/module/PID before applying output bounds. Reporter PIDs are admitted
only through explicit game process declarations. Unrelated adjacent system rows
are excluded; Linux integration covers an exit followed by10,000 unrelated rows.
There is no new device access and no attempt to suppress process termination.

Liquid Acrylic's bar and selection panels gain an original portable treatment
with separate directional near/far rims and restrained inner bands. Screen,
soft-light and multiply blending respond to the rendered surface; the gradient
strengths/depths are reconstruction choices. The verified COUI blur/tint fallback,
250x56dp bar,48dp items,28dp sheet corners and floating content overlap remain.
No OEM shader is copied, and no exact native shader/refraction equivalence is claimed.

Local validation: ARM64 native compilation passed; testDebugUnitTest,
lintVitalRelease, assembleRelease and13 Compose previews passed. The68 JVM
cases reported64 passed and4 skipped: three Unix-only shell/publication checks
and the optional scanner fixture. Actual PC pack fixtures ran successfully.
Actions repeats the Unix checks before signing; no physical device test was run.

## test.9: applied-pack index migration and ROM material boundary

A user-supplied test.8 / game1.119.0 diagnostic showed working file redirects
and font replacement, but TextAsset startup reported an unusable resource index.
The old message also covered permission, size/read and parse failures; it does
not prove the user reapplied a pack or identify the exact filesystem failure.
The raw diagnostic (including unrelated system events) is not committed.

Opening LCPatch now repairs the existing applied pack's index from filenames,
without downloading a pack or reading/replacing JSON contents. Launch through
LCPatch stops the previous game process before repair, since native setup reads
the index once. Disabled translation retains its previous launch behavior.
Inventory has time/output/file-count/UTF-8-byte limits, NUL-separated paths and
an explicit successful-command trailer. Incomplete output is rejected even at
a complete-file boundary. Publication uses a same-directory temporary file,
0644 permissions and atomic rename, retaining the previous index on failure.
An index symlink is replaced rather than followed; unchanged content still has
read permissions repaired. Directory identity checks reject concurrent pack swaps.
Native diagnostics distinguish open/stat/empty/size/read/parse failures with errno.

The local ColorOS-17-Rodin super.img was read offline (no flash/device access).
Its sparse carrier contains an11GiB logical super image. The selected partitions:

- system_a SHA-256 f880b34e34c83622ac4bc12b6c05a3b0e5536e9a982551190d869af67317d204
- system_ext_a SHA-256 099d86882b973518dd90c2bbfc225a37b158818d82fd4e2711d3207f5ae8a25e

Both are EROFS. Actual system build.prop declares Android17/API37; system_ext
etc/build.prop declares Oplus API40, sub-API23. These are package declarations,
not a runtime query or authentication of the port's provenance.

Verified extracted oplus-framework.jar SHA-256:
7c4ab3a64d853cd798ac2fbbb3db6f784cfeb963a4ba948e1f7dded7c508613f.
Verified extracted libhwui.so SHA-256:
1ccc77af079ce680e0bc884c3bf52fa101255e7af2cb89b00579135f40f38b81.
The observed route is app COUI helper -> public Oplus View wrapper -> RenderNode
-> CriticalNative JNI -> libhwui. This rendering boundary is in-process, not a
Binder hop. A separate AppFeatureProvider query controls feature availability;
animation level, blur-disable and power settings also affect it.

JNI table candidates match actual framework signatures, including
nSetOplusMaterialCornerParams(JIFF)Z at linked0x733340 and
nSetCausticShadowParams(JFFFFFF)Z at linked0xa13120. These addresses describe
this offline binary only and are not hardcoded into LCPatch. Readable shader
fragments establish shape masks, tint, directional edge highlights and adaptive
inner shadows; the eight-light shader's relation to caustic APIs is unproven.
No claim of a recovered general-purpose refraction algorithm is made.

The stable portable Backdrop path now implements the observed COUI bottom-bar
fallback chain:25 raw-pixel MIRROR blur; light #B3666666 COLOR_DODGE followed by
#99F5F5F5 LUMINOSITY; dark #80666666 OVERLAY followed by #B3262626 LUMINOSITY.
It filters only the captured background, with no extra opaque tint on that path.
Experimental Haze retains its separate renderer approximation. No OEM shader,
binary or framework JAR is shipped; no hidden-API exemptions or system settings
are changed. Full OEM native material/caustic rendering is still not claimed.

Local validation: ARM64 native build, Release lint/assembly and13 Compose renders
passed;64 unit tests reported61 passed and3 skipped. Two skips are Unix atomic
publication/pack-swap integrations that run on Linux Actions; the other is the
existing optional scanner fixture. The real PC archive integration ran locally.

## test.8: supplied clock APK references

The user supplied two static references; neither APK was installed or executed.
No APK, recovered OEM code, image, icon, or font is included in LCPatch.

| Reference | Identity and SHA-256 |
|---|---|
| Xiaomi Clock | com.android.deskclock 18.28.0 (1306002800), cf26966aa67bdb135706e39b90def94d4ab3ec1244f15b0699e8ea5fb3951ab9 |
| OPPO Clock | com.coloros.alarmclock 17.6.70 (17006070), 09b5b1920a60f6c0872e9250e7982a3cc6829a5b734be2dd4e2dfb8edfa9a62a |

Xiaomi's manifest uses `com.miui.system`, MIUI permissions and Xiaomi wearable
integration. The app-linked MainActivityTheme selects Theme.DayNight.Navigator.
The package version alone does not establish a particular HyperOS release.
OPPO's actual AlarmClock.H1 inflates COUIBottomFloatingNavigationBar over its
pager. Archive inventories had no integrity contradictions. REA summary Evidence:
`ev_425f517bf0b6e16edadf6fe11cc413d4833870a982fc050bab7b32a006586756`
and `ev_8306a5d39b0c4f047736f6329d5700721643e8576d796a50e531c8eec95e8510`.
XML, resources and selected DEX/JADX class methods provided the UI evidence;
REA's separate JADX MCP provider was not configured.

| Component | 柔光玻璃 | Liquid Acrylic |
|---|---|---|
| Page light | main_bg / surface_low_light #F3F3F3 | main_pager_background #F0F1F2 |
| Card | alarm_item_radius 20dp, white / #24FFFFFF over black | generic COUI corner 12dp, white / #1AFFFFFF over black |
| Expanded title | WindowTitle.Expand 32sp regular | COUICollapsingToolbarLayoutStyle 32dp semibold |
| Collapsed title | 18sp medium | 24dp semibold |
| Switch | sliding_button_width 49dp, height28dp, thumb20dp | COUISwitchStyle 44x24dp, thumb18dp |
| Bottom bar | height64dp, large-font80dp, actual XML icon26dp, label11sp override | height56dp, item48dp, icon24dp, label10dp medium |
| Three-item width | bounded to 3x96+16dp, portable layout choice | BaseBottomFloatingMenuView.onMeasure: 3x70+2x12+2x8 =250dp |
| Active navigation | app override black / near-white | 90% black/white, original filled LCPatch icon |
| Preferences | min56dp, 16sp title, horizontal16dp / vertical14dp | text-only rows matching alarm_setting_prefs.xml, 16sp medium title, 13sp summary |
| Floating sheet | MIUIX big radius36dp, external12dp | bottom-sheet radius28dp, external16dp |

COUI NavMenu.setSelectedItem -> NavItem.setSelected -> NavIcon.setShowMask only
changes label/icon selection and an icon-local mask. COUIMaskRippleDrawable's
expanded 86x48dp rectangle belongs to press/hover feedback, **not a persistent
selected capsule**. Liquid Acrylic removes the permanent capsule and keeps the
floating bar itself. Original icons have independent filled/outline states.

OEM material services remain unavailable outside their frameworks:
MIUIX MiuiBlurUtils/GlassToken and COUI TYPE_FRAMEWORK_BOTTOM_BAR. Compose uses
real backdrop blur with restrained tint and an opaque unsupported-device fallback;
its blur radius/rim and Xiaomi selector are reconstruction choices, not recovered
runtime shader values. COUI's force-dark rank transforms some resource colors;
black Compose canvas is an explicit inference, not an observed device result.
The native blue accent is retained for controls; text CTA fill is darkened for
white-label readability. System fonts replace OEM fonts. At large text sizes the
COUI bar grows to72dp to keep labels reachable.

Validation adds two shared sheet-surface previews to the existing11 (13 total),
plus Release lint/build and the existing unit/native/ICU checks. Translation code
and native text hooks are unchanged from test.7.

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
