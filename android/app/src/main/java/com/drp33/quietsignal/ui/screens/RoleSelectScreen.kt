package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drp33.quietsignal.model.UserRole

/** Highest tree stage (matches the backend GROWTH_THRESHOLDS: stages 0..9). */
private const val MAX_TREE_STAGE = 9

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleSelectScreen(
    onSelect: (UserRole) -> Unit,
    onSetStage: (Int) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = { onSelect(UserRole.NORMAN) }) {
            Text("Norman")
        }

        Button(onClick = { onSelect(UserRole.SADIE) }) {
            Text("Sadie")
        }

        // ---- Demo controls: jump the shared tree to any growth stage ----
        Spacer(Modifier.height(48.dp))
        Text("Demo · set tree stage", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Grows the shared tree instantly (no moments needed)", fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (stage in 0..MAX_TREE_STAGE) {
                OutlinedButton(onClick = { onSetStage(stage) }) {
                    Text("Stage $stage")
                }
            }
        }
    }
}
