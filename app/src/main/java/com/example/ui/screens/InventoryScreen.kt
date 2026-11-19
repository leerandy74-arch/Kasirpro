package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.localization.AppStrings
import com.example.model.Branch
import com.example.model.Product
import com.example.model.StaffAccount
import com.example.model.StaffRole
import com.example.ui.components.formatIdr
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerSoft
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.MintSoft

@Composable
fun InventoryScreen(
    strings: AppStrings,
    activeBranch: Branch?,
    activeStaff: StaffAccount?,
    products: List<Product>,
    onAdjustStock: (Product, Long) -> Unit,
    onSaveProduct: (Product) -> Unit
) {
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val isAdmin = activeStaff?.staffRole == StaffRole.CENTRAL_ADMIN || activeStaff?.staffRole == StaffRole.BRANCH_ADMIN

    val branchProducts = remember(products, activeBranch) {
        val bid = activeBranch?.id
        if (bid.isNullOrBlank()) products else products.filter { it.branchId == bid }
    }
    val lowStockCount = remember(branchProducts) { branchProducts.count { it.lowStock } }
    val totalUnits = remember(branchProducts) { branchProducts.sumOf { it.stock } }

    Scaffold(
        floatingActionButton = {
            if (isAdmin && activeBranch != null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("add_product_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.addProductButton)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Total SKU", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${branchProducts.size} (${totalUnits} unit)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        color = if (lowStockCount > 0) AmberSoft else MintSoft,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (lowStockCount > 0) Icons.Default.Warning else Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = if (lowStockCount > 0) Color(0xFF92400E) else EmeraldDeep
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(strings.lowStockBadge, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "$lowStockCount produk",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            items(branchProducts, key = { it.id }) { product ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("inventory_card_${product.sku}")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${product.sku} • ${product.category} • ${formatIdr(product.price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isAdmin) {
                                IconButton(onClick = { editingProduct = product }) {
                                    Icon(Icons.Default.Edit, contentDescription = strings.editProductTitle)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val badgeColor = when {
                                product.stock <= 0L -> DangerSoft
                                product.lowStock -> AmberSoft
                                else -> MintSoft
                            }
                            val textColor = when {
                                product.stock <= 0L -> DangerRed
                                product.lowStock -> Color(0xFF92400E)
                                else -> EmeraldDeep
                            }
                            Surface(color = badgeColor, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    text = "${strings.stockLabel}: ${product.stock} ${product.unit} (min ${product.minStock})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = textColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            if (isAdmin) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onAdjustStock(product, -1L) },
                                        enabled = product.stock > 0L
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "-1")
                                    }
                                    IconButton(onClick = { onAdjustStock(product, +1L) }) {
                                        Icon(Icons.Default.Add, contentDescription = "+1")
                                    }
                                    OutlinedButton(onClick = { onAdjustStock(product, +10L) }) {
                                        Text("+10")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && activeBranch != null) {
        ProductEditorDialog(
            strings = strings,
            initial = Product(branchId = activeBranch.id, unit = "pcs", minStock = 5L),
            onDismiss = { showAddDialog = false },
            onSave = { p ->
                showAddDialog = false
                onSaveProduct(p)
            }
        )
    }

    editingProduct?.let { target ->
        ProductEditorDialog(
            strings = strings,
            initial = target,
            onDismiss = { editingProduct = null },
            onSave = { p ->
                editingProduct = null
                onSaveProduct(p)
            }
        )
    }
}

@Composable
private fun ProductEditorDialog(
    strings: AppStrings,
    initial: Product,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var sku by remember { mutableStateOf(initial.sku) }
    var category by remember { mutableStateOf(initial.category) }
    var price by remember { mutableStateOf(if (initial.price > 0) initial.price.toLong().toString() else "") }
    var costPrice by remember { mutableStateOf(if (initial.costPrice > 0) initial.costPrice.toLong().toString() else "") }
    var stock by remember { mutableStateOf(initial.stock.toString()) }
    var minStock by remember { mutableStateOf(initial.minStock.toString()) }
    var unit by remember { mutableStateOf(initial.unit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editProductTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(strings.productNameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text(strings.skuLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text(strings.categoryLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter(Char::isDigit) },
                        label = { Text(strings.sellPriceLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = costPrice,
                        onValueChange = { costPrice = it.filter(Char::isDigit) },
                        label = { Text(strings.costPriceLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it.filter(Char::isDigit) },
                        label = { Text(strings.currentStockLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minStock,
                        onValueChange = { minStock = it.filter(Char::isDigit) },
                        label = { Text(strings.minStockLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(strings.unitLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                sku = sku.trim().ifBlank { "SKU-${System.currentTimeMillis() % 10000}" },
                                category = category.trim().ifBlank { "Umum" },
                                price = price.toDoubleOrNull() ?: 0.0,
                                costPrice = costPrice.toDoubleOrNull() ?: 0.0,
                                stock = stock.toLongOrNull() ?: 0L,
                                minStock = minStock.toLongOrNull() ?: 5L,
                                unit = unit.trim().ifBlank { "pcs" }
                            )
                        )
                    }
                }
            ) {
                Text(strings.saveButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelButton) }
        }
    )
}
