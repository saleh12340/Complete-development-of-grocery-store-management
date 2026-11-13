package com.saleh.alazzi.grocery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saleh.alazzi.grocery.data.local.dao.*
import com.saleh.alazzi.grocery.data.local.entity.*

@Database(
    entities = [
        CustomerEntity::class,
        SupplierEntity::class,
        CategoryEntity::class,
        UnitEntity::class,
        ItemEntity::class,
        SalesInvoiceEntity::class,
        SalesInvoiceItemEntity::class,
        PurchaseInvoiceEntity::class,
        PurchaseInvoiceItemEntity::class,
        CustomerTransactionEntity::class,
        SupplierTransactionEntity::class,
        StockMovementEntity::class,
        CashTransactionEntity::class,
        ExpenseEntity::class,
        TransferEntity::class,
        ReturnEntity::class,
        ReturnItemEntity::class,
        NoteEntity::class,
        AppSettingEntity::class,
        ActivityLogEntity::class,
        UserEntity::class,
        BackupHistoryEntity::class,
        InvoicePaymentEntity::class,
        CashReconciliationEntity::class,
        AppErrorLogEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class GroceryDatabase : RoomDatabase() {
    abstract fun customers(): CustomerDao
    abstract fun suppliers(): SupplierDao
    abstract fun items(): ItemDao
    abstract fun salesInvoices(): SalesInvoiceDao
    abstract fun purchaseInvoices(): PurchaseInvoiceDao
    abstract fun invoicePayments(): InvoicePaymentDao
    abstract fun ledger(): LedgerDao
    abstract fun stock(): StockDao
    abstract fun cash(): CashDao
    abstract fun backup(): BackupDao
    abstract fun returns(): ReturnDao
    abstract fun expenses(): ExpenseDao
    abstract fun transfers(): TransferDao
    abstract fun references(): ReferenceDao

    companion object {
        @Volatile private var INSTANCE: GroceryDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `customers` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `suppliers` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `storeNameSnapshot` TEXT")
                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `storePhoneSnapshot` TEXT")
                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `storeAddressSnapshot` TEXT")
                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `storeFooterSnapshot` TEXT")

                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `storeNameSnapshot` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `storePhoneSnapshot` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `storeAddressSnapshot` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `storeFooterSnapshot` TEXT")

                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL("ALTER TABLE `transfers` ADD COLUMN `senderName` TEXT")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'عام'")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `activity_logs` ADD COLUMN `actorName` TEXT")
                db.execSQL("ALTER TABLE `activity_logs` ADD COLUMN `previousValue` TEXT")
                db.execSQL("ALTER TABLE `activity_logs` ADD COLUMN `newValue` TEXT")
                db.execSQL("ALTER TABLE `activity_logs` ADD COLUMN `reason` TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `invoice_payments` (
                        `id` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `invoiceType` TEXT NOT NULL,
                        `invoiceId` TEXT NOT NULL,
                        `partyId` TEXT,
                        `amount` INTEGER NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_invoice_payments_operationId` ON `invoice_payments` (`operationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_payments_invoiceId` ON `invoice_payments` (`invoiceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_payments_partyId` ON `invoice_payments` (`partyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_payments_createdAt` ON `invoice_payments` (`createdAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cash_reconciliations` (
                        `id` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `bookBalance` INTEGER NOT NULL,
                        `actualCount` INTEGER NOT NULL,
                        `difference` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `actorId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cash_reconciliations_operationId` ON `cash_reconciliations` (`operationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_reconciliations_createdAt` ON `cash_reconciliations` (`createdAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_error_logs` (
                        `id` TEXT NOT NULL,
                        `tag` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `details` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_error_logs_createdAt` ON `app_error_logs` (`createdAt`)")
            }
        }

        fun getInstance(context: Context): GroceryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GroceryDatabase::class.java,
                    "al_azzi_grocery.db"
                )
                    // Never discard financial or inventory data during an upgrade.
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
