package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.drp33.quietsignal.model.UserRole

@Composable
fun RoleSelectScreen(
    onSelect: (UserRole) -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Button(onClick = { onSelect(UserRole.NORMAN) }) {
            Text("Norman")
        }

        Button(onClick = { onSelect(UserRole.SADIE) }) {
            Text("Sadie")
        }
    }
}