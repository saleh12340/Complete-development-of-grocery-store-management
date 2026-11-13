package com.saleh.alazzi.grocery.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupPayload(
    val formatVersion: Int,
    val createdAt: Long,
    val appVersion: String? = "1.0.0",
    val customers: List<CustomerEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val units: List<UnitEntity> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val salesInvoices: List<SalesInvoiceEntity> = emptyList(),
    val salesInvoiceItems: List<SalesInvoiceItemEntity> = emptyList(),
    val purchaseInvoices: List<PurchaseInvoiceEntity> = emptyList(),
    val purchaseInvoiceItems: List<PurchaseInvoiceItemEntity> = emptyList(),
    val customerTransactions: List<CustomerTransactionEntity> = emptyList(),
    val supplierTransactions: List<SupplierTransactionEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val cashTransactions: List<CashTransactionEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val transfers: List<TransferEntity> = emptyList(),
    val returns: List<ReturnEntity> = emptyList(),
    val returnItems: List<ReturnItemEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val settings: List<AppSettingEntity> = emptyList(),
    val users: List<UserEntity> = emptyList(),
    val activityLogs: List<ActivityLogEntity> = emptyList(),
    val backupHistory: List<BackupHistoryEntity> = emptyList(),
    val invoicePayments: List<InvoicePaymentEntity>? = emptyList(),
    val cashReconciliations: List<CashReconciliationEntity>? = emptyList(),
    val journalEntries: List<JournalEntryEntity>? = emptyList(),
    val journalEntryLines: List<JournalEntryLineEntity>? = emptyList(),
    val accountingPeriods: List<AccountingPeriodEntity>? = emptyList()
)

data class BackupEnvelope(
    val formatVersion: Int,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val sha256: String
)

data class BackupInspection(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val recordCount: Int,
    val checksum: String,
    val customersCount: Int,
    val suppliersCount: Int,
    val itemsCount: Int,
    val salesInvoicesCount: Int,
    val purchaseInvoicesCount: Int,
    val relationsValid: Boolean,
    val relationWarnings: List<String>
)

data class BackupResult(
    val recordCount: Int,
    val checksum: String,
    val preRestoreFile: String? = null,
    val relationWarnings: List<String> = emptyList()
)

