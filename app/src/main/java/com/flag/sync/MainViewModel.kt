 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flag.sync.R
import com.flag.sync.logic.*
import com.flag.sync.model.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    var flatA by mutableStateOf<Map<String, Any?>?>(null)
        private set
    var countA by mutableIntStateOf(0)
        private set
    var flatB by mutableStateOf<Map<String, Any?>?>(null)
        private set
    var countB by mutableIntStateOf(0)
        private set
    var nameA by mutableStateOf<String?>(null)
        private set
    var nameB by mutableStateOf<String?>(null)
        private set
    var mapper by mutableStateOf<FlagMapper?>(null)
        private set
    var isDefaultMapper by mutableStateOf(false)
        private set
    var mappingUpdateAvailable by mutableStateOf(false)
    var isCheckingUpdate by mutableStateOf(false)
    var isUpdatingMapping by mutableStateOf(false)
    var autoCheckMappingUpdates by mutableStateOf(true)
        private set

    var appUpdateInfo by mutableStateOf<AppUpdateProvider.UpdateInfo?>(null)
    var isCheckingAppUpdate by mutableStateOf(false)
    var autoCheckAppUpdates by mutableStateOf(true)
        private set

    var showUpdateDialog by mutableStateOf(false)
    var showAppUpdateDialog by mutableStateOf(false)

    var mappingUpToDate by mutableStateOf(false)
    var appUpToDate by mutableStateOf(false)

    private var mappingNoticeJob: Job? = null
    private var appNoticeJob: Job? = null

    // Validation errors (in the VM to survive UI theme recreations)
    var errorA by mutableStateOf<String?>(null)
        private set
    var errorB by mutableStateOf<String?>(null)
        private set

    var compareMode by mutableStateOf(CompareMode.COMPARE)
        private set

    var isGroupingA by mutableStateOf(false)
        private set
    var isGroupingB by mutableStateOf(false)
        private set
    var isGroupingDiffs by mutableStateOf(false)
        private set
    var isGroupingSingleA by mutableStateOf(false)
        private set
    var isGroupingSingleB by mutableStateOf(false)
        private set

    var isGroupingMoved by mutableStateOf(false)
        private set
    var isGroupingDeleted by mutableStateOf(false)
        private set
    var isGroupingEdited by mutableStateOf(false)
        private set

    fun toggleGroupingA() { isGroupingA = !isGroupingA }
    fun toggleGroupingB() { isGroupingB = !isGroupingB }
    fun toggleGroupingDiffs() { isGroupingDiffs = !isGroupingDiffs }
    fun toggleGroupingSingleA() { isGroupingSingleA = !isGroupingSingleA }
    fun toggleGroupingSingleB() { isGroupingSingleB = !isGroupingSingleB }
    fun toggleGroupingMoved() { isGroupingMoved = !isGroupingMoved }
    fun toggleGroupingDeleted() { isGroupingDeleted = !isGroupingDeleted }
    fun toggleGroupingEdited() { isGroupingEdited = !isGroupingEdited }

    fun updateCompareMode(mode: CompareMode) {
        compareMode = mode
        if (mode != CompareMode.DOUBLE_CHECK) {
            similarity = 0f
        } else {
            calculateSimilarity()
        }
    }
    var searchScopeA by mutableStateOf(SearchScope.FLAGS)
    var searchScopeB by mutableStateOf(SearchScope.FLAGS)

    // Ghost Search Terms (Centralized to avoid UI recomposition)
    private val ghostTerms = listOf("feed", "reels", "stories", "notes", "direct", "profile")
    var currentGhostTerm by mutableStateOf(ghostTerms[0])
        private set

    fun checkMappingUpdate(context: android.content.Context, onComplete: ((Boolean) -> Unit)? = null) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        mappingUpToDate = false
        viewModelScope.launch {
            val result = FlagMapperProvider.checkForUpdate(context)
            val available = result.getOrNull() ?: mappingUpdateAvailable
            mappingUpdateAvailable = available
            isCheckingUpdate = false
            
            if (!available && onComplete != null) {
                mappingUpToDate = true
                mappingNoticeJob?.cancel()
                mappingNoticeJob = viewModelScope.launch {
                    delay(3000)
                    mappingUpToDate = false
                }
            }
            
            onComplete?.invoke(available)
        }
    }

    suspend fun loadFileFromUri(
        context: android.content.Context,
        uri: android.net.Uri,
        onName: (String) -> Unit,
        onFlat: (Map<String, Any?>) -> Unit,
        onError: (String) -> Unit
    ) {
        val name = com.flag.sync.utils.displayNameFromUri(context, uri)
        onName(name)
        try {
            val raw = com.flag.sync.utils.readTextFromUri(context, uri)
            val result = withContext(Dispatchers.Default) {
                val outcome = BackupConverter.convert(name, raw)
                when (outcome) {
                    is ConvertOutcome.Success -> JsonDiff.flatten(outcome.data)
                    is ConvertOutcome.Error -> {
                        try {
                            val parsed = JsonDiff.parse(raw)
                            if (parsed is List<*>) null else JsonDiff.flatten(parsed)
                        } catch (_: Exception) { null }
                    }
                }
            }
            if (result == null) {
                onError(context.getString(R.string.error_mapping_as_backup))
            } else {
                onFlat(result)
            }
        } catch (e: Exception) {
            onError(context.getString(R.string.error_generic) + ": ${e.message}")
        }
    }

    fun updateAutoCheckUpdates(enabled: Boolean) {
        autoCheckMappingUpdates = enabled
    }

    fun checkAppUpdate(context: android.content.Context, onComplete: ((Boolean) -> Unit)? = null) {
        if (isCheckingAppUpdate) return
        isCheckingAppUpdate = true
        appUpToDate = false
        viewModelScope.launch {
            val result = AppUpdateProvider.checkForUpdate(context)
            val info = result.getOrNull()
            appUpdateInfo = info
            isCheckingAppUpdate = false

            if (info == null && onComplete != null) {
                appUpToDate = true
                appNoticeJob?.cancel()
                appNoticeJob = viewModelScope.launch {
                    delay(3000)
                    appUpToDate = false
                }
            }

            onComplete?.invoke(info != null)
        }
    }

    fun updateAutoCheckAppUpdates(enabled: Boolean) {
        autoCheckAppUpdates = enabled
    }

    init {
        viewModelScope.launch {
            var index = 0
            while (true) {
                delay(3000)
                index = (index + 1) % ghostTerms.size
                currentGhostTerm = ghostTerms[index]
            }
        }
    }

    val groupedOnlyA by derivedStateOf {
        if (isGroupingA && !filteredA.isNullOrEmpty()) {
            groupFlagsLogic(filteredA, mapper) { it.path }
        } else emptyMap()
    }

    val groupedOnlyB by derivedStateOf {
        if (isGroupingB && !filteredB.isNullOrEmpty()) {
            groupFlagsLogic(filteredB, mapper) { it.path }
        } else emptyMap()
    }

    val groupedDiffs by derivedStateOf {
        if (isGroupingDiffs && !filteredDiffs.isNullOrEmpty()) {
            groupFlagsLogic(filteredDiffs, mapper) { it.path }
        } else emptyMap()
    }

    val groupedSingleA by derivedStateOf {
        if (isGroupingSingleA && !filteredSingleA.isNullOrEmpty()) {
            groupFlagsLogic(filteredSingleA, mapper) { it.key }
        } else emptyMap()
    }

    val groupedSingleB by derivedStateOf {
        if (isGroupingSingleB && !filteredSingleB.isNullOrEmpty()) {
            groupFlagsLogic(filteredSingleB, mapper) { it.key }
        } else emptyMap()
    }

    val groupedMovedA by derivedStateOf {
        if (isGroupingMoved && movedFlagsA.isNotEmpty()) {
            groupFlagsLogic(movedFlagsA, mapper) { it.path }
        } else emptyMap()
    }
    val groupedMovedB by derivedStateOf {
        if (isGroupingMoved && movedFlagsB.isNotEmpty()) {
            groupFlagsLogic(movedFlagsB, mapper) { it.path }
        } else emptyMap()
    }
    val groupedDeletedA by derivedStateOf {
        if (isGroupingDeleted && deletedFlagsA.isNotEmpty()) {
            groupFlagsLogic(deletedFlagsA, mapper) { it.path }
        } else emptyMap()
    }
    val groupedDeletedB by derivedStateOf {
        if (isGroupingDeleted && deletedFlagsB.isNotEmpty()) {
            groupFlagsLogic(deletedFlagsB, mapper) { it.path }
        } else emptyMap()
    }
    val groupedEditedA by derivedStateOf {
        val summary = getEditedFlagsSummaryA()
        if (isGroupingEdited && summary.isNotEmpty()) {
            groupFlagsLogic(summary, mapper) { it.path }
        } else emptyMap()
    }
    val groupedEditedB by derivedStateOf {
        val summary = getEditedFlagsSummaryB()
        if (isGroupingEdited && summary.isNotEmpty()) {
            groupFlagsLogic(summary, mapper) { it.path }
        } else emptyMap()
    }

    private fun <T> groupFlagsLogic(list: List<T>, mapper: FlagMapper?, pathSelector: (T) -> String): Map<String, List<T>> {
        val categoryMapping = mapOf(
            "Stories" to listOf("stories", "story"),
            "Feed" to listOf("feed"),
            "Reels" to listOf("reels"),
            "Direct" to listOf("direct", "inbox"),
            "Notes" to listOf("notes"),
            "Instants" to listOf("instants", "quicksnap", "quick snap"),
            "Live" to listOf("live"),
            "Profile" to listOf("profile"),
            "Search" to listOf("search"),
            "Explore" to listOf("explore"),
            "Post" to listOf("post"),
            "Clips" to listOf("clips"),
            "Camera" to listOf("camera"),
            "Notification" to listOf("notification", "nf"),
            "Plus" to listOf("plus", "aura", "consumer subs"),
            "Meta IA" to listOf("metaai", "meta ai", "ai meta", "genai", "ai", "meta"),
            "Threads" to listOf("threads", "p92"),
            "Edits" to listOf("basel", "edits")
        )

        val categoryOrder = listOf(
            "Stories", "Feed", "Reels", "Direct", "Notes", "Instants", "Live",
            "Profile", "Search", "Explore", "Post", "Clips", "Camera",
            "Notification", "Plus", "Meta IA", "Threads", "Edits", "GENERAL"
        )

        return list.groupBy { item ->
            val path = pathSelector(item)
            val displayName = mapper?.getDisplayName(path)?.lowercase() ?: path.lowercase()
            val spaceNormalized = displayName.replace("_", " ")
            val compressedName = displayName.replace("_", "").replace(" ", "")

            var bestCategory: String? = null
            var firstOccurrence = Int.MAX_VALUE

            for ((category, keywords) in categoryMapping) {
                for (keyword in keywords) {
                    val index = if (keyword == "ai") {
                        val regex = Regex("\\bai\\b")
                        val match = regex.find(spaceNormalized)
                        match?.range?.first ?: -1
                    } else {
                        val compressedKeyword = keyword.replace("_", "").replace(" ", "")
                        compressedName.indexOf(compressedKeyword)
                    }

                    if (index != -1 && index < firstOccurrence) {
                        firstOccurrence = index
                        bestCategory = category
                    }
                }
            }

            if (bestCategory != null) {
                bestCategory
            } else {
                val bare = displayName.removeSuffix(":")
                val colonIndex = bare.indexOf(':')
                if (colonIndex >= 0) {
                    bare.substring(0, colonIndex).replaceFirstChar { it.uppercase() }
                } else {
                    val dotIndex = bare.indexOf('.')
                    if (dotIndex >= 0) {
                        bare.substring(0, dotIndex).replaceFirstChar { it.uppercase() }
                    } else {
                        "GENERAL"
                    }
                }
            }
        }.toSortedMap { a, b ->
            val idxA = categoryOrder.indexOf(a).let { if (it == -1) categoryOrder.indexOf("GENERAL") else it }
            val idxB = categoryOrder.indexOf(b).let { if (it == -1) categoryOrder.indexOf("GENERAL") else it }
            if (idxA != idxB) idxA.compareTo(idxB) else a.compareTo(b)
        }
    }

    var movedFlagsA by mutableStateOf<List<MovedFlag>>(emptyList())
        private set
    var movedFlagsB by mutableStateOf<List<MovedFlag>>(emptyList())
        private set
    var deletedFlagsA by mutableStateOf<List<FlatFlag>>(emptyList())
        private set
    var deletedFlagsB by mutableStateOf<List<FlatFlag>>(emptyList())
        private set
    var editedFlagsA by mutableStateOf<Set<String>>(emptySet())
        private set
    var editedFlagsB by mutableStateOf<Set<String>>(emptySet())
        private set

    var originalValuesA by mutableStateOf<Map<String, Any?>>(emptyMap())
        private set
    var originalValuesB by mutableStateOf<Map<String, Any?>>(emptyMap())
        private set

    var similarity by mutableStateOf(0f)
        private set

    fun addMovedFlagA(flag: FlatFlag, originalValue: Any?, hadOriginal: Boolean) {
        if (movedFlagsA.none { it.path == flag.path }) {
            movedFlagsA = movedFlagsA + MovedFlag(flag.path, flag.value, originalValue, hadOriginal)
        }
    }

    fun addMovedFlagB(flag: FlatFlag, originalValue: Any?, hadOriginal: Boolean) {
        if (movedFlagsB.none { it.path == flag.path }) {
            movedFlagsB = movedFlagsB + MovedFlag(flag.path, flag.value, originalValue, hadOriginal)
        }
    }

    fun removeMovedFlagA(path: String) {
        val moved = movedFlagsA.find { it.path == path }
        movedFlagsA = movedFlagsA.filter { it.path != path }

        val newA = (flatA ?: emptyMap()).toMutableMap()
        if (moved?.hadOriginalValue == true) {
            newA[path] = moved.originalValue
        } else {
            newA.remove(path)
        }
        updateFlatA(newA)
    }

    fun removeMovedFlagB(path: String) {
        val moved = movedFlagsB.find { it.path == path }
        movedFlagsB = movedFlagsB.filter { it.path != path }

        val newB = (flatB ?: emptyMap()).toMutableMap()
        if (moved?.hadOriginalValue == true) {
            newB[path] = moved.originalValue
        } else {
            newB.remove(path)
        }
        updateFlatB(newB)
    }

    fun clearMovedFlagsA() {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        movedFlagsA.forEach { moved ->
            if (moved.hadOriginalValue) {
                newA[moved.path] = moved.originalValue
            } else {
                newA.remove(moved.path)
            }
        }
        movedFlagsA = emptyList()
        updateFlatA(newA)
    }

    fun clearMovedFlagsB() {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        movedFlagsB.forEach { moved ->
            if (moved.hadOriginalValue) {
                newB[moved.path] = moved.originalValue
            } else {
                newB.remove(moved.path)
            }
        }
        movedFlagsB = emptyList()
        updateFlatB(newB)
    }

    fun revertMovedCategoryA(flags: List<MovedFlag>) {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        val pathsToRemove = flags.map { it.path }.toSet()
        flags.forEach { moved ->
            if (moved.hadOriginalValue) {
                newA[moved.path] = moved.originalValue
            } else {
                newA.remove(moved.path)
            }
        }
        movedFlagsA = movedFlagsA.filter { it.path !in pathsToRemove }
        updateFlatA(newA)
    }

    fun revertMovedCategoryB(flags: List<MovedFlag>) {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        val pathsToRemove = flags.map { it.path }.toSet()
        flags.forEach { moved ->
            if (moved.hadOriginalValue) {
                newB[moved.path] = moved.originalValue
            } else {
                newB.remove(moved.path)
            }
        }
        movedFlagsB = movedFlagsB.filter { it.path !in pathsToRemove }
        updateFlatB(newB)
    }

    fun addDeletedFlagA(flag: FlatFlag) {
        if (deletedFlagsA.none { it.path == flag.path }) {
            deletedFlagsA = deletedFlagsA + flag
        }
    }

    fun addDeletedFlagB(flag: FlatFlag) {
        if (deletedFlagsB.none { it.path == flag.path }) {
            deletedFlagsB = deletedFlagsB + flag
        }
    }

    fun addFlagA(path: String, value: Any?) {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        newA[path] = value
        deletedFlagsA = deletedFlagsA.filter { it.path != path }
        updateFlatA(newA)
    }

    fun addFlagB(path: String, value: Any?) {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        newB[path] = value
        deletedFlagsB = deletedFlagsB.filter { it.path != path }
        updateFlatB(newB)
    }

    fun deleteAllFlagsA() {
        val current = flatA ?: return
        if (current.isEmpty()) return
        val newDeleted = current.map { FlatFlag(it.key, it.value) }
        val existingPaths = deletedFlagsA.map { it.path }.toSet()
        deletedFlagsA = deletedFlagsA + newDeleted.filter { it.path !in existingPaths }
        updateFlatA(emptyMap())
    }

    fun deleteAllFlagsB() {
        val current = flatB ?: return
        if (current.isEmpty()) return
        val newDeleted = current.map { FlatFlag(it.key, it.value) }
        val existingPaths = deletedFlagsB.map { it.path }.toSet()
        deletedFlagsB = deletedFlagsB + newDeleted.filter { it.path !in existingPaths }
        updateFlatB(emptyMap())
    }

    fun deleteAllFlagsExceptA(predicate: (String, Any?) -> Boolean) {
        val current = flatA ?: return
        if (current.isEmpty()) return
        val toKeep = mutableMapOf<String, Any?>()
        val toDelete = mutableListOf<FlatFlag>()
        val existingPaths = deletedFlagsA.map { it.path }.toSet()

        current.forEach { (path, value) ->
            if (predicate(path, value)) {
                toKeep[path] = value
            } else {
                if (path !in existingPaths) {
                    toDelete.add(FlatFlag(path, value))
                }
            }
        }
        deletedFlagsA = deletedFlagsA + toDelete
        updateFlatA(toKeep)
    }

    fun deleteAllFlagsExceptB(predicate: (String, Any?) -> Boolean) {
        val current = flatB ?: return
        if (current.isEmpty()) return
        val toKeep = mutableMapOf<String, Any?>()
        val toDelete = mutableListOf<FlatFlag>()
        val existingPaths = deletedFlagsB.map { it.path }.toSet()

        current.forEach { (path, value) ->
            if (predicate(path, value)) {
                toKeep[path] = value
            } else {
                if (path !in existingPaths) {
                    toDelete.add(FlatFlag(path, value))
                }
            }
        }
        deletedFlagsB = deletedFlagsB + toDelete
        updateFlatB(toKeep)
    }

    fun removeDeletedFlagA(path: String) {
        val flag = deletedFlagsA.find { it.path == path }
        deletedFlagsA = deletedFlagsA.filter { it.path != path }
        if (flag != null) {
            val newA = (flatA ?: emptyMap()).toMutableMap()
            newA[flag.path] = flag.value
            updateFlatA(newA)
        }
    }

    fun removeDeletedFlagB(path: String) {
        val flag = deletedFlagsB.find { it.path == path }
        deletedFlagsB = deletedFlagsB.filter { it.path != path }
        if (flag != null) {
            val newB = (flatB ?: emptyMap()).toMutableMap()
            newB[flag.path] = flag.value
            updateFlatB(newB)
        }
    }

    fun clearDeletedFlagsA() {
        deletedFlagsA = emptyList()
    }

    fun clearDeletedFlagsB() {
        deletedFlagsB = emptyList()
    }

    fun restoreAllDeletedFlagsA() {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        deletedFlagsA.forEach { newA[it.path] = it.value }
        updateFlatA(newA)
        deletedFlagsA = emptyList()
    }

    fun restoreAllDeletedFlagsB() {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        deletedFlagsB.forEach { newB[it.path] = it.value }
        updateFlatB(newB)
        deletedFlagsB = emptyList()
    }

    fun restoreDeletedCategoryA(flags: List<FlatFlag>) {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        val pathsToRemove = flags.map { it.path }.toSet()
        flags.forEach { newA[it.path] = it.value }
        deletedFlagsA = deletedFlagsA.filter { it.path !in pathsToRemove }
        updateFlatA(newA)
    }

    fun restoreDeletedCategoryB(flags: List<FlatFlag>) {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        val pathsToRemove = flags.map { it.path }.toSet()
        flags.forEach { newB[it.path] = it.value }
        deletedFlagsB = deletedFlagsB.filter { it.path !in pathsToRemove }
        updateFlatB(newB)
    }

    fun updateFlagValueA(path: String, newValue: Any?) {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        if (!originalValuesA.containsKey(path)) {
            originalValuesA = originalValuesA + (path to newA[path])
        }

        val originalValue = originalValuesA[path]
        val isBackToOriginal = JsonDiff.toCompactJsonString(newValue) == JsonDiff.toCompactJsonString(originalValue)

        newA[path] = newValue
        if (isBackToOriginal) {
            editedFlagsA = editedFlagsA - path
            originalValuesA = originalValuesA - path
        } else {
            editedFlagsA = editedFlagsA + path
        }
        updateFlatA(newA)
    }

    fun updateFlagValueB(path: String, newValue: Any?) {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        if (!originalValuesB.containsKey(path)) {
            originalValuesB = originalValuesB + (path to newB[path])
        }

        val originalValue = originalValuesB[path]
        val isBackToOriginal = JsonDiff.toCompactJsonString(newValue) == JsonDiff.toCompactJsonString(originalValue)

        newB[path] = newValue
        if (isBackToOriginal) {
            editedFlagsB = editedFlagsB - path
            originalValuesB = originalValuesB - path
        } else {
            editedFlagsB = editedFlagsB + path
        }
        updateFlatB(newB)
    }

    fun getEditedFlagsSummaryA(): List<com.flag.sync.model.EditedFlag> {
        return editedFlagsA.map { path ->
            com.flag.sync.model.EditedFlag(path, flatA?.get(path), originalValuesA[path])
        }
    }

    fun getEditedFlagsSummaryB(): List<EditedFlag> {
        return editedFlagsB.map { path ->
            EditedFlag(path, flatB?.get(path), originalValuesB[path])
        }
    }

    fun revertFlagEditA(path: String) {
        val original = originalValuesA[path] ?: return
        val newA = (flatA ?: emptyMap()).toMutableMap()
        newA[path] = original
        editedFlagsA = editedFlagsA - path
        originalValuesA = originalValuesA - path
        updateFlatA(newA)
    }

    fun revertFlagEditB(path: String) {
        val original = originalValuesB[path] ?: return
        val newB = (flatB ?: emptyMap()).toMutableMap()
        newB[path] = original
        editedFlagsB = editedFlagsB - path
        originalValuesB = originalValuesB - path
        updateFlatB(newB)
    }

    fun restoreAllEditsA() {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        originalValuesA.forEach { (path, value) ->
            newA[path] = value
        }
        editedFlagsA = emptySet()
        originalValuesA = emptyMap()
        updateFlatA(newA)
    }

    fun restoreAllEditsB() {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        originalValuesB.forEach { (path, value) ->
            newB[path] = value
        }
        editedFlagsB = emptySet()
        originalValuesB = emptyMap()
        updateFlatB(newB)
    }

    fun revertEditedCategoryA(flags: List<EditedFlag>) {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        flags.forEach { edited ->
            newA[edited.path] = edited.originalValue
            editedFlagsA = editedFlagsA - edited.path
            originalValuesA = originalValuesA - edited.path
        }
        updateFlatA(newA)
    }

    fun revertEditedCategoryB(flags: List<EditedFlag>) {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        flags.forEach { edited ->
            newB[edited.path] = edited.originalValue
            editedFlagsB = editedFlagsB - edited.path
            originalValuesB = originalValuesB - edited.path
        }
        updateFlatB(newB)
    }

    fun copyFlagsToB(flags: List<FlatFlag>) {
        val newB = (flatB ?: emptyMap()).toMutableMap()
        flags.forEach { flag ->
            val original = newB[flag.path]
            val hadOriginal = newB.containsKey(flag.path)
            newB[flag.path] = flag.value
            addMovedFlagB(flag, original, hadOriginal)
        }
        updateFlatB(newB)
    }

    fun copyFlagsToA(flags: List<FlatFlag>) {
        val newA = (flatA ?: emptyMap()).toMutableMap()
        flags.forEach { flag ->
            val original = newA[flag.path]
            val hadOriginal = newA.containsKey(flag.path)
            newA[flag.path] = flag.value
            addMovedFlagA(flag, original, hadOriginal)
        }
        updateFlatA(newA)
    }

    var onlyAExpanded by mutableStateOf(false)
    var onlyBExpanded by mutableStateOf(false)
    var diffsExpanded by mutableStateOf(false)

    // Search and Filters
    var searchA by mutableStateOf("")
        private set
    var searchB by mutableStateOf("")
        private set
    var searchDiffs by mutableStateOf("")
        private set
    var searchSingleA by mutableStateOf("")
        private set
    var searchSingleB by mutableStateOf("")
        private set

    var filteredA by mutableStateOf<List<FlatFlag>>(emptyList())
        private set
    var filteredB by mutableStateOf<List<FlatFlag>>(emptyList())
        private set
    var filteredDiffs by mutableStateOf<List<FlagDiff>>(emptyList())
        private set
    var filteredSingleA by mutableStateOf<List<Map.Entry<String, Any?>>>(emptyList())
        private set
    var filteredSingleB by mutableStateOf<List<Map.Entry<String, Any?>>>(emptyList())
        private set

    var compareResult by mutableStateOf<CompareResult?>(null)
        private set

    private var filterJobA: Job? = null
    private var filterJobB: Job? = null
    private var filterJobDiffs: Job? = null
    private var filterJobSingleA: Job? = null
    private var filterJobSingleB: Job? = null

    fun updateSearchScopeA(scope: SearchScope) {
        searchScopeA = scope
        updateSearchA(searchA)
        updateSearchDiffs(searchDiffs)
        updateSearchSingleA(searchSingleA)
    }

    fun updateSearchScopeB(scope: SearchScope) {
        searchScopeB = scope
        updateSearchB(searchB)
        updateSearchSingleB(searchSingleB)
    }

    fun updateSearchA(query: String) {
        searchA = query
        filterJobA?.cancel()
        val currentResult = compareResult
        if (currentResult == null) {
            filteredA = emptyList()
            return
        }

        if (query.isEmpty()) {
            filteredA = currentResult.onlyInA
            return
        }

        filterJobA = viewModelScope.launch {
            delay(300)
            val compressedQuery = query.replace("_", "").replace(" ", "").lowercase()
            filteredA = withContext(Dispatchers.Default) {
                currentResult.onlyInA.filter { flag ->
                    val compressedPath = flag.path.replace("_", "").replace(" ", "").lowercase()
                    val compressedDisplay = mapper?.getDisplayName(flag.path)?.replace("_", "")?.replace(" ", "")?.lowercase() ?: ""

                    if (searchScopeA == SearchScope.FLAGS) {
                        compressedPath.contains(compressedQuery) ||
                                compressedDisplay.contains(compressedQuery)
                    } else {
                        val subs = JsonDiff.getFormattedSubFlags(flag.path, flag.value, mapper)
                        subs.any { (lbl, v) ->
                            lbl.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery) ||
                                    v.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery)
                        }
                    }
                }
            }
        }
    }

    fun updateSearchB(query: String) {
        searchB = query
        filterJobB?.cancel()
        val currentResult = compareResult
        if (currentResult == null) {
            filteredB = emptyList()
            return
        }

        if (query.isEmpty()) {
            filteredB = currentResult.onlyInB
            return
        }

        filterJobB = viewModelScope.launch {
            delay(300)
            val compressedQuery = query.replace("_", "").replace(" ", "").lowercase()
            filteredB = withContext(Dispatchers.Default) {
                currentResult.onlyInB.filter { flag ->
                    val compressedPath = flag.path.replace("_", "").replace(" ", "").lowercase()
                    val compressedDisplay = mapper?.getDisplayName(flag.path)?.replace("_", "")?.replace(" ", "")?.lowercase() ?: ""

                    if (searchScopeB == SearchScope.FLAGS) {
                        compressedPath.contains(compressedQuery) ||
                                compressedDisplay.contains(compressedQuery)
                    } else {
                        val subs = JsonDiff.getFormattedSubFlags(flag.path, flag.value, mapper)
                        subs.any { (lbl, v) ->
                            lbl.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery) ||
                                    v.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery)
                        }
                    }
                }
            }
        }
    }

    fun updateSearchSingleA(query: String) {
        searchSingleA = query
        filterJobSingleA?.cancel()
        val currentData = flatA
        if (currentData == null) {
            filteredSingleA = emptyList()
            return
        }

        if (query.isEmpty()) {
            filteredSingleA = currentData.entries.sortedBy { it.key }.toList()
            return
        }

        filterJobSingleA = viewModelScope.launch {
            delay(300)
            val compressedQuery = query.replace("_", "").replace(" ", "").lowercase()
            filteredSingleA = withContext(Dispatchers.Default) {
                currentData.entries.filter { entry ->
                    val compressedPath = entry.key.replace("_", "").replace(" ", "").lowercase()
                    val compressedDisplay = mapper?.getDisplayName(entry.key)?.replace("_", "")?.replace(" ", "")?.lowercase() ?: ""

                    if (searchScopeA == SearchScope.FLAGS) {
                        compressedPath.contains(compressedQuery) ||
                                compressedDisplay.contains(compressedQuery)
                    } else {
                        val subs = JsonDiff.getFormattedSubFlags(entry.key, entry.value, mapper)
                        subs.any { (lbl, v) ->
                            lbl.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery) ||
                                    v.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery)
                        }
                    }
                }.sortedBy { it.key }.toList()
            }
        }
    }

    fun updateSearchSingleB(query: String) {
        searchSingleB = query
        filterJobSingleB?.cancel()
        val currentData = flatB
        if (currentData == null) {
            filteredSingleB = emptyList()
            return
        }

        if (query.isEmpty()) {
            filteredSingleB = currentData.entries.sortedBy { it.key }.toList()
            return
        }

        filterJobSingleB = viewModelScope.launch {
            delay(300)
            val compressedQuery = query.replace("_", "").replace(" ", "").lowercase()
            filteredSingleB = withContext(Dispatchers.Default) {
                currentData.entries.filter { entry ->
                    val compressedPath = entry.key.replace("_", "").replace(" ", "").lowercase()
                    val compressedDisplay = mapper?.getDisplayName(entry.key)?.replace("_", "")?.replace(" ", "")?.lowercase() ?: ""

                    if (searchScopeB == SearchScope.FLAGS) {
                        compressedPath.contains(compressedQuery) ||
                                compressedDisplay.contains(compressedQuery)
                    } else {
                        val subs = JsonDiff.getFormattedSubFlags(entry.key, entry.value, mapper)
                        subs.any { (lbl, v) ->
                            lbl.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery) ||
                                    v.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery)
                        }
                    }
                }.sortedBy { it.key }.toList()
            }
        }
    }

    fun updateSearchDiffs(query: String) {
        searchDiffs = query
        filterJobDiffs?.cancel()
        val currentResult = compareResult
        if (currentResult == null) {
            filteredDiffs = emptyList()
            return
        }

        if (query.isEmpty()) {
            filteredDiffs = currentResult.diffs
            return
        }

        filterJobDiffs = viewModelScope.launch {
            delay(300)
            val compressedQuery = query.replace("_", "").replace(" ", "").lowercase()
            filteredDiffs = withContext(Dispatchers.Default) {
                currentResult.diffs.filter { diff ->
                    val compressedPath = diff.path.replace("_", "").replace(" ", "").lowercase()
                    val compressedDisplay = mapper?.getDisplayName(diff.path)?.replace("_", "")?.replace(" ", "")?.lowercase() ?: ""

                    if (searchScopeA == SearchScope.FLAGS) {
                        compressedPath.contains(compressedQuery) ||
                                compressedDisplay.contains(compressedQuery)
                    } else {
                        val subsA = JsonDiff.getFormattedSubFlags(diff.path, diff.valueA, mapper)
                        val subsB = JsonDiff.getFormattedSubFlags(diff.path, diff.valueB, mapper)
                        subsA.any { (lbl, v) ->
                            lbl.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery) ||
                                    v.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery)
                        } ||
                                subsB.any { (lbl, v) ->
                                    lbl.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery) ||
                                            v.replace("_", "").replace(" ", "").lowercase().contains(compressedQuery)
                                }
                    }
                }
            }
        }
    }

    fun updateCompareResult(result: CompareResult?) {
        compareResult = result
        updateSearchA(searchA)
        updateSearchB(searchB)
        updateSearchDiffs(searchDiffs)
    }

    fun updateFlatA(data: Map<String, Any?>?, topLevelCount: Int = -1) {
        flatA = data
        countA = if (topLevelCount >= 0) topLevelCount else calculateTopLevelCount(data)
        if (data == null) {
            movedFlagsA = emptyList()
            deletedFlagsA = emptyList()
            editedFlagsA = emptySet()
            originalValuesA = emptyMap()
            similarity = 0f
            countA = 0
        } else {
            calculateSimilarity()
        }
        updateSearchSingleA(searchSingleA)
    }

    fun updateFlatB(data: Map<String, Any?>?, topLevelCount: Int = -1) {
        flatB = data
        countB = if (topLevelCount >= 0) topLevelCount else calculateTopLevelCount(data)
        if (data == null) {
            movedFlagsB = emptyList()
            deletedFlagsB = emptyList()
            editedFlagsB = emptySet()
            originalValuesB = emptyMap()
            similarity = 0f
            countB = 0
        } else {
            calculateSimilarity()
        }
        updateSearchSingleB(searchSingleB)
    }

    private fun calculateTopLevelCount(data: Map<String, Any?>?): Int {
        if (data == null) return 0
        return data.keys.map { it.substringBefore('.') }.distinct().size
    }

    private fun calculateSimilarity() {
        val a = flatA ?: return
        val b = flatB ?: return

        if (a.isEmpty() || b.isEmpty()) {
            similarity = 0f
            return
        }

        val keysA = a.keys
        val keysB = b.keys
        val intersectionSize = keysA.intersect(keysB).size
        val maxSize = maxOf(keysA.size, keysB.size)

        similarity = intersectionSize.toFloat() / maxSize.toFloat()
    }

    fun generateChangelogText(limit: Int = -1): ChangelogResult {
        val a = flatA ?: emptyMap()
        val b = flatB ?: emptyMap()
        val indexA = a.mapKeys { JsonDiff.normalizedFlagId(it.key) }
        val indexB = b.mapKeys { JsonDiff.normalizedFlagId(it.key) }
        val allIds = (indexA.keys + indexB.keys).filter { !it.startsWith("_") }.sorted()
        if (allIds.isEmpty()) return ChangelogResult("", 0)

        var unmappedCount = 0
        var totalChanges = 0
        val lines = mutableListOf<String>()

        for (id in allIds) {
            val valA = indexA[id]
            val valB = indexB[id]
            val strA = JsonDiff.toCompactJsonString(valA)
            val strB = JsonDiff.toCompactJsonString(valB)
            if (strA == strB) continue
            totalChanges++
            val path = b.keys.find { JsonDiff.normalizedFlagId(it) == id }
                ?: a.keys.find { JsonDiff.normalizedFlagId(it) == id }
                ?: id

            if (valA == null) {
                val mapped = mapper?.getDisplayName(path)
                if (mapped == null || mapped == path) unmappedCount++
            }

            if (limit == -1 || lines.size < limit) {
                fun isTrue(v: Any?): Boolean {
                    val s = v?.toString()?.lowercase()?.trim() ?: ""
                    return s == "true" || s == "1"
                }
                fun isFalse(v: Any?): Boolean {
                    val s = v?.toString()?.lowercase()?.trim() ?: ""
                    return s == "false" || s == "0"
                }

                val status = when {
                    (valA == null || isFalse(valA)) && isTrue(valB) -> "Enabled"
                    isTrue(valA) && isFalse(valB) -> "Disabled"
                    valB == null -> "Removed"
                    else -> "Modified"
                }
                val cleanName = cleanFlagName(mapper?.getDisplayName(path) ?: path)
                lines.add("■ $cleanName $status")
            }
        }

        val resultText = StringBuilder(lines.joinToString("\n"))
        if (limit != -1 && totalChanges > limit) {
            val remaining = totalChanges - limit
            resultText.append("\n\nand $remaining more flags changed")
        }
        return ChangelogResult(resultText.toString(), unmappedCount)
    }

    private fun cleanFlagName(name: String): String {
        val noPrefix = if (name.contains(":")) name.substringAfter(":") else name
        val noCodes = noPrefix.replace(Regex("\\b\\d{5,}\\b"), "")
            .replace(Regex("\\b[0-9a-fA-F]{8,}\\b"), "")
        val cleaned = noCodes.replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifEmpty { name.substringAfterLast('.').replace("_", " ") }
    }

    fun updateNameA(name: String?) { nameA = name }
    fun updateNameB(name: String?) { nameB = name }
    fun updateErrorA(message: String?) { errorA = message }
    fun updateErrorB(message: String?) { errorB = message }

    fun updateMapper(newMapper: FlagMapper?, isDefault: Boolean = false) {
        mapper = newMapper
        isDefaultMapper = isDefault
        filteredA = emptyList()
        filteredB = emptyList()
        filteredSingleA = emptyList()
        filteredSingleB = emptyList()
        updateSearchA(searchA)
        updateSearchB(searchB)
        updateSearchSingleA(searchSingleA)
        updateSearchSingleB(searchSingleB)
    }

    var cleanDataConv by mutableStateOf<Map<String, Any?>?>(null)
    var fileNameConv by mutableStateOf<String?>(null)
    var statusConv by mutableStateOf<String?>(null)
    var flagCountConv by mutableIntStateOf(0)
}