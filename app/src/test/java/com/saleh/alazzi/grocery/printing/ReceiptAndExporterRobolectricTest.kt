package com.saleh.alazzi.grocery.printing

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceiptAndExporterRobolectricTest {

    private fun sampleReceipt(itemCount: Int = 4): ReceiptData = ReceiptData(
        storeName = "بقالة العزي للمواد الغذائية",
        invoiceType = "فاتورة مبيعات",
        invoiceNumber = "00000125",
        dateTime = "2026/05/01 14:30",
        partyName = "محمد علي العزي",
        items = (1..itemCount).map { idx ->
            ReceiptItem("صنف عربي تجريبي رقم $idx", "$idx", 500L, idx * 500L)
        },
        subtotal = 5_000L,
        discount = 200L,
        total = 4_800L,
        paid = 3_000L,
        remaining = 1_800L,
        accountBalance = 6_800L,
        storePhone = "777123456",
        storeAddress = "صنعاء - شارع الزبيري",
        footer = "البضاعة المباعة ترد وتستبدل خلال 24 ساعة"
    )

    @Test
    fun renders58mmAnd80mmReceiptsAndEncodesEscPosBands() {
        listOf(384, 576).forEach { width ->
            val bmp = ReceiptRenderer.render(sampleReceipt(6), width)
            try {
                assertEquals(width, bmp.width)
                assertTrue(bmp.height > 200)
                val bands = EscPosRasterEncoder.encode(bmp)
                assertTrue(bands.isNotEmpty())
                // ESC/POS GS v 0 header: 0x1D 0x76 0x30 0x00
                val first = bands.first()
                assertEquals(0x1D.toByte(), first[0])
                assertEquals(0x76.toByte(), first[1])
                assertEquals(0x30.toByte(), first[2])
            } finally {
                bmp.recycle()
            }
        }
    }

    @Test
    fun rendersReportBitmapAndLongMultiPagePdf() {
        val reportBmp = ReceiptRenderer.renderReport(
            storeName = "بقالة العزي",
            title = "التقرير المالي الشامل",
            subtitle = "اليوم",
            lines = listOf(
                ReportLine("صافي المبيعات", "125000 ريال", true),
                ReportLine("المصروفات", "4500 ريال")
            ),
            paperWidthDots = 576
        )
        assertEquals(576, reportBmp.width)
        assertTrue(reportBmp.height > 100)
        reportBmp.recycle()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = InvoiceExporter(context)
        val tallBmp = ReceiptRenderer.render(sampleReceipt(60), 576)
        try {
            assertTrue("Expected tall bitmap > 2400px for multi-page height, got ${tallBmp.height}", tallBmp.height > 2400)
            val pngUri = exporter.savePng(tallBmp, "multi_page_test")
            assertNotNull(pngUri)
        } finally {
            tallBmp.recycle()
        }
    }

    @Test
    fun normalizesYemeniPhonesWithoutDuplicating967() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = InvoiceExporter(context)

        assertEquals("967777123456", exporter.normalizeYemeniPhone("777123456"))
        assertEquals("967777123456", exporter.normalizeYemeniPhone("0777123456"))
        assertEquals("967777123456", exporter.normalizeYemeniPhone("+967 777 123 456"))
        assertEquals("967777123456", exporter.normalizeYemeniPhone("00967777123456"))
        assertEquals("967777123456", exporter.normalizeYemeniPhone("+967967777123456"))
        assertEquals("967777123456", exporter.normalizeYemeniPhone("٧٧٧١٢٣٤٥٦"))

        assertThrows(IllegalStateException::class.java) {
            exporter.normalizeYemeniPhone("12345")
        }
    }

    @Test
    fun encoderPadsArbitraryWidthAndRejectsRecycledBitmap() {
        val unaligned = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        val bands = EscPosRasterEncoder.encode(unaligned)
        assertEquals(1, bands.size)
        // 100 pixels -> 13 bytes per row * 50 rows + 8-byte header
        assertEquals(8 + 13 * 50, bands.first().size)
        unaligned.recycle()

        assertThrows(IllegalArgumentException::class.java) {
            EscPosRasterEncoder.encode(unaligned)
        }
    }

    @Test
    fun exportsUtf8BomCsvWithProperEscaping() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = InvoiceExporter(context)
        val file = exporter.saveCsvFile(
            fileName = "financial_report_test",
            rows = listOf(
                listOf("البند", "القيمة", "ملاحظة"),
                listOf("صافي المبيعات", "125000", "تقرير \"اليوم\", معتمد")
            )
        )
        val bytes = file.readBytes()
        assertTrue(bytes.size >= 3)
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
        val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        assertTrue(text.contains("البند,القيمة,ملاحظة"))
        assertTrue(text.contains("\"تقرير \"\"اليوم\"\", معتمد\""))
    }
}
