package com.shoplist.app.ui.products

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoplist.app.data.local.Product
import com.shoplist.app.ui.AppViewModel
import com.shoplist.app.ui.common.ConfirmDialog
import com.shoplist.app.ui.common.EmptyState
import com.shoplist.app.utils.Categories
import com.shoplist.app.utils.Format
import com.shoplist.app.utils.TextNormalizer

@Composable
fun ProductsScreen(vm: AppViewModel) {
    val products by vm.products.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Product?>(null) }
    var deleting by remember { mutableStateOf<Product?>(null) }

    val q = TextNormalizer.normalize(query)
    val shown = if (q.isEmpty()) products else products.filter { it.normalizedName.contains(q) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("מוצרים", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("חיפוש מוצר") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    message = if (products.isEmpty()) "עדיין אין מוצרים." else "לא נמצאו מוצרים.",
                    hint = if (products.isEmpty()) "מוצרים נוצרים אוטומטית כשמוסיפים אותם לרשימה." else null
                )
            }
        }
        items(shown, key = { it.id }) { p ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Categories.emoji(p.name, p.category), fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${p.category} · ${Format.times(p.purchaseCount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { editing = p }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "ערוך ${p.name}")
                    }
                    IconButton(onClick = { deleting = p }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "מחק ${p.name}")
                    }
                }
            }
        }
        item { Spacer(Modifier.size(72.dp)) }
    }

    editing?.let { p ->
        EditProductDialog(vm, p, onDismiss = { editing = null })
    }
    deleting?.let { p ->
        ConfirmDialog(
            title = "למחוק את ${p.name}?",
            message = "המוצר יימחק מהרשימה ומהסדר השמור בכל הסניפים.",
            confirmLabel = "מחק",
            onConfirm = {
                vm.deleteProduct(p.id)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun EditProductDialog(vm: AppViewModel, product: Product, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(product.name) }
    var category by remember { mutableStateOf(product.category) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עריכת מוצר") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = false
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text("מוצר בשם הזה כבר קיים", color = MaterialTheme.colorScheme.error)
                }
                Text("קטגוריה", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Categories.all.forEach { c ->
                        FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateProduct(product.id, name, category) { ok ->
                    if (ok) onDismiss() else error = true
                }
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
