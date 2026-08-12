 /*
  * Copyright (C) 2026 ViictorNunes (https://github.com/ViictorNunes/FlagSync).
  * Licensed under the GNU General Public License v3.0.
  */


package com.flag.sync.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flag.sync.ui.theme.LocalAccentColors
import com.flag.sync.ui.theme.MonoFamily

@Composable
fun AppHeader(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val accents = LocalAccentColors.current
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 44.dp, start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Circle,
                contentDescription = null,
                tint = accents.teal,
                modifier = Modifier.size(6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = eyebrow.uppercase(),
                color = accents.teal,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
        )
        Text(
            text = description,
            color = accents.textMuted,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Justify
        )
    }
}
