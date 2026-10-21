package ru.shkola.russkiy6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.shkola.russkiy6.data.Repository
import ru.shkola.russkiy6.ui.AppNavigation
import ru.shkola.russkiy6.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repository.init(this)
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }
}
