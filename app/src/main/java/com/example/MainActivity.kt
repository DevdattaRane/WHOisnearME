package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.WhoIsNearMeAppShell
import com.example.ui.theme.WhoIsNearMeTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WhoIsNearMeApp
        setContent {
            WhoIsNearMeTheme {
                val mainViewModel: MainViewModel = viewModel(
                    factory = MainViewModel.Factory(
                        repository = app.friendRepository,
                        proximityNotificationService = app.proximityNotificationService
                    )
                )
                WhoIsNearMeAppShell(viewModel = mainViewModel)
            }
        }
    }
}
