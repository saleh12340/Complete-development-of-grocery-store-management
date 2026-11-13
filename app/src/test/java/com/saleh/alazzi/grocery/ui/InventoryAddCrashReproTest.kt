package com.saleh.alazzi.grocery.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.saleh.alazzi.grocery.data.backup.GroceryBackupManager
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.CategoryEntity
import com.saleh.alazzi.grocery.data.local.entity.UnitEntity
import com.saleh.alazzi.grocery.data.local.entity.UserEntity
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import com.saleh.alazzi.grocery.security.PinHasher
import com.saleh.alazzi.grocery.ui.theme.AlAzziTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InventoryAddCrashReproTest {

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
        val cred = PinHasher.create("123456".toCharArray())
        db.references().insertUser(
            UserEntity(
                id = "mgr-1",
                username = "admin",
                displayName = "المدير",
                pinSalt = cred.saltBase64,
                pinHash = cred.hashBase64,
                role = UserRoles.MANAGER,
                isActive = true,
                createdAt = 1_000L
            )
        )
        db.references().insertCategory(CategoryEntity("cat-1", "مواد غذائية", 1_000L))
        db.references().insertUnit(UnitEntity("unit-1", "كيلو", 1_000L))
        viewModel = GroceryViewModel(db, GroceryRepository(db))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fullGroceryAppInventoryAddEditAdjustMovementsAndOverflowSafeFlow() {
        composeRule.setContent {
            AlAzziTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    GroceryApp(
                        viewModel = viewModel,
                        backupManager = GroceryBackupManager(context, db)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Login
        composeRule.onNodeWithTag("login_username_input").performTextReplacement("admin")
        composeRule.onNodeWithTag("login_pin_input").performTextReplacement("123456")
        composeRule.onNodeWithTag("login_button").performClick()
        composeRule.waitForIdle()

        // Navigate to Inventory tab
        composeRule.onNodeWithTag("nav_tab_inventory").performClick()
        composeRule.waitForIdle()

        // Open Categories/Units dialog and add one
        composeRule.onNodeWithContentDescription("التصنيفات والوحدات").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("تصنيف جديد").performTextInput("مشروبات")
        composeRule.onAllNodesWithText("إضافة")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("إغلاق").performClick()
        composeRule.waitForIdle()

        // Open Add Item dialog, pick category & unit, enter Arabic decimal + thousand separator & save
        composeRule.onNodeWithText("صنف جديد").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("item_name_input").performTextInput("شاي أحمر")
        composeRule.onNodeWithTag("item_qty_input").performTextReplacement("١٠٫٥")
        composeRule.onNodeWithTag("item_buy_input").performTextReplacement("٨٠٠")
        composeRule.onNodeWithTag("item_sell_input").performTextReplacement("١,٢٠٠")
        composeRule.onNodeWithText("مشروبات").performScrollTo().performClick()
        composeRule.onNodeWithText("كيلو").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_item_button").performClick()
        composeRule.waitForIdle()

        val items = runBlocking { db.items().readAllSync() }
        assertEquals(1, items.size)
        assertEquals("شاي أحمر", items.first().name)
        assertEquals(10_500L, items.first().quantityMilli)
        assertEquals(800L, items.first().purchasePrice)
        assertEquals(1_200L, items.first().salePrice)

        // Add huge-value item that would overflow Long in MoneyMath.lineTotal during Inventory valuation
        composeRule.onNodeWithText("صنف جديد").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("item_name_input").performTextInput("بضاعة ضخمة")
        composeRule.onNodeWithTag("item_qty_input").performTextReplacement("1000000")
        composeRule.onNodeWithTag("item_buy_input").performTextReplacement("1000000000000")
        composeRule.onNodeWithTag("item_sell_input").performTextReplacement("1200000000000")
        composeRule.onNodeWithTag("save_item_button").performClick()
        composeRule.waitForIdle()

        // Open movements dialog on first item
        composeRule.onAllNodesWithText("الحركات")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("إغلاق").performClick()
        composeRule.waitForIdle()

        // Open adjust stock dialog
        composeRule.onAllNodesWithText("جرد / تسوية")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("سبب التسوية (مطلوب)").performTextInput("مطابقة")
        composeRule.onNodeWithText("حفظ التسوية").performClick()
        composeRule.waitForIdle()

        // Open edit dialog
        composeRule.onAllNodesWithText("تعديل")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_item_button").performClick()
        composeRule.waitForIdle()
    }
}
