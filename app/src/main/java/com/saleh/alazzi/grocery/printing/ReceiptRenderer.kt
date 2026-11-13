package com.saleh.alazzi.grocery.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

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

object ReceiptRenderer {
    private sealed interface DrawEntry {
        data class TextBlock(val text: String, val size: Float, val bold: Boolean, val alignment: Layout.Alignment) : DrawEntry
        data class KeyValueRow(val label: String, val value: String, val size: Float, val bold: Boolean) : DrawEntry
        data class TableRow(val index: String, val name: String, val qty: String, val unit: String, val total: String, val bold: Boolean) : DrawEntry
        data object Divider : DrawEntry
    }

    fun render(data: ReceiptData, paperWidthDots: Int): Bitmap {
        require(paperWidthDots == 384 || paperWidthDots == 576) {
            "Paper width must be 384 dots (58mm) or 576 dots (80mm)"
        }
        val scale = if (paperWidthDots == 576) 1.18f else 1.0f
        val horizontalPadding = 12
        val contentWidth = paperWidthDots - horizontalPadding * 2
        val statusText = data.paymentStatus ?: when {
            data.remaining == 0L -> "مدفوعة بالكامل"
            data.paid == 0L -> "آجل"
            else -> "سداد جزئي"
        }

        val entries = buildList {
            add(DrawEntry.TextBlock(data.storeName, 26f * scale, true, Layout.Alignment.ALIGN_CENTER))
            data.storePhone?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.TextBlock("هاتف: $it", 16f * scale, false, Layout.Alignment.ALIGN_CENTER))
            }
            data.storeAddress?.takeIf { it.isNotBlank() }?.let {
                add(DrawEntry.TextBlock(it, 16f * scale, false, Layout.Alignment.ALIGN_CENTER))
            }
            add(DrawEntry.Divider)
            add(DrawEntry.TextBlock("${data.invoiceType}  ($statusText)", 21f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.KeyValueRow("رقم الفاتورة", "#${data.invoiceNumber}", 17f * scale, true))
            add(DrawEntry.KeyValueRow("التاريخ والوقت", data.dateTime, 16f * scale, false))
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
            add(DrawEntry.KeyValueRow("الإجمالي العام", "${data.total} ${data.currencyLabel}", 21f * scale, true))
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
        paperWidthDots: Int = 576
    ): Bitmap {
        val scale = if (paperWidthDots == 576) 1.15f else 1.0f
        val horizontalPadding = 16
        val contentWidth = paperWidthDots - horizontalPadding * 2
        val entries = buildList {
            add(DrawEntry.TextBlock(storeName, 25f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.TextBlock(title, 21f * scale, true, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.TextBlock(subtitle, 16f * scale, false, Layout.Alignment.ALIGN_CENTER))
            add(DrawEntry.Divider)
            lines.forEach { line ->
                add(DrawEntry.KeyValueRow(line.label, line.value, if (line.highlight) 19f * scale else 17f * scale, line.highlight))
            }
            add(DrawEntry.Divider)
        }
        return renderEntries(entries, paperWidthDots, horizontalPadding, contentWidth, scale)
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
                        // RTL: label on the right, value on the left
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
                    // RTL columns left-to-right on canvas: [Total] [Unit] [Qty] [Name] [#]
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
