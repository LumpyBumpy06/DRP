package com.drp33.quietsignal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
/*
TODO
1. Need a button to copy token
*/

class AdultViewModel: ViewModel() {
    var state by mutableStateOf(AdultState())
        private set

    fun onRemoteTokenChange(newToken: String) {
        state = state.copy(
            remoteToken = newToken
        )
    }


}