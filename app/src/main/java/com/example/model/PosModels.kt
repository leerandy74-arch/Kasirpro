package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

enum class StaffRole {
    CENTRAL_ADMIN,
    BRANCH_ADMIN,
    CASHIER;

    companion object {
        fun fromRaw(raw: String?): StaffRole =
            entries.firstOrNull { it.name == raw } ?: CASHIER
    }
}

enum class PaymentMethod {
    QRIS,
    CASH,
    CARD,
    EWALLET;

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

enum class TransactionStatus {
    COMPLETED,
    VOIDED;

    companion object {
        fun fromRaw(raw: String?): TransactionStatus =
            entries.firstOrNull { it.name == raw } ?: COMPLETED
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class WorkspaceConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storeName: String = "KasirPro Store",
    val e2eeSalt: String = "",
    val e2eeKeyCheck: String = "",
    val qrisMerchantId: String = "ID1020304050607",
    val qrisMerchantName: String = "KASIRPRO STORE",
    val autoBackupIntervalHours: Long = 12L,
    val lastBackupEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Branch(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val code: String = "",
    val address: String = "",
    val phone: String = "",
    val headquarters: Boolean = false,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StaffAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val fullName: String = "",
    val role: String = StaffRole.CASHIER.name,
    val branchId: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val staffRole: StaffRole
        get() = StaffRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val branchId: String = "",
    val sku: String = "",
    val name: String = "",
    val category: String = "Umum",
    val price: Double = 0.0,
    val costPrice: Double = 0.0,
    val stock: Long = 0L,
    val minStock: Long = 5L,
    val unit: String = "pcs",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val lowStock: Boolean
        get() = stock <= minStock
}

data class CartItem(
    val product: Product,
    val quantity: Int
) {
    val lineTotal: Double
        get() = product.price * quantity

    val lineCost: Double
        get() = product.costPrice * quantity
}

data class ReceiptLineItem(
    val productId: String,
    val sku: String,
    val name: String,
    val unit: String,
    val unitPrice: Double,
    val costPrice: Double,
    val quantity: Int
) {
    val lineTotal: Double
        get() = unitPrice * quantity
}

data class DecryptedTxPayload(
    val customerName: String = "",
    val customerPhone: String = "",
    val paymentReference: String = "",
    val notes: String = "",
    val items: List<ReceiptLineItem> = emptyList()
) {
    fun toJsonString(): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("productId", item.productId)
                    put("sku", item.sku)
                    put("name", item.name)
                    put("unit", item.unit)
                    put("unitPrice", item.unitPrice)
                    put("costPrice", item.costPrice)
                    put("quantity", item.quantity)
                }
            )
        }
        return JSONObject().apply {
            put("customerName", customerName)
            put("customerPhone", customerPhone)
            put("paymentReference", paymentReference)
            put("notes", notes)
            put("items", arr)
        }.toString()
    }

    companion object {
        fun fromJsonString(raw: String): DecryptedTxPayload? {
            return try {
                val obj = JSONObject(raw)
                val arr = obj.optJSONArray("items") ?: JSONArray()
                val parsedItems = buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        add(
                            ReceiptLineItem(
                                productId = item.optString("productId"),
                                sku = item.optString("sku"),
                                name = item.optString("name"),
                                unit = item.optString("unit", "pcs"),
                                unitPrice = item.optDouble("unitPrice", 0.0),
                                costPrice = item.optDouble("costPrice", 0.0),
                                quantity = item.optInt("quantity", 1)
                            )
                        )
                    }
                }
                DecryptedTxPayload(
                    customerName = obj.optString("customerName"),
                    customerPhone = obj.optString("customerPhone"),
                    paymentReference = obj.optString("paymentReference"),
                    notes = obj.optString("notes"),
                    items = parsedItems
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Keep
@IgnoreExtraProperties
data class PosTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val receiptNumber: String = "",
    val cashierId: String = "",
    val cashierName: String = "",
    val paymentMethod: String = PaymentMethod.CASH.name,
    val status: String = TransactionStatus.COMPLETED.name,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val changeAmount: Double = 0.0,
    val itemCount: Long = 0L,
    val encryptedPayload: String = "",
    val e2eeIv: String = "",
    val e2eeVerified: Boolean = true,
    val whatsappSent: Boolean = false,
    val monthKey: String = "",
    val dayKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val paymentEnum: PaymentMethod
        get() = PaymentMethod.fromRaw(paymentMethod)

    val statusEnum: TransactionStatus
        get() = TransactionStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val branchId: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val actorRole: String = StaffRole.CASHIER.name,
    val actionType: String = "",
    val summary: String = "",
    val encryptedDetail: String? = null,
    val e2eeIv: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudBackupRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val backupType: String = "MANUAL",
    val encryptedSnapshot: String = "",
    val e2eeIv: String = "",
    val checksumSha256: String = "",
    val recordCount: Long = 0L,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
