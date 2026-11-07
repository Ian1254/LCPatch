package com.lcpatch

/**
 * Style selection controls presentation only, never translation state or tasks.
 * The persisted preference is stable across app upgrades.
 */
internal enum class UiStyle(val label: String, val key: String) {
    HYPER_OS_4("HyperOS 4", "hyperos4"),
    COLOR_OS_17("ColorOS 17（原型）", "coloros17");

    companion object {
        fun fromKey(value: String?): UiStyle =
            entries.firstOrNull { it.key == value } ?: HYPER_OS_4
    }
}

internal data class GameOverviewUiState(
    val installed: Boolean,
    val version: String,
)

internal data class LanguageOverviewUiState(
    val label: String,
    val prefix: String,
)

internal data class RootlessAccessUiState(
    val status: String,
    val actionLabel: String,
)

internal data class RuntimeOverviewUiState(
    val jsonFiles: Int,
    val matchingFiles: Int,
    val fontBytes: Long,
) {
    val ready: Boolean get() = jsonFiles > 0 && matchingFiles > 0
    val fontReady: Boolean get() = fontBytes >= 12L

    companion object {
        fun from(value: RuntimeInspection): RuntimeOverviewUiState =
            RuntimeOverviewUiState(value.jsonFiles, value.matchingFiles, value.fontBytes)
    }
}

/** Snapshot of observable data; no composables, callbacks or overlay geometry. */
internal data class OverviewUiState(
    val rootMode: Boolean,
    val busy: Boolean,
    val rootlessAccess: RootlessAccessUiState,
    val game: GameOverviewUiState,
    val activeName: String,
    val activeScript: String,
    val scopeStatus: String,
    val rootStatus: String,
    val translationEnabled: Boolean,
    val targetLanguage: LanguageOverviewUiState,
    val runtime: RuntimeOverviewUiState,
)

/** All effects continue to be owned by MainActivity and its task view-model. */
internal data class OverviewActions(
    val onLaunchGame: () -> Unit,
    val onDownload: () -> Unit,
    val onDownloaded: () -> Unit,
    val onGameFolder: () -> Unit,
    val onCheckScope: () -> Unit,
    val onRequestRoot: () -> Unit,
    val onTranslationEnabled: (Boolean) -> Unit,
)
