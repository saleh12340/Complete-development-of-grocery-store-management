package com.saleh.alazzi.grocery.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.saleh.alazzi.grocery.data.backup.GroceryBackupManager
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.CustomerEntity
import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import com.saleh.alazzi.grocery.data.local.entity.SupplierEntity
import com.saleh.alazzi.grocery.data.local.entity.UserEntity
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import com.saleh.alazzi.grocery.security.PinHasher
import com.saleh.alazzi.grocery.ui.theme.AlAzziTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class InvoiceItemSelectionCrashTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: GroceryDatabase
    private lateinit var viewModel: GroceryViewModel

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, GroceryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val credential = PinHasher.create("123456".toCharArray())
        db.references().insertUser(
            UserEntity(
                id = "invoice-manager",
                username = "admin",
                displayName = "المدير",
                pinSalt = credential.saltBase64,
                pinHash = credential.hashBase64,
                role = UserRoles.MANAGER,
                isActive = true,
                createdAt = 1_000L
            )
        )
        db.customers().insert(CustomerEntity("invoice-customer", "عميل اختبار", balance = 0, createdAt = 1_000L, updatedAt = 1_000L))
        db.suppliers().insert(SupplierEntity("invoice-supplier", "مورد اختبار", balance = 0, createdAt = 1_000L, updatedAt = 1_000L))
        db.items().insert(
            ItemEntity(
                id = "overflow-item",
                name = "صنف بسعر كبير",
                quantityMilli = 9_000_000_000L,
                purchasePrice = 9_000_000_000_000_000_000L,
                salePrice = 9_000_000_000_000_000_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L
            )
        )
        viewModel = GroceryViewModel(db, GroceryRepository(db))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun selectingItemWithOverflowingLineTotalDoesNotCrashInvoiceScreen() {
        composeRule.setContent {
            AlAzziTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    GroceryApp(viewModel = viewModel, backupManager = GroceryBackupManager(context, db))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("login_username_input").performTextReplacement("admin")
        composeRule.onNodeWithTag("login_pin_input").performTextReplacement("123456")
        composeRule.onNodeWithTag("login_button").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("nav_tab_invoices").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("nav_tab_invoices").performClick()
        composeRule.onNodeWithTag("hub_new_sale").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("line_qty_input").performTextReplacement("1000000000")
        composeRule.onNodeWithTag("line_item_picker_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("invoice_item_overflow-item").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("صنف بسعر كبير").assertExists()
        composeRule.onNodeWithText("تعذر حساب إجمالي الصنف بسبب كبر الكمية أو السعر. أدخل إجمالياً صالحاً أو راجع بيانات الصنف.").assertExists()
        composeRule.onNodeWithTag("new_sale_screen").assertExists()
    }
}
