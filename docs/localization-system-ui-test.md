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
  deliberately removed files/rows/fields. See `pc-pack-2026111301-audit.md`.
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
