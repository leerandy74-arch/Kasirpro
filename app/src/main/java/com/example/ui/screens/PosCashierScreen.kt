package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.integrations.QrisGenerator
import com.example.localization.AppStrings
import com.example.model.Branch
import com.example.model.CartItem
import com.example.model.PaymentMethod
import com.example.model.Product
import com.example.model.WorkspaceConfig
import com.example.ui.components.formatIdr
import com.example.ui.theme.AmberGold
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerSoft
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.MintSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosCashierScreen(
    strings: AppStrings,
    workspace: WorkspaceConfig?,
    activeBranch: Branch?,
    products: List<Product>,
    cartItems: List<CartItem>,
    isBusy: Boolean,
    hasPrinterConfigured: Boolean,
    onAddToCart: (Product) -> Unit,
    onDecrementCart: (String) -> Unit,
    onClearCart: () -> Unit,
    onConfirmCheckout: (
        paymentMethod: PaymentMethod,
        discount: Double,
        paidAmount: Double,
        customerName: String,
        customerPhone: String,
        whatsappSent: Boolean,
        autoPrintBluetooth: Boolean
    ) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showCheckoutSheet by remember { mutableStateOf(false) }

    val branchProducts = remember(products, activeBranch) {
        val bid = activeBranch?.id
        if (bid.isNullOrBlank()) products else products.filter { it.branchId == bid && it.active }
    }

    val categories = remember(branchProducts) {
        branchProducts.map { it.category }.distinct().sorted()
    }

    val filteredProducts = remember(branchProducts, searchQuery, selectedCategory) {
        branchProducts.filter { p ->
            val matchCategory = selectedCategory == null || p.category == selectedCategory
            val q = searchQuery.trim()
            val matchQuery = q.isEmpty() ||
                p.name.contains(q, ignoreCase = true) ||
                p.sku.contains(q, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    val cartCount = cartItems.sumOf { it.quantity }
    val cartSubtotal = cartItems.sumOf { it.lineTotal }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 760.dp

        if (isWideLayout) {
            // Tablet / Desktop Canonical Split Cashier Layout
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1.45f)
                        .fillMaxHeight()
                ) {
                    ProductCatalogPane(
                        strings = strings,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        products = filteredProducts,
                        cartItems = cartItems,
                        onAddToCart = onAddToCart
                    )
                }

                Surface(
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CheckoutPanelContent(
                        strings = strings,
                        workspace = workspace,
                        activeBranch = activeBranch,
                        cartItems = cartItems,
                        isBusy = isBusy,
                        hasPrinterConfigured = hasPrinterConfigured,
                        onAddToCart = onAddToCart,
                        onDecrementCart = onDecrementCart,
                        onClearCart = onClearCart,
                        onConfirmCheckout = onConfirmCheckout
                    )
                }
            }
        } else {
            // Compact Phone Layout with Sticky Bottom Cart Bar
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (cartCount > 0) 84.dp else 0.dp)
                ) {
                    ProductCatalogPane(
                        strings = strings,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        products = filteredProducts,
                        cartItems = cartItems,
                        onAddToCart = onAddToCart
                    )
                }

                if (cartCount > 0) {
                    Surface(
                        color = EmeraldDeep,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clickable { showCheckoutSheet = true }
                            .testTag("open_checkout_sheet_bar")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = EmeraldBright,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint = Color(0xFF002B20),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = "$cartCount ${strings.itemsCountLabel}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color(0xFF002B20)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = formatIdr(cartSubtotal),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                color = AmberGold,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = strings.checkoutButton,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF1F1600),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (showCheckoutSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showCheckoutSheet = false },
                    sheetState = sheetState
                ) {
                    CheckoutPanelContent(
                        strings = strings,
                        workspace = workspace,
                        activeBranch = activeBranch,
                        cartItems = cartItems,
                        isBusy = isBusy,
                        hasPrinterConfigured = hasPrinterConfigured,
                        onAddToCart = onAddToCart,
                        onDecrementCart = onDecrementCart,
                        onClearCart = onClearCart,
                        onConfirmCheckout = { method, disc, paid, custName, custPhone, wa, printBt ->
                            showCheckoutSheet = false
                            onConfirmCheckout(method, disc, paid, custName, custPhone, wa, printBt)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductCatalogPane(
    strings: AppStrings,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    products: List<Product>,
    cartItems: List<CartItem>,
    onAddToCart: (Product) -> Unit
) {
    val cartQtyById = remember(cartItems) { cartItems.associate { it.product.id to it.quantity } }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(strings.searchProductsHint) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("pos_search_input")
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text(strings.allCategories) }
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(if (selectedCategory == cat) null else cat) },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(products, key = { it.id }) { product ->
                val inCartQty = cartQtyById[product.id] ?: 0
                val outOfStock = product.stock <= 0L
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (inCartQty > 0) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(enabled = !outOfStock) { onAddToCart(product) }
                        .testTag("product_card_${product.sku}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = product.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            if (inCartQty > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "${inCartQty}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.height(40.dp)
                        )

                        Text(
                            text = formatIdr(product.price),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        val stockBg = when {
                            outOfStock -> DangerSoft
                            product.lowStock -> AmberSoft
                            else -> MintSoft
                        }
                        val stockFg = when {
                            outOfStock -> DangerRed
                            product.lowStock -> Color(0xFF92400E)
                            else -> EmeraldDeep
                        }
                        Surface(color = stockBg, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = when {
                                    outOfStock -> strings.outOfStockBadge
                                    product.lowStock -> "${strings.lowStockBadge}: ${product.stock} ${product.unit}"
                                    else -> "${strings.stockLabel}: ${product.stock} ${product.unit}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = stockFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutPanelContent(
    strings: AppStrings,
    workspace: WorkspaceConfig?,
    activeBranch: Branch?,
    cartItems: List<CartItem>,
    isBusy: Boolean,
    hasPrinterConfigured: Boolean,
    onAddToCart: (Product) -> Unit,
    onDecrementCart: (String) -> Unit,
    onClearCart: () -> Unit,
    onConfirmCheckout: (
        paymentMethod: PaymentMethod,
        discount: Double,
        paidAmount: Double,
        customerName: String,
        customerPhone: String,
        whatsappSent: Boolean,
        autoPrintBluetooth: Boolean
    ) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(PaymentMethod.QRIS) }
    var discountText by remember { mutableStateOf("") }
    var cashPaidText by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var sendWhatsapp by remember { mutableStateOf(true) }
    var autoPrint by remember { mutableStateOf(hasPrinterConfigured) }

    val subtotal = cartItems.sumOf { it.lineTotal }
    val discount = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)
    val total = (subtotal - discount).coerceAtLeast(0.0)
    val paidAmount = if (selectedMethod == PaymentMethod.CASH) {
        cashPaidText.toDoubleOrNull() ?: total
    } else {
        total
    }
    val change = (paidAmount - total).coerceAtLeast(0.0)

    val qrisPayload = remember(total, workspace, activeBranch) {
        if (total > 0) {
            QrisGenerator.buildQrisPayload(
                merchantId = workspace?.qrisMerchantId ?: "ID1020304050607",
                merchantName = workspace?.qrisMerchantName ?: "KASIRPRO",
                branchCity = activeBranch?.code ?: "JAKARTA",
                amountIdr = total,
                referenceId = "KP${System.currentTimeMillis() % 100000}"
            )
        } else ""
    }
    val qrisBitmap = remember(qrisPayload) {
        if (qrisPayload.isNotBlank()) QrisGenerator.renderQrBitmap(qrisPayload, 440) else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.cartTitle, style = MaterialTheme.typography.titleLarge)
            if (cartItems.isNotEmpty()) {
                IconButton(onClick = onClearCart) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear cart", tint = DangerRed)
                }
            }
        }

        if (cartItems.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(strings.cartEmptyTitle, style = MaterialTheme.typography.titleSmall)
                    Text(
                        strings.cartEmptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Cart items
            cartItems.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatIdr(item.product.price)} x ${item.quantity} = ${formatIdr(item.lineTotal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onDecrementCart(item.product.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }
                        Text(
                            text = item.quantity.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(onClick = { onAddToCart(item.product) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = discountText,
                onValueChange = { discountText = it.filter { ch -> ch.isDigit() } },
                label = { Text(strings.discountLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Payment Methods
            Text(strings.paymentMethodTitle, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentChip(PaymentMethod.QRIS, strings.payQris, Icons.Default.QrCode2, selectedMethod == PaymentMethod.QRIS, Modifier.weight(1f)) {
                    selectedMethod = PaymentMethod.QRIS
                }
                PaymentChip(PaymentMethod.CASH, strings.payCash, Icons.Default.Payments, selectedMethod == PaymentMethod.CASH, Modifier.weight(1f)) {
                    selectedMethod = PaymentMethod.CASH
                }
                PaymentChip(PaymentMethod.CARD, strings.payCard, Icons.Default.CreditCard, selectedMethod == PaymentMethod.CARD, Modifier.weight(1f)) {
                    selectedMethod = PaymentMethod.CARD
                }
                PaymentChip(PaymentMethod.EWALLET, strings.payEwallet, Icons.Default.Wallet, selectedMethod == PaymentMethod.EWALLET, Modifier.weight(1f)) {
                    selectedMethod = PaymentMethod.EWALLET
                }
            }

            // QRIS Live Dynamic QR Card
            if (selectedMethod == PaymentMethod.QRIS && qrisBitmap != null) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "QRIS • ${workspace?.qrisMerchantName ?: "KASIRPRO"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldDeep
                        )
                        Spacer(Modifier.height(8.dp))
                        Image(
                            bitmap = qrisBitmap.asImageBitmap(),
                            contentDescription = "QRIS Dynamic QR Code",
                            modifier = Modifier.size(180.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatIdr(total),
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldDeep,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.qrisScanInstruction,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4B6360)
                        )
                    }
                }
            }

            // Cash Nominal Controls
            if (selectedMethod == PaymentMethod.CASH) {
                OutlinedTextField(
                    value = cashPaidText,
                    onValueChange = { cashPaidText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(strings.cashReceivedLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cashPaidText = total.toLong().toString() }) {
                        Text(strings.exactCashButton)
                    }
                    OutlinedButton(onClick = { cashPaidText = "50000" }) { Text("50rb") }
                    OutlinedButton(onClick = { cashPaidText = "100000" }) { Text("100rb") }
                    OutlinedButton(onClick = { cashPaidText = "200000" }) { Text("200rb") }
                }
                Text(
                    text = "${strings.changeLabel}: ${formatIdr(change)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Customer & WhatsApp Auto-Notify
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text(strings.customerNameOptional) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text(strings.customerWhatsappOptional) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().testTag("checkout_whatsapp_phone")
            )

            if (customerPhone.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.autoSendWhatsappToggle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(checked = sendWhatsapp, onCheckedChange = { sendWhatsapp = it })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.autoPrintBluetoothToggle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = autoPrint, onCheckedChange = { autoPrint = it })
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(strings.totalLabel, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = formatIdr(total),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("E2EE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Button(
                onClick = {
                    onConfirmCheckout(
                        selectedMethod,
                        discount,
                        paidAmount,
                        customerName,
                        customerPhone,
                        sendWhatsapp && customerPhone.isNotBlank(),
                        autoPrint
                    )
                },
                enabled = !isBusy && (selectedMethod != PaymentMethod.CASH || paidAmount >= total),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_payment_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(strings.confirmPaymentButton, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PaymentChip(
    method: PaymentMethod,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("payment_method_${method.name}")
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
