package com.shoplist.app.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import com.shoplist.app.data.local.ListEntry
import com.shoplist.app.data.local.SessionInfo
import com.shoplist.app.parser.ShoppingInputParser
import com.shoplist.app.ui.AppViewModel
import com.shoplist.app.ui.common.EmptyState
import com.shoplist.app.ui.common.TextIconButton
import com.shoplist.app.utils.Categories
import com.shoplist.app.utils.TextNormalizer

@Composable
fun HomeScreen(vm: AppViewModel, onOpenShop: () -> Unit, onAddClick: () -> Unit) {
    val list by vm.list.collectAsState()
    val session by vm.activeSession.collectAsState()
    val last by vm.lastBranch.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val products by vm.products.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (text.isNotBlank()) {
            vm.add(text)
            text = ""
        }
    }

    val parsed = ShoppingInputParser.parse(text)
    val query = TextNormalizer.normalize(parsed?.name ?: "")
    val matches = if (query.isEmpty()) {
        emptyList()
    } else {
        products.filter { it.normalizedName.startsWith(query) && it.normalizedName != query }.take(6)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("מה חסר בבית?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("כתוב מוצר שחסר...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { submit() },
                    modifier = Modifier.size(56.dp).semantics { contentDescription = "הוסף מוצר" }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
        if (matches.isNotEmpty()) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matches.forEach { p ->
                        SuggestionChip(
                            onClick = {
                                val q = parsed?.quantity ?: 1
                                text = if (q > 1) "${p.name} $q" else p.name
                            },
                            label = { Text(p.name) }
                        )
                    }
                }
            }
        }

        val s = session
        if (s != null) {
            item { ContinueCard(vm, s, onOpenShop) }
        } else if (last != null) {
            item {
                val l = last
                if (l != null) {
                    LastStoreCard(
                        label = "${l.storeName} — ${l.branchName}",
                        enabled = list.isNotEmpty(),
                        onStart = { vm.startSession(l.storeId, l.branchId) { onOpenShop() } }
                    )
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            item {
                Text(
                    "אולי חסר לך?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(suggestions, key = { "sg${it.product.id}" }) { sg ->
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Categories.emoji(sg.product.name, sg.product.category), fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(sg.product.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            onClick = { vm.addProduct(sg.product.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "הוסף ${sg.product.name} לרשימה")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "רשימת הקניות שלי",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (list.isEmpty()) {
            item {
                EmptyState(
                    message = "כרגע אין שום דבר ברשימת הקניות.",
                    hint = "מה חסר בבית?",
                    buttonLabel = "הוסף מוצר",
                    onButton = onAddClick
                )
            }
        } else {
            items(list, key = { it.itemId }) { e ->
                ListItemCard(
                    e,
                    onMinus = { vm.setQuantity(e.itemId, e.quantity - 1) },
                    onPlus = { vm.setQuantity(e.itemId, e.quantity + 1) },
                    onDelete = { vm.remove(e.itemId) }
                )
            }
        }
        item { Spacer(Modifier.size(72.dp)) }
    }
}

@Composable
private fun ListItemCard(e: ListEntry, onMinus: () -> Unit, onPlus: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Categories.emoji(e.name, e.category), fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextIconButton("−", "הפחת כמות של ${e.name}", enabled = e.quantity > 1, onClick = onMinus)
            Text("${e.quantity}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextIconButton("+", "הגדל כמות של ${e.name}", onClick = onPlus)
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "מחק ${e.name}")
            }
        }
    }
}

@Composable
private fun ContinueCard(vm: AppViewModel, s: SessionInfo, onOpenShop: () -> Unit) {
    val items by remember(s.id) { vm.sessionItems(s.id) }.collectAsState(initial = emptyList())
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("קנייה בתהליך", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${s.storeName} — ${s.branchName}")
            Text("${items.count { it.isCompleted }} / ${items.size} מוצרים")
            Button(onClick = onOpenShop, modifier = Modifier.padding(top = 6.dp)) { Text("המשך קנייה") }
        }
    }
}

@Composable
private fun LastStoreCard(label: String, enabled: Boolean, onStart: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("הסופר האחרון", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("🛒 $label")
            Button(onClick = onStart, enabled = enabled, modifier = Modifier.padding(top = 6.dp)) {
                Text("התחל קנייה")
            }
        }
    }
}
