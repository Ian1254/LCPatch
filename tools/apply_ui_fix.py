from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {text.count(old)}")
    return text.replace(old, new, 1)


main_path = Path("app/src/main/java/com/filepermwebui/MainActivity.kt")
main = main_path.read_text()

for line in (
    "import top.yukonga.miuix.kmp.theme.darkColorScheme\n",
    "import top.yukonga.miuix.kmp.theme.lightColorScheme\n",
    "import dev.chrisbanes.haze.rememberHazeState\n",
    "import dev.chrisbanes.haze.hazeSource\n",
):
    main = replace_once(main, line, "", f"remove import {line.strip()}")

controller_pattern = re.compile(
    r"            val controller = remember\(themeMode\) \{\n"
    r"                ThemeController\(\n"
    r"                    colorSchemeMode = when \(themeMode\) \{\n"
    r".*?"
    r"                \)\n"
    r"            \}\n"
    r"            MiuixTheme\(controller = controller\) \{",
    re.S,
)
controller_replacement = (
    "            val controller = remember(themeMode) {\n"
    "                ThemeController(\n"
    "                    colorSchemeMode = when (themeMode) {\n"
    "                        \"light\" -> ColorSchemeMode.Light\n"
    "                        \"dark\" -> ColorSchemeMode.Dark\n"
    "                        else -> ColorSchemeMode.System\n"
    "                    }\n"
    "                )\n"
    "            }\n"
    "            MiuixTheme(controller = controller) {"
)
main, count = controller_pattern.subn(controller_replacement, main, count=1)
if count != 1:
    raise SystemExit(f"theme controller: expected one match, found {count}")

main = replace_once(main, "        val topHazeState = rememberHazeState()\n", "", "remove top haze state")
main = replace_once(
    main,
    "                            TopLevelBlurBar(topHazeState)\n",
    "                            TopLevelGlassBar(activeBarBackdrop, visibleTitle)\n",
    "wire black glass top bar",
)
main = replace_once(main, "                            .hazeSource(topHazeState)\n", "", "remove progressive haze source")
main_path.write_text(main)

env_path = Path("app/src/main/java/com/filepermwebui/EnvironmentStatusCard.kt")
env = env_path.read_text()
anchor = "import androidx.compose.foundation.interaction.collectIsPressedAsState\n"
if "import androidx.compose.foundation.isSystemInDarkTheme\n" not in env:
    env = replace_once(
        env,
        anchor,
        anchor + "import androidx.compose.foundation.isSystemInDarkTheme\n",
        "add dark-theme import",
    )

old_colors = (
    '    val healthy = scopeStatus == "已啟用"\n'
    '    val hasError = scopeStatus == "尚未授權遊戲"\n'
    '    val cardColor = when {\n'
    '        hasError -> MiuixTheme.colorScheme.error.copy(alpha = 0.16f)\n'
    '        healthy -> MiuixTheme.colorScheme.secondaryContainer\n'
    '        else -> MiuixTheme.colorScheme.surfaceContainer\n'
    '    }\n'
    '    val accent = when {\n'
    '        hasError -> MiuixTheme.colorScheme.error\n'
    '        healthy -> MiuixTheme.colorScheme.primary\n'
    '        else -> MiuixTheme.colorScheme.primary.copy(alpha = 0.62f)\n'
    '    }\n'
)
new_colors = (
    '    val healthy = scopeStatus == "已啟用"\n'
    '    val hasError = scopeStatus == "尚未授權遊戲"\n'
    '    val dark = isSystemInDarkTheme()\n'
    '    val cardColor = when {\n'
    '        healthy -> if (dark) Color(0xFF173D27) else Color(0xFFDFFAE4)\n'
    '        hasError -> if (dark) Color(0xFF472224) else Color(0xFFFFDAD9)\n'
    '        else -> MiuixTheme.colorScheme.secondaryContainer\n'
    '    }\n'
    '    val accent = when {\n'
    '        healthy -> Color(0xFF43D477)\n'
    '        hasError -> Color(0xFFFF6B70)\n'
    '        else -> MiuixTheme.colorScheme.primary.copy(alpha = 0.62f)\n'
    '    }\n'
)
env = replace_once(env, old_colors, new_colors, "restore environment semantic colors")
env_path.write_text(env)
