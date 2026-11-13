package com.saleh.alazzi.grocery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.data.backup.GroceryBackupManager
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import com.saleh.alazzi.grocery.ui.GroceryApp
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.ThemeMode
import com.saleh.alazzi.grocery.ui.theme.AlAzziTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val database = GroceryDatabase.getInstance(applicationContext)
        val repository = GroceryRepository(database)
        val viewModel = ViewModelProvider(
            this,
            GroceryViewModel.Factory(database, repository)
        )[GroceryViewModel::class.java]

        setContent {
            val settings by viewModel.storeSettings.collectAsStateWithLifecycle()
            val themeKey = settings.firstOrNull { it.key == "theme_mode" }?.value
            val themeMode = ThemeMode.entries.firstOrNull { it.key == themeKey } ?: ThemeMode.SYSTEM

            AlAzziTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    GroceryApp(viewModel, GroceryBackupManager(applicationContext, database))
                }
            }
        }
    }
}
