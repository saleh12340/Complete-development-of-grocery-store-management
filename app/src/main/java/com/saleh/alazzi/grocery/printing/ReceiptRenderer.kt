package com.saleh.alazzi.grocery.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

data class PrintSettings(
    val paperWidthDots: Int = 576,
    val fontScale: Float = 1.0f,
    val marginPx: Int = 12,
    val showLogo: Boolean = true,
    val showDateTime: Boolean = true,
    val copies: Int = 1,
    val ascendingOrder: Boolean = false
)

data class ReceiptItem(
    val name: String,
    val quantity: String,
    val unitPrice: Long,
    val total: Long
)

data class ReceiptData(
    val storeName: String = "بقالة العزي للمواد الغذائية",
    val invoiceType: String,
    val invoiceNumber: String,
    val dateTime: String,
    val partyName: String?,
    val items: List<ReceiptItem>,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val paid: Long,
    val remaining: Long,
    val accountBalance: Long? = null,
    val storePhone: String? = null,
    val storeAddress: String? = null,
    val footer: String? = null,
    val paymentStatus: String? = null,
    val currencyLabel: String = "ريال"
)

data class ReportLine(val label: String, val value: String, val highlight: Boolean = false)

data class StatementRow(
    val date: String,
    val type: String,
    val reference: String,
    val debit: Long,
    val credit: Long,
    val balanceAfter: Long
)

data class StatementPrintData(
    val storeName: String = "بقالة العزي للمواد الغذائية",
    val storePhone: String? = null,
    val storeAddress: String? = null,
    val title: String,
    val partyName: String,
    val partyPhone: String? = null,
    val periodLabel: String,
    val openingBalance: Long,
    val rows: List<StatementRow>,
    val closingBalance: Long,
    val currencyLabel: String = "ريال"
)

data class JournalVoucherLine(
    val accountName: String,
    val debit: Long,
    val credit: Long,
    val memo: String? = null
)

data class JournalVoucherData(
    val storeName: String = "بقالة العزي للمواد الغذائية",
    val entryNumber: String,
    val dateTime: String,
    val entryType: String,
    val status: String,
    val description: String,
    val amount: Long,
    val createdBy: String? = null,
    val notes: String? = null,
    val lines: List<JournalVoucherLine>,
    val currencyLabel: String = "ريال"
)

data class OperationVoucherData(
    val storeName: String = "بقالة العزي للمواد الغذائية",
    val voucherTitle: String,
    val reference: String,
    val dateTime: String,
    val partyOrCategory: String,
    val amount: Long,
    val paymentMethod: String,
    val balanceBefore: Long? = null,
    val balanceAfter: Long? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val status: String = "مرحل",
    val notes: String? = null,
    val currencyLabel: String = "ريال"
)

data class TableColumn(
    val header: String,
    val weight: Float,
    val alignCenter: Boolean = false
)

data class TablePrintData(
    val storeName: String = "بقالة العزي للمواد الغذائية",
    val title: String,
    val subtitle: String,
    val columns: List<TableColumn>,
    val rows: List<List<String>>,
    val summaryLines: List<ReportLine> = emptyList()
)

object ReceiptRenderer {
    private sealed interface DrawEntry {
        data object LogoBadge : DrawEntry
        data class TextBlock(val text: String, val size: Float, val bold: Boolean, val alignment: Layout.Alignment) : DrawEntry
        data class KeyValueRow(val label: String, val value: String, val size: Float, val bold: Boolean) : DrawEntry
        data class TableRow(val index: String, val name: String, val qty: String, val unit: String, val total: String, val bold: Boolean) : DrawEntry
        data class DynamicTableRow(val cells: List<String>, val columns: List<TableColumn>, val bold: Boolean) : DrawEntry
        data object Divider : DrawEntry
    }

    fun render(data: ReceiptData, paperWidthDots: Int): Bitmap =
        render(data, PrintSettings(paperWidthDots = paperWidthDots))

