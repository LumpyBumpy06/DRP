package com.drp33.quietsignal.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.drp33.quietsignal.viewmodels.AdultViewModel

@Composable
fun AdultScreen(viewModel: AdultViewModel) {

    val state = viewModel.state

    Column {

        if (state.checkedIn) {
            Text("✅ Norman has checked in")
        } else {
            Text("Waiting for Norman...")
        }
    }
}