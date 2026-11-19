package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.data.local.LocalPosRepository
import com.example.integrations.BluetoothPrinterDevice
import com.example.integrations.BluetoothPrinterManager
import com.example.localization.AppLanguage
import com.example.model.AuditLog
import com.example.model.Branch
import com.example.model.CartItem
import com.example.model.CloudBackupRecord
import com.example.model.DecryptedTxPayload
import com.example.model.PaymentMethod
import com.example.model.PosTransaction
import com.example.model.Product
import com.example.model.StaffAccount
import com.example.model.StaffRole
import com.example.model.TransactionStatus
import com.example.model.UiState
import com.example.model.WorkspaceConfig
import com.example.security.CryptoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey

data class DailyChartPoint(
    val label: String,
    val revenue: Double,
    val txCount: Int
)

data class BranchMetric(
    val branch: Branch,
    val revenue: Double,
    val txCount: Int,
    val lowStockCount: Int
)

data class TopProductMetric(
    val name: String,
    val quantitySold: Int,
    val revenue: Double
)

data class MonthlyAnalytics(
    val monthKey: String,
    val totalRevenue: Double,
    val completedCount: Int,
    val voidedCount: Int,
    val avgBasket: Double,
    val estimatedProfit: Double,
    val dailyPoints: List<DailyChartPoint>,
    val paymentTotals: Map<PaymentMethod, Double>,
    val branchMetrics: List<BranchMetric>,
    val topProducts: List<TopProductMetric>
)

