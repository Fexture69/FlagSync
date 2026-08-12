 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.R
import com.flag.sync.logic.FlagMapper
import com.flag.sync.logic.JsonDiff
import com.flag.sync.model.SearchScope
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@Composable
fun ResultCard(
    path: String,
    rawValue: Any?,
    modifier: Modifier = Modifier,
    pushAccent: androidx.compose.ui.graphics.Color? = null,
    mapper: FlagMapper? = null,
    highlightQuery: String? = null,
    highlightScope: SearchScope = SearchScope.FLAGS,
    backgroundColor: Color? = null,
    onPush: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    deleteIcon: ImageVector = Icons.Rounded.Delete,
    onEdit: (() -> Unit)? = null,
    isEdited: Boolean = false
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    val pAccent = pushAccent ?: accents.teal
    val bgColor = backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant

    val clipboardManager = LocalClipboardManager.current
    val displayName = remember(path, mapper) { mapper?.getDisplayName(path) ?: path }
    val subFlags = remember(path, rawValue, mapper) { JsonDiff.getFormattedSubFlags(path, rawValue, mapper) }
    var expanded by remember { mutableStateOf(false) }
    val shouldShowExpand = subFlags.size > 8

    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .shadow(
                elevation = 0.8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(pAccent.copy(alpha = 0.1f))
                .border(1.dp, pAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable { clipboardManager.setText(AnnotatedString(displayName.replace("_", " "))) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.result_copy_name),
                tint = pAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val titleHighlight = if (highlightScope == SearchScope.FLAGS) highlightQuery else null
                Text(
                    text = getAnnotatedHighlight(displayName, titleHighlight, pAccent),
                    fontFamily = MonoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isEdited) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(accents.violet.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.result_edited_label),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accents.violet,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            
            if (subFlags.isEmpty()) {
                val valueStr = rawValue.toString()
                val valueHighlight = if (highlightScope == SearchScope.SUBFLAGS) highlightQuery else null
                Text(
                    text = getAnnotatedHighlight(valueStr, valueHighlight, pAccent),
                    fontFamily = MonoFamily,
                    fontSize = 11.5.sp,
                    color = when {
                        valueStr.trim().lowercase() == "true" -> accents.success
                        valueStr.trim().lowercase() == "false" -> accents.danger
                        else -> accents.textMuted
                    },
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    val staticItems = subFlags.take(8)
                    val hiddenItems = if (subFlags.size > 8) subFlags.drop(8) else emptyList()
                    val subHighlight = if (highlightScope == SearchScope.SUBFLAGS) highlightQuery else null
                    
                    staticItems.forEach { (lbl, v) ->
                        TextValueLine(lbl, v, accents, subHighlight, pAccent)
                    }
                    
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            hiddenItems.forEach { (lbl, v) ->
                                TextValueLine(lbl, v, accents, subHighlight, pAccent)
                            }
                        }
                    }
                    
                    if (shouldShowExpand) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accents.violet.copy(alpha = 0.2f))
                                .border(1.5.dp, accents.violet.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (expanded) stringResource(R.string.result_collapse) else stringResource(R.string.result_expand_values, subFlags.size),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accents.violet
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = accents.violet,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (onPush != null || onDelete != null || onEdit != null) {
            Row(
                modifier = Modifier.padding(start = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onEdit != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accents.violet.copy(alpha = 0.12f))
                            .border(1.dp, accents.violet.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEdit()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.edit_action_description),
                            tint = accents.violet,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (onDelete != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accents.danger.copy(alpha = 0.12f))
                            .border(1.dp, accents.danger.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDelete()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = deleteIcon,
                            contentDescription = stringResource(R.string.delete_action_description),
                            tint = accents.danger,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (onPush != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(pAccent.copy(alpha = 0.15f))
                            .border(1.dp, pAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPush()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = stringResource(R.string.result_push_other),
                            tint = pAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextValueLine(lbl: String, v: String, accents: com.flag.sync.ui.theme.AccentColors, highlight: String?, accent: Color) {
    val fullText = if (lbl.isEmpty()) v else "$lbl: $v"
    Text(
        text = getAnnotatedHighlight(fullText, highlight, accent),
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        color = when {
            v.trim().lowercase() == "true" -> accents.success
            v.trim().lowercase() == "false" -> accents.danger
            else -> accents.textMuted
        }
    )
}

@Composable
fun DiffResultCard(
    path: String,
    valueA: Any?,
    valueB: Any?,
    modifier: Modifier = Modifier,
    highlightQuery: String? = null,
    highlightScope: SearchScope = SearchScope.FLAGS,
    mapper: FlagMapper? = null,
    backgroundColor: Color? = null,
    onPushA: (() -> Unit)? = null,
    onPushB: (() -> Unit)? = null,
    onEditA: (() -> Unit)? = null,
    onEditB: (() -> Unit)? = null
) {
    val accents = LocalAccentColors.current
    val bgColor = backgroundColor ?: MaterialTheme.colorScheme.surfaceVariant
    val displayName = remember(path, mapper) { mapper?.getDisplayName(path) ?: path }
    val subFlagsA = remember(path, valueA, mapper) { JsonDiff.getFormattedSubFlags(path, valueA, mapper) }
    val subFlagsB = remember(path, valueB, mapper) { JsonDiff.getFormattedSubFlags(path, valueB, mapper) }
    var expanded by remember { mutableStateOf(false) }
    val shouldShowExpand = subFlagsA.size > 8 || subFlagsB.size > 8

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        val titleHighlight = if (highlightScope == SearchScope.FLAGS) highlightQuery else null
        val highlightedName = getAnnotatedHighlight(displayName, titleHighlight, accents.violet)
        Text(
            text = highlightedName,
            fontFamily = MonoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            ValueColumn(
                label = "A", 
                rawValue = valueA, 
                subFlags = subFlagsA, 
                isExpanded = expanded, 
                color = accents.teal, 
                bgColor = accents.tealDim,
                displayName = displayName,
                highlight = highlightQuery,
                highlightScope = highlightScope,
                onPush = onPushA,
                onEdit = onEditA
            )
            ValueColumn(
                label = "B", 
                rawValue = valueB, 
                subFlags = subFlagsB, 
                isExpanded = expanded, 
                color = accents.amber, 
                bgColor = accents.amberDim,
                displayName = displayName,
                highlight = highlightQuery,
                highlightScope = highlightScope,
                onPush = onPushB,
                onEdit = onEditB
            )
        }
        
        if (shouldShowExpand) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accents.violet.copy(alpha = 0.2f))
                    .border(1.5.dp, accents.violet.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) stringResource(R.string.result_collapse) else stringResource(R.string.result_see_all),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accents.violet
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = accents.violet,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ValueColumn(
    label: String,
    rawValue: Any?,
    subFlags: List<Pair<String, String>>,
    isExpanded: Boolean,
    color: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    displayName: String,
    highlight: String?,
    highlightScope: SearchScope = SearchScope.FLAGS,
    onPush: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val accents = LocalAccentColors.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable { 
                if (onEdit != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                } else {
                    clipboardManager.setText(AnnotatedString(displayName.replace("_", " ")))
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .weight(1f)
    ) {
        Text(text = "$label:", fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        if (subFlags.isEmpty()) {
            val v = rawValue.toString()
            val isBoolean = v.trim().lowercase() == "true" || v.trim().lowercase() == "false"
            val contentColor = if (isBoolean) {
                if (v.trim().lowercase() == "true") accents.success else accents.danger
            } else color
            val valueHighlight = if (highlightScope == SearchScope.SUBFLAGS) highlight else null
            Text(
                text = getAnnotatedHighlight(v, valueHighlight, color),
                fontFamily = MonoFamily, 
                fontSize = 11.5.sp, 
                color = contentColor
            )
        } else {
            val staticItems = subFlags.take(8)
            val hiddenItems = if (subFlags.size > 8) subFlags.drop(8) else emptyList()
            val subHighlight = if (highlightScope == SearchScope.SUBFLAGS) highlight else null
            
            staticItems.forEach { (lbl, v) ->
                SubFlagText(lbl, v, color, subHighlight, color)
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    hiddenItems.forEach { (lbl, v) ->
                        SubFlagText(lbl, v, color, subHighlight, color)
                    }
                }
            }
        }

        if (onPush != null || onEdit != null) {
            Row(
                modifier = Modifier.padding(top = 8.dp).align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onEdit != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accents.violet.copy(alpha = 0.15f))
                            .border(1.dp, accents.violet.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEdit()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.edit_action_description),
                            tint = accents.violet,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (onPush != null) {
                    Box(
                        modifier = Modifier
                            .size(height = 32.dp, width = 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color.copy(alpha = 0.2f))
                            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPush()
                            }
                            .padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = stringResource(R.string.result_push_other),
                            tint = color,
                            modifier = Modifier.size(16.dp).align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubFlagText(lbl: String, v: String, color: androidx.compose.ui.graphics.Color, highlight: String?, accent: Color) {
    val accents = LocalAccentColors.current
    val isBoolean = v.trim().lowercase() == "true" || v.trim().lowercase() == "false"
    val contentColor = if (isBoolean) {
        if (v.trim().lowercase() == "true") accents.success else accents.danger
    } else color
    val fullText = if (lbl.isEmpty()) v else "$lbl: $v"
    Text(
        text = getAnnotatedHighlight(fullText, highlight, accent),
        fontFamily = MonoFamily,
        fontSize = 10.sp,
        color = contentColor
    )
}

private fun getAnnotatedHighlight(text: String, query: String?, accent: Color): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)

    val normalizedQuery = query.replace("_", "").replace(" ", "").lowercase()
    if (normalizedQuery.isEmpty()) return AnnotatedString(text)

    // Build a map of normalized position -> original index
    val indexMapping = mutableListOf<Int>()
    val normalizedText = StringBuilder()
    for (i in text.indices) {
        val c = text[i]
        if (c != '_' && c != ' ') {
            normalizedText.append(c.lowercaseChar())
            indexMapping.add(i)
        }
    }

    val normalizedString = normalizedText.toString()
    val highlightRanges = mutableListOf<IntRange>()
    var searchIdx = normalizedString.indexOf(normalizedQuery)
    
    while (searchIdx != -1) {
        val startOrig = indexMapping[searchIdx]
        val endOrig = indexMapping[searchIdx + normalizedQuery.length - 1]
        highlightRanges.add(startOrig..endOrig)
        searchIdx = normalizedString.indexOf(normalizedQuery, searchIdx + normalizedQuery.length)
    }

    if (highlightRanges.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var lastIndex = 0
        highlightRanges.forEach { range ->
            append(text.substring(lastIndex, range.first))
            withStyle(
                style = SpanStyle(
                    background = accent.copy(alpha = 0.25f),
                    color = accent,
                    fontWeight = FontWeight.Black
                )
            ) {
                append(text.substring(range.first, range.last + 1))
            }
            lastIndex = range.last + 1
        }
        append(text.substring(lastIndex))
    }
}
