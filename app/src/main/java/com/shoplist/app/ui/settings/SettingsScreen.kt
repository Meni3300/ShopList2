package com.shoplist.app.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoplist.app.ui.AppViewModel
import com.shoplist.app.ui.common.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: AppViewModel, onHistory: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by vm.themeMode.collectAsState()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = try {
                    val json = vm.exportJson()
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri, "wt")?.use {
                            it.write(json.toByteArray(Charsets.UTF_8))
                        } != null
                    }
                } catch (e: Exception) {
                    false
                }
                toast(if (ok) "הנתונים יוצאו בהצלחה" else "הייצוא נכשל")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("הגדרות", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("מראה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOf("system" to "ברירת מחדל של המערכת", "light" to "בהיר", "dark" to "כהה").forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = mode == key, role = Role.RadioButton, onClick = { vm.setTheme(key) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == key, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("גיבוי נתונים", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "ייצוא לקובץ JSON ושחזור ממנו.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { exportLauncher.launch("shoplist-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export Data — ייצוא נתונים")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Data — ייבוא נתונים")
                }
            }
        }

        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("היסטוריית קניות") }

        Text(
            "ShopList 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val uri = pendingImport
    if (uri != null) {
        ConfirmDialog(
            title = "ייבוא נתונים",
            message = "ייבוא נתונים יכול להחליף מידע קיים.",
            confirmLabel = "Import",
            onConfirm = {
                pendingImport = null
                scope.launch {
                    val ok = try {
                        val text = withContext(Dispatchers.IO) {
                            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        }
                        if (text == null) false else vm.importJson(text)
                    } catch (e: Exception) {
                        false
                    }
                    toast(if (ok) "הנתונים שוחזרו בהצלחה" else "הקובץ אינו גיבוי תקין של ShopList")
                }
            },
            onDismiss = { pendingImport = null }
        )
    }
}
