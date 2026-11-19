package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.integrations.ReportExporter
import com.example.localization.AppStrings
import com.example.model.Branch
import com.example.model.DecryptedTxPayload
import com.example.model.PaymentMethod
import com.example.model.PosTransaction
import com.example.model.Product
import com.example.model.StaffAccount
import com.example.model.StaffRole
import com.example.model.TransactionStatus
import com.example.ui.components.formatIdr
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerSoft
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MintSoft
import com.example.viewmodel.DailyChartPoint
import com.example.viewmodel.MonthlyAnalytics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsAndReportsScreen(
    strings: AppStrings,
    storeName: String,
    activeStaff: StaffAccount?,
    branches: List<Branch>,
    products: List<Product>,
    transactions: List<PosTransaction>,
    analytics: MonthlyAnalytics,
    selectedMonthKey: String,
    reportBranchFilter: String?,
    onSelectBranchFilter: (String?) -> Unit,
    decryptPayload: (PosTransaction) -> DecryptedTxPayload?,
    onPrintReceipt: (PosTransaction) -> Unit,
    onSendWhatsapp: (PosTransaction, DecryptedTxPayload?) -> Unit,
    onVoidTransaction: (PosTransaction) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val isCentralAdmin = activeStaff?.staffRole == StaffRole.CENTRAL_ADMIN
    val isAnyAdmin = activeStaff?.staffRole == StaffRole.CENTRAL_ADMIN || activeStaff?.staffRole == StaffRole.BRANCH_ADMIN

    val visibleTransactions = remember(transactions, reportBranchFilter, activeStaff, selectedMonthKey) {
        transactions.filter { tx ->
            val branchMatch = when {
                !isCentralAdmin && activeStaff != null -> tx.branchId == activeStaff.branchId
                reportBranchFilter != null -> tx.branchId == reportBranchFilter
                else -> true
            }
            val monthMatch = tx.monthKey == selectedMonthKey || tx.monthKey.isBlank()
            branchMatch && monthMatch
        }
    }

    val branchFilterLabel = remember(reportBranchFilter, branches, isCentralAdmin, activeStaff) {
        when {
            !isCentralAdmin && activeStaff != null -> branches.firstOrNull { it.id == activeStaff.branchId }?.name ?: activeStaff.branchId
            reportBranchFilter == null -> strings.allBranchesLabel
            else -> branches.firstOrNull { it.id == reportBranchFilter }?.name ?: reportBranchFilter
        }
    }

    // SAF Document Launchers for downloading PDF and Excel (.xlsx) directly to device storage
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writePdfReport(
                        outputStream = out,
                        storeName = storeName,
                        periodLabel = selectedMonthKey,
                        branchFilterLabel = branchFilterLabel,
                        transactions = visibleTransactions,
                        products = products
                    )
                }
                onShowMessage("${strings.exportSuccessMsg} (PDF)")
            } catch (e: Exception) {
                onShowMessage("PDF Error: ${e.localizedMessage}")
            }
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writeExcelXlsxReport(
                        outputStream = out,
                        storeName = storeName,
                        periodLabel = selectedMonthKey,
                        branches = branches,
                        transactions = visibleTransactions,
                        products = products
                    )
                }
                onShowMessage("${strings.exportSuccessMsg} (Excel .xlsx)")
            } catch (e: Exception) {
                onShowMessage("Excel Error: ${e.localizedMessage}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(strings.analyticsTab) },
                modifier = Modifier.testTag("reports_tab_analytics")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("${strings.realtimeFeedTab} (${visibleTransactions.size})") },
                modifier = Modifier.testTag("reports_tab_feed")
            )
        }

        // Branch Filter Row + Export Action Bar
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isCentralAdmin) {
                item {
                    FilterChip(
                        selected = reportBranchFilter == null,
                        onClick = { onSelectBranchFilter(null) },
                        label = { Text(strings.allBranchesLabel) }
                    )
                }
                items(branches) { b ->
                    FilterChip(
                        selected = reportBranchFilter == b.id,
                        onClick = { onSelectBranchFilter(b.id) },
                        label = { Text(b.code) }
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        pdfLauncher.launch("KasirPro_Report_$selectedMonthKey.pdf")
                    },
                    modifier = Modifier.testTag("export_pdf_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = DangerRed)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportPdfButton)
                }
            }
            item {
                Button(
                    onClick = {
                        excelLauncher.launch("KasirPro_Report_$selectedMonthKey.xlsx")
                    },
                    modifier = Modifier.testTag("export_excel_button")
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.exportExcelButton)
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        try {
                            val file = ReportExporter.createCacheReportFile(context, "KasirPro_$selectedMonthKey.xlsx")
                            file.outputStream().use { out ->
                                ReportExporter.writeExcelXlsxReport(
                                    outputStream = out,
                                    storeName = storeName,
                                    periodLabel = selectedMonthKey,
                                    branches = branches,
                                    transactions = visibleTransactions,
                                    products = products
                                )
                            }
                            ReportExporter.shareFile(
                                context,
                                file,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                strings.exportExcelButton
                            )
                        } catch (e: Exception) {
                            onShowMessage(e.localizedMessage ?: "Share failed")
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Excel", modifier = Modifier.size(16.dp))
                }
            }
        }

        if (selectedTab == 0) {
            AnalyticsDashboardBody(
                strings = strings,
                analytics = analytics,
                isCentralAdmin = isCentralAdmin
            )
        } else {
            RealtimeTransactionFeedBody(
                strings = strings,
                transactions = visibleTransactions,
                isAnyAdmin = isAnyAdmin,
                decryptPayload = decryptPayload,
                onPrintReceipt = onPrintReceipt,
                onSendWhatsapp = onSendWhatsapp,
                onVoidTransaction = onVoidTransaction
            )
        }
    }
}

