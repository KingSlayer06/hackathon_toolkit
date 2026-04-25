package com.kingslayer06.vox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TranscriptCard(
    text: String,
    interim: String,
    listening: Boolean,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var edit by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val display = (text + (if (interim.isNotEmpty()) " $interim" else "")).trim()
    val canSubmit = display.isNotEmpty() && !busy

    Panel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(
                    text = when {
                        listening -> "● recording"
                        edit -> "type instead"
                        else -> "transcript"
                    },
                    color = if (listening) VoxColors.Bad else VoxColors.Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        edit = !edit
                        draft = display
                    }) {
                        Text(
                            if (edit) "use voice" else "type instead",
                            color = VoxColors.Muted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (display.isNotEmpty() || edit) {
                        TextButton(onClick = {
                            onClear()
                            draft = ""
                            edit = false
                        }) {
                            Text(
                                "clear",
                                color = VoxColors.Muted,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            if (edit) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(
                            "Move 50 from Groceries to Travel, save 15% of any salary into Emergency...",
                            color = VoxColors.Muted,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = VoxColors.Panel2,
                        unfocusedContainerColor = VoxColors.Panel2,
                        focusedBorderColor = VoxColors.Accent,
                        unfocusedBorderColor = VoxColors.Line,
                        focusedTextColor = VoxColors.Ink,
                        unfocusedTextColor = VoxColors.Ink,
                        cursorColor = VoxColors.Accent,
                    ),
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .background(VoxColors.Panel2.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, VoxColors.Line, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        display.ifEmpty {
                            "Hold the mic and tell Vox what to do — e.g. \u201Cmove 50 from groceries to travel, set aside 15% of every salary into emergency.\u201D"
                        },
                        color = if (display.isEmpty()) VoxColors.Muted else VoxColors.Ink,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryButton(
                    text = if (busy) "Planning…" else "Plan it",
                    onClick = {
                        val t = (if (edit) draft else display).trim()
                        if (t.isNotEmpty()) onSubmit(t)
                    },
                    enabled = canSubmit || (edit && draft.trim().isNotEmpty()),
                )
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") TextAlign.Center
}
