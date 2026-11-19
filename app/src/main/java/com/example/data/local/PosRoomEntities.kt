package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AuditLog
import com.example.model.Branch
import com.example.model.CloudBackupRecord
import com.example.model.PosTransaction
import com.example.model.Product
import com.example.model.StaffAccount
import com.example.model.WorkspaceConfig
import com.google.firebase.Timestamp
import java.util.Date

@Entity(tableName = "workspace_config")
data class WorkspaceEntity(
    @PrimaryKey val id: String = "default_workspace",
    val storeName: String,
    val e2eeSalt: String,
    val e2eeKeyCheck: String,
    val qrisMerchantId: String,
    val qrisMerchantName: String,
    val autoBackupIntervalHours: Long,
    val lastBackupEpochMs: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toModel(userId: String = "local"): WorkspaceConfig = WorkspaceConfig(
        id = id,
        userId = userId,
        storeName = storeName,
        e2eeSalt = e2eeSalt,
        e2eeKeyCheck = e2eeKeyCheck,
        qrisMerchantId = qrisMerchantId,
        qrisMerchantName = qrisMerchantName,
        autoBackupIntervalHours = autoBackupIntervalHours,
        lastBackupEpochMs = lastBackupEpochMs,
        createdAt = Timestamp(Date(createdAtMs)),
        updatedAt = Timestamp(Date(updatedAtMs))
    )
}

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val address: String,
    val phone: String,
    val headquarters: Boolean,
    val active: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toModel(userId: String = "local"): Branch = Branch(
        id = id,
        userId = userId,
        name = name,
        code = code,
        address = address,
        phone = phone,
        headquarters = headquarters,
        active = active,
        createdAt = Timestamp(Date(createdAtMs)),
        updatedAt = Timestamp(Date(updatedAtMs))
    )
}

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey val id: String,
    val username: String,
    val fullName: String,
    val role: String,
    val branchId: String,
    val passwordHash: String,
    val passwordSalt: String,
    val active: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toModel(userId: String = "local"): StaffAccount = StaffAccount(
        id = id,
        userId = userId,
        username = username,
        fullName = fullName,
        role = role,
        branchId = branchId,
        passwordHash = passwordHash,
        passwordSalt = passwordSalt,
        active = active,
        createdAt = Timestamp(Date(createdAtMs)),
        updatedAt = Timestamp(Date(updatedAtMs))
    )
}

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val branchId: String,
    val sku: String,
    val name: String,
    val category: String,
    val price: Double,
    val costPrice: Double,
    val stock: Long,
    val minStock: Long,
    val unit: String,
    val active: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toModel(userId: String = "local"): Product = Product(
        id = id,
        userId = userId,
        branchId = branchId,
        sku = sku,
        name = name,
        category = category,
        price = price,
        costPrice = costPrice,
        stock = stock,
        minStock = minStock,
        unit = unit,
        active = active,
        createdAt = Timestamp(Date(createdAtMs)),
        updatedAt = Timestamp(Date(updatedAtMs))
    )
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val branchId: String,
    val branchName: String,
    val receiptNumber: String,
    val cashierId: String,
    val cashierName: String,
    val paymentMethod: String,
    val status: String,
    val subtotal: Double,
    val discount: Double,
    val tax: Double,
    val totalAmount: Double,
    val paidAmount: Double,
    val changeAmount: Double,
    val itemCount: Long,
    val encryptedPayload: String,
    val e2eeIv: String,
    val e2eeVerified: Boolean,
    val whatsappSent: Boolean,
    val monthKey: String,
    val dayKey: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toModel(userId: String = "local"): PosTransaction = PosTransaction(
        id = id,
        userId = userId,
        branchId = branchId,
        branchName = branchName,
        receiptNumber = receiptNumber,
        cashierId = cashierId,
        cashierName = cashierName,
        paymentMethod = paymentMethod,
        status = status,
        subtotal = subtotal,
        discount = discount,
        tax = tax,
        totalAmount = totalAmount,
        paidAmount = paidAmount,
        changeAmount = changeAmount,
        itemCount = itemCount,
        encryptedPayload = encryptedPayload,
        e2eeIv = e2eeIv,
        e2eeVerified = e2eeVerified,
        whatsappSent = whatsappSent,
        monthKey = monthKey,
        dayKey = dayKey,
        createdAt = Timestamp(Date(createdAtMs)),
        updatedAt = Timestamp(Date(updatedAtMs))
    )
}

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val branchId: String,
    val actorId: String,
    val actorName: String,
    val actorRole: String,
    val actionType: String,
    val summary: String,
    val createdAtMs: Long
) {
    fun toModel(userId: String = "local"): AuditLog = AuditLog(
        id = id,
        userId = userId,
        branchId = branchId,
        actorId = actorId,
        actorName = actorName,
        actorRole = actorRole,
        actionType = actionType,
        summary = summary,
        createdAt = Timestamp(Date(createdAtMs))
    )
}

@Entity(tableName = "cloud_backups")
data class BackupEntity(
    @PrimaryKey val id: String,
    val backupType: String,
    val encryptedSnapshot: String,
    val e2eeIv: String,
    val checksumSha256: String,
    val recordCount: Long,
    val createdBy: String,
    val syncedToCloud: Boolean,
    val createdAtMs: Long
) {
    fun toModel(userId: String = "local"): CloudBackupRecord = CloudBackupRecord(
        id = id,
        userId = userId,
        backupType = backupType,
        encryptedSnapshot = encryptedSnapshot,
        e2eeIv = e2eeIv,
        checksumSha256 = checksumSha256,
        recordCount = recordCount,
        createdBy = createdBy,
        createdAt = Timestamp(Date(createdAtMs))
    )
}
