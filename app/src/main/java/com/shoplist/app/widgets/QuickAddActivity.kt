package com.shoplist.app.widgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.shoplist.app.ShopListApp
import com.shoplist.app.ui.common.AddProductDialog
import com.shoplist.app.ui.theme.ShopListTheme
import kotlinx.coroutines.launch

/** Small dialog opened from the home-screen widget: type, press Enter, done. */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = ShopListApp.from(this).repo
        setContent {
            val mode by repo.themeMode.collectAsState(initial = "system")
            ShopListTheme(mode) {
                AddProductDialog(
                    onSubmit = { text ->
                        lifecycleScope.launch {
                            repo.addFromText(text)
                            finish()
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
