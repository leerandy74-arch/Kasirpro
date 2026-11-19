package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.StaffLoginScreen
import com.example.auth.authStateFlow
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOut
import com.example.data.PreferencesManager
import com.example.data.local.LocalPosRepository
import com.example.integrations.BluetoothPrinterManager
import com.example.integrations.WhatsAppNotifier
import com.example.localization.stringsFor
import com.example.model.UiState
import com.example.ui.components.ReceiptSuccessDialog
import com.example.ui.components.RoleSwitchDialog
import com.example.ui.components.TopWorkspaceHeader
import com.example.ui.screens.AdminAndCentralScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.PosCashierScreen
import com.example.ui.screens.TransactionsAndReportsScreen
import com.example.ui.theme.KasirProTheme
import com.example.viewmodel.PosViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KasirProTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val viewModel: PosViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                PosViewModel(
                    repository = LocalPosRepository(app),
                    preferencesManager = PreferencesManager(app),
                    printerManager = BluetoothPrinterManager(app)
                )
            }
        }
    )

    val prefs by viewModel.localPreferences.collectAsStateWithLifecycle()
    val strings = remember(prefs.language) { stringsFor(prefs.language) }
    val activeStaff by viewModel.activeStaff.collectAsStateWithLifecycle()
    val isBootstrapping by viewModel.isBootstrapping.collectAsStateWithLifecycle()
    val isLoggingIn by viewModel.isLoggingIn.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    if (activeStaff == null) {
        StaffLoginScreen(
            strings = strings,
            currentLanguage = prefs.language,
            isBootstrapping = isBootstrapping,
            isLoggingIn = isLoggingIn,
            loginError = loginError,
            onLanguageChange = viewModel::setLanguage,
            onLoginSubmit = viewModel::loginWithCredentials
        )
    } else {
        KasirProRoot(viewModel = viewModel)
    }
}