class GroceryBackupManager(
    private val context: Context,
    private val database: GroceryDatabase
) {
    companion object {
        const val FORMAT_VERSION = 3
        private const val MIN_SUPPORTED_VERSION = 1
        private const val PBKDF2_ITERATIONS = 310_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
    }

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val random = SecureRandom()

    suspend fun exportTo(uri: Uri, passphrase: String): BackupResult = withContext(Dispatchers.IO) {
        require(passphrase.length >= 8) { "كلمة مرور النسخة يجب ألا تقل عن 8 أحرف" }
        val payload = readSnapshot()
        val payloadJson = gson.toJson(payload)
        val checksum = sha256(payloadJson)
        val envelope = encrypt(payloadJson, passphrase, checksum)
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(gson.toJson(envelope))
        } ?: error("تعذر فتح ملف النسخة الاحتياطية")
        database.references().insertBackup(
            BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                uri = uri.toString(),
                formatVersion = FORMAT_VERSION,
                checksumSha256 = checksum,
                createdAt = System.currentTimeMillis(),
                result = "EXPORTED_ENCRYPTED"
            )
        )
        BackupResult(countRecords(payload), checksum)
    }

    suspend fun inspectBackup(uri: Uri, passphrase: String): BackupInspection = withContext(Dispatchers.IO) {
        require(passphrase.length >= 8) { "أدخل كلمة مرور النسخة الاحتياطية (8 أحرف على الأقل)" }
        val (_, payload, calculated) = readAndVerifyPayload(uri, passphrase)
        val warnings = validateRelations(payload)
        BackupInspection(
            formatVersion = payload.formatVersion,
            appVersion = payload.appVersion ?: "1.0.0",
            createdAt = payload.createdAt,
            recordCount = countRecords(payload),
            checksum = calculated,
            customersCount = payload.customers.size,
            suppliersCount = payload.suppliers.size,
            itemsCount = payload.items.size,
            salesInvoicesCount = payload.salesInvoices.size,
            purchaseInvoicesCount = payload.purchaseInvoices.size,
            relationsValid = warnings.isEmpty(),
            relationWarnings = warnings
        )
    }

    suspend fun restoreFrom(uri: Uri, passphrase: String, confirmed: Boolean): BackupResult = withContext(Dispatchers.IO) {
        require(confirmed) { "يجب تأكيد الاستعادة صراحةً" }
        require(passphrase.length >= 8) { "أدخل كلمة مرور النسخة الاحتياطية" }
        val (_, payload, calculated) = readAndVerifyPayload(uri, passphrase)
        val warnings = validateRelations(payload)
        require(warnings.isEmpty()) {
            "النسخة تحتوي على علاقات غير مترابطة: ${warnings.first()}"
        }

        val before = readSnapshot()
        val beforeJson = gson.toJson(before)
        val preRestore = File(context.filesDir, "pre_restore_${System.currentTimeMillis()}_${UUID.randomUUID()}.json")
        preRestore.writeText(gson.toJson(encrypt(beforeJson, passphrase, sha256(beforeJson))), Charsets.UTF_8)

        database.withTransaction {
            clearCurrentData()
            insertPayload(payload)
            database.references().insertBackup(
                BackupHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    uri = preRestore.absolutePath,
                    formatVersion = FORMAT_VERSION,
                    checksumSha256 = sha256(beforeJson),
                    createdAt = System.currentTimeMillis(),
                    result = "RESTORE_PREIMAGE_ENCRYPTED"
                )
            )
            database.references().insertBackup(
                BackupHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    uri = uri.toString(),
                    formatVersion = FORMAT_VERSION,
                    checksumSha256 = calculated,
                    createdAt = System.currentTimeMillis(),
                    result = "RESTORED_ENCRYPTED"
                )
            )
        }
        BackupResult(countRecords(payload), calculated, preRestore.absolutePath, warnings)
    }

    private fun readAndVerifyPayload(uri: Uri, passphrase: String): Triple<BackupEnvelope, BackupPayload, String> {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("تعذر قراءة ملف النسخة الاحتياطية")
        val envelope = runCatching { gson.fromJson(json, BackupEnvelope::class.java) }.getOrNull()
            ?: error("ملف النسخة غير صالح أو تالف")
        require(envelope.formatVersion in MIN_SUPPORTED_VERSION..FORMAT_VERSION) {
            "إصدار النسخة غير مدعوم: ${envelope.formatVersion}"
        }
        val payloadJson = decrypt(envelope, passphrase)
        val calculated = sha256(payloadJson)
        require(MessageDigest.isEqual(calculated.toByteArray(), envelope.sha256.toByteArray())) {
            "فشل التحقق من سلامة النسخة الاحتياطية (SHA-256 غير مطابق)"
        }
        val payload = runCatching { gson.fromJson(payloadJson, BackupPayload::class.java) }.getOrNull()
            ?: error("بيانات النسخة غير موجودة أو تالفة")
        require(payload.formatVersion in MIN_SUPPORTED_VERSION..FORMAT_VERSION) {
            "مخطط بيانات النسخة غير مدعوم"
        }
        return Triple(envelope, payload, calculated)
    }

    private fun validateRelations(p: BackupPayload): List<String> {
        val warnings = mutableListOf<String>()
        val itemIds = p.items.map { it.id }.toSet()
        val saleIds = p.salesInvoices.map { it.id }.toSet()
        val purchaseIds = p.purchaseInvoices.map { it.id }.toSet()
        val customerIds = p.customers.map { it.id }.toSet()
        val supplierIds = p.suppliers.map { it.id }.toSet()
        val returnIds = p.returns.map { it.id }.toSet()

        if (p.salesInvoiceItems.any { it.invoiceId !in saleIds || it.itemId !in itemIds }) {
            warnings += "بعض بنود فواتير البيع مرتبطة بفاتورة أو صنف مفقود"
        }
        if (p.purchaseInvoiceItems.any { it.invoiceId !in purchaseIds || it.itemId !in itemIds }) {
            warnings += "بعض بنود فواتير الشراء مرتبطة بفاتورة أو صنف مفقود"
        }
        if (p.customerTransactions.any { it.customerId !in customerIds }) {
            warnings += "توجد قيود عملاء مرتبطة بعميل غير موجود"
        }
        if (p.supplierTransactions.any { it.supplierId !in supplierIds }) {
            warnings += "توجد قيود موردين مرتبطة بمورد غير موجود"
        }
        if (p.stockMovements.any { it.itemId !in itemIds }) {
            warnings += "توجد حركات مخزون لصنف غير موجود"
        }
        if (p.returnItems.any { it.returnId !in returnIds }) {
            warnings += "توجد بنود مرتجعات غير مرتبطة بمرتجع صالح"
        }
        val entryIds = p.journalEntries.orEmpty().map { it.id }.toSet()
        val linesByEntry = p.journalEntryLines.orEmpty().groupBy { it.entryId }
        if (p.journalEntryLines.orEmpty().any { it.entryId !in entryIds }) {
            warnings += "توجد بنود قيود محاسبية غير مرتبطة بقيد صالح"
        }
        for (entry in p.journalEntries.orEmpty()) {
            val entryLines = linesByEntry[entry.id].orEmpty()
            if (entryLines.isNotEmpty()) {
                val debitSum = entryLines.sumOf { it.debit }
                val creditSum = entryLines.sumOf { it.credit }
                if (debitSum != creditSum) {
                    warnings += "القيد رقم ${entry.entryNumber} غير متوازن"
                }
            }
        }
        return warnings
    }

    private fun encrypt(plainText: String, passphrase: String, checksum: String): BackupEnvelope {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return BackupEnvelope(
            formatVersion = FORMAT_VERSION,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(encrypted),
            sha256 = checksum
        )
    }

    private fun decrypt(envelope: BackupEnvelope, passphrase: String): String {
        return try {
            val salt = Base64.getDecoder().decode(envelope.saltBase64)
            val iv = Base64.getDecoder().decode(envelope.ivBase64)
            val ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64)
            require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "بيانات تشفير النسخة غير صحيحة" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalArgumentException("كلمة المرور غير صحيحة أو ملف النسخة تالف", error)
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private suspend fun readSnapshot(): BackupPayload = database.withTransaction {
        val dao = database.backup()
        val appVer = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
        BackupPayload(
            formatVersion = FORMAT_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = appVer,
            customers = dao.readCustomers(),
            suppliers = dao.readSuppliers(),
            categories = dao.readCategories(),
            units = dao.readUnits(),
            items = dao.readItems(),
            salesInvoices = dao.readSalesInvoices(),
            salesInvoiceItems = dao.readSalesInvoiceItems(),
            purchaseInvoices = dao.readPurchaseInvoices(),
            purchaseInvoiceItems = dao.readPurchaseInvoiceItems(),
            customerTransactions = dao.readCustomerTransactions(),
            supplierTransactions = dao.readSupplierTransactions(),
            stockMovements = dao.readStockMovements(),
            cashTransactions = dao.readCashTransactions(),
            expenses = dao.readExpenses(),
            transfers = dao.readTransfers(),
            returns = dao.readReturns(),
            returnItems = dao.readReturnItems(),
            notes = dao.readNotes(),
            settings = dao.readAppSettings(),
            users = dao.readUsers(),
            activityLogs = dao.readActivityLogs(),
            backupHistory = dao.readBackupHistory(),
            invoicePayments = dao.readInvoicePayments(),
            cashReconciliations = dao.readCashReconciliations(),
            journalEntries = dao.readJournalEntries(),
            journalEntryLines = dao.readJournalEntryLines(),
            accountingPeriods = dao.readAccountingPeriods()
        )
    }

    private suspend fun clearCurrentData() {
        val dao = database.backup()
        dao.clearJournalEntryLines()
        dao.clearJournalEntries()
        dao.clearAccountingPeriods()
        dao.clearCashReconciliations()
        dao.clearInvoicePayments()
        dao.clearReturnItems()
        dao.clearReturns()
        dao.clearSalesInvoiceItems()
        dao.clearPurchaseInvoiceItems()
        dao.clearCustomerTransactions()
        dao.clearSupplierTransactions()
        dao.clearStockMovements()
        dao.clearCashTransactions()
        dao.clearExpenses()
        dao.clearTransfers()
        dao.clearSalesInvoices()
        dao.clearPurchaseInvoices()
        dao.clearItems()
        dao.clearCustomers()
        dao.clearSuppliers()
        dao.clearCategories()
        dao.clearUnits()
        dao.clearNotes()
        dao.clearAppSettings()
        dao.clearUsers()
        dao.clearActivityLogs()
        dao.clearBackupHistory()
    }

    private suspend fun insertPayload(p: BackupPayload) {
        val dao = database.backup()
        dao.insertCategories(p.categories.orEmpty())
        dao.insertUnits(p.units.orEmpty())
        dao.insertCustomers(p.customers.orEmpty())
        dao.insertSuppliers(p.suppliers.orEmpty())
        dao.insertItems(p.items.orEmpty())
        dao.insertSalesInvoices(p.salesInvoices.orEmpty())
        dao.insertSalesInvoiceItems(p.salesInvoiceItems.orEmpty())
        dao.insertPurchaseInvoices(p.purchaseInvoices.orEmpty())
        dao.insertPurchaseInvoiceItems(p.purchaseInvoiceItems.orEmpty())
        dao.insertCustomerTransactions(p.customerTransactions.orEmpty())
        dao.insertSupplierTransactions(p.supplierTransactions.orEmpty())
        dao.insertStockMovements(p.stockMovements.orEmpty())
        dao.insertCashTransactions(p.cashTransactions.orEmpty())
        dao.insertExpenses(p.expenses.orEmpty())
        dao.insertTransfers(p.transfers.orEmpty())
        dao.insertReturns(p.returns.orEmpty())
        dao.insertReturnItems(p.returnItems.orEmpty())
        dao.insertNotes(p.notes.orEmpty())
        dao.insertAppSettings(p.settings.orEmpty())
        dao.insertUsers(p.users.orEmpty())
        dao.insertActivityLogs(p.activityLogs.orEmpty())
        dao.insertBackupHistory(p.backupHistory.orEmpty())
        dao.insertInvoicePayments(p.invoicePayments.orEmpty())
        dao.insertCashReconciliations(p.cashReconciliations.orEmpty())
        dao.insertJournalEntries(p.journalEntries.orEmpty())
        dao.insertJournalEntryLines(p.journalEntryLines.orEmpty())
        dao.insertAccountingPeriods(p.accountingPeriods.orEmpty())
    }

    private fun countRecords(p: BackupPayload): Int =
        p.customers.size + p.suppliers.size + p.categories.size + p.units.size + p.items.size +
            p.salesInvoices.size + p.salesInvoiceItems.size + p.purchaseInvoices.size + p.purchaseInvoiceItems.size +
            p.customerTransactions.size + p.supplierTransactions.size + p.stockMovements.size + p.cashTransactions.size +
            p.expenses.size + p.transfers.size + p.returns.size + p.returnItems.size + p.notes.size +
            p.settings.size + p.users.size + p.activityLogs.size + p.backupHistory.size +
            (p.invoicePayments?.size ?: 0) + (p.cashReconciliations?.size ?: 0) +
            (p.journalEntries?.size ?: 0) + (p.journalEntryLines?.size ?: 0) + (p.accountingPeriods?.size ?: 0)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
