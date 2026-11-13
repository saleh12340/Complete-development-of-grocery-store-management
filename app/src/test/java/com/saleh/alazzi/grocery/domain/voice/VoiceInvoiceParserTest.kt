package com.saleh.alazzi.grocery.domain.voice

import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceInvoiceParserTest {
    private val catalog = listOf(
        ItemEntity("1", "سكر أبيض 10 كجم", "1001", null, null, 5_000, 1_000, 4000, 4500, true, 0L, 0L),
        ItemEntity("2", "عصير كرتون مانجو", "1002", null, null, 10_000, 2_000, 1800, 2000, true, 0L, 0L),
        ItemEntity("3", "زيت طبخ 4 لتر", "1003", null, null, 8_000, 2_000, 2800, 3200, true, 0L, 0L)
    )

    @Test
    fun parsesArabicOrderItemTotalQty() {
        val parsed = VoiceInvoiceParser.parse("عصير كرتون 4000 الكمية 2", catalog)
        assertEquals("2", parsed.matchedItem?.id)
        assertEquals("4000", parsed.totalAmountInput)
        assertEquals("2", parsed.quantityInput)
    }

    @Test
    fun parsesEasternArabicNumeralsAndDecimalQuantities() {
        val parsed = VoiceInvoiceParser.parse("الكمية ٢.٥ زيت طبخ ٧٥٠٠", catalog)
        assertEquals("3", parsed.matchedItem?.id)
        assertEquals("7500", parsed.totalAmountInput)
        assertEquals("2.5", parsed.quantityInput)
    }

    @Test
    fun infersTotalAndQuantityWhenKeywordOmitted() {
        val parsed = VoiceInvoiceParser.parse("سكر أبيض 9000 2", catalog)
        assertEquals("1", parsed.matchedItem?.id)
        assertEquals("9000", parsed.totalAmountInput)
        assertEquals("2", parsed.quantityInput)
    }

    @Test
    fun handlesSingleAmountWithDefaultQuantityOne() {
        val parsed = VoiceInvoiceParser.parse("سكر ١٣٥٠٠", catalog)
        assertEquals("1", parsed.matchedItem?.id)
        assertEquals("13500", parsed.totalAmountInput)
        assertEquals("1", parsed.quantityInput)
    }

    @Test
    fun handlesUnknownItemAndBlankInputGracefully() {
        val unknown = VoiceInvoiceParser.parse("شوكولاتة مستوردة 1500 العدد 3", catalog)
        assertNull(unknown.matchedItem)
        assertEquals("1500", unknown.totalAmountInput)
        assertEquals("3", unknown.quantityInput)

        val blank = VoiceInvoiceParser.parse("   ", catalog)
        assertNull(blank.matchedItem)
        assertEquals("", blank.totalAmountInput)
        assertEquals("1", blank.quantityInput)
    }
}
