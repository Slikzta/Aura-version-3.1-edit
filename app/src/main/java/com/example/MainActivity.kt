package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aura.di.AuraContainer
import com.example.aura.ui.AuraApp
import com.example.aura.ui.AuraMainViewModel
import com.example.aura.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {

    private lateinit var auraContainer: AuraContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        auraContainer = AuraContainer.getInstance(applicationContext)

        handleAssistIntent(intent)

        setContent {
            AuraTheme {
                val viewModel: AuraMainViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return AuraMainViewModel(auraContainer) as T
                        }
                    }
                )
                AuraApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::auraContainer.isInitialized) {
            auraContainer.assistantManager.refreshStatus()
        }
    }

    private fun handleAssistIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == Intent.ACTION_ASSIST ||
            action == Intent.ACTION_VOICE_COMMAND ||
            action == "android.intent.action.VOICE_ASSIST") {
            if (::auraContainer.isInitialized) {
                auraContainer.assistantManager.processAssistantInvocation(
                    source = "MainActivity_$action",
                    intent = intent
                )
            }
        }
    }
}
