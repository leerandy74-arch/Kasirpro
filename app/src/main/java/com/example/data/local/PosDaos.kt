package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspace_config LIMIT 1")
    fun observeWorkspace(): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM workspace_config LIMIT 1")
    suspend fun getWorkspaceOnce(): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workspace: WorkspaceEntity)

    @Query("UPDATE workspace_config SET lastBackupEpochMs = :epochMs, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateLastBackup(id: String, epochMs: Long, updatedAtMs: Long)
}

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY headquarters DESC, name ASC")
    fun observeAll(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches ORDER BY headquarters DESC, name ASC")
    suspend fun getAllOnce(): List<BranchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BranchEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(branch: BranchEntity)
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY username ASC")
    fun observeAll(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff ORDER BY username ASC")
    suspend fun getAllOnce(): List<StaffEntity>

    @Query("SELECT * FROM staff WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findByUsername(username: String): StaffEntity?

    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): StaffEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StaffEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(staff: StaffEntity)

    @Query("UPDATE staff SET passwordHash = :hash, passwordSalt = :salt, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun updatePassword(id: String, hash: String, salt: String, nowMs: Long)

    @Query("UPDATE staff SET active = :active, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun updateActive(id: String, active: Boolean, nowMs: Long)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Query("UPDATE products SET stock = :newStock, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun updateStock(id: String, newStock: Long, nowMs: Long)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TransactionEntity)

    @Query("UPDATE transactions SET whatsappSent = 1, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun markWhatsappSent(id: String, nowMs: Long)

    @Query("UPDATE transactions SET status = :status, updatedAtMs = :nowMs WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, nowMs: Long)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AuditLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM cloud_backups ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<BackupEntity>>

    @Query("SELECT * FROM cloud_backups ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<BackupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(backup: BackupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BackupEntity>)
}

@Dao
interface PosTransactionRunnerDao {
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun findProduct(productId: String): ProductEntity?

    @Query("UPDATE products SET stock = :newStock, updatedAtMs = :nowMs WHERE id = :productId")
    suspend fun updateProductStock(productId: String, newStock: Long, nowMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Transaction
    suspend fun executeCheckoutAtomic(
        stockDeductions: List<Pair<String, Int>>,
        tx: TransactionEntity,
        auditLog: AuditLogEntity,
        nowMs: Long
    ) {
        stockDeductions.forEach { (pid, qty) ->
            val current = findProduct(pid)
                ?: throw IllegalStateException("Produk tidak ditemukan.")
            if (current.stock < qty) {
                throw IllegalStateException("Stok '${current.name}' tidak mencukupi (sisa ${current.stock}).")
            }
            updateProductStock(pid, current.stock - qty, nowMs)
        }
        insertTransaction(tx)
        insertAuditLog(auditLog)
    }
}
