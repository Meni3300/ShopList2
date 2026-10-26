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
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.shoplist.app.ui.common.TextInputDialog
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
    val householdCode by vm.householdCode.collectAsState()
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmJoinCode by remember { mutableStateOf<String?>(null) }

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
                Text("בית משותף", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!vm.syncAvailable) {
                    Text(
                        "הסנכרון עדיין לא הוגדר בגרסה הזאת של האפליקציה (חסר קובץ google-services.json).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (householdCode == null) {
                    Text(
                        "חבר את הטלפון של בן/בת הזוג כדי לשתף רשימה, סופרים, סדר מוצרים והיסטוריה.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (busy) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                busy = true
                                vm.createHousehold { code ->
                                    busy = false
                                    if (code == null) toast("יצירת הבית נכשלה. בדוק חיבור לאינטרנט והגדרות Firebase.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("צור בית חדש") }
                        OutlinedButton(onClick = { showJoin = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("הצטרף לבית עם קוד")
                        }
                    }
                } else {
                    Text("הטלפון מחובר לבית משותף ✅")
                    Text("קוד הבית:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        householdCode ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "תן את הקוד הזה לבן/בת הזוג. בטלפון שלהם: הגדרות ← הצטרף לבית עם קוד.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(householdCode ?: ""))
                            toast("הקוד הועתק")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("העתק קוד") }
                    TextButton(onClick = { confirmLeave = true }) { Text("עזוב את הבית") }
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
                    enabled = householdCode == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Data — ייבוא נתונים")
                }
            }
        }

        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("היסטוריית קניות") }

        Text(
            "ShopList 1.1.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showJoin) {
        TextInputDialog(
            title = "הצטרפות לבית",
            placeholder = "קוד הבית",
            confirmLabel = "המשך",
            errorText = if (joinError) "הבית לא נמצא. בדוק את הקוד ואת החיבור לאינטרנט." else null,
            onConfirm = { code ->
                joinError = false
                confirmJoinCode = code
                showJoin = false
            },
            onDismiss = {
                showJoin = false
                joinError = false
            }
        )
    }
    val pendingJoin = confirmJoinCode
    if (pendingJoin != null) {
        ConfirmDialog(
            title = "להצטרף לבית?",
            message = "הרשימה, הסופרים וההיסטוריה שבטלפון הזה יוחלפו בנתוני הבית המשותף.",
            confirmLabel = "הצטרף",
            onConfirm = {
                confirmJoinCode = null
                busy = true
                vm.joinHousehold(pendingJoin) { ok ->
                    busy = false
                    if (ok) toast("הצטרפת לבית") else {
                        joinError = true
                        showJoin = true
                    }
                }
            },
            onDismiss = { confirmJoinCode = null }
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = "לעזוב את הבית?",
            message = "הטלפון הזה יפסיק להסתנכרן. הנתונים שכבר נמצאים בו יישארו.",
            confirmLabel = "עזוב",
            onConfirm = {
                confirmLeave = false
                vm.leaveHousehold()
            },
            onDismiss = { confirmLeave = false }
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