class PosViewModel(
    private val repository: LocalPosRepository,
    private val preferencesManager: PreferencesManager,
    private val printerManager: BluetoothPrinterManager
) : ViewModel() {

    companion object {
        private const val TAG = "PosViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
        const val DEFAULT_E2EE_PASSPHRASE = "kasirpro-e2ee-default"
    }

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing $label", error)
                emit(UiState.Error(error.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val workspaceState: StateFlow<UiState<WorkspaceConfig?>> =
        repository.observeWorkspace().toUiState("workspace")

    val branchesState: StateFlow<UiState<List<Branch>>> =
        repository.observeBranches().toUiState("branches")

    val staffState: StateFlow<UiState<List<StaffAccount>>> =
        repository.observeStaff().toUiState("staff")

    val productsState: StateFlow<UiState<List<Product>>> =
        repository.observeProducts().toUiState("products")

    val transactionsState: StateFlow<UiState<List<PosTransaction>>> =
        repository.observeTransactions().toUiState("transactions")

    val auditLogsState: StateFlow<UiState<List<AuditLog>>> =
        repository.observeAuditLogs().toUiState("auditLogs")

    val cloudBackupsState: StateFlow<UiState<List<CloudBackupRecord>>> =
        repository.observeBackups().toUiState("cloudBackups")

    val localPreferences = preferencesManager.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = com.example.data.LocalAppPreferences()
        )

    private val _isBootstrapping = MutableStateFlow(true)
    val isBootstrapping: StateFlow<Boolean> = _isBootstrapping.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _sessionStaffOverride = MutableStateFlow<String?>(null)

    val activeStaff: StateFlow<StaffAccount?> = combine(
        staffState,
        localPreferences,
        _sessionStaffOverride
    ) { state, prefs, overrideId ->
        val list = (state as? UiState.Success)?.data.orEmpty()
        val targetId = overrideId ?: prefs.loggedInStaffId.takeIf { it.isNotBlank() }
        if (targetId != null) {
            list.firstOrNull { it.id == targetId && it.active }
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeBranch: StateFlow<Branch?> = combine(
        branchesState,
        localPreferences,
        activeStaff
    ) { bState, prefs, staff ->
        val branches = (bState as? UiState.Success)?.data.orEmpty()
        val chosen = branches.firstOrNull { it.id == prefs.activeBranchId }
        val staffBranch = staff?.let { s -> branches.firstOrNull { it.id == s.branchId } }
        if (staff?.staffRole == StaffRole.CENTRAL_ADMIN && chosen != null) {
            chosen
        } else {
            staffBranch ?: chosen ?: branches.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _e2eeKey = MutableStateFlow<SecretKey?>(null)
    val e2eeKey: StateFlow<SecretKey?> = _e2eeKey.asStateFlow()

    private val _cart = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cartItems: StateFlow<List<CartItem>> = combine(productsState, _cart) { pState, cartMap ->
        val products = (pState as? UiState.Success)?.data.orEmpty().associateBy { it.id }
        cartMap.mapNotNull { (pid, qty) ->
            val p = products[pid] ?: return@mapNotNull null
            if (qty <= 0) null else CartItem(p, qty.coerceAtMost(p.stock.toInt().coerceAtLeast(0)))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _selectedMonthKey = MutableStateFlow(SimpleDateFormat("yyyy-MM", Locale.US).format(Date()))
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _reportBranchFilter = MutableStateFlow<String?>(null)
    val reportBranchFilter: StateFlow<String?> = _reportBranchFilter.asStateFlow()

    private val _lastCompletedTx = MutableStateFlow<PosTransaction?>(null)
    val lastCompletedTx: StateFlow<PosTransaction?> = _lastCompletedTx.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _bondedPrinters = MutableStateFlow<List<BluetoothPrinterDevice>>(emptyList())
    val bondedPrinters: StateFlow<List<BluetoothPrinterDevice>> = _bondedPrinters.asStateFlow()

    val monthlyAnalytics: StateFlow<MonthlyAnalytics> = combine(
        transactionsState,
        branchesState,
        productsState,
        selectedMonthKey,
        reportBranchFilter,
        e2eeKey
    ) { arr ->
        val txList = (arr[0] as? UiState.Success<List<PosTransaction>>)?.data.orEmpty()
        val branches = (arr[1] as? UiState.Success<List<Branch>>)?.data.orEmpty()
        val products = (arr[2] as? UiState.Success<List<Product>>)?.data.orEmpty()
        val monthKey = arr[3] as String
        val branchFilter = arr[4] as String?
        val key = arr[5] as SecretKey?

        val monthTxs = txList.filter { it.monthKey == monthKey || it.monthKey.isBlank() }
        val filteredTxs = if (branchFilter == null) monthTxs else monthTxs.filter { it.branchId == branchFilter }
        val completed = filteredTxs.filter { it.statusEnum == TransactionStatus.COMPLETED }
        val voided = filteredTxs.filter { it.statusEnum == TransactionStatus.VOIDED }

        val totalRevenue = completed.sumOf { it.totalAmount }
        val avgBasket = if (completed.isNotEmpty()) totalRevenue / completed.size else 0.0

        var totalCost = 0.0
        val productAgg = mutableMapOf<String, Pair<Int, Double>>()
        completed.forEach { tx ->
            val payload = if (key != null) decryptPayload(tx, key) else null
            if (payload != null && payload.items.isNotEmpty()) {
                payload.items.forEach { item ->
                    totalCost += item.costPrice * item.quantity
                    val prev = productAgg[item.name] ?: (0 to 0.0)
                    productAgg[item.name] = (prev.first + item.quantity) to (prev.second + item.lineTotal)
                }
            } else {
                totalCost += tx.totalAmount * 0.48
            }
        }
        val estProfit = (totalRevenue - totalCost).coerceAtLeast(0.0)

        val dailyGroups = completed.groupBy { it.dayKey.takeLast(2).ifBlank { "--" } }
            .toSortedMap()
            .map { (day, list) ->
                DailyChartPoint(
                    label = day,
                    revenue = list.sumOf { it.totalAmount },
                    txCount = list.size
                )
            }

        val paymentTotals = PaymentMethod.entries.associateWith { method ->
            completed.filter { it.paymentEnum == method }.sumOf { it.totalAmount }
        }

        val branchMetrics = branches.map { b ->
            val bCompleted = monthTxs.filter { it.branchId == b.id && it.statusEnum == TransactionStatus.COMPLETED }
            val bLowStock = products.count { it.branchId == b.id && it.lowStock }
            BranchMetric(
                branch = b,
                revenue = bCompleted.sumOf { it.totalAmount },
                txCount = bCompleted.size,
                lowStockCount = bLowStock
            )
        }.sortedByDescending { it.revenue }

        val topProducts = productAgg.entries
            .map { (name, pair) -> TopProductMetric(name, pair.first, pair.second) }
            .sortedByDescending { it.revenue }
            .take(5)

        MonthlyAnalytics(
            monthKey = monthKey,
            totalRevenue = totalRevenue,
            completedCount = completed.size,
            voidedCount = voided.size,
            avgBasket = avgBasket,
            estimatedProfit = estProfit,
            dailyPoints = dailyGroups,
            paymentTotals = paymentTotals,
            branchMetrics = branchMetrics,
            topProducts = topProducts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MonthlyAnalytics(
            monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date()),
            totalRevenue = 0.0,
            completedCount = 0,
            voidedCount = 0,
            avgBasket = 0.0,
            estimatedProfit = 0.0,
            dailyPoints = emptyList(),
            paymentTotals = emptyMap(),
            branchMetrics = emptyList(),
            topProducts = emptyList()
        )
    )

    init {
        initializeWorkspaceAndVault()
        startAutoBackupWatcher()
    }

    private fun initializeWorkspaceAndVault() {
        viewModelScope.launch {
            _isBootstrapping.value = true
            val prefs = localPreferences.first()
            val passphrase = prefs.cachedVaultPassphrase ?: DEFAULT_E2EE_PASSPHRASE
            repository.bootstrapIfNeeded(passphrase)
                .onFailure { _uiMessage.value = it.localizedMessage }
            _isBootstrapping.value = false

            workspaceState.collect { state ->
                val ws = (state as? UiState.Success)?.data ?: return@collect
                if (_e2eeKey.value == null && ws.e2eeSalt.isNotBlank()) {
                    val candidatePass = localPreferences.value.cachedVaultPassphrase ?: DEFAULT_E2EE_PASSPHRASE
                    val derived = CryptoManager.deriveE2eeKey(candidatePass, ws.e2eeSalt)
                    if (CryptoManager.computeKeyVerifier(derived) == ws.e2eeKeyCheck) {
                        _e2eeKey.value = derived
                    }
                }
            }
        }
    }

    private fun startAutoBackupWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val ws = (workspaceState.value as? UiState.Success)?.data ?: continue
                val key = _e2eeKey.value ?: continue
                val branches = (branchesState.value as? UiState.Success)?.data.orEmpty()
                val products = (productsState.value as? UiState.Success)?.data.orEmpty()
                val txs = (transactionsState.value as? UiState.Success)?.data.orEmpty()
                if (branches.isEmpty()) continue
                val intervalMs = ws.autoBackupIntervalHours.coerceAtLeast(1L) * 3_600_000L
                val now = System.currentTimeMillis()
                if (ws.lastBackupEpochMs == 0L || (now - ws.lastBackupEpochMs) >= intervalMs) {
                    repository.createBackup(
                        backupType = "AUTO",
                        branches = branches,
                        products = products,
                        transactions = txs,
                        actorName = activeStaff.value?.fullName ?: "Auto Backup",
                        e2eeKey = key
                    )
                }
            }
        }
    }

    fun loginWithCredentials(username: String, password: String) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value = null
            val res = repository.authenticateStaff(username, password)
            res.onSuccess { staff ->
                _sessionStaffOverride.value = staff.id
                preferencesManager.setLoggedInStaffId(staff.id)
                _cart.value = emptyMap()
                _reportBranchFilter.value = if (staff.staffRole == StaffRole.CENTRAL_ADMIN) null else staff.branchId
            }.onFailure { err ->
                _loginError.value = err.localizedMessage ?: "Username atau password salah."
            }
            _isLoggingIn.value = false
        }
    }

    fun logoutStaff() {
        viewModelScope.launch {
            _sessionStaffOverride.value = null
            _cart.value = emptyMap()
            _loginError.value = null
            preferencesManager.setLoggedInStaffId(null)
        }
    }

    fun consumeMessage() {
        _uiMessage.value = null
    }

    fun showMessage(msg: String) {
        _uiMessage.value = msg
    }

    fun clearLastCompletedTx() {
        _lastCompletedTx.value = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesManager.setLanguage(language) }
    }

    fun selectActiveBranch(branchId: String) {
        viewModelScope.launch {
            preferencesManager.setActiveBranchId(branchId)
            _cart.value = emptyMap()
        }
    }

    fun setReportBranchFilter(branchId: String?) {
        _reportBranchFilter.value = branchId
    }

    fun setSelectedMonthKey(monthKey: String) {
        _selectedMonthKey.value = monthKey
    }

    fun authenticateStaff(username: String, password: String): Boolean {
        val list = (staffState.value as? UiState.Success)?.data.orEmpty()
        val target = list.firstOrNull {
            it.username.equals(username.trim(), ignoreCase = true) && it.active
        } ?: return false
        val ok = CryptoManager.verifyPassword(password, target.passwordHash, target.passwordSalt)
        if (ok) {
            _sessionStaffOverride.value = target.id
            viewModelScope.launch { preferencesManager.setLoggedInStaffId(target.id) }
            _cart.value = emptyMap()
            _reportBranchFilter.value = if (target.staffRole == StaffRole.CENTRAL_ADMIN) null else target.branchId
        }
        return ok
    }

    fun authenticateAdminGate(username: String, password: String): Boolean {
        val list = (staffState.value as? UiState.Success)?.data.orEmpty()
        val target = list.firstOrNull {
            it.username.equals(username.trim(), ignoreCase = true) &&
                it.active &&
                (it.staffRole == StaffRole.BRANCH_ADMIN || it.staffRole == StaffRole.CENTRAL_ADMIN)
        } ?: return false
        val ok = CryptoManager.verifyPassword(password, target.passwordHash, target.passwordSalt)
        if (ok) {
            _sessionStaffOverride.value = target.id
            viewModelScope.launch { preferencesManager.setLoggedInStaffId(target.id) }
            _reportBranchFilter.value = if (target.staffRole == StaffRole.CENTRAL_ADMIN) null else target.branchId
        }
        return ok
    }

    fun addToCart(product: Product) {
        if (product.stock <= 0L) return
        val current = _cart.value.toMutableMap()
        val nextQty = (current[product.id] ?: 0) + 1
        if (nextQty <= product.stock) {
            current[product.id] = nextQty
            _cart.value = current
        } else {
            _uiMessage.value = "Stok ${product.name} maksimal ${product.stock} ${product.unit}"
        }
    }

    fun decrementCart(productId: String) {
        val current = _cart.value.toMutableMap()
        val qty = current[productId] ?: return
        if (qty <= 1) current.remove(productId) else current[productId] = qty - 1
        _cart.value = current
    }

    fun clearCart() {
        _cart.value = emptyMap()
    }

    fun decryptPayload(tx: PosTransaction, key: SecretKey? = _e2eeKey.value): DecryptedTxPayload? {
        if (key == null) return null
        val json = CryptoManager.decrypt(tx.encryptedPayload, tx.e2eeIv, key) ?: return null
        return DecryptedTxPayload.fromJsonString(json)
    }

    fun unlockE2eeVault(passphrase: String): Boolean {
        val ws = (workspaceState.value as? UiState.Success)?.data ?: return false
        val derived = CryptoManager.deriveE2eeKey(passphrase, ws.e2eeSalt)
        val ok = CryptoManager.computeKeyVerifier(derived) == ws.e2eeKeyCheck
        if (ok) {
            _e2eeKey.value = derived
            viewModelScope.launch { preferencesManager.saveVaultPassphrase(passphrase) }
        }
        return ok
    }

    fun lockE2eeVault() {
        _e2eeKey.value = null
        viewModelScope.launch { preferencesManager.clearVaultPassphrase() }
    }

    fun completeCheckout(
        paymentMethod: PaymentMethod,
        discount: Double,
        paidAmount: Double,
        customerName: String,
        customerPhone: String,
        whatsappSent: Boolean,
        autoPrintBluetooth: Boolean,
        onPrintedOrResult: (PosTransaction, DecryptedTxPayload?) -> Unit
    ) {
        val branch = activeBranch.value ?: return
        val cashier = activeStaff.value ?: return
        val items = cartItems.value
        val key = _e2eeKey.value
        if (items.isEmpty()) return
        if (key == null) {
            _uiMessage.value = "Buka kunci Brankas E2EE terlebih dahulu di menu Admin."
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.completeCheckout(
                branch = branch,
                cashier = cashier,
                cartItems = items,
                paymentMethod = paymentMethod,
                discount = discount,
                paidAmount = paidAmount,
                customerName = customerName,
                customerPhone = customerPhone,
                whatsappSent = whatsappSent,
                e2eeKey = key
            )
            _isBusy.value = false
            res.onSuccess { tx ->
                _cart.value = emptyMap()
                _lastCompletedTx.value = tx
                val decrypted = decryptPayload(tx, key)
                val mac = localPreferences.value.printerMac
                if (autoPrintBluetooth && mac.isNotBlank()) {
                    val storeName = (workspaceState.value as? UiState.Success)?.data?.storeName ?: "KasirPro"
                    printerManager.printEscPosReceipt(mac, storeName, tx, decrypted)
                        .onFailure { err -> _uiMessage.value = "Bluetooth: ${err.localizedMessage}" }
                }
                onPrintedOrResult(tx, decrypted)
            }.onFailure { err ->
                _uiMessage.value = err.localizedMessage ?: "Checkout gagal"
            }
        }
    }

    fun markWhatsappSent(txId: String) {
        viewModelScope.launch { repository.markWhatsappSent(txId) }
    }

    fun voidTransaction(tx: PosTransaction) {
        val actor = activeStaff.value ?: return
        viewModelScope.launch {
            repository.voidTransaction(tx.id, actor, tx.branchId)
                .onSuccess { _uiMessage.value = "Transaksi ${tx.receiptNumber} dibatalkan." }
                .onFailure { _uiMessage.value = it.localizedMessage }
        }
    }

    fun adjustStock(product: Product, delta: Long) {
        val actor = activeStaff.value ?: return
        viewModelScope.launch {
            repository.adjustProductStock(product, (product.stock + delta).coerceAtLeast(0L), actor)
                .onFailure { _uiMessage.value = it.localizedMessage }
        }
    }

    fun saveProduct(product: Product) {
        val actor = activeStaff.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.upsertProduct(product, actor)
                .onSuccess { _uiMessage.value = "Produk ${product.name} tersimpan." }
                .onFailure { _uiMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun createBranch(name: String, code: String, address: String, phone: String) {
        val actor = activeStaff.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createBranch(name, code, address, phone, actor)
                .onSuccess { _uiMessage.value = "Cabang $name berhasil ditambahkan." }
                .onFailure { _uiMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun createStaff(username: String, fullName: String, role: StaffRole, branchId: String, password: String) {
        val actor = activeStaff.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createStaffAccount(username, fullName, role, branchId, password, actor)
                .onSuccess { _uiMessage.value = "Akun @$username dibuat. Kini dapat langsung login." }
                .onFailure { _uiMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun changeStaffPassword(target: StaffAccount, newPassword: String) {
        val actor = activeStaff.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.changeStaffPassword(target, newPassword, actor)
                .onSuccess { _uiMessage.value = "Password @${target.username} berhasil diubah." }
                .onFailure { _uiMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun toggleStaffActive(target: StaffAccount, active: Boolean) {
        val actor = activeStaff.value ?: return
        viewModelScope.launch {
            repository.toggleStaffActive(target, active, actor)
                .onFailure { _uiMessage.value = it.localizedMessage }
        }
    }

    fun updateSettings(
        storeName: String,
        qrisMerchantId: String,
        qrisMerchantName: String,
        autoBackupIntervalHours: Long
    ) {
        val ws = (workspaceState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateWorkspaceSettings(
                workspace = ws,
                storeName = storeName,
                qrisMerchantId = qrisMerchantId,
                qrisMerchantName = qrisMerchantName,
                autoBackupIntervalHours = autoBackupIntervalHours
            ).onSuccess {
                _uiMessage.value = "Pengaturan toko & QRIS diperbarui."
            }.onFailure {
                _uiMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun triggerManualCloudBackup() {
        val key = _e2eeKey.value ?: run {
            _uiMessage.value = "Buka kunci E2EE Vault terlebih dahulu."
            return
        }
        val branches = (branchesState.value as? UiState.Success)?.data.orEmpty()
        val products = (productsState.value as? UiState.Success)?.data.orEmpty()
        val txs = (transactionsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _isBusy.value = true
            repository.createBackup(
                backupType = "MANUAL",
                branches = branches,
                products = products,
                transactions = txs,
                actorName = activeStaff.value?.fullName ?: "Admin",
                e2eeKey = key
            ).onSuccess {
                _uiMessage.value = "Backup terenkripsi berhasil disimpan."
            }.onFailure {
                _uiMessage.value = it.localizedMessage
            }
            _isBusy.value = false
        }
    }

    fun restoreBackup(backup: CloudBackupRecord) {
        val key = _e2eeKey.value ?: run {
            _uiMessage.value = "Buka kunci E2EE Vault terlebih dahulu."
            return
        }
        val actor = activeStaff.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.restoreFromBackup(backup, key, actor)
                .onSuccess { count -> _uiMessage.value = "Berhasil memulihkan $count produk dari Backup." }
                .onFailure { _uiMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun syncWithCloudNow() {
        viewModelScope.launch {
            _isBusy.value = true
            repository.syncToFirestore()
                .onSuccess { count -> _uiMessage.value = "Sinkronisasi Cloud selesai ($count data tersinkron)." }
                .onFailure { _uiMessage.value = it.localizedMessage ?: "Gagal sinkronisasi cloud." }
            _isBusy.value = false
        }
    }

    fun refreshBondedPrinters() {
        _bondedPrinters.value = printerManager.getBondedPrinters()
    }

    fun selectPrinter(device: BluetoothPrinterDevice) {
        viewModelScope.launch {
            preferencesManager.setPrinter(device.address, device.name)
            _uiMessage.value = "Printer ${device.name} dipilih."
        }
    }

    fun printTransactionReceipt(tx: PosTransaction) {
        val mac = localPreferences.value.printerMac
        if (mac.isBlank()) {
            _uiMessage.value = "Pilih printer Bluetooth di menu Admin > Printer & Bahasa."
            return
        }
        val storeName = (workspaceState.value as? UiState.Success)?.data?.storeName ?: "KasirPro"
        val payload = decryptPayload(tx)
        viewModelScope.launch {
            _isBusy.value = true
            printerManager.printEscPosReceipt(mac, storeName, tx, payload)
                .onSuccess { _uiMessage.value = "Struk ${tx.receiptNumber} dicetak ke printer Bluetooth." }
                .onFailure { _uiMessage.value = "Gagal cetak: ${it.localizedMessage}" }
            _isBusy.value = false
        }
    }
}
