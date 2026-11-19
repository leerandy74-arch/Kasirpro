package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkspaceEntity::class,
        BranchEntity::class,
        StaffEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        AuditLogEntity::class,
        BackupEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KasirProDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun branchDao(): BranchDao
    abstract fun staffDao(): StaffDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun backupDao(): BackupDao
    abstract fun txRunnerDao(): PosTransactionRunnerDao

    companion object {
        @Volatile
        private var INSTANCE: KasirProDatabase? = null

        fun getInstance(context: Context): KasirProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KasirProDatabase::class.java,
                    "kasirpro_pos.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
