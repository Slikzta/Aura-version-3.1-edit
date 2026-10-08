package com.example

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

        auraContainer = AuraContainer(applicationContext)

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
}

