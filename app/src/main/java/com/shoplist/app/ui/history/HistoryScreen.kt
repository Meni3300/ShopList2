package com.shoplist.app.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.shoplist.app.data.local.HistoryRow
import com.shoplist.app.ui.AppViewModel
import com.shoplist.app.ui.common.EmptyState
import com.shoplist.app.utils.Format

@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val history by vm.history.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "חזרה") }
            Text("היסטוריית קניות", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        if (history.isEmpty()) {
            EmptyState(message = "עדיין אין קניות בהיסטוריה.", hint = "סיים קנייה ראשונה והיא תופיע כאן.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { h -> HistoryCard(vm, h) }
            }
        }
    }
}

@Composable
private fun HistoryCard(vm: AppViewModel, h: HistoryRow) {
    var expanded by remember { mutableStateOf(false) }
    val items by remember(h.id) { vm.historyItems(h.id) }.collectAsState(initial = emptyList())
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded }.animateContentSize()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(Format.date(h.createdAt), style = MaterialTheme.typography.labelLarge)
            Text(
                "${h.storeName} — ${h.branchName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("${h.itemCount} מוצרים", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                items.forEach {
                    Text(
                        "• ${it.productNameSnapshot}" + if (it.quantity > 1) " × ${it.quantity}" else "",
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
