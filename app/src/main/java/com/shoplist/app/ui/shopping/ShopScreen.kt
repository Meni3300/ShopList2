package com.shoplist.app.ui.shopping

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoplist.app.data.local.Branch
import com.shoplist.app.data.local.BranchProductOrder
import com.shoplist.app.data.local.ListEntry
import com.shoplist.app.data.local.SessionEntry
import com.shoplist.app.data.local.SessionInfo
import com.shoplist.app.sorting.ShoppingListSorter
import com.shoplist.app.ui.AppViewModel
import com.shoplist.app.ui.common.ConfirmDialog
import com.shoplist.app.ui.common.EmptyState
import com.shoplist.app.ui.common.ReorderableColumn
import com.shoplist.app.utils.Categories
import kotlinx.coroutines.flow.flowOf

@Composable
fun ShopScreen(vm: AppViewModel, onGoStores: () -> Unit, onHistory: () -> Unit) {
    val session by vm.activeSession.collectAsState()
    val s = session
    if (s != null) {
        ShoppingModeScreen(vm, s)
    } else {
        BranchPickerScreen(vm, onGoStores, onHistory)
    }
}

// ------------------------------------------------------------------
// Before shopping: pick store → branch, arrange the list, start.
// ------------------------------------------------------------------
@Composable
private fun BranchPickerScreen(vm: AppViewModel, onGoStores: () -> Unit, onHistory: () -> Unit) {
    val stores by vm.stores.collectAsState()
    val list by vm.list.collectAsState()
    val last by vm.lastBranch.collectAsState()
    val selStoreId by vm.selectedStoreId.collectAsState()
    val selBranchId by vm.selectedBranchId.collectAsState()

    LaunchedEffect(last, selBranchId, selStoreId) {
        val l = last
        if (l != null && selBranchId == null && selStoreId == null) vm.selectBranch(l.storeId, l.branchId)
    }

    val branches by remember(selStoreId) {
        val id = selStoreId
        if (id == null) flowOf(emptyList<Branch>()) else vm.branches(id)
    }.collectAsState(initial = emptyList())
    val branch = branches.firstOrNull { it.id == selBranchId }

    val orders by remember(branch?.id) {
        val id = branch?.id
        if (id == null) flowOf(emptyList<BranchProductOrder>()) else vm.orders(id)
    }.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "קנייה",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onHistory) { Text("היסטוריה") }
        }

        if (stores.isEmpty()) {
            EmptyState(
                message = "עדיין לא הוספת סופר.",
                buttonLabel = "הוסף סופר",
                onButton = onGoStores
            )
            return@Column
        }

        Text("רשת", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stores.forEach { st ->
                FilterChip(
                    selected = st.id == selStoreId,
                    onClick = { vm.selectStore(st.id) },
                    label = { Text(st.name) }
                )
            }
        }

        if (selStoreId != null) {
            Text("סניף", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            if (branches.isEmpty()) {
                Text(
                    "אין סניפים ברשת הזאת עדיין.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onGoStores) { Text("הוסף סניף במסך הסופרים") }
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    branches.forEach { b ->
                        FilterChip(
                            selected = b.id == selBranchId,
                            onClick = { vm.selectBranch(b.storeId, b.id) },
                            label = { Text(b.name) }
                        )
                    }
                }
            }
        }

        if (branch == null) {
            Spacer(Modifier.size(16.dp))
            Text(
                "בחר רשת וסניף כדי לראות את הרשימה לפי הסדר שלך בסופר.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        val orderMap = orders.associate { it.productId to it.sortIndex }
        val sorted = ShoppingListSorter.sort(list, { it.productId }, { it.createdAt }, orderMap)
        val firstVisit = orders.isEmpty()

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp)
        ) {
            if (list.isEmpty()) {
                EmptyState(
                    message = "כרגע אין שום דבר ברשימת הקניות.",
                    hint = "מה חסר בבית? לחץ על + כדי להוסיף."
                )
            } else {
                if (firstVisit) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("זו הפעם הראשונה שלך בסניף הזה.", fontWeight = FontWeight.Bold)
                            Text("סדר את המוצרים לפי המסלול שלך בסופר — אנחנו נזכור בפעם הבאה.")
                        }
                    }
                }
                ReorderableColumn(
                    items = sorted,
                    keyOf = { it.item.productId },
                    onCommit = { newOrder -> vm.reorder(branch.id, newOrder.map { it.item.productId }) }
                ) { sortedItem, _, handle, dragging ->
                    PickerRow(sortedItem.item, sortedItem.isNew, handle, dragging)
                }
            }
        }

        Button(
            onClick = { vm.startSession(branch.storeId, branch.id) },
            enabled = list.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp)
        ) {
            Text("התחל קנייה", fontSize = 18.sp)
        }
    }
}

@Composable
private fun PickerRow(e: ListEntry, isNew: Boolean, handle: Modifier, dragging: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 1.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "גרור כדי לשנות את מיקום ${e.name}",
                modifier = handle.size(48.dp).padding(12.dp)
            )
            Text(Categories.emoji(e.name, e.category), fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium)
                if (isNew) {
                    Text(
                        "חדש",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (e.quantity > 1) Text("× ${e.quantity}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ------------------------------------------------------------------
// Shopping mode
// ------------------------------------------------------------------
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun ShoppingModeScreen(vm: AppViewModel, session: SessionInfo) {
    KeepScreenOn()
    val rows by remember(session.id) { vm.sessionItems(session.id) }.collectAsState(initial = emptyList())
    val total = rows.size
    val done = rows.count { it.isCompleted }
    var confirmCancel by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "🛒 ${session.storeName} — ${session.branchName}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text("$total מוצרים", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(10.dp)
        )
        Text("$done / $total", modifier = Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold)

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (total > 0 && done == total) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎉", fontSize = 40.sp)
                            Text(
                                "הקנייה הושלמה",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text("כל $total המוצרים נקנו.")
                        }
                    }
                }
            }
            items(rows, key = { it.id }) { e -> ShoppingRow(e) { vm.toggle(e.id) } }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { confirmCancel = true }) { Text("בטל קנייה") }
            Button(
                onClick = { vm.finishSession(session.id) },
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("סיים קנייה", fontSize = 18.sp)
            }
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "לבטל את הקנייה?",
            message = "הרשימה תישאר כמו שהיא ולא תישמר היסטוריה.",
            confirmLabel = "בטל קנייה",
            onConfirm = {
                confirmCancel = false
                vm.cancelSession(session.id)
            },
            onDismiss = { confirmCancel = false }
        )
    }
}

@Composable
private fun ShoppingRow(e: SessionEntry, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (e.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        label = "rowBg"
    )
    val shape = RoundedCornerShape(18.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = e.isCompleted, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(Categories.emoji(e.name, e.category), fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                e.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                textDecoration = if (e.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )
            if (e.quantity > 1) {
                Text("× ${e.quantity}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
