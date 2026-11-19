package com.example.data.local

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.model.AuditLog
import com.example.model.Branch
import com.example.model.CartItem
import com.example.model.CloudBackupRecord
import com.example.model.DecryptedTxPayload
import com.example.model.PaymentMethod
import com.example.model.PosTransaction
import com.example.model.Product
import com.example.model.ReceiptLineItem
import com.example.model.StaffAccount
import com.example.model.StaffRole
import com.example.model.TransactionStatus
import com.example.model.WorkspaceConfig
import com.example.security.CryptoManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKey

class LocalPosRepository(
    private val db: KasirProDatabase,
    private val firestore: FirebaseFirestore? = null,
    private val auth: FirebaseAuth? = null
) {
    constructor(context: Context) : this(
        db = KasirProDatabase.getInstance(context.applicationContext),
        firestore = createFirestoreOrNull(context.applicationContext),
        auth = createAuthOrNull()
    )

    companion object {
        private fun createFirestoreOrNull(appContext: Context): FirebaseFirestore? {
            return try {
                val dbId = appContext.getString(R.string.firestore_database_id)
                FirebaseFirestore.getInstance(dbId)
            } catch (_: Exception) {
                null
            }
        }

        private fun createAuthOrNull(): FirebaseAuth? {
            return try {
                Firebase.auth
            } catch (_: Exception) {
                null
            }
        }
    }

    private val workspaceDao = db.workspaceDao()
    private val branchDao = db.branchDao()
    private val staffDao = db.staffDao()
    private val productDao = db.productDao()
    private val transactionDao = db.transactionDao()
    private val auditLogDao = db.auditLogDao()
    private val backupDao = db.backupDao()
    private val txRunnerDao = db.txRunnerDao()

    private fun activeCloudUid(): String? = auth?.currentUser?.uid?.takeIf { it.isNotBlank() }

    fun observeWorkspace(): Flow<WorkspaceConfig?> =
        workspaceDao.observeWorkspace().map { it?.toModel(activeCloudUid() ?: "local") }

    fun observeBranches(): Flow<List<Branch>> =
        branchDao.observeAll().map { list -> list.map { it.toModel(activeCloudUid() ?: "local") } }

    fun observeStaff(): Flow<List<StaffAccount>> =
        staffDao.observeAll().map { list -> list.map { it.toModel(activeCloudUid() ?: "local") } }

    fun observeProducts(): Flow<List<Product>> =
        productDao.observeAll().map { list -> list.map { it.toModel(activeCloudUid() ?: "local") } }

    fun observeTransactions(): Flow<List<PosTransaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toModel(activeCloudUid() ?: "local") } }

    fun observeAuditLogs(): Flow<List<AuditLog>> =
        auditLogDao.observeAll().map { list -> list.map { it.toModel(activeCloudUid() ?: "local") } }

    fun observeBackups(): Flow<List<CloudBackupRecord>> =
        backupDao.observeAll().map { list -> list.map { it.toModel(activeCloudUid() ?: "local") } }

    suspend fun bootstrapIfNeeded(defaultVaultPassphrase: String): Result<Unit> {
        return try {
            if (workspaceDao.getWorkspaceOnce() != null) return Result.success(Unit)
            val nowMs = System.currentTimeMillis()
            val salt = CryptoManager.generateSaltBase64()
            val e2eeKey = CryptoManager.deriveE2eeKey(defaultVaultPassphrase, salt)
            val keyCheck = CryptoManager.computeKeyVerifier(e2eeKey)

            workspaceDao.upsert(
                WorkspaceEntity(
                    id = "default_workspace",
                    storeName = "KasirPro Nusantara",
                    e2eeSalt = salt,
                    e2eeKeyCheck = keyCheck,
                    qrisMerchantId = "ID1020304050607",
                    qrisMerchantName = "KASIRPRO NUSANTARA",
                    autoBackupIntervalHours = 12L,
                    lastBackupEpochMs = 0L,
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs
                )
            )

            val branches = listOf(
                BranchEntity("branch_hq", "Cabang Pusat Jakarta", "HQ-JKT", "Jl. Sudirman Kav. 52, Jakarta", "0215550199", true, true, nowMs, nowMs),
                BranchEntity("branch_bdg", "Cabang Dago Bandung", "BDG-02", "Jl. Ir. H. Juanda No. 88, Bandung", "0225550144", false, true, nowMs, nowMs),
                BranchEntity("branch_sby", "Cabang Tunjungan Surabaya", "SBY-03", "Jl. Tunjungan No. 41, Surabaya", "0315550188", false, true, nowMs, nowMs)
            )
            branchDao.upsertAll(branches)

            val staffSeed = listOf(
                listOf("staff_central", "admin.pusat", "Admin Pusat", StaffRole.CENTRAL_ADMIN.name, "branch_hq", "pusat123"),
                listOf("staff_branch", "admin.cabang", "Admin Cabang JKT", StaffRole.BRANCH_ADMIN.name, "branch_hq", "cabang123"),
                listOf("staff_cashier", "kasir.01", "Rina (Kasir 01)", StaffRole.CASHIER.name, "branch_hq", "kasir123"),
                listOf("staff_cashier_bdg", "kasir.bdg", "Budi (Kasir BDG)", StaffRole.CASHIER.name, "branch_bdg", "kasir123")
            )
            val staffEntities = staffSeed.map { row ->
                val digest = CryptoManager.hashPassword(row[5])
                StaffEntity(
                    id = row[0],
                    username = row[1],
                    fullName = row[2],
                    role = row[3],
                    branchId = row[4],
                    passwordHash = digest.hashBase64,
                    passwordSalt = digest.saltBase64,
                    active = true,
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs
                )
            }
            staffDao.upsertAll(staffEntities)

            val products = listOf(
                ProductEntity("prod_jkt_1", "branch_hq", "KPI-001", "Kopi Susu Gula Aren", "Minuman", 24000.0, 10000.0, 48L, 10L, "cup", true, nowMs, nowMs),
                ProductEntity("prod_jkt_2", "branch_hq", "KPI-002", "Matcha Oat Latte", "Minuman", 32000.0, 14000.0, 22L, 8L, "cup", true, nowMs, nowMs),
                ProductEntity("prod_jkt_3", "branch_hq", "SNK-001", "Croissant Butter", "Roti & Kue", 28000.0, 12000.0, 16L, 5L, "pcs", true, nowMs, nowMs),
                ProductEntity("prod_jkt_4", "branch_hq", "MKN-001", "Rice Bowl Chicken Teriyaki", "Makanan", 38000.0, 18000.0, 25L, 6L, "porsi", true, nowMs, nowMs),
                ProductEntity("prod_jkt_5", "branch_hq", "BNS-001", "Biji Kopi Arabika 250g", "Merchandise", 85000.0, 48000.0, 4L, 5L, "pack", true, nowMs, nowMs),
                ProductEntity("prod_jkt_6", "branch_hq", "TEA-001", "Lychee Iced Tea", "Minuman", 22000.0, 7500.0, 35L, 8L, "cup", true, nowMs, nowMs),
                ProductEntity("prod_bdg_1", "branch_bdg", "KPI-101", "Espresso Tonic Bandung", "Minuman", 29000.0, 11000.0, 30L, 8L, "cup", true, nowMs, nowMs),
                ProductEntity("prod_bdg_2", "branch_bdg", "SNK-101", "Bolen Pisang Keju", "Roti & Kue", 45000.0, 22000.0, 14L, 5L, "box", true, nowMs, nowMs),
                ProductEntity("prod_sby_1", "branch_sby", "KPI-201", "Cold Brew Tunjungan", "Minuman", 27000.0, 10000.0, 28L, 8L, "botol", true, nowMs, nowMs),
                ProductEntity("prod_sby_2", "branch_sby", "MKN-201", "Nasi Katsu Sambal Matah", "Makanan", 36000.0, 17000.0, 18L, 5L, "porsi", true, nowMs, nowMs)
            )
            productDao.upsertAll(products)

            val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(nowMs))
            val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
            val sampleTxs = listOf(
                Triple(
                    "tx_seed_1",
                    Pair("branch_hq", "Cabang Pusat Jakarta"),
                    Triple(
                        PaymentMethod.QRIS.name,
                        76000.0,
                        DecryptedTxPayload(
                            customerName = "Andi Wijaya",
                            customerPhone = "6281234567890",
                            paymentReference = "QRIS-882101",
                            items = listOf(
                                ReceiptLineItem("prod_jkt_1", "KPI-001", "Kopi Susu Gula Aren", "cup", 24000.0, 10000.0, 2),
                                ReceiptLineItem("prod_jkt_3", "SNK-001", "Croissant Butter", "pcs", 28000.0, 12000.0, 1)
                            )
                        )
                    )
                ),
                Triple(
                    "tx_seed_2",
                    Pair("branch_bdg", "Cabang Dago Bandung"),
                    Triple(
                        PaymentMethod.CASH.name,
                        103000.0,
                        DecryptedTxPayload(
                            customerName = "Sari Dewi",
                            customerPhone = "6281398765432",
                            paymentReference = "CASH-REG01",
                            items = listOf(
                                ReceiptLineItem("prod_bdg_1", "KPI-101", "Espresso Tonic Bandung", "cup", 29000.0, 11000.0, 2),
                                ReceiptLineItem("prod_bdg_2", "SNK-101", "Bolen Pisang Keju", "box", 45000.0, 22000.0, 1)
                            )
                        )
                    )
                ),
                Triple(
                    "tx_seed_3",
                    Pair("branch_sby", "Cabang Tunjungan Surabaya"),
                    Triple(
                        PaymentMethod.QRIS.name,
                        99000.0,
                        DecryptedTxPayload(
                            customerName = "Hendra Pratama",
                            customerPhone = "6285611223344",
                            paymentReference = "QRIS-994412",
                            items = listOf(
                                ReceiptLineItem("prod_sby_1", "KPI-201", "Cold Brew Tunjungan", "botol", 27000.0, 10000.0, 1),
                                ReceiptLineItem("prod_sby_2", "MKN-201", "Nasi Katsu Sambal Matah", "porsi", 36000.0, 17000.0, 2)
                            )
                        )
                    )
                )
            )
            val txEntities = sampleTxs.mapIndexed { idx, (txId, branchInfo, detail) ->
                val blob = CryptoManager.encrypt(detail.third.toJsonString(), e2eeKey)
                val itemCount = detail.third.items.sumOf { it.quantity }.toLong()
                TransactionEntity(
                    id = txId,
                    branchId = branchInfo.first,
                    branchName = branchInfo.second,
                    receiptNumber = "INV-${dayKey.replace("-", "")}-00${idx + 1}",
                    cashierId = "staff_cashier",
                    cashierName = "Rina (Kasir 01)",
                    paymentMethod = detail.first,
                    status = TransactionStatus.COMPLETED.name,
                    subtotal = detail.second,
                    discount = 0.0,
                    tax = 0.0,
                    totalAmount = detail.second,
                    paidAmount = detail.second,
                    changeAmount = 0.0,
                    itemCount = itemCount,
                    encryptedPayload = blob.ciphertextBase64,
                    e2eeIv = blob.ivBase64,
                    e2eeVerified = true,
                    whatsappSent = (idx == 0),
                    monthKey = monthKey,
                    dayKey = dayKey,
                    createdAtMs = nowMs - ((3 - idx) * 60_000L),
                    updatedAtMs = nowMs
                )
            }
            transactionDao.upsertAll(txEntities)

            auditLogDao.insert(
                AuditLogEntity(
                    id = "log_seed_1",
                    branchId = "branch_hq",
                    actorId = "system",
                    actorName = "KasirPro",
                    actorRole = "SYSTEM",
                    actionType = "WORKSPACE_INIT",
                    summary = "Inisialisasi workspace multi-cabang & enkripsi E2EE AES-256-GCM aktif.",
                    createdAtMs = nowMs
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authenticateStaff(username: String, password: String): Result<StaffAccount> {
        return try {
            val cleanUser = username.trim().lowercase()
            if (cleanUser.isBlank() || password.isBlank()) {
                return Result.failure(IllegalArgumentException("Username dan password wajib diisi."))
            }
            val entity = staffDao.findByUsername(cleanUser)
            if (entity == null || !entity.active) {
                return Result.failure(IllegalArgumentException("Username tidak ditemukan atau akun nonaktif."))
            }
            val valid = CryptoManager.verifyPassword(password, entity.passwordHash, entity.passwordSalt)
            if (!valid) {
                return Result.failure(IllegalArgumentException("Password salah."))
            }
            val nowMs = System.currentTimeMillis()
            auditLogDao.insert(
                AuditLogEntity(
                    id = "log_${UUID.randomUUID()}",
                    branchId = entity.branchId,
                    actorId = entity.id,
                    actorName = entity.fullName.take(80),
                    actorRole = entity.role,
                    actionType = "STAFF_LOGIN",
                    summary = "${entity.fullName} (@${entity.username}) masuk ke aplikasi.".take(300),
                    createdAtMs = nowMs
                )
            )
            Result.success(entity.toModel(activeCloudUid() ?: "local"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeCheckout(
        branch: Branch,
        cashier: StaffAccount,
        cartItems: List<CartItem>,
        paymentMethod: PaymentMethod,
        discount: Double,
        paidAmount: Double,
        customerName: String,
        customerPhone: String,
        whatsappSent: Boolean,
        e2eeKey: SecretKey
    ): Result<PosTransaction> {
        return try {
            require(cartItems.isNotEmpty()) { "Cart cannot be empty" }
            val subtotal = cartItems.sumOf { it.lineTotal }
            val safeDiscount = discount.coerceIn(0.0, subtotal)
            val total = (subtotal - safeDiscount).coerceAtLeast(0.0)
            val safePaid = if (paymentMethod == PaymentMethod.CASH) paidAmount.coerceAtLeast(total) else total
            val change = (safePaid - total).coerceAtLeast(0.0)
            val itemCount = cartItems.sumOf { it.quantity }.toLong()

            val nowMs = System.currentTimeMillis()
            val nowDate = Date(nowMs)
            val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(nowDate)
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(nowDate)
            val receiptNo = "INV-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(nowDate)}"

            val lineItems = cartItems.map {
                ReceiptLineItem(
                    productId = it.product.id,
                    sku = it.product.sku,
                    name = it.product.name,
                    unit = it.product.unit,
                    unitPrice = it.product.price,
                    costPrice = it.product.costPrice,
                    quantity = it.quantity
                )
            }
            val payloadObj = DecryptedTxPayload(
                customerName = customerName.trim().take(80),
                customerPhone = customerPhone.trim().take(30),
                paymentReference = "${paymentMethod.name}-${receiptNo.takeLast(6)}",
                notes = "",
                items = lineItems
            )
            val blob = CryptoManager.encrypt(payloadObj.toJsonString(), e2eeKey)
            val txId = "tx_${UUID.randomUUID()}"
            val txEntity = TransactionEntity(
                id = txId,
                branchId = branch.id,
                branchName = branch.name.take(80),
                receiptNumber = receiptNo,
                cashierId = cashier.id,
                cashierName = cashier.fullName.take(80),
                paymentMethod = paymentMethod.name,
                status = TransactionStatus.COMPLETED.name,
                subtotal = subtotal,
                discount = safeDiscount,
                tax = 0.0,
                totalAmount = total,
                paidAmount = safePaid,
                changeAmount = change,
                itemCount = itemCount,
                encryptedPayload = blob.ciphertextBase64,
                e2eeIv = blob.ivBase64,
                e2eeVerified = true,
                whatsappSent = whatsappSent,
                monthKey = monthKey,
                dayKey = dayKey,
                createdAtMs = nowMs,
                updatedAtMs = nowMs
            )
            val logEntity = AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = branch.id,
                actorId = cashier.id,
                actorName = cashier.fullName.take(80),
                actorRole = cashier.role,
                actionType = "CHECKOUT_${paymentMethod.name}",
                summary = "Transaksi $receiptNo (${itemCount} item) Rp ${total.toLong()} di ${branch.name}".take(300),
                createdAtMs = nowMs
            )

            txRunnerDao.executeCheckoutAtomic(
                stockDeductions = cartItems.map { it.product.id to it.quantity },
                tx = txEntity,
                auditLog = logEntity,
                nowMs = nowMs
            )
            mirrorIfSignedIn()
            Result.success(txEntity.toModel(activeCloudUid() ?: "local"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markWhatsappSent(txId: String): Result<Unit> = runCatching {
        require(txId.isNotBlank())
        transactionDao.markWhatsappSent(txId, System.currentTimeMillis())
        mirrorIfSignedIn()
    }

    suspend fun voidTransaction(txId: String, actor: StaffAccount, branchId: String): Result<Unit> = runCatching {
        require(txId.isNotBlank())
        val nowMs = System.currentTimeMillis()
        transactionDao.updateStatus(txId, TransactionStatus.VOIDED.name, nowMs)
        auditLogDao.insert(
            AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = branchId,
                actorId = actor.id,
                actorName = actor.fullName.take(80),
                actorRole = actor.role,
                actionType = "VOID_TRANSACTION",
                summary = "Membatalkan transaksi $txId".take(300),
                createdAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
    }

    suspend fun adjustProductStock(product: Product, newStock: Long, actor: StaffAccount): Result<Unit> = runCatching {
        val safeStock = newStock.coerceAtLeast(0L)
        val nowMs = System.currentTimeMillis()
        productDao.updateStock(product.id, safeStock, nowMs)
        auditLogDao.insert(
            AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = product.branchId,
                actorId = actor.id,
                actorName = actor.fullName.take(80),
                actorRole = actor.role,
                actionType = "STOCK_ADJUST",
                summary = "Stok ${product.name}: ${product.stock} -> $safeStock ${product.unit}".take(300),
                createdAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
    }

    suspend fun upsertProduct(product: Product, actor: StaffAccount): Result<Unit> = runCatching {
        val nowMs = System.currentTimeMillis()
        val isNew = product.id.isBlank()
        val existing = if (!isNew) productDao.findById(product.id) else null
        val id = if (isNew) "prod_${UUID.randomUUID()}" else product.id
        productDao.upsert(
            ProductEntity(
                id = id,
                branchId = product.branchId,
                sku = product.sku.trim().take(40).ifBlank { "SKU-${nowMs % 10000}" },
                name = product.name.trim().take(100),
                category = product.category.trim().take(50).ifBlank { "Umum" },
                price = product.price.coerceAtLeast(0.0),
                costPrice = product.costPrice.coerceAtLeast(0.0),
                stock = product.stock.coerceAtLeast(0L),
                minStock = product.minStock.coerceAtLeast(0L),
                unit = product.unit.trim().take(20).ifBlank { "pcs" },
                active = product.active,
                createdAtMs = existing?.createdAtMs ?: nowMs,
                updatedAtMs = nowMs
            )
        )
        auditLogDao.insert(
            AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = product.branchId,
                actorId = actor.id,
                actorName = actor.fullName.take(80),
                actorRole = actor.role,
                actionType = if (isNew) "PRODUCT_ADD" else "PRODUCT_EDIT",
                summary = "${if (isNew) "Menambah" else "Memperbarui"} produk ${product.name}".take(300),
                createdAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
    }

    suspend fun createBranch(
        name: String,
        code: String,
        address: String,
        phone: String,
        actor: StaffAccount
    ): Result<Unit> = runCatching {
        val nowMs = System.currentTimeMillis()
        val id = "branch_${UUID.randomUUID()}"
        branchDao.upsert(
            BranchEntity(
                id = id,
                name = name.trim().take(80),
                code = code.trim().uppercase().take(20),
                address = address.trim().take(200),
                phone = phone.trim().take(30),
                headquarters = false,
                active = true,
                createdAtMs = nowMs,
                updatedAtMs = nowMs
            )
        )
        auditLogDao.insert(
            AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = id,
                actorId = actor.id,
                actorName = actor.fullName.take(80),
                actorRole = actor.role,
                actionType = "BRANCH_CREATE",
                summary = "Membuka cabang baru: $name ($code)".take(300),
                createdAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
    }

    suspend fun createStaffAccount(
        username: String,
        fullName: String,
        role: StaffRole,
        branchId: String,
        rawPassword: String,
        actor: StaffAccount
    ): Result<Unit> = runCatching {
        val cleanUsername = username.trim().lowercase().take(40)
        require(cleanUsername.length >= 3) { "Username minimal 3 karakter" }
        require(rawPassword.length >= 6) { "Password minimal 6 karakter" }
        if (staffDao.findByUsername(cleanUsername) != null) {
            throw IllegalStateException("Username @$cleanUsername sudah digunakan.")
        }
        val digest = CryptoManager.hashPassword(rawPassword)
        val nowMs = System.currentTimeMillis()
        staffDao.upsert(
            StaffEntity(
                id = "staff_${UUID.randomUUID()}",
                username = cleanUsername,
                fullName = fullName.trim().take(80),
                role = role.name,
                branchId = branchId,
                passwordHash = digest.hashBase64,
                passwordSalt = digest.saltBase64,
                active = true,
                createdAtMs = nowMs,
                updatedAtMs = nowMs
            )
        )
        auditLogDao.insert(
            AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = branchId,
                actorId = actor.id,
                actorName = actor.fullName.take(80),
                actorRole = actor.role,
                actionType = "STAFF_CREATE",
                summary = "Menambahkan akun ${role.name}: $fullName (@$cleanUsername)".take(300),
                createdAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
    }

    suspend fun changeStaffPassword(
        targetStaff: StaffAccount,
        newPassword: String,
        actor: StaffAccount
    ): Result<Unit> = runCatching {
        require(newPassword.length >= 6) { "Password baru minimal 6 karakter" }
        val digest = CryptoManager.hashPassword(newPassword)
        val nowMs = System.currentTimeMillis()
        staffDao.updatePassword(targetStaff.id, digest.hashBase64, digest.saltBase64, nowMs)
        auditLogDao.insert(
            AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = targetStaff.branchId,
                actorId = actor.id,
                actorName = actor.fullName.take(80),
                actorRole = actor.role,
                actionType = "PASSWORD_CHANGE",
                summary = "${actor.fullName} mengubah password untuk ${targetStaff.fullName} (@${targetStaff.username})".take(300),
                createdAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
    }

    suspend fun toggleStaffActive(targetStaff: StaffAccount, active: Boolean, actor: StaffAccount): Result<Unit> = runCatching {
        val nowMs = System.currentTimeMillis()
        staffDao.updateActive(targetStaff.id, active, nowMs)
        auditLogDao.insert(
            AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = targetStaff.branchId,
                actorId = actor.id,
                actorName = actor.fullName.take(80),
                actorRole = actor.role,
                actionType = "STAFF_STATUS",
                summary = "${targetStaff.fullName} diubah status menjadi ${if (active) "AKTIF" else "NONAKTIF"}".take(300),
                createdAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
    }

    suspend fun updateWorkspaceSettings(
        workspace: WorkspaceConfig,
        storeName: String,
        qrisMerchantId: String,
        qrisMerchantName: String,
        autoBackupIntervalHours: Long,
        newE2eeSalt: String? = null,
        newE2eeKeyCheck: String? = null
    ): Result<Unit> = runCatching {
        val current = workspaceDao.getWorkspaceOnce()
        val nowMs = System.currentTimeMillis()
        workspaceDao.upsert(
            WorkspaceEntity(
                id = current?.id ?: "default_workspace",
                storeName = storeName.trim().take(80).ifBlank { workspace.storeName },
                e2eeSalt = newE2eeSalt ?: workspace.e2eeSalt,
                e2eeKeyCheck = newE2eeKeyCheck ?: workspace.e2eeKeyCheck,
                qrisMerchantId = qrisMerchantId.trim().take(128).ifBlank { workspace.qrisMerchantId },
                qrisMerchantName = qrisMerchantName.trim().take(80).ifBlank { workspace.qrisMerchantName },
                autoBackupIntervalHours = autoBackupIntervalHours.coerceIn(1L, 168L),
                lastBackupEpochMs = workspace.lastBackupEpochMs,
                createdAtMs = current?.createdAtMs ?: nowMs,
                updatedAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
    }

    suspend fun createBackup(
        backupType: String,
        branches: List<Branch>,
        products: List<Product>,
        transactions: List<PosTransaction>,
        actorName: String,
        e2eeKey: SecretKey
    ): Result<Unit> = runCatching {
        val nowMs = System.currentTimeMillis()
        val snapshotJson = JSONObject().apply {
            put("version", 1)
            put("timestamp", nowMs)
            put("branches", JSONArray().apply {
                branches.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.id)
                        put("name", b.name)
                        put("code", b.code)
                        put("address", b.address)
                        put("phone", b.phone)
                        put("headquarters", b.headquarters)
                        put("active", b.active)
                    })
                }
            })
            put("products", JSONArray().apply {
                products.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("branchId", p.branchId)
                        put("sku", p.sku)
                        put("name", p.name)
                        put("category", p.category)
                        put("price", p.price)
                        put("costPrice", p.costPrice)
                        put("stock", p.stock)
                        put("minStock", p.minStock)
                        put("unit", p.unit)
                        put("active", p.active)
                    })
                }
            })
            put("txCount", transactions.size)
            put("totalRevenue", transactions.filter { it.statusEnum == TransactionStatus.COMPLETED }.sumOf { it.totalAmount })
        }.toString()

        val checksum = CryptoManager.sha256Hex(snapshotJson)
        val blob = CryptoManager.encrypt(snapshotJson, e2eeKey)
        val recordCount = (branches.size + products.size + transactions.size).toLong()
        val backupEntity = BackupEntity(
            id = "bkp_${UUID.randomUUID()}",
            backupType = if (backupType == "AUTO") "AUTO" else "MANUAL",
            encryptedSnapshot = blob.ciphertextBase64.take(499000),
            e2eeIv = blob.ivBase64,
            checksumSha256 = checksum,
            recordCount = recordCount,
            createdBy = actorName.take(80),
            syncedToCloud = activeCloudUid() != null,
            createdAtMs = nowMs
        )
        backupDao.insert(backupEntity)
        val ws = workspaceDao.getWorkspaceOnce()
        if (ws != null) {
            workspaceDao.updateLastBackup(ws.id, nowMs, nowMs)
        }
        mirrorIfSignedIn()
    }

    suspend fun restoreFromBackup(
        backup: CloudBackupRecord,
        e2eeKey: SecretKey,
        actor: StaffAccount
    ): Result<Int> = runCatching {
        val decrypted = CryptoManager.decrypt(backup.encryptedSnapshot, backup.e2eeIv, e2eeKey)
            ?: throw IllegalStateException("Gagal dekripsi backup. Pastikan kunci E2EE terbuka dan sesuai.")
        val actualChecksum = CryptoManager.sha256Hex(decrypted)
        if (actualChecksum != backup.checksumSha256) {
            throw IllegalStateException("Checksum SHA-256 tidak cocok! Data backup korup.")
        }
        val root = JSONObject(decrypted)
        val productsArr = root.optJSONArray("products") ?: JSONArray()
        val nowMs = System.currentTimeMillis()
        var restoredCount = 0
        val toRestore = mutableListOf<ProductEntity>()
        for (i in 0 until productsArr.length()) {
            val p = productsArr.getJSONObject(i)
            val pid = p.optString("id")
            val branchId = p.optString("branchId")
            if (pid.isNotBlank() && branchId.isNotBlank()) {
                toRestore.add(
                    ProductEntity(
                        id = pid,
                        branchId = branchId,
                        sku = p.optString("sku", "SKU-$i"),
                        name = p.optString("name", "Item"),
                        category = p.optString("category", "Umum"),
                        price = p.optDouble("price", 0.0),
                        costPrice = p.optDouble("costPrice", 0.0),
                        stock = p.optLong("stock", 0L),
                        minStock = p.optLong("minStock", 5L),
                        unit = p.optString("unit", "pcs"),
                        active = p.optBoolean("active", true),
                        createdAtMs = nowMs,
                        updatedAtMs = nowMs
                    )
                )
                restoredCount++
            }
        }
        if (toRestore.isNotEmpty()) productDao.upsertAll(toRestore)
        auditLogDao.insert(
            AuditLogEntity(
                id = "log_${UUID.randomUUID()}",
                branchId = actor.branchId,
                actorId = actor.id,
                actorName = actor.fullName.take(80),
                actorRole = actor.role,
                actionType = "BACKUP_RESTORE",
                summary = "Memulihkan $restoredCount produk dari Backup (${backup.backupType})".take(300),
                createdAtMs = nowMs
            )
        )
        mirrorIfSignedIn()
        restoredCount
    }

    private suspend fun mirrorIfSignedIn() {
        if (activeCloudUid() != null && firestore != null) {
            runCatching { syncToFirestore() }
        }
    }

    /**
     * Optional Cloud Sync to Firestore. Strictly requires Firebase.auth.currentUser != null.
     * Never called unless the user explicitly links Google Cloud Sync inside Admin > Cloud Backup.
     */
    suspend fun syncToFirestore(): Result<Int> {
        val cloudDb = firestore ?: return Result.failure(IllegalStateException("Firestore tidak tersedia."))
        val uid = activeCloudUid() ?: return Result.failure(IllegalStateException("Cloud Sync Google belum dihubungkan."))
        val userDoc = cloudDb.collection("users").document(uid)
        return try {
            val ws = workspaceDao.getWorkspaceOnce()
                ?: return Result.failure(IllegalStateException("Workspace belum diinisialisasi."))
            val branches = branchDao.getAllOnce()
            val staff = staffDao.getAllOnce()
            val products = productDao.getAllOnce()
            val txs = transactionDao.getAllOnce()
            val backups = backupDao.getAllOnce()

            val existingWs = userDoc.get().await()
            val batch = cloudDb.batch()
            if (!existingWs.exists()) {
                batch.set(
                    userDoc,
                    mapOf(
                        "userId" to uid,
                        "storeName" to ws.storeName,
                        "e2eeSalt" to ws.e2eeSalt,
                        "e2eeKeyCheck" to ws.e2eeKeyCheck,
                        "qrisMerchantId" to ws.qrisMerchantId,
                        "qrisMerchantName" to ws.qrisMerchantName,
                        "autoBackupIntervalHours" to ws.autoBackupIntervalHours,
                        "lastBackupEpochMs" to ws.lastBackupEpochMs,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    userDoc,
                    mapOf(
                        "storeName" to ws.storeName,
                        "e2eeSalt" to ws.e2eeSalt,
                        "e2eeKeyCheck" to ws.e2eeKeyCheck,
                        "qrisMerchantId" to ws.qrisMerchantId,
                        "qrisMerchantName" to ws.qrisMerchantName,
                        "autoBackupIntervalHours" to ws.autoBackupIntervalHours,
                        "lastBackupEpochMs" to ws.lastBackupEpochMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            branches.forEach { b ->
                val ref = userDoc.collection("branches").document(b.id)
                val snap = ref.get().await()
                if (!snap.exists()) {
                    batch.set(ref, mapOf(
                        "userId" to uid, "name" to b.name, "code" to b.code, "address" to b.address,
                        "phone" to b.phone, "headquarters" to b.headquarters, "active" to b.active,
                        "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                    ))
                } else {
                    batch.update(ref, mapOf(
                        "name" to b.name, "code" to b.code, "address" to b.address, "phone" to b.phone,
                        "headquarters" to b.headquarters, "active" to b.active, "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }

            staff.forEach { s ->
                val ref = userDoc.collection("staff").document(s.id)
                val snap = ref.get().await()
                if (!snap.exists()) {
                    batch.set(ref, mapOf(
                        "userId" to uid, "username" to s.username, "fullName" to s.fullName, "role" to s.role,
                        "branchId" to s.branchId, "passwordHash" to s.passwordHash, "passwordSalt" to s.passwordSalt,
                        "active" to s.active, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                    ))
                } else {
                    batch.update(ref, mapOf(
                        "fullName" to s.fullName, "role" to s.role, "branchId" to s.branchId,
                        "passwordHash" to s.passwordHash, "passwordSalt" to s.passwordSalt,
                        "active" to s.active, "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }

            products.forEach { p ->
                val ref = userDoc.collection("products").document(p.id)
                val snap = ref.get().await()
                if (!snap.exists()) {
                    batch.set(ref, mapOf(
                        "userId" to uid, "branchId" to p.branchId, "sku" to p.sku, "name" to p.name,
                        "category" to p.category, "price" to p.price, "costPrice" to p.costPrice,
                        "stock" to p.stock, "minStock" to p.minStock, "unit" to p.unit, "active" to p.active,
                        "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                    ))
                } else {
                    batch.update(ref, mapOf(
                        "sku" to p.sku, "name" to p.name, "category" to p.category, "price" to p.price,
                        "costPrice" to p.costPrice, "stock" to p.stock, "minStock" to p.minStock,
                        "unit" to p.unit, "active" to p.active, "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }

            txs.forEach { t ->
                val ref = userDoc.collection("transactions").document(t.id)
                val snap = ref.get().await()
                if (!snap.exists()) {
                    batch.set(ref, mapOf(
                        "userId" to uid, "branchId" to t.branchId, "branchName" to t.branchName,
                        "receiptNumber" to t.receiptNumber, "cashierId" to t.cashierId, "cashierName" to t.cashierName,
                        "paymentMethod" to t.paymentMethod, "status" to t.status, "subtotal" to t.subtotal,
                        "discount" to t.discount, "tax" to t.tax, "totalAmount" to t.totalAmount,
                        "paidAmount" to t.paidAmount, "changeAmount" to t.changeAmount, "itemCount" to t.itemCount,
                        "encryptedPayload" to t.encryptedPayload, "e2eeIv" to t.e2eeIv, "e2eeVerified" to t.e2eeVerified,
                        "whatsappSent" to t.whatsappSent, "monthKey" to t.monthKey, "dayKey" to t.dayKey,
                        "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }

            backups.take(10).forEach { bk ->
                val ref = userDoc.collection("cloudBackups").document(bk.id)
                val snap = ref.get().await()
                if (!snap.exists()) {
                    batch.set(ref, mapOf(
                        "userId" to uid, "backupType" to bk.backupType,
                        "encryptedSnapshot" to bk.encryptedSnapshot, "e2eeIv" to bk.e2eeIv,
                        "checksumSha256" to bk.checksumSha256, "recordCount" to bk.recordCount,
                        "createdBy" to bk.createdBy, "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
            }

            batch.commit().await()
            Result.success(branches.size + staff.size + products.size + txs.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }
}