    fun render(data: ReceiptData, settings: PrintSettings): Bitmap {
        val paperWidthDots = settings.paperWidthDots
        require(paperWidthDots == 384 || paperWidthDots == 576) {
            "Paper width must be 384 dots (58mm) or 576 dots (80mm)"
        }
        val baseScale = if (paperWidthDots == 576) 1.18f else 1.0f
        val scale = baseScale * settings.fontScale.coerceIn(0.8f, 1.35f)
        val horizontalPadding = settings.marginPx.coerceIn(4, 32)
        val contentWidth = paperWidthDots - horizontalPadding * 2
        val statusText = data.paymentStatus ?: when {
            data.remaining == 0L -> "مدفوعة بالكامل"
            data.paid == 0L -> "آجل"
            else -> "سداد جزئي"
        }

        val entries = buildList {
            if (settings.showLogo) add(DrawEntry.LogoBadge)
            add(DrawEntry.TextBlock(data.storeName, 25f * scale, true, Layout.Alignment.ALIGN_CENTER))
            data.storePhone?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.TextBlock("هاتف: $it", 16f * scale, false, Layout.Alignment.ALIGN_CENTER))
            }
            data.storeAddress?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.TextBlock(it, 16f * scale, false, Layout.Alignment.ALIGN_CENTER))
            }
            add(DrawEntry.Divider)
            add(DrawEntry.TextBlock("${data.invoiceType} ($statusText)", 20f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.KeyValueRow("رقم الفاتورة", "#${data.invoiceNumber}", 17f * scale, true))
            if (settings.showDateTime) {
                add(DrawEntry.KeyValueRow("التاريخ والوقت", data.dateTime, 16f * scale, false))
            }
            data.partyName?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.KeyValueRow("الحساب", it, 17f * scale, true))
            }
            add(DrawEntry.Divider)
            add(DrawEntry.TableRow("#", "الصنف", "العدد", "السعر", "الإجمالي", true))
            add(DrawEntry.Divider)
            data.items.forEachIndexed { idx, item ->
                add(
                    DrawEntry.TableRow(
                        index = "${idx + 1}",
                        name = item.name,
                        qty = item.quantity,
                        unit = item.unitPrice.toString(),
                        total = item.total.toString(),
                        bold = false
                    )
                )
            }
            add(DrawEntry.Divider)
            add(DrawEntry.KeyValueRow("المجموع", "${data.subtotal} ${data.currencyLabel}", 17f * scale, false))
            if (data.discount > 0) {
                add(DrawEntry.KeyValueRow("الخصم", "${data.discount} ${data.currencyLabel}", 17f * scale, false))
            }
            add(DrawEntry.KeyValueRow("الإجمالي العام", "${data.total} ${data.currencyLabel}", 20f * scale, true))
            add(DrawEntry.KeyValueRow("المبلغ الواصل", "${data.paid} ${data.currencyLabel}", 18f * scale, false))
            add(DrawEntry.KeyValueRow("متبقي الفاتورة", "${data.remaining} ${data.currencyLabel}", 19f * scale, true))
            data.accountBalance?.let {
                add(DrawEntry.KeyValueRow("الرصيد التراكمي للحساب", "$it ${data.currencyLabel}", 18f * scale, true))
            }
            add(DrawEntry.Divider)
            add(DrawEntry.TextBlock("شكراً لتعاملكم معنا", 18f * scale, true, Layout.Alignment.ALIGN_CENTER))
            data.footer?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.TextBlock(it, 15f * scale, false, Layout.Alignment.ALIGN_CENTER))
            }
        }

        return renderEntries(entries, paperWidthDots, horizontalPadding, contentWidth, scale)
    }

    fun renderReport(
        storeName: String,
        title: String,
        subtitle: String,
        lines: List<ReportLine>,
        paperWidthDots: Int = 576,
        settings: PrintSettings = PrintSettings(paperWidthDots = paperWidthDots)
    ): Bitmap {
        val baseScale = if (settings.paperWidthDots == 576) 1.15f else 1.0f
        val scale = baseScale * settings.fontScale.coerceIn(0.8f, 1.35f)
        val horizontalPadding = settings.marginPx.coerceIn(4, 32)
        val contentWidth = settings.paperWidthDots - horizontalPadding * 2
        val entries = buildList {
            if (settings.showLogo) add(DrawEntry.LogoBadge)
            add(DrawEntry.TextBlock(storeName, 24f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.TextBlock(title, 20f * scale, true, Layout.Alignment.ALIGN_CENTER))
            if (settings.showDateTime) {
                add(DrawEntry.TextBlock(subtitle, 16f * scale, false, Layout.Alignment.ALIGN_CENTER))
            }
            add(DrawEntry.Divider)
            lines.forEach { line ->
                add(DrawEntry.KeyValueRow(line.label, line.value, if (line.highlight) 19f * scale else 17f * scale, line.highlight))
            }
            add(DrawEntry.Divider)
        }
        return renderEntries(entries, settings.paperWidthDots, horizontalPadding, contentWidth, scale)
    }

    fun renderStatement(data: StatementPrintData, settings: PrintSettings = PrintSettings()): Bitmap {
        val baseScale = if (settings.paperWidthDots == 576) 1.12f else 0.96f
        val scale = baseScale * settings.fontScale.coerceIn(0.8f, 1.3f)
        val horizontalPadding = settings.marginPx.coerceIn(4, 32)
        val contentWidth = settings.paperWidthDots - horizontalPadding * 2
        val columns = listOf(
            TableColumn("الرصيد", 0.19f, true),
            TableColumn("دائن", 0.17f, true),
            TableColumn("مدين", 0.17f, true),
            TableColumn("البيان / المرجع", 0.27f, false),
            TableColumn("التاريخ", 0.20f, true)
        )
        val orderedRows = if (settings.ascendingOrder) data.rows else data.rows.reversed()
        val entries = buildList {
            if (settings.showLogo) add(DrawEntry.LogoBadge)
            add(DrawEntry.TextBlock(data.storeName, 24f * scale, true, Layout.Alignment.ALIGN_CENTER))
            data.storePhone?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.TextBlock("هاتف: $it", 15f * scale, false, Layout.Alignment.ALIGN_CENTER))
            }
            add(DrawEntry.Divider)
            add(DrawEntry.TextBlock(data.title, 20f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.KeyValueRow("الاسم", data.partyName, 17f * scale, true))
            data.partyPhone?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.KeyValueRow("الهاتف", it, 16f * scale, false))
            }
            add(DrawEntry.KeyValueRow("الفترة", data.periodLabel, 16f * scale, false))
            add(DrawEntry.KeyValueRow("الرصيد الافتتاحي", "${data.openingBalance} ${data.currencyLabel}", 17f * scale, true))
            add(DrawEntry.Divider)
            add(DrawEntry.DynamicTableRow(columns.map { it.header }, columns, true))
            add(DrawEntry.Divider)
            orderedRows.forEach { row ->
                add(
                    DrawEntry.DynamicTableRow(
                        cells = listOf(
                            row.balanceAfter.toString(),
                            if (row.credit > 0) row.credit.toString() else "-",
                            if (row.debit > 0) row.debit.toString() else "-",
                            "${row.type} ${row.reference}".trim(),
                            row.date
                        ),
                        columns = columns,
                        bold = false
                    )
                )
            }
            add(DrawEntry.Divider)
            add(DrawEntry.KeyValueRow("عدد الحركات", "${orderedRows.size}", 16f * scale, false))
            add(DrawEntry.KeyValueRow("الرصيد الختامي الإجمالي", "${data.closingBalance} ${data.currencyLabel}", 20f * scale, true))
        }
        return renderEntries(entries, settings.paperWidthDots, horizontalPadding, contentWidth, scale)
    }

    fun renderJournalVoucher(data: JournalVoucherData, settings: PrintSettings = PrintSettings()): Bitmap {
        val baseScale = if (settings.paperWidthDots == 576) 1.15f else 0.98f
        val scale = baseScale * settings.fontScale.coerceIn(0.8f, 1.3f)
        val horizontalPadding = settings.marginPx.coerceIn(4, 32)
        val contentWidth = settings.paperWidthDots - horizontalPadding * 2
        val columns = listOf(
            TableColumn("البيان", 0.24f, false),
            TableColumn("دائن", 0.21f, true),
            TableColumn("مدين", 0.21f, true),
            TableColumn("الحساب", 0.34f, false)
        )
        val totalDebit = data.lines.sumOf { it.debit }
        val totalCredit = data.lines.sumOf { it.credit }
        val entries = buildList {
            if (settings.showLogo) add(DrawEntry.LogoBadge)
            add(DrawEntry.TextBlock(data.storeName, 24f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.TextBlock("سند قيد محاسبي #${data.entryNumber}", 20f * scale, true, Layout.Alignment.ALIGN_CENTER))
            if (settings.showDateTime) {
                add(DrawEntry.KeyValueRow("التاريخ", data.dateTime, 16f * scale, false))
            }
            add(DrawEntry.KeyValueRow("نوع القيد", data.entryType, 16f * scale, false))
            add(DrawEntry.KeyValueRow("الحالة", data.status, 16f * scale, true))
            data.createdBy?.let { add(DrawEntry.KeyValueRow("بواسطة", it, 16f * scale, false)) }
            add(DrawEntry.KeyValueRow("الوصف", data.description, 16f * scale, false))
            add(DrawEntry.Divider)
            add(DrawEntry.DynamicTableRow(columns.map { it.header }, columns, true))
            add(DrawEntry.Divider)
            data.lines.forEach { line ->
                add(
                    DrawEntry.DynamicTableRow(
                        cells = listOf(
                            line.memo ?: "-",
                            line.credit.toString(),
                            line.debit.toString(),
                            line.accountName
                        ),
                        columns = columns,
                        bold = false
                    )
                )
            }
            add(DrawEntry.Divider)
            add(DrawEntry.KeyValueRow("إجمالي المدين", "$totalDebit ${data.currencyLabel}", 18f * scale, true))
            add(DrawEntry.KeyValueRow("إجمالي الدائن", "$totalCredit ${data.currencyLabel}", 18f * scale, true))
            data.notes?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.KeyValueRow("ملاحظات", it, 15f * scale, false))
            }
        }
        return renderEntries(entries, settings.paperWidthDots, horizontalPadding, contentWidth, scale)
    }

    fun renderOperationVoucher(data: OperationVoucherData, settings: PrintSettings = PrintSettings()): Bitmap {
        val baseScale = if (settings.paperWidthDots == 576) 1.15f else 1.0f
        val scale = baseScale * settings.fontScale.coerceIn(0.8f, 1.3f)
        val horizontalPadding = settings.marginPx.coerceIn(4, 32)
        val contentWidth = settings.paperWidthDots - horizontalPadding * 2
        val entries = buildList {
            if (settings.showLogo) add(DrawEntry.LogoBadge)
            add(DrawEntry.TextBlock(data.storeName, 24f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.Divider)
            add(DrawEntry.TextBlock(data.voucherTitle, 20f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.KeyValueRow("المرجع", data.reference, 16f * scale, false))
            if (settings.showDateTime) {
                add(DrawEntry.KeyValueRow("التاريخ", data.dateTime, 16f * scale, false))
            }
            add(DrawEntry.KeyValueRow("الحساب / البند", data.partyOrCategory, 17f * scale, true))
            add(DrawEntry.KeyValueRow("المبلغ", "${data.amount} ${data.currencyLabel}", 20f * scale, true))
            add(DrawEntry.KeyValueRow("وسيلة الدفع", data.paymentMethod, 16f * scale, false))
            data.balanceBefore?.let {
                add(DrawEntry.KeyValueRow("الرصيد قبل", "$it ${data.currencyLabel}", 16f * scale, false))
            }
            data.balanceAfter?.let {
                add(DrawEntry.KeyValueRow("الرصيد بعد", "$it ${data.currencyLabel}", 17f * scale, true))
            }
            add(DrawEntry.KeyValueRow("الحالة", data.status, 16f * scale, true))
            data.createdBy?.let { add(DrawEntry.KeyValueRow("المنشئ", it, 15f * scale, false)) }
            data.updatedBy?.let { add(DrawEntry.KeyValueRow("آخر تعديل", it, 15f * scale, false)) }
            data.notes?.takeIf { it.isNotBlank() }?.let { add(DrawEntry.KeyValueRow("ملاحظات", it, 15f * scale, false)) }
            add(DrawEntry.Divider)
            add(DrawEntry.TextBlock("بقالة العزي للمواد الغذائية", 16f * scale, true, Layout.Alignment.ALIGN_CENTER))
        }
        return renderEntries(entries, settings.paperWidthDots, horizontalPadding, contentWidth, scale)
    }

    fun renderTableDocument(data: TablePrintData, settings: PrintSettings = PrintSettings()): Bitmap {
        val baseScale = if (settings.paperWidthDots == 576) 1.12f else 0.96f
        val scale = baseScale * settings.fontScale.coerceIn(0.8f, 1.3f)
        val horizontalPadding = settings.marginPx.coerceIn(4, 32)
        val contentWidth = settings.paperWidthDots - horizontalPadding * 2
        val orderedRows = if (settings.ascendingOrder) data.rows else data.rows.reversed()
        val entries = buildList {
            if (settings.showLogo) add(DrawEntry.LogoBadge)
            add(DrawEntry.TextBlock(data.storeName, 24f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.TextBlock(data.title, 20f * scale, true, Layout.Alignment.ALIGN_CENTER))
            if (settings.showDateTime) {
                add(DrawEntry.TextBlock(data.subtitle, 15f * scale, false, Layout.Alignment.ALIGN_CENTER))
            }
            add(DrawEntry.Divider)
            if (data.columns.isNotEmpty()) {
                add(DrawEntry.DynamicTableRow(data.columns.map { it.header }, data.columns, true))
                add(DrawEntry.Divider)
                orderedRows.forEach { row ->
                    add(DrawEntry.DynamicTableRow(row, data.columns, false))
                }
                add(DrawEntry.Divider)
            }
            data.summaryLines.forEach { summary ->
                add(DrawEntry.KeyValueRow(summary.label, summary.value, if (summary.highlight) 18f * scale else 16f * scale, summary.highlight))
            }
        }
        return renderEntries(entries, settings.paperWidthDots, horizontalPadding, contentWidth, scale)
    }

    private fun renderEntries(
        entries: List<DrawEntry>,
        paperWidthDots: Int,
        horizontalPadding: Int,
        contentWidth: Int,
        scale: Float
    ): Bitmap {
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }

        data class PreparedRow(val height: Int, val draw: (Canvas, Float) -> Unit)

        val prepared = entries.map { entry ->
            when (entry) {
                is DrawEntry.LogoBadge -> PreparedRow(54) { canvas, y ->
                    val cx = paperWidthDots / 2f
                    val box = RectF(cx - 24f, y + 4f, cx + 24f, y + 48f)
                    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                    }
                    canvas.drawRoundRect(box, 12f, 12f, border)
                    val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = 24f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("ع", cx, y + 35f, mono)
                }

                is DrawEntry.Divider -> PreparedRow(14) { canvas, y ->
                    canvas.drawLine(
                        horizontalPadding.toFloat(),
                        y + 6f,
                        (paperWidthDots - horizontalPadding).toFloat(),
                        y + 6f,
                        linePaint
                    )
                }

                is DrawEntry.TextBlock -> {
                    val paint = makePaint(entry.size, entry.bold)
                    val layout = buildRtlLayout(entry.text, paint, contentWidth, entry.alignment)
                    PreparedRow(layout.height + 6) { canvas, y ->
                        canvas.save()
                        canvas.translate(horizontalPadding.toFloat(), y)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                }

                is DrawEntry.KeyValueRow -> {
                    val valueWidth = (contentWidth * 0.44f).toInt()
                    val labelWidth = contentWidth - valueWidth - 8
                    val paint = makePaint(entry.size, entry.bold)
                    val labelLayout = buildRtlLayout(entry.label, paint, labelWidth, Layout.Alignment.ALIGN_NORMAL)
                    val valLayout = buildRtlLayout(entry.value, paint, valueWidth, Layout.Alignment.ALIGN_OPPOSITE)
                    val h = maxOf(labelLayout.height, valLayout.height) + 6
                    PreparedRow(h) { canvas, y ->
                        canvas.save()
                        canvas.translate((horizontalPadding + valueWidth + 8).toFloat(), y)
                        labelLayout.draw(canvas)
                        canvas.restore()

                        canvas.save()
                        canvas.translate(horizontalPadding.toFloat(), y)
                        valLayout.draw(canvas)
                        canvas.restore()
                    }
                }

                is DrawEntry.TableRow -> {
                    val colIdx = (contentWidth * 0.08f).toInt()
                    val colTotal = (contentWidth * 0.21f).toInt()
                    val colUnit = (contentWidth * 0.17f).toInt()
                    val colQty = (contentWidth * 0.15f).toInt()
                    val colName = contentWidth - (colIdx + colTotal + colUnit + colQty) - 8
                    val fontSize = (if (entry.bold) 16f else 15f) * scale
                    val paint = makePaint(fontSize, entry.bold)

                    val totalLayout = buildRtlLayout(entry.total, paint, colTotal, Layout.Alignment.ALIGN_OPPOSITE)
                    val unitLayout = buildRtlLayout(entry.unit, paint, colUnit, Layout.Alignment.ALIGN_CENTER)
                    val qtyLayout = buildRtlLayout(entry.qty, paint, colQty, Layout.Alignment.ALIGN_CENTER)
                    val nameLayout = buildRtlLayout(entry.name, paint, colName, Layout.Alignment.ALIGN_NORMAL)
                    val idxLayout = buildRtlLayout(entry.index, paint, colIdx, Layout.Alignment.ALIGN_CENTER)
                    val rowH = maxOf(totalLayout.height, unitLayout.height, qtyLayout.height, nameLayout.height, idxLayout.height) + 6

                    PreparedRow(rowH) { canvas, y ->
                        var x = horizontalPadding.toFloat()
                        drawAt(canvas, totalLayout, x, y); x += colTotal
                        drawAt(canvas, unitLayout, x, y); x += colUnit
                        drawAt(canvas, qtyLayout, x, y); x += colQty
                        drawAt(canvas, nameLayout, x + 4f, y); x += colName + 8f
                        drawAt(canvas, idxLayout, x, y)
                    }
                }

                is DrawEntry.DynamicTableRow -> {
                    val totalWeight = entry.columns.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
                    val widths = entry.columns.map { ((contentWidth * (it.weight / totalWeight)).toInt()).coerceAtLeast(24) }
                    val fontSize = (if (entry.bold) 15f else 13.5f) * scale
                    val paint = makePaint(fontSize, entry.bold)
                    val layouts = entry.columns.indices.map { i ->
                        val text = entry.cells.getOrElse(i) { "" }
                        val align = if (entry.columns[i].alignCenter) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
                        buildRtlLayout(text, paint, widths[i] - 4, align)
                    }
                    val rowH = (layouts.maxOfOrNull { it.height } ?: 16) + 6
                    PreparedRow(rowH) { canvas, y ->
                        var x = horizontalPadding.toFloat()
                        layouts.forEachIndexed { idx, layout ->
                            drawAt(canvas, layout, x + 2f, y)
                            x += widths[idx]
                        }
                    }
                }
            }
        }

        val totalHeight = (prepared.sumOf { it.height } + 36).coerceAtLeast(220)
        val bitmap = Bitmap.createBitmap(paperWidthDots, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var y = 16f
        prepared.forEach { row ->
            row.draw(canvas, y)
            y += row.height
        }
        return bitmap
    }

    private fun makePaint(size: Float, bold: Boolean): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

    private fun buildRtlLayout(text: String, paint: TextPaint, width: Int, alignment: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(alignment)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setLineSpacing(2f, 1f)
            .build()

    private fun drawAt(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }
}
