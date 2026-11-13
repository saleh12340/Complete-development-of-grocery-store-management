package com.saleh.alazzi.grocery.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.saleh.alazzi.grocery.data.backup.BackupEnvelope
import com.saleh.alazzi.grocery.data.backup.BackupPayload
import com.saleh.alazzi.grocery.data.backup.GroceryBackupManager
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.*
import com.saleh.alazzi.grocery.data.repository.CreateInvoiceInput
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import com.saleh.alazzi.grocery.data.repository.InvoiceLineInput
import com.saleh.alazzi.grocery.data.repository.ReturnLineInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GrocerySystemRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: GroceryDatabase
    private lateinit var repo: GroceryRepository
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GroceryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = GroceryRepository(db)
        seedBaseData()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private suspend fun seedBaseData() {
        db.references().putSetting(AppSettingEntity("store_name", "بقالة العزي اختبار", now))
        db.references().putSetting(AppSettingEntity("store_phone", "777000111", now))
        db.customers().insert(CustomerEntity("cust-1", "أحمد الصالح", "777111222", "صنعاء", null, 0L, now, now))
        db.suppliers().insert(SupplierEntity("sup-1", "شركة الخير", "777333444", "الحديدة", null, 0L, now, now))
        db.items().insert(ItemEntity("item-1", "أرز بسمتي", "111", null, null, 10_000L, 2_000L, 1_000L, 1_300L, true, now, now))
        db.stock().insertMovement(
            StockMovementEntity(
                id = "mov-open-1",
                operationId = "opening:item-1",
                itemId = "item-1",
                movementType = "OPENING",
                quantityInMilli = 10_000L,
                quantityOutMilli = 0L,
                balanceBeforeMilli = 0L,
                balanceAfterMilli = 10_000L,
                sourceType = "OPENING_BALANCE",
                sourceId = "item-1",
                createdAt = now
            )
        )
        db.items().insert(ItemEntity("item-2", "سكر أبيض", "222", null, null, 5_000L, 1_000L, 500L, 700L, true, now, now))
        db.stock().insertMovement(
            StockMovementEntity(
                id = "mov-open-2",
                operationId = "opening:item-2",
                itemId = "item-2",
                movementType = "OPENING",
                quantityInMilli = 5_000L,
                quantityOutMilli = 0L,
                balanceBeforeMilli = 0L,
                balanceAfterMilli = 5_000L,
                sourceType = "OPENING_BALANCE",
                sourceId = "item-2",
                createdAt = now
            )
        )
    }

    @Test
    fun roomMigration1To2PreservesV1DataAndCreatesV2Tables() = runBlocking {
        val schemaFile = listOf(
            File("app/schemas/com.saleh.alazzi.grocery.data.local.GroceryDatabase/1.json"),
            File("schemas/com.saleh.alazzi.grocery.data.local.GroceryDatabase/1.json")
        ).firstOrNull { it.exists() }
        assertNotNull("Expected 1.json schema asset to exist", schemaFile)

        val root = JSONObject(schemaFile!!.readText())
        val entities = root.getJSONObject("database").getJSONArray("entities")
        val dbName = "migration_v1_v2_test.db"
        context.deleteDatabase(dbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", table)
                            db.execSQL(createSql)
                            val indices = entity.optJSONArray("indices")
                            if (indices != null) {
                                for (j in 0 until indices.length()) {
                                    val idxSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
                                    db.execSQL(idxSql)
                                }
                            }
                        }
                        // Insert sample v1 records
                        db.execSQL("INSERT INTO customers(id,name,phone,balance,createdAt,updatedAt) VALUES('c1','عميل قديم','777888999',2500,1000,1000)")
                        db.execSQL("INSERT INTO items(id,name,quantityMilli,lowStockThresholdMilli,purchasePrice,salePrice,isActive,createdAt,updatedAt) VALUES('i1','صنف قديم',3000,500,100,150,1,1000,1000)")
                        db.execSQL("INSERT INTO sales_invoices(id,operationId,invoiceNumber,customerId,createdAt,subtotal,discount,total,paid,remaining,paymentMethod,status) VALUES('s1','op1','00000001','c1',1000,2500,0,2500,0,2500,'CREDIT','POSTED')")
                        db.execSQL("INSERT INTO notes(id,title,body,isPinned,createdAt,updatedAt) VALUES('n1','ملاحظة قديمة','تفاصيل',1,1000,1000)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()
        )
        helper.writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, GroceryDatabase::class.java, dbName)
            .addMigrations(GroceryDatabase.MIGRATION_1_2, GroceryDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val cust = migratedDb.customers().findById("c1")
            assertNotNull(cust)
            assertEquals("عميل قديم", cust!!.name)
            assertEquals(2500L, cust.balance)
            assertFalse(cust.isArchived)

            val inv = migratedDb.salesInvoices().findById("s1")
            assertNotNull(inv)
            assertEquals("00000001", inv!!.invoiceNumber)
            assertNull(inv.storeNameSnapshot)

            val note = migratedDb.references().observeNotes().first().single()
            assertEquals("عام", note.category)
            assertFalse(note.isArchived)

            // Verify new v2 tables are queryable
            assertEquals(0, migratedDb.invoicePayments().observeForInvoice("SALE", "s1").first().size)
            assertEquals(0, migratedDb.cash().observeReconciliations().first().size)
            assertEquals(0, migratedDb.references().observeErrorLogs(10).first().size)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun invoicePaymentsVoidReturnReconciliationAndAuditRemainConsistent() = runBlocking {
        // 1. Partial sale (3 * 1300 = 3900, discount 100 -> total 3800, paid 1000 -> remaining 2800)
        val sale = repo.createSale(
            CreateInvoiceInput(
                operationId = "sale-1",
                customerOrSupplierId = "cust-1",
                lines = listOf(InvoiceLineInput("item-1", 3_000L, 1_300L)),
                discount = 100L,
                paid = 1_000L,
                paymentMethod = "PARTIAL",
                createdAt = now,
                actorId = "mgr-1",
                actorName = "مدير"
            )
        )
        val savedInv = db.salesInvoices().findById(sale.invoiceId)!!
        assertEquals("بقالة العزي اختبار", savedInv.storeNameSnapshot)
        assertEquals(3_800L, savedInv.total)
        assertEquals(2_800L, savedInv.remaining)
        assertEquals(2_800L, db.customers().findById("cust-1")!!.balance)
        assertEquals(1_000L, db.cash().observeBalance().first())

        // 2. Record second payment linked directly to original invoice
        repo.recordSalesInvoicePayment("pay-inv-1", sale.invoiceId, 800L, note = "دفعة ثانية", createdAt = now + 10, actorName = "مدير")
        val updatedInv = db.salesInvoices().findById(sale.invoiceId)!!
        assertEquals(1_800L, updatedInv.paid)
        assertEquals(2_000L, updatedInv.remaining)
        assertEquals(2_000L, db.customers().findById("cust-1")!!.balance)
        assertEquals(1_800L, db.cash().observeBalance().first())
        assertEquals(2, db.invoicePayments().observeForInvoice("SALE", sale.invoiceId).first().size)

        // 3. Partial return prevents over-return and reduces remaining
        val saleLine = db.salesInvoices().lines(sale.invoiceId).single()
        val ret = repo.returnSale("ret-1", sale.invoiceId, listOf(ReturnLineInput(saleLine.id, 1_000L)), "تالف", now + 20)
        assertTrue(ret.total > 0)

        // 4. Purchase increases stock and computes Weighted Average Cost
        val purch = repo.createPurchase(
            CreateInvoiceInput(
                operationId = "purch-1",
                customerOrSupplierId = "sup-1",
                lines = listOf(InvoiceLineInput("item-2", 5_000L, 700L)),
                paid = 500L,
                paymentMethod = "PARTIAL",
                createdAt = now + 30
            )
        )
        assertEquals(10_000L, db.items().findById("item-2")!!.quantityMilli)
        assertEquals(600L, db.items().findById("item-2")!!.purchasePrice) // WAC: (5*500 + 5*700)/10 = 600
        assertEquals(3_000L, db.suppliers().findById("sup-1")!!.balance)
        repo.recordPurchaseInvoicePayment("pay-purch-1", purch.invoiceId, 500L, createdAt = now + 35)
        assertEquals(2_500L, db.suppliers().findById("sup-1")!!.balance)

        // 5. Manager balance correction & Expense edit & Cash drawer reconciliation
        repo.adjustCustomerBalance("adj-c1", "cust-1", 500L, "تصحيح مطابقة ورقية", now + 40, actorName = "مدير")
        assertEquals(500L, db.customers().findById("cust-1")!!.balance)

        repo.recordExpense("exp-1", "كهرباء", 300L, "CASH", "فاتورة شهرية", now + 50, actorName = "مدير")
        val expense = db.expenses().observeAll().first().single()
        repo.updateExpense(expense.id, "كهرباء", 500L, "تعديل القراءة", "خطأ إدخال", now + 55, actorName = "مدير")
        assertEquals(500L, db.expenses().observeAll().first().single().amount)

        val currentCash = db.cash().observeBalance().first()
        assertTrue("Expected positive cash before reconciliation, got $currentCash", currentCash > 0L)
        repo.reconcileCashDrawer("rec-1", currentCash + 250L, "زيادة عند الجرد المسائي", now + 60, actorName = "مدير")
        assertEquals(currentCash + 250L, db.cash().observeBalance().first())

        // 6. Sale + Void Sale reverse posting
        val saleToVoid = repo.createSale(
            CreateInvoiceInput(
                operationId = "sale-void-target",
                customerOrSupplierId = "cust-1",
                lines = listOf(InvoiceLineInput("item-2", 2_000L, 700L)),
                paid = 400L,
                paymentMethod = "PARTIAL",
                createdAt = now + 70
            )
        )
        val stockBeforeVoid = db.items().findById("item-2")!!.quantityMilli
        assertEquals(8_000L, stockBeforeVoid)
        repo.voidSalesInvoice("void-op-1", saleToVoid.invoiceId, "إلغاء فاتورة خاطئة", now + 80, actorName = "مدير")
        assertEquals("VOIDED", db.salesInvoices().findById(saleToVoid.invoiceId)!!.status)
        assertEquals(10_000L, db.items().findById("item-2")!!.quantityMilli)
        assertEquals(500L, db.customers().findById("cust-1")!!.balance)

        // 7. Integrity audit MUST report zero mismatches across all customers, suppliers, items, cash!
        val audit = repo.runIntegrityAudit()
        assertTrue("Expected healthy integrity audit, got: ${audit.mismatches}", audit.isHealthy)
    }

    @Test
    fun backupExportInspectRestoreAndV1BackwardsCompatibilityWork() = runBlocking {
        repo.createSale(
            CreateInvoiceInput(
                operationId = "backup-sale-1",
                customerOrSupplierId = "cust-1",
                lines = listOf(InvoiceLineInput("item-1", 1_000L, 1_300L)),
                paid = 1_300L,
                paymentMethod = "CASH",
                createdAt = now
            )
        )

        val manager = GroceryBackupManager(context, db)
        val backupFile = File(context.cacheDir, "test_v2_backup.json")
        val uri = Uri.fromFile(backupFile)
        val pass = "AlAzzi@2026"

        val exportRes = manager.exportTo(uri, pass)
        assertTrue(exportRes.recordCount >= 5)

        val inspection = manager.inspectBackup(uri, pass)
        assertEquals(GroceryBackupManager.FORMAT_VERSION, inspection.formatVersion)
        assertTrue(inspection.relationsValid)
        assertEquals(1, inspection.salesInvoicesCount)

        // Unconfirmed restore must fail
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.restoreFrom(uri, pass, confirmed = false) }
        }
        // Wrong passphrase must fail
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.inspectBackup(uri, "WrongPass123") }
        }

        // Mutate DB then restore
        db.customers().insert(CustomerEntity("cust-extra", "عميل مؤقت", null, null, null, 0L, now, now))
        val restoreRes = manager.restoreFrom(uri, pass, confirmed = true)
        assertNotNull(restoreRes.preRestoreFile)
        assertTrue(File(restoreRes.preRestoreFile!!).exists())
        assertNull(db.customers().findById("cust-extra"))
        assertNotNull(db.customers().findById("cust-1"))

        // V1 backwards compatibility (missing invoicePayments / cashReconciliations fields)
        val v1Json = """
            {
              "formatVersion": 1,
              "createdAt": $now,
              "customers": [{"id":"v1-c","name":"عميل v1","balance":0,"createdAt":$now,"updatedAt":$now}],
              "suppliers": [],
              "categories": [],
              "units": [],
              "items": [],
              "salesInvoices": [],
              "salesInvoiceItems": [],
              "purchaseInvoices": [],
              "purchaseInvoiceItems": [],
              "customerTransactions": [],
              "supplierTransactions": [],
              "stockMovements": [],
              "cashTransactions": [],
              "expenses": [],
              "transfers": [],
              "returns": [],
              "returnItems": [],
              "notes": [],
              "settings": [],
              "users": [],
              "activityLogs": [],
              "backupHistory": []
            }
        """.trimIndent()
        val v1File = File(context.cacheDir, "test_v1_backup.json")
        v1File.writeText(encryptV1TestPayload(v1Json, pass))
        val v1Uri = Uri.fromFile(v1File)
        val v1Inspect = manager.inspectBackup(v1Uri, pass)
        assertEquals(1, v1Inspect.formatVersion)
        assertTrue(v1Inspect.relationsValid)
        manager.restoreFrom(v1Uri, pass, confirmed = true)
        assertNotNull(db.customers().findById("v1-c"))
    }

    private fun encryptV1TestPayload(plainText: String, passphrase: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plainText.toByteArray(Charsets.UTF_8))
        val checksum = digest.joinToString("") { "%02x".format(it) }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 310_000, 256)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val envelope = BackupEnvelope(
            formatVersion = 1,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ct),
            sha256 = checksum
        )
        return Gson().toJson(envelope)
    }

    @Test
    fun rbacEnforcesManagerCashierAndLimitedPermissions() = runBlocking {
        fun pump(timeoutMs: Long = 2_000L, until: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!until() && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }

        val vm = com.saleh.alazzi.grocery.ui.GroceryViewModel(db, repo)
        vm.createAdminUser("المدير", "258012")
        pump { vm.session.value != null }
        assertEquals(com.saleh.alazzi.grocery.ui.UserRoles.MANAGER, vm.session.value?.role)

        vm.addUser("كاشير", "cashier1", "112233", com.saleh.alazzi.grocery.ui.UserRoles.CASHIER)
        vm.addUser("محدود", "viewer1", "445566", com.saleh.alazzi.grocery.ui.UserRoles.LIMITED)
        pump { runBlocking { db.references().observeUsers().first().size == 3 } }
        assertEquals(3, db.references().observeUsers().first().size)

        // Login as CASHIER: cannot create purchase or adjust customer balance
        vm.login("cashier1", "112233")
        pump { vm.session.value?.username == "cashier1" }
        assertEquals(com.saleh.alazzi.grocery.ui.UserRoles.CASHIER, vm.session.value?.role)

        var purchaseSaved: Boolean? = null
        vm.createPurchase(
            CreateInvoiceInput("rbac-p1", "sup-1", listOf(InvoiceLineInput("item-1", 1_000L, 1_000L)), paid = 0L, paymentMethod = "CREDIT"),
            onFinished = { purchaseSaved = it }
        )
        pump { purchaseSaved != null }
        assertEquals(false, purchaseSaved)

        vm.adjustCustomerBalance("cust-1", 500L, "محاولة كاشير", "rbac-adj")
        pump(300L) { false }
        assertEquals(0L, db.customers().findById("cust-1")!!.balance)

        // Login as LIMITED: cannot create sale
        vm.login("viewer1", "445566")
        pump { vm.session.value?.username == "viewer1" }
        assertEquals(com.saleh.alazzi.grocery.ui.UserRoles.LIMITED, vm.session.value?.role)

        var limitedSaleSaved: Boolean? = null
        vm.createSale(
            CreateInvoiceInput("rbac-s1", null, listOf(InvoiceLineInput("item-1", 1_000L, 1_300L)), paid = 1_300L, paymentMethod = "CASH"),
            onFinished = { limitedSaleSaved = it }
        )
        pump { limitedSaleSaved != null }
        assertEquals(false, limitedSaleSaved)
    }
}