@Composable
fun KasirProRoot(viewModel: PosViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val printerManager = remember { BluetoothPrinterManager(context.applicationContext) }

    val auth = remember { Firebase.auth }
    val googleCloudUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val prefs by viewModel.localPreferences.collectAsStateWithLifecycle()
    val strings = remember(prefs.language) { stringsFor(prefs.language) }
    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val branchesState by viewModel.branchesState.collectAsStateWithLifecycle()
    val staffState by viewModel.staffState.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val cloudBackupsState by viewModel.cloudBackupsState.collectAsStateWithLifecycle()

    val activeStaff by viewModel.activeStaff.collectAsStateWithLifecycle()
    val activeBranch by viewModel.activeBranch.collectAsStateWithLifecycle()
    val e2eeKey by viewModel.e2eeKey.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val analytics by viewModel.monthlyAnalytics.collectAsStateWithLifecycle()
    val selectedMonthKey by viewModel.selectedMonthKey.collectAsStateWithLifecycle()
    val reportBranchFilter by viewModel.reportBranchFilter.collectAsStateWithLifecycle()
    val lastCompletedTx by viewModel.lastCompletedTx.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val bondedPrinters by viewModel.bondedPrinters.collectAsStateWithLifecycle()

    val workspace = (workspaceState as? UiState.Success)?.data
    val branches = (branchesState as? UiState.Success)?.data.orEmpty()
    val staffList = (staffState as? UiState.Success)?.data.orEmpty()
    val products = (productsState as? UiState.Success)?.data.orEmpty()
    val transactions = (transactionsState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()
    val cloudBackups = (cloudBackupsState as? UiState.Success)?.data.orEmpty()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showRoleDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        val msg = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    val navItems = listOf(
        Triple(strings.navCashier, Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale),
        Triple(strings.navReports, Icons.Filled.Assessment, Icons.Outlined.Assessment),
        Triple(strings.navInventory, Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
        Triple(strings.navAdmin, Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        navItems.forEachIndexed { idx, (label, filled, outlined) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) filled else outlined,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_tab_$idx")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        navItems.forEachIndexed { idx, (label, filled, outlined) ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) filled else outlined,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_rail_$idx")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    TopWorkspaceHeader(
                        strings = strings,
                        storeName = workspace?.storeName ?: strings.appTitle,
                        activeBranch = activeBranch,
                        branches = branches,
                        activeStaff = activeStaff,
                        e2eeUnlocked = e2eeKey != null,
                        onSelectBranch = viewModel::selectActiveBranch,
                        onOpenRoleSwitcher = { showRoleDialog = true },
                        onSignOut = { viewModel.logoutStaff() }
                    )

                    if (branchesState is UiState.Loading && branches.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        when (selectedTab) {
                            0 -> PosCashierScreen(
                                strings = strings,
                                workspace = workspace,
                                activeBranch = activeBranch,
                                products = products,
                                cartItems = cartItems,
                                isBusy = isBusy,
                                hasPrinterConfigured = prefs.printerMac.isNotBlank(),
                                onAddToCart = viewModel::addToCart,
                                onDecrementCart = viewModel::decrementCart,
                                onClearCart = viewModel::clearCart,
                                onConfirmCheckout = { method, discount, paid, custName, custPhone, sendWa, autoPrint ->
                                    viewModel.completeCheckout(
                                        paymentMethod = method,
                                        discount = discount,
                                        paidAmount = paid,
                                        customerName = custName,
                                        customerPhone = custPhone,
                                        whatsappSent = sendWa,
                                        autoPrintBluetooth = autoPrint
                                    ) { tx, decrypted ->
                                        if (sendWa && custPhone.isNotBlank()) {
                                            val msg = WhatsAppNotifier.buildOrderCompletedMessage(
                                                strings = strings,
                                                storeName = workspace?.storeName ?: strings.appTitle,
                                                tx = tx,
                                                payload = decrypted
                                            )
                                            WhatsAppNotifier.openWhatsAppChat(context, custPhone, msg)
                                        }
                                    }
                                }
                            )
                            1 -> TransactionsAndReportsScreen(
                                strings = strings,
                                storeName = workspace?.storeName ?: strings.appTitle,
                                activeStaff = activeStaff,
                                branches = branches,
                                products = products,
                                transactions = transactions,
                                analytics = analytics,
                                selectedMonthKey = selectedMonthKey,
                                reportBranchFilter = reportBranchFilter,
                                onSelectBranchFilter = viewModel::setReportBranchFilter,
                                decryptPayload = { tx -> viewModel.decryptPayload(tx) },
                                onPrintReceipt = viewModel::printTransactionReceipt,
                                onSendWhatsapp = { tx, decrypted ->
                                    val phone = decrypted?.customerPhone.orEmpty()
                                    if (phone.isNotBlank()) {
                                        val msg = WhatsAppNotifier.buildOrderCompletedMessage(
                                            strings = strings,
                                            storeName = workspace?.storeName ?: strings.appTitle,
                                            tx = tx,
                                            payload = decrypted
                                        )
                                        WhatsAppNotifier.openWhatsAppChat(context, phone, msg)
                                            .onSuccess { viewModel.markWhatsappSent(tx.id) }
                                    }
                                },
                                onVoidTransaction = viewModel::voidTransaction,
                                onShowMessage = viewModel::showMessage
                            )
                            2 -> InventoryScreen(
                                strings = strings,
                                activeBranch = activeBranch,
                                activeStaff = activeStaff,
                                products = products,
                                onAdjustStock = viewModel::adjustStock,
                                onSaveProduct = viewModel::saveProduct
                            )
                            else -> AdminAndCentralScreen(
                                strings = strings,
                                currentLanguage = prefs.language,
                                workspace = workspace,
                                activeStaff = activeStaff,
                                branches = branches,
                                branchMetrics = analytics.branchMetrics,
                                staffList = staffList,
                                auditLogs = auditLogs,
                                cloudBackups = cloudBackups,
                                latestTx = transactions.firstOrNull(),
                                e2eeKey = e2eeKey,
                                printerMac = prefs.printerMac,
                                printerName = prefs.printerName,
                                bondedPrinters = bondedPrinters,
                                googleCloudEmail = googleCloudUser?.email ?: googleCloudUser?.displayName,
                                isBusy = isBusy,
                                onAuthenticateAdmin = viewModel::authenticateAdminGate,
                                onCreateBranch = viewModel::createBranch,
                                onCreateStaff = viewModel::createStaff,
                                onChangeStaffPassword = viewModel::changeStaffPassword,
                                onToggleStaffActive = viewModel::toggleStaffActive,
                                onUnlockVault = viewModel::unlockE2eeVault,
                                onLockVault = viewModel::lockE2eeVault,
                                onTriggerBackup = viewModel::triggerManualCloudBackup,
                                onRestoreBackup = viewModel::restoreBackup,
                                onConnectGoogleCloud = {
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            viewModel.syncWithCloudNow()
                                        },
                                        onAuthError = { msg -> viewModel.showMessage(msg) },
                                        scope = scope
                                    )
                                },
                                onDisconnectGoogleCloud = {
                                    signOut(credentialManager, scope) {
                                        viewModel.showMessage("Sinkronisasi Cloud Google diputus. Data lokal tetap aman.")
                                    }
                                },
                                onSyncCloudNow = viewModel::syncWithCloudNow,
                                onUpdateSettings = viewModel::updateSettings,
                                onRefreshPrinters = viewModel::refreshBondedPrinters,
                                onSelectPrinter = viewModel::selectPrinter,
                                onTestPrint = {
                                    val sample = transactions.firstOrNull()
                                    if (sample != null) {
                                        viewModel.printTransactionReceipt(sample)
                                    } else {
                                        viewModel.showMessage("Buat 1 transaksi terlebih dahulu untuk tes cetak.")
                                    }
                                },
                                onLanguageChange = viewModel::setLanguage,
                                onShowMessage = viewModel::showMessage
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRoleDialog) {
        RoleSwitchDialog(
            strings = strings,
            adminOnly = false,
            onDismiss = { showRoleDialog = false },
            onAuthenticate = { u, p -> viewModel.authenticateStaff(u, p) }
        )
    }

    lastCompletedTx?.let { tx ->
        val decrypted = remember(tx.id) { viewModel.decryptPayload(tx) }
        val preview = remember(tx.id) {
            printerManager.formatReceiptPreviewText(
                storeName = workspace?.storeName ?: strings.appTitle,
                tx = tx,
                payload = decrypted
            )
        }
        ReceiptSuccessDialog(
            strings = strings,
            tx = tx,
            receiptPreview = preview,
            customerPhone = decrypted?.customerPhone.orEmpty(),
            onPrintBluetooth = { viewModel.printTransactionReceipt(tx) },
            onSendWhatsapp = {
                val phone = decrypted?.customerPhone.orEmpty()
                if (phone.isNotBlank()) {
                    val msg = WhatsAppNotifier.buildOrderCompletedMessage(
                        strings = strings,
                        storeName = workspace?.storeName ?: strings.appTitle,
                        tx = tx,
                        payload = decrypted
                    )
                    WhatsAppNotifier.openWhatsAppChat(context, phone, msg)
                        .onSuccess { viewModel.markWhatsappSent(tx.id) }
                }
            },
            onDismiss = { viewModel.clearLastCompletedTx() }
        )
    }
}
