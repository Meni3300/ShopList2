package com.shoplist.app.ui.stores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoplist.app.ui.AppViewModel
import com.shoplist.app.ui.common.ConfirmDialog
import com.shoplist.app.ui.common.EmptyState
import com.shoplist.app.ui.common.ReorderableColumn
import com.shoplist.app.ui.common.TextInputDialog
import com.shoplist.app.utils.Format

// ------------------------------------------------------------------
// הסופרים שלי
// ------------------------------------------------------------------
@Composable
fun StoresScreen(vm: AppViewModel, onOpenStore: (Long) -> Unit) {
    val stores by vm.stores.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "הסופרים שלי",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { showAdd = true }) { Text("+ הוסף רשת") }
            }
        }
        if (stores.isEmpty()) {
            item {
                EmptyState(
                    message = "עדיין לא הוספת סופר.",
                    buttonLabel = "הוסף סופר",
                    onButton = { showAdd = true }
                )
            }
        } else {
            items(stores, key = { it.id }) { st ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onOpenStore(st.id) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🛒", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(st.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                Format.branches(st.branchCount),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.size(72.dp)) }
    }

    if (showAdd) {
        TextInputDialog(
            title = "רשת חדשה",
            placeholder = "שם הרשת, לדוגמה: ויקטורי",
            confirmLabel = "הוסף",
            errorText = if (duplicate) "רשת בשם הזה כבר קיימת" else null,
            onConfirm = { name ->
                vm.createStore(name) { ok ->
                    if (ok) {
                        showAdd = false
                        duplicate = false
                    } else {
                        duplicate = true
                    }
                }
            },
            onDismiss = {
                showAdd = false
                duplicate = false
            }
        )
    }
}

// ------------------------------------------------------------------
// רשת → סניפים
// ------------------------------------------------------------------
@Composable
fun StoreDetailScreen(vm: AppViewModel, storeId: Long, onBack: () -> Unit, onOpenBranch: (Long) -> Unit) {
    val stores by vm.stores.collectAsState()
    val store = stores.firstOrNull { it.id == storeId }
    val branches by remember(storeId) { vm.branches(storeId) }.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "חזרה") }
            Text(
                store?.name ?: "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "מחק רשת")
            }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.padding(vertical = 8.dp)) { Text("+ הוסף סניף") }

        if (branches.isEmpty()) {
            EmptyState(message = "עדיין אין סניפים ברשת הזאת.", buttonLabel = "הוסף סניף", onButton = { showAdd = true })
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(branches, key = { it.id }) { b ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenBranch(b.id) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(store?.name ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("└── ${b.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog(
            title = "סניף חדש",
            placeholder = "לדוגמה: נתיבות",
            confirmLabel = "הוסף",
            errorText = if (duplicate) "סניף בשם הזה כבר קיים ברשת" else null,
            onConfirm = { name ->
                vm.createBranch(storeId, name) { ok ->
                    if (ok) {
                        showAdd = false
                        duplicate = false
                    } else {
                        duplicate = true
                    }
                }
            },
            onDismiss = {
                showAdd = false
                duplicate = false
            }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "למחוק את הרשת?",
            message = "הרשת, כל הסניפים שלה והסדר השמור בהם יימחקו. המוצרים עצמם לא יימחקו.",
            confirmLabel = "מחק",
            onConfirm = {
                confirmDelete = false
                vm.deleteStore(storeId)
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ------------------------------------------------------------------
// סניף: הסדר האישי
// ------------------------------------------------------------------
@Composable
fun BranchScreen(vm: AppViewModel, branchId: Long, onBack: () -> Unit) {
    val info by remember(branchId) { vm.branchInfo(branchId) }.collectAsState(initial = null)
    val order by remember(branchId) { vm.branchOrder(branchId) }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "חזרה") }
            Text(
                info?.let { "${it.storeName} — ${it.branchName}" } ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "מחק סניף")
            }
        }
        Text(
            "סדר הקנייה שלי",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (order.isEmpty()) {
                EmptyState(
                    message = "עדיין אין סדר שמור לסניף הזה.",
                    hint = "התחל קנייה בסניף וסדר את המוצרים בגרירה — הסדר יישמר."
                )
            } else if (editing) {
                ReorderableColumn(
                    items = order,
                    keyOf = { it.productId },
                    onCommit = { vm.reorder(branchId, it.map { o -> o.productId }) }
                ) { o, index, handle, dragging ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (dragging) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "גרור כדי לשנות את מיקום ${o.name}",
                                modifier = handle.size(48.dp).padding(12.dp)
                            )
                            Text("${index + 1}. ${o.name}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else {
                order.forEachIndexed { i, o ->
                    Text(
                        "${i + 1}. ${o.name}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = !editing }, enabled = order.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text(if (editing) "סיום עריכה" else "ערוך סדר")
            }
            OutlinedButton(onClick = { confirmReset = true }, enabled = order.isNotEmpty()) { Text("אפס סדר") }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "אפס סדר",
            message = "לאפס את סדר המוצרים בסניף?",
            confirmLabel = "אפס",
            onConfirm = {
                confirmReset = false
                editing = false
                vm.resetOrder(branchId)
            },
            onDismiss = { confirmReset = false }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "למחוק את הסניף?",
            message = "הסניף והסדר השמור שלו יימחקו.",
            confirmLabel = "מחק",
            onConfirm = {
                confirmDelete = false
                vm.deleteBranch(branchId)
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
