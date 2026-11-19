package com.example.data

import android.content.Context
import com.example.R
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKey

class PosRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private inline fun <T> safeCollectionFlow(
        uid: String,
        path: String,
        crossinline block: () -> Flow<T>
    ): Flow<T> = flow {
        emitAll(
            block().catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
        )
    }

    fun observeWorkspace(uid: String): Flow<WorkspaceConfig?> =
        safeCollectionFlow(uid, "users/$uid") {
            userDoc(uid).snapshots().map { snap ->
                if (snap.exists()) {
                    snap.toObject(WorkspaceConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
        }

    fun observeBranches(uid: String): Flow<List<Branch>> =
        safeCollectionFlow(uid, "users/$uid/branches") {
            userDoc(uid).collection("branches").snapshots().map { snap ->
                snap.toObjects(Branch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<Branch> { it.headquarters }.thenBy { it.name })
            }
        }

    fun observeStaff(uid: String): Flow<List<StaffAccount>> =
        safeCollectionFlow(uid, "users/$uid/staff") {
            userDoc(uid).collection("staff").snapshots().map { snap ->
                snap.toObjects(StaffAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.username }
            }
        }

    fun observeProducts(uid: String): Flow<List<Product>> =
        safeCollectionFlow(uid, "users/$uid/products") {
            userDoc(uid).collection("products").snapshots().map { snap ->
                snap.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name }
            }
        }

    fun observeTransactions(uid: String): Flow<List<PosTransaction>> =
        safeCollectionFlow(uid, "users/$uid/transactions") {
            userDoc(uid).collection("transactions").snapshots().map { snap ->
                snap.toObjects(PosTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
        }

    fun observeAuditLogs(uid: String): Flow<List<AuditLog>> =
        safeCollectionFlow(uid, "users/$uid/auditLogs") {
            userDoc(uid).collection("auditLogs").snapshots().map { snap ->
                snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
        }

    fun observeCloudBackups(uid: String): Flow<List<CloudBackupRecord>> =
        safeCollectionFlow(uid, "users/$uid/cloudBackups") {
            userDoc(uid).collection("cloudBackups").snapshots().map { snap ->
                snap.toObjects(CloudBackupRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
        }

    suspend fun bootstrapWorkspaceIfNeeded(defaultVaultPassphrase: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val path = "users/$uid"
        return try {
            val snap = userDoc(uid).get().await()
            if (snap.exists()) return Result.success(Unit)

            val salt = CryptoManager.generateSaltBase64()
            val e2eeKey = CryptoManager.deriveE2eeKey(defaultVaultPassphrase, salt)
            val keyCheck = CryptoManager.computeKeyVerifier(e2eeKey)

            val batch = db.batch()
            batch.set(
                userDoc(uid),
                mapOf(
                    "userId" to uid,
                    "storeName" to "KasirPro Nusantara",
                    "e2eeSalt" to salt,
                    "e2eeKeyCheck" to keyCheck,
                    "qrisMerchantId" to "ID1020304050607",
                    "qrisMerchantName" to "KASIRPRO NUSANTARA",
                    "autoBackupIntervalHours" to 12L,
                    "lastBackupEpochMs" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val branches = listOf(
                Triple("branch_hq", "Cabang Pusat Jakarta", Triple("HQ-JKT", "Jl. Sudirman Kav. 52, Jakarta", true)),
                Triple("branch_bdg", "Cabang Dago Bandung", Triple("BDG-02", "Jl. Ir. H. Juanda No. 88, Bandung", false)),
                Triple("branch_sby", "Cabang Tunjungan Surabaya", Triple("SBY-03", "Jl. Tunjungan No. 41, Surabaya", false))
            )
            branches.forEach { (id, name, meta) ->
                batch.set(
                    userDoc(uid).collection("branches").document(id),
                    mapOf(
                        "userId" to uid,
                        "name" to name,
                        "code" to meta.first,
                        "address" to meta.second,
                        "phone" to "0215550199",
                        "headquarters" to meta.third,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Seed role-based staff accounts (all hashed with PBKDF2-HMAC-SHA256)
            val staffSeed = listOf(
                listOf("staff_central", "admin.pusat", "Admin Pusat", StaffRole.CENTRAL_ADMIN.name, "branch_hq", "pusat123"),
                listOf("staff_branch", "admin.cabang", "Admin Cabang JKT", StaffRole.BRANCH_ADMIN.name, "branch_hq", "cabang123"),
                listOf("staff_cashier", "kasir.01", "Rina (Kasir 01)", StaffRole.CASHIER.name, "branch_hq", "kasir123"),
                listOf("staff_cashier_bdg", "kasir.bdg", "Budi (Kasir BDG)", StaffRole.CASHIER.name, "branch_bdg", "kasir123")
            )
            staffSeed.forEach { row ->
                val digest = CryptoManager.hashPassword(row[5])
                batch.set(
                    userDoc(uid).collection("staff").document(row[0]),
                    mapOf(
                        "userId" to uid,
                        "username" to row[1],
                        "fullName" to row[2],
                        "role" to row[3],
                        "branchId" to row[4],
                        "passwordHash" to digest.hashBase64,
                        "passwordSalt" to digest.saltBase64,
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Seed starter products across branches
            val products = listOf(
                listOf("prod_jkt_1", "branch_hq", "KPI-001", "Kopi Susu Gula Aren", "Minuman", 24000.0, 10000.0, 48L, 10L, "cup"),
                listOf("prod_jkt_2", "branch_hq", "KPI-002", "Matcha Oat Latte", "Minuman", 32000.0, 14000.0, 22L, 8L, "cup"),
                listOf("prod_jkt_3", "branch_hq", "SNK-001", "Croissant Butter", "Roti & Kue", 28000.0, 12000.0, 16L, 5L, "pcs"),
                listOf("prod_jkt_4", "branch_hq", "MKN-001", "Rice Bowl Chicken Teriyaki", "Makanan", 38000.0, 18000.0, 25L, 6L, "porsi"),
                listOf("prod_jkt_5", "branch_hq", "BNS-001", "Biji Kopi Arabika 250g", "Merchandise", 85000.0, 48000.0, 4L, 5L, "pack"),
                listOf("prod_jkt_6", "branch_hq", "TEA-001", "Lychee Iced Tea", "Minuman", 22000.0, 7500.0, 35L, 8L, "cup"),
                listOf("prod_bdg_1", "branch_bdg", "KPI-101", "Espresso Tonic Bandung", "Minuman", 29000.0, 11000.0, 30L, 8L, "cup"),
                listOf("prod_bdg_2", "branch_bdg", "SNK-101", "Bolen Pisang Keju", "Roti & Kue", 45000.0, 22000.0, 14L, 5L, "box"),
                listOf("prod_sby_1", "branch_sby", "KPI-201", "Cold Brew Tunjungan", "Minuman", 27000.0, 10000.0, 28L, 8L, "botol"),
                listOf("prod_sby_2", "branch_sby", "MKN-201", "Nasi Katsu Sambal Matah", "Makanan", 36000.0, 17000.0, 18L, 5L, "porsi")
            )
            products.forEach { p ->
                batch.set(
                    userDoc(uid).collection("products").document(p[0] as String),
                    mapOf(
                        "userId" to uid,
                        "branchId" to p[1],
                        "sku" to p[2],
                        "name" to p[3],
                        "category" to p[4],
                        "price" to p[5],
                        "costPrice" to p[6],
                        "stock" to p[7],
                        "minStock" to p[8],
                        "unit" to p[9],
                        "active" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Seed 3 encrypted sample transactions across branches for immediate analytics visibility
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
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
            sampleTxs.forEachIndexed { idx, (txId, branchInfo, detail) ->
                val payloadJson = detail.third.toJsonString()
                val blob = CryptoManager.encrypt(payloadJson, e2eeKey)
                val itemCount = detail.third.items.sumOf { it.quantity }.toLong()
                batch.set(
                    userDoc(uid).collection("transactions").document(txId),
                    mapOf(
                        "userId" to uid,
                        "branchId" to branchInfo.first,
                        "branchName" to branchInfo.second,
                        "receiptNumber" to "INV-${dayKey.replace("-", "")}-00${idx + 1}",
                        "cashierId" to "staff_cashier",
                        "cashierName" to "Rina (Kasir 01)",
                        "paymentMethod" to detail.first,
                        "status" to TransactionStatus.COMPLETED.name,
                        "subtotal" to detail.second,
                        "discount" to 0.0,
                        "tax" to 0.0,
                        "totalAmount" to detail.second,
                        "paidAmount" to detail.second,
                        "changeAmount" to 0.0,
                        "itemCount" to itemCount,
                        "encryptedPayload" to blob.ciphertextBase64,
                        "e2eeIv" to blob.ivBase64,
                        "e2eeVerified" to true,
                        "whatsappSent" to (idx == 0),
                        "monthKey" to monthKey,
                        "dayKey" to dayKey,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.set(
                userDoc(uid).collection("auditLogs").document("log_seed_1"),
                mapOf(
                    "userId" to uid,
                    "branchId" to "branch_hq",
                    "actorId" to "system",
                    "actorName" to "KasirPro Cloud",
                    "actorRole" to "SYSTEM",
                    "actionType" to "WORKSPACE_INIT",
                    "summary" to "Inisialisasi workspace multi-cabang & enkripsi E2EE AES-256-GCM aktif.",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
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
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        require(cartItems.isNotEmpty()) { "Cart cannot be empty" }
        val txRef = userDoc(uid).collection("transactions").document()
        val logRef = userDoc(uid).collection("auditLogs").document()

        val subtotal = cartItems.sumOf { it.lineTotal }
        val safeDiscount = discount.coerceIn(0.0, subtotal)
        val total = (subtotal - safeDiscount).coerceAtLeast(0.0)
        val safePaid = if (paymentMethod == PaymentMethod.CASH) paidAmount.coerceAtLeast(total) else total
        val change = (safePaid - total).coerceAtLeast(0.0)
        val itemCount = cartItems.sumOf { it.quantity }.toLong()

        val now = Date()
        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(now)
        val receiptNo = "INV-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(now)}"

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
        val encryptedPayloadObj = DecryptedTxPayload(
            customerName = customerName.trim().take(80),
            customerPhone = customerPhone.trim().take(30),
            paymentReference = "${paymentMethod.name}-${receiptNo.takeLast(6)}",
            notes = "",
            items = lineItems
        )
        val blob = CryptoManager.encrypt(encryptedPayloadObj.toJsonString(), e2eeKey)

        return try {
            db.runTransaction { transaction ->
                val productSnapshots = cartItems.map { item ->
                    val ref = userDoc(uid).collection("products").document(item.product.id)
                    val snap = transaction.get(ref)
                    val currentStock = snap.getLong("stock") ?: 0L
                    if (currentStock < item.quantity) {
                        throw IllegalStateException("Stok '${item.product.name}' tidak mencukupi (sisa $currentStock).")
                    }
                    Triple(ref, currentStock - item.quantity, item.product.name)
                }

                // 1. Auto-deduct inventory stock
                productSnapshots.forEach { (ref, newStock, _) ->
                    transaction.update(
                        ref,
                        mapOf(
                            "stock" to newStock,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                // 2. Write E2EE encrypted transaction
                transaction.set(
                    txRef,
                    mapOf(
                        "userId" to uid,
                        "branchId" to branch.id,
                        "branchName" to branch.name.take(80),
                        "receiptNumber" to receiptNo,
                        "cashierId" to cashier.id,
                        "cashierName" to cashier.fullName.take(80),
                        "paymentMethod" to paymentMethod.name,
                        "status" to TransactionStatus.COMPLETED.name,
                        "subtotal" to subtotal,
                        "discount" to safeDiscount,
                        "tax" to 0.0,
                        "totalAmount" to total,
                        "paidAmount" to safePaid,
                        "changeAmount" to change,
                        "itemCount" to itemCount,
                        "encryptedPayload" to blob.ciphertextBase64,
                        "e2eeIv" to blob.ivBase64,
                        "e2eeVerified" to true,
                        "whatsappSent" to whatsappSent,
                        "monthKey" to monthKey,
                        "dayKey" to dayKey,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                // 3. Write immutable AuditLog
                transaction.set(
                    logRef,
                    mapOf(
                        "userId" to uid,
                        "branchId" to branch.id,
                        "actorId" to cashier.id,
                        "actorName" to cashier.fullName.take(80),
                        "actorRole" to cashier.role,
                        "actionType" to "CHECKOUT_${paymentMethod.name}",
                        "summary" to "Transaksi $receiptNo (${itemCount} item) Rp ${total.toLong()} di ${branch.name}".take(300),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()

            val createdTx = PosTransaction(
                id = txRef.id,
                userId = uid,
                branchId = branch.id,
                branchName = branch.name,
                receiptNumber = receiptNo,
                cashierId = cashier.id,
                cashierName = cashier.fullName,
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
                dayKey = dayKey
            )
            Result.success(createdTx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            Result.failure(e)
        }
    }

    suspend fun markWhatsappSent(txId: String): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/transactions/$txId"
        return try {
            val uid = requireUserId()
            require(txId.isNotBlank())
            userDoc(uid).collection("transactions").document(txId)
                .update(mapOf("whatsappSent" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun voidTransaction(txId: String, actor: StaffAccount, branchId: String): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/transactions/$txId"
        return try {
            val uid = requireUserId()
            require(txId.isNotBlank())
            val batch = db.batch()
            batch.update(
                userDoc(uid).collection("transactions").document(txId),
                mapOf("status" to TransactionStatus.VOIDED.name, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.set(
                userDoc(uid).collection("auditLogs").document(),
                mapOf(
                    "userId" to uid,
                    "branchId" to branchId,
                    "actorId" to actor.id,
                    "actorName" to actor.fullName.take(80),
                    "actorRole" to actor.role,
                    "actionType" to "VOID_TRANSACTION",
                    "summary" to "Membatalkan transaksi $txId".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adjustProductStock(product: Product, newStock: Long, actor: StaffAccount): Result<Unit> {
        val safeStock = newStock.coerceAtLeast(0L)
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/products/${product.id}"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            batch.update(
                userDoc(uid).collection("products").document(product.id),
                mapOf("stock" to safeStock, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.set(
                userDoc(uid).collection("auditLogs").document(),
                mapOf(
                    "userId" to uid,
                    "branchId" to product.branchId,
                    "actorId" to actor.id,
                    "actorName" to actor.fullName.take(80),
                    "actorRole" to actor.role,
                    "actionType" to "STOCK_ADJUST",
                    "summary" to "Stok ${product.name}: ${product.stock} -> $safeStock ${product.unit}".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertProduct(product: Product, actor: StaffAccount): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/products"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("products")
            val isNew = product.id.isBlank()
            val ref = if (isNew) col.document() else col.document(product.id)
            val batch = db.batch()
            if (isNew) {
                batch.set(
                    ref,
                    mapOf(
                        "userId" to uid,
                        "branchId" to product.branchId,
                        "sku" to product.sku.trim().take(40).ifBlank { "SKU-${System.currentTimeMillis() % 10000}" },
                        "name" to product.name.trim().take(100),
                        "category" to product.category.trim().take(50).ifBlank { "Umum" },
                        "price" to product.price.coerceAtLeast(0.0),
                        "costPrice" to product.costPrice.coerceAtLeast(0.0),
                        "stock" to product.stock.coerceAtLeast(0L),
                        "minStock" to product.minStock.coerceAtLeast(0L),
                        "unit" to product.unit.trim().take(20).ifBlank { "pcs" },
                        "active" to product.active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    ref,
                    mapOf(
                        "sku" to product.sku.trim().take(40),
                        "name" to product.name.trim().take(100),
                        "category" to product.category.trim().take(50),
                        "price" to product.price.coerceAtLeast(0.0),
                        "costPrice" to product.costPrice.coerceAtLeast(0.0),
                        "stock" to product.stock.coerceAtLeast(0L),
                        "minStock" to product.minStock.coerceAtLeast(0L),
                        "unit" to product.unit.trim().take(20),
                        "active" to product.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.set(
                userDoc(uid).collection("auditLogs").document(),
                mapOf(
                    "userId" to uid,
                    "branchId" to product.branchId,
                    "actorId" to actor.id,
                    "actorName" to actor.fullName.take(80),
                    "actorRole" to actor.role,
                    "actionType" to if (isNew) "PRODUCT_ADD" else "PRODUCT_EDIT",
                    "summary" to "${if (isNew) "Menambah" else "Memperbarui"} produk ${product.name}".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createBranch(
        name: String,
        code: String,
        address: String,
        phone: String,
        actor: StaffAccount
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/branches"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("branches").document()
            val batch = db.batch()
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "name" to name.trim().take(80),
                    "code" to code.trim().uppercase().take(20),
                    "address" to address.trim().take(200),
                    "phone" to phone.trim().take(30),
                    "headquarters" to false,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                userDoc(uid).collection("auditLogs").document(),
                mapOf(
                    "userId" to uid,
                    "branchId" to ref.id,
                    "actorId" to actor.id,
                    "actorName" to actor.fullName.take(80),
                    "actorRole" to actor.role,
                    "actionType" to "BRANCH_CREATE",
                    "summary" to "Membuka cabang baru: $name ($code)".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createStaffAccount(
        username: String,
        fullName: String,
        role: StaffRole,
        branchId: String,
        rawPassword: String,
        actor: StaffAccount
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/staff"
        return try {
            val uid = requireUserId()
            require(username.trim().length >= 3) { "Username minimal 3 karakter" }
            require(rawPassword.length >= 6) { "Password minimal 6 karakter" }
            val digest = CryptoManager.hashPassword(rawPassword)
            val ref = userDoc(uid).collection("staff").document()
            val batch = db.batch()
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "username" to username.trim().lowercase().take(40),
                    "fullName" to fullName.trim().take(80),
                    "role" to role.name,
                    "branchId" to branchId,
                    "passwordHash" to digest.hashBase64,
                    "passwordSalt" to digest.saltBase64,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                userDoc(uid).collection("auditLogs").document(),
                mapOf(
                    "userId" to uid,
                    "branchId" to branchId,
                    "actorId" to actor.id,
                    "actorName" to actor.fullName.take(80),
                    "actorRole" to actor.role,
                    "actionType" to "STAFF_CREATE",
                    "summary" to "Menambahkan akun ${role.name}: $fullName (@$username)".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Allows Central Admin to change password for ANY user in any branch.
     */
    suspend fun changeStaffPassword(
        targetStaff: StaffAccount,
        newPassword: String,
        actor: StaffAccount
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/staff/${targetStaff.id}"
        return try {
            val uid = requireUserId()
            require(newPassword.length >= 6) { "Password baru minimal 6 karakter" }
            val digest = CryptoManager.hashPassword(newPassword)
            val batch = db.batch()
            batch.update(
                userDoc(uid).collection("staff").document(targetStaff.id),
                mapOf(
                    "passwordHash" to digest.hashBase64,
                    "passwordSalt" to digest.saltBase64,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                userDoc(uid).collection("auditLogs").document(),
                mapOf(
                    "userId" to uid,
                    "branchId" to targetStaff.branchId,
                    "actorId" to actor.id,
                    "actorName" to actor.fullName.take(80),
                    "actorRole" to actor.role,
                    "actionType" to "PASSWORD_CHANGE",
                    "summary" to "${actor.fullName} mengubah password untuk ${targetStaff.fullName} (@${targetStaff.username})".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleStaffActive(targetStaff: StaffAccount, active: Boolean, actor: StaffAccount): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/staff/${targetStaff.id}"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            batch.update(
                userDoc(uid).collection("staff").document(targetStaff.id),
                mapOf(
                    "fullName" to targetStaff.fullName,
                    "role" to targetStaff.role,
                    "branchId" to targetStaff.branchId,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                userDoc(uid).collection("auditLogs").document(),
                mapOf(
                    "userId" to uid,
                    "branchId" to targetStaff.branchId,
                    "actorId" to actor.id,
                    "actorName" to actor.fullName.take(80),
                    "actorRole" to actor.role,
                    "actionType" to "STAFF_STATUS",
                    "summary" to "${targetStaff.fullName} diubah status menjadi ${if (active) "AKTIF" else "NONAKTIF"}".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateWorkspaceSettings(
        workspace: WorkspaceConfig,
        storeName: String,
        qrisMerchantId: String,
        qrisMerchantName: String,
        autoBackupIntervalHours: Long,
        newE2eeSalt: String? = null,
        newE2eeKeyCheck: String? = null
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}"
        return try {
            val uid = requireUserId()
            userDoc(uid).update(
                mapOf(
                    "storeName" to storeName.trim().take(80).ifBlank { workspace.storeName },
                    "e2eeSalt" to (newE2eeSalt ?: workspace.e2eeSalt),
                    "e2eeKeyCheck" to (newE2eeKeyCheck ?: workspace.e2eeKeyCheck),
                    "qrisMerchantId" to qrisMerchantId.trim().take(128).ifBlank { workspace.qrisMerchantId },
                    "qrisMerchantName" to qrisMerchantName.trim().take(80).ifBlank { workspace.qrisMerchantName },
                    "autoBackupIntervalHours" to autoBackupIntervalHours.coerceIn(1L, 168L),
                    "lastBackupEpochMs" to workspace.lastBackupEpochMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createCloudBackup(
        backupType: String,
        branches: List<Branch>,
        products: List<Product>,
        transactions: List<PosTransaction>,
        actorName: String,
        e2eeKey: SecretKey
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/cloudBackups"
        return try {
            val uid = requireUserId()
            val snapshotJson = JSONObject().apply {
                put("version", 1)
                put("timestamp", System.currentTimeMillis())
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
            val nowMs = System.currentTimeMillis()

            val batch = db.batch()
            val bkpRef = userDoc(uid).collection("cloudBackups").document()
            batch.set(
                bkpRef,
                mapOf(
                    "userId" to uid,
                    "backupType" to if (backupType == "AUTO") "AUTO" else "MANUAL",
                    "encryptedSnapshot" to blob.ciphertextBase64.take(499000),
                    "e2eeIv" to blob.ivBase64,
                    "checksumSha256" to checksum,
                    "recordCount" to recordCount,
                    "createdBy" to actorName.take(80),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userDoc(uid),
                mapOf(
                    "lastBackupEpochMs" to nowMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreFromCloudBackup(
        backup: CloudBackupRecord,
        e2eeKey: SecretKey,
        actor: StaffAccount
    ): Result<Int> {
        val path = "users/${auth.currentUser?.uid ?: "unauth"}/cloudBackups/${backup.id}"
        return try {
            val uid = requireUserId()
            val decrypted = CryptoManager.decrypt(backup.encryptedSnapshot, backup.e2eeIv, e2eeKey)
                ?: return Result.failure(IllegalStateException("Gagal dekripsi backup. Pastikan kunci E2EE terbuka dan sesuai."))
            val actualChecksum = CryptoManager.sha256Hex(decrypted)
            if (actualChecksum != backup.checksumSha256) {
                return Result.failure(IllegalStateException("Checksum SHA-256 tidak cocok! Data backup korup."))
            }

            val root = JSONObject(decrypted)
            val productsArr = root.optJSONArray("products") ?: JSONArray()
            val batch = db.batch()
            var restoredCount = 0
            for (i in 0 until productsArr.length()) {
                val p = productsArr.getJSONObject(i)
                val pid = p.optString("id")
                val branchId = p.optString("branchId")
                if (pid.isNotBlank() && branchId.isNotBlank()) {
                    batch.set(
                        userDoc(uid).collection("products").document(pid),
                        mapOf(
                            "userId" to uid,
                            "branchId" to branchId,
                            "sku" to p.optString("sku", "SKU-$i"),
                            "name" to p.optString("name", "Item"),
                            "category" to p.optString("category", "Umum"),
                            "price" to p.optDouble("price", 0.0),
                            "costPrice" to p.optDouble("costPrice", 0.0),
                            "stock" to p.optLong("stock", 0L),
                            "minStock" to p.optLong("minStock", 5L),
                            "unit" to p.optString("unit", "pcs"),
                            "active" to p.optBoolean("active", true),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    restoredCount++
                }
            }
            batch.set(
                userDoc(uid).collection("auditLogs").document(),
                mapOf(
                    "userId" to uid,
                    "branchId" to actor.branchId,
                    "actorId" to actor.id,
                    "actorName" to actor.fullName.take(80),
                    "actorRole" to actor.role,
                    "actionType" to "BACKUP_RESTORE",
                    "summary" to "Memulihkan $restoredCount produk dari Cloud Backup (${backup.backupType})".take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(restoredCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
