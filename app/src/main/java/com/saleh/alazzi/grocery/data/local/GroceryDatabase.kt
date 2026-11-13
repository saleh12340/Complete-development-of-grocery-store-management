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
        AppErrorLogEntity::class,
        JournalEntryEntity::class,
        JournalEntryLineEntity::class,
        AccountingPeriodEntity::class
    ],
    version = 3,
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
    abstract fun journal(): JournalDao

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

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `createdByUserId` TEXT")
                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `createdByUserName` TEXT")
                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `updatedByUserId` TEXT")
                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `updatedByUserName` TEXT")
                db.execSQL("ALTER TABLE `sales_invoices` ADD COLUMN `updatedAt` INTEGER")

                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `createdByUserId` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `createdByUserName` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `updatedByUserId` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `updatedByUserName` TEXT")
                db.execSQL("ALTER TABLE `purchase_invoices` ADD COLUMN `updatedAt` INTEGER")

                db.execSQL("ALTER TABLE `customer_transactions` ADD COLUMN `balanceBefore` INTEGER")
                db.execSQL("ALTER TABLE `customer_transactions` ADD COLUMN `createdByUserId` TEXT")
                db.execSQL("ALTER TABLE `customer_transactions` ADD COLUMN `createdByUserName` TEXT")
                db.execSQL("ALTER TABLE `customer_transactions` ADD COLUMN `updatedByUserId` TEXT")
                db.execSQL("ALTER TABLE `customer_transactions` ADD COLUMN `updatedByUserName` TEXT")
                db.execSQL("ALTER TABLE `customer_transactions` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL("ALTER TABLE `customer_transactions` ADD COLUMN `isReversed` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `supplier_transactions` ADD COLUMN `balanceBefore` INTEGER")
                db.execSQL("ALTER TABLE `supplier_transactions` ADD COLUMN `createdByUserId` TEXT")
                db.execSQL("ALTER TABLE `supplier_transactions` ADD COLUMN `createdByUserName` TEXT")
                db.execSQL("ALTER TABLE `supplier_transactions` ADD COLUMN `updatedByUserId` TEXT")
                db.execSQL("ALTER TABLE `supplier_transactions` ADD COLUMN `updatedByUserName` TEXT")
                db.execSQL("ALTER TABLE `supplier_transactions` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL("ALTER TABLE `supplier_transactions` ADD COLUMN `isReversed` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `cash_transactions` ADD COLUMN `createdByUserId` TEXT")
                db.execSQL("ALTER TABLE `cash_transactions` ADD COLUMN `createdByUserName` TEXT")

                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `createdByUserId` TEXT")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `createdByUserName` TEXT")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `updatedByUserId` TEXT")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `updatedByUserName` TEXT")
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `isReversed` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `transfers` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL("ALTER TABLE `transfers` ADD COLUMN `createdByUserId` TEXT")
                db.execSQL("ALTER TABLE `transfers` ADD COLUMN `createdByUserName` TEXT")
                db.execSQL("ALTER TABLE `transfers` ADD COLUMN `updatedByUserId` TEXT")
                db.execSQL("ALTER TABLE `transfers` ADD COLUMN `updatedByUserName` TEXT")

                db.execSQL("ALTER TABLE `users` ADD COLUMN `permissions` TEXT")

                db.execSQL("ALTER TABLE `invoice_payments` ADD COLUMN `createdByUserId` TEXT")
                db.execSQL("ALTER TABLE `invoice_payments` ADD COLUMN `createdByUserName` TEXT")
                db.execSQL("ALTER TABLE `invoice_payments` ADD COLUMN `updatedByUserId` TEXT")
                db.execSQL("ALTER TABLE `invoice_payments` ADD COLUMN `updatedByUserName` TEXT")
                db.execSQL("ALTER TABLE `invoice_payments` ADD COLUMN `updatedAt` INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_entries` (
                        `id` TEXT NOT NULL,
                        `entryNumber` TEXT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `entryType` TEXT NOT NULL,
                        `debitAccount` TEXT NOT NULL,
                        `creditAccount` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceId` TEXT,
                        `createdByUserId` TEXT,
                        `createdByUserName` TEXT,
                        `updatedByUserId` TEXT,
                        `updatedByUserName` TEXT,
                        `status` TEXT NOT NULL,
                        `reversedEntryId` TEXT,
                        `reversalEntryId` TEXT,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_entryNumber` ON `journal_entries` (`entryNumber`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_operationId` ON `journal_entries` (`operationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_createdAt` ON `journal_entries` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_entryType` ON `journal_entries` (`entryType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_sourceId` ON `journal_entries` (`sourceId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_entry_lines` (
                        `id` TEXT NOT NULL,
                        `entryId` TEXT NOT NULL,
                        `accountCode` TEXT NOT NULL,
                        `accountName` TEXT NOT NULL,
                        `debit` INTEGER NOT NULL,
                        `credit` INTEGER NOT NULL,
                        `memo` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entry_lines_entryId` ON `journal_entry_lines` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entry_lines_accountName` ON `journal_entry_lines` (`accountName`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounting_periods` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `startAt` INTEGER NOT NULL,
                        `endAt` INTEGER NOT NULL,
                        `isClosed` INTEGER NOT NULL DEFAULT 1,
                        `closedByUserId` TEXT,
                        `closedByUserName` TEXT,
                        `closedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounting_periods_startAt` ON `accounting_periods` (`startAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounting_periods_endAt` ON `accounting_periods` (`endAt`)")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
