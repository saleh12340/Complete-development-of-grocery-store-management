package com.saleh.alazzi.grocery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.UserEntity
import com.saleh.alazzi.grocery.data.repository.AccountingRepository
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import com.saleh.alazzi.grocery.data.repository.JournalLineInput
import com.saleh.alazzi.grocery.security.PinHasher
import com.saleh.alazzi.grocery.security.SessionManager
import com.saleh.alazzi.grocery.security.UserPermissions
import com.saleh.alazzi.grocery.security.hasPermission
import com.saleh.alazzi.grocery.ui.UserRoles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddendumSystemRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: GroceryDatabase
    private lateinit var repository: GroceryRepository
    private lateinit var accounting: AccountingRepository
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = androidx.room.Room.inMemoryDatabaseBuilder(context, GroceryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GroceryRepository(database)
        accounting = AccountingRepository(database)
        sessionManager = SessionManager(context)
    }

    @After
    fun tearDown() {
        sessionManager.clearSession()
        database.close()
    }

    @Test
    fun testSessionEncryptionAndRestoration() = runBlocking {
        val cred = PinHasher.create("123456".toCharArray())
        val user = UserEntity(
            id = "user-admin",
            username = "admin",
            displayName = "مدير نظام تجريبي",
            pinSalt = cred.saltBase64,
            pinHash = cred.hashBase64,
            role = UserRoles.MANAGER,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        database.references().insertUser(user)

        val payload = sessionManager.saveSession(user, "inventory", 24)
        assertEquals("inventory", payload.lastRoute)
        assertEquals("user-admin", payload.userId)

        val restored = sessionManager.loadValidSession()
        assertNotNull(restored)
        assertEquals("inventory", restored?.lastRoute)
        assertEquals("admin", restored?.username)
    }

    @Test
    fun testGranularPermissionsAndRoleIsolation() = runBlocking {
        val cred = PinHasher.create("654321".toCharArray())
        val cashier = UserEntity(
            id = "user-cashier",
            username = "cashier1",
            displayName = "كاشير",
            pinSalt = cred.saltBase64,
            pinHash = cred.hashBase64,
            role = UserRoles.CASHIER,
            isActive = true,
            createdAt = System.currentTimeMillis(),
            permissions = "CAN_SELL,CAN_VIEW_CUSTOMERS"
        )
        database.references().insertUser(cashier)

        assertTrue(cashier.hasPermission(UserPermissions.CAN_SELL))
        assertTrue(cashier.hasPermission(UserPermissions.CAN_VIEW_CUSTOMERS))
        assertFalse(cashier.hasPermission(UserPermissions.CAN_EDIT_INVENTORY))
    }

    @Test
    fun testAccountingJournalEntryCreationAndReversal() = runBlocking {
        val opId = UUID.randomUUID().toString()
        val entry = accounting.postAutoJournal(
            operationId = opId,
            entryType = "MANUAL",
            description = "تسوية مصروفات إضافية",
            sourceType = "MANUAL",
            sourceId = null,
            lines = listOf(
                JournalLineInput("1010", "الصندوق", 0L, 5000L),
                JournalLineInput("5110", "المصروفات التشغيلية", 5000L, 0L)
            ),
            createdAt = System.currentTimeMillis(),
            actorId = "admin",
            actorName = "المدير"
        )

        assertNotNull(entry)
        assertEquals(5000L, entry?.amount)
        assertEquals("POSTED", entry?.status)

        val revOpId = UUID.randomUUID().toString()
        val revEntry = accounting.reverseJournalEntry(
            operationId = revOpId,
            entryId = entry!!.id,
            reason = "خطأ في التسجيل",
            actorId = "admin",
            actorName = "المدير"
        )

        assertNotNull(revEntry)
        val allEntries = database.journal().observeAllEntries().first()
        val target = allEntries.firstOrNull { it.id == entry.id }
        assertEquals("REVERSED", target?.status)
    }

    @Test
    fun testIntegrityAuditHealth() = runBlocking {
        val report = repository.runIntegrityAudit()
        assertTrue(report.isHealthy)
        assertTrue(report.mismatches.isEmpty())
    }
}
