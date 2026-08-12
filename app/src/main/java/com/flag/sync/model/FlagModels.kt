 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.model

import com.flag.sync.R

data class FlatFlag(
    val path: String,
    val value: Any?,
)

data class CompareResult(
    val onlyInA: List<FlatFlag>,
    val onlyInB: List<FlatFlag>,
    val diffs: List<FlagDiff>,
    val totalA: Int,
    val totalB: Int,
)

data class FlagDiff(
    val path: String,
    val valueA: Any?,
    val valueB: Any?,
)

data class ChangelogResult(
    val text: String,
    val unmappedCount: Int,
)

data class MovedFlag(
    val path: String,
    val pushedValue: Any?,
    val originalValue: Any?,
    val hadOriginalValue: Boolean,
)

data class EditedFlag(
    val path: String,
    val currentValue: Any?,
    val originalValue: Any?,
)

enum class PickerTarget {
    BACKUP_A,
    BACKUP_B,
    MAPPING,
    CONVERTED
}

enum class CompareMode {
    COMPARE,
    DOUBLE_CHECK,
    SINGLE_CHECK
}

enum class SearchScope {
    FLAGS,
    SUBFLAGS
}

enum class NavDestination(val route: String, val labelRes: Int) {
    COMPARE("compare", R.string.nav_compare),
    CONVERTER("converter", R.string.nav_converter),
    SETTINGS("settings", R.string.nav_settings)
}