@Composable
private fun AnalyticsDashboardBody(
    strings: AppStrings,
    analytics: MonthlyAnalytics,
    isCentralAdmin: Boolean
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 2x2 KPI Bento Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = strings.monthlyRevenueTitle,
                    value = formatIdr(analytics.totalRevenue),
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = strings.transactionsCountTitle,
                    value = analytics.completedCount.toString(),
                    accentColor = InfoBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = strings.avgBasketTitle,
                    value = formatIdr(analytics.avgBasket),
                    accentColor = AmberGold,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = strings.estProfitTitle,
                    value = formatIdr(analytics.estimatedProfit),
                    accentColor = EmeraldBright,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Interactive Monthly Sales Chart
        item {
            MonthlySalesChartCard(
                title = "${strings.monthlyChartTitle} (${analytics.monthKey})",
                points = analytics.dailyPoints
            )
        }

        // Payment Method Composition
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.paymentBreakdownTitle, style = MaterialTheme.typography.titleMedium)
                    val total = analytics.totalRevenue.coerceAtLeast(1.0)
                    PaymentMethod.entries.forEach { method ->
                        val amount = analytics.paymentTotals[method] ?: 0.0
                        val ratio = (amount / total).toFloat().coerceIn(0f, 1f)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(method.name, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${formatIdr(amount)} (${(ratio * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = when (method) {
                                    PaymentMethod.QRIS -> EmeraldBright
                                    PaymentMethod.CASH -> AmberGold
                                    PaymentMethod.CARD -> InfoBlue
                                    PaymentMethod.EWALLET -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                        }
                    }
                }
            }
        }

        // All-Branch Performance (Visible especially for Central Admin)
        if (analytics.branchMetrics.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.branchPerformanceTitle, style = MaterialTheme.typography.titleMedium)
                            if (isCentralAdmin) {
                                Surface(color = MintSoft, shape = RoundedCornerShape(50)) {
                                    Text(
                                        "PUSAT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldDeep,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        analytics.branchMetrics.forEach { bm ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bm.branch.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${bm.txCount} tx • ${bm.lowStockCount} ${strings.lowStockBadge}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    formatIdr(bm.revenue),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        }
                    }
                }
            }
        }

        // Top Selling Products (Decrypted via E2EE)
        if (analytics.topProducts.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.topProductsTitle, style = MaterialTheme.typography.titleMedium)
                        analytics.topProducts.forEachIndexed { idx, prod ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${idx + 1}. ${prod.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${prod.quantitySold}x • ${formatIdr(prod.revenue)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .background(accentColor, RoundedCornerShape(50))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MonthlySalesChartCard(
    title: String,
    points: List<DailyChartPoint>
) {
    var selectedIdx by remember(points) { mutableIntStateOf((points.size - 1).coerceAtLeast(0)) }
    val barColor = MaterialTheme.colorScheme.primary
    val highlightColor = AmberGold
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (points.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Belum ada data penjualan pada bulan ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val activePoint = points.getOrNull(selectedIdx)
                if (activePoint != null) {
                    Text(
                        text = "Tgl ${activePoint.label}: ${formatIdr(activePoint.revenue)} (${activePoint.txCount} tx)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                val maxRev = (points.maxOfOrNull { it.revenue } ?: 1.0).coerceAtLeast(1.0)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    // Horizontal grid lines
                    for (step in 0..3) {
                        val y = h * (step / 3f)
                        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                    }
                    val slotWidth = w / points.size.coerceAtLeast(1)
                    val barWidth = (slotWidth * 0.56f).coerceIn(10.dp.toPx(), 34.dp.toPx())
                    val linePath = Path()

                    points.forEachIndexed { idx, pt ->
                        val ratio = (pt.revenue / maxRev).toFloat().coerceIn(0.05f, 1f)
                        val barHeight = (h - 14.dp.toPx()) * ratio
                        val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                        val top = h - barHeight
                        drawRoundRect(
                            color = if (idx == selectedIdx) highlightColor else barColor.copy(alpha = 0.78f),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        val cx = left + barWidth / 2f
                        if (idx == 0) linePath.moveTo(cx, top) else linePath.lineTo(cx, top)
                    }
                    if (points.size > 1) {
                        drawPath(
                            path = linePath,
                            color = EmeraldDeep,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    points.forEachIndexed { idx, pt ->
                        Surface(
                            color = if (idx == selectedIdx) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { selectedIdx = idx }
                        ) {
                            Text(
                                text = pt.label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealtimeTransactionFeedBody(
    strings: AppStrings,
    transactions: List<PosTransaction>,
    isAnyAdmin: Boolean,
    decryptPayload: (PosTransaction) -> DecryptedTxPayload?,
    onPrintReceipt: (PosTransaction) -> Unit,
    onSendWhatsapp: (PosTransaction, DecryptedTxPayload?) -> Unit,
    onVoidTransaction: (PosTransaction) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(strings.noTransactionsYet, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(transactions, key = { it.id }) { tx ->
            var expanded by remember { mutableStateOf(false) }
            val decrypted = remember(tx.id, tx.encryptedPayload) { decryptPayload(tx) }
            val isVoided = tx.statusEnum == TransactionStatus.VOIDED

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("tx_card_${tx.receiptNumber}")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.receiptNumber,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "${tx.branchName} • ${tx.cashierName} • ${dateFmt.format(tx.createdAt?.toDate() ?: Date())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatIdr(tx.totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isVoided) DangerRed else MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                tx.paymentMethod,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = if (isVoided) DangerSoft else MintSoft,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isVoided) strings.voidedBadge else strings.completedBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isVoided) DangerRed else EmeraldDeep,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (tx.e2eeVerified) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("E2EE", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (expanded) {
                        HorizontalDivider()
                        if (decrypted != null) {
                            if (decrypted.customerName.isNotBlank() || decrypted.customerPhone.isNotBlank()) {
                                Text(
                                    "Pelanggan: ${decrypted.customerName} (${decrypted.customerPhone})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            decrypted.items.forEach { line ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${line.quantity}x ${line.name}", style = MaterialTheme.typography.bodySmall)
                                    Text(formatIdr(line.lineTotal), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text(
                                "🔒 Ciphertext: ${tx.encryptedPayload.take(44)}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onPrintReceipt(tx) }) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Struk")
                            }
                            if (!decrypted?.customerPhone.isNullOrBlank()) {
                                OutlinedButton(onClick = { onSendWhatsapp(tx, decrypted) }) {
                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("WA")
                                }
                            }
                            if (isAnyAdmin && !isVoided) {
                                OutlinedButton(
                                    onClick = { onVoidTransaction(tx) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.voidTransactionButton)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
