package com.saleh.alazzi.grocery.printing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class ExportedDocument(
    val uri: Uri,
    val file: File,
    val mimeType: String,
    val byteLength: Long
)

class InvoiceExporter(private val context: Context) {

    private fun exportsDir(): File = File(context.filesDir, "exports").apply { mkdirs() }

    private fun toVerifiedFileProviderUri(file: File): Uri {
        check(file.exists() && file.length() > 0L) { "فشل إنشاء ملف التصدير أو أن الملف فارغ" }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun savePngFile(bitmap: Bitmap, fileName: String): File {
        val file = File(exportsDir(), uniqueSafeName(fileName, "png"))
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "تعذر حفظ الصورة" }
            output.flush()
        }
        check(file.exists() && file.length() > 0L) { "تعذر التحقق من ملف الصورة المُصدّر" }
        return file
    }

    fun savePng(bitmap: Bitmap, fileName: String): Uri =
        toVerifiedFileProviderUri(savePngFile(bitmap, fileName))

    fun savePdfFile(bitmap: Bitmap, fileName: String): File {
        val widthPoints = (bitmap.width * 72f / 203f).toInt().coerceAtLeast(1)
        val maxPageHeightPixels = 2200
        val document = PdfDocument()
        var top = 0
        var pageNumber = 1
        while (top < bitmap.height) {
            val sliceHeightPx = minOf(maxPageHeightPixels, bitmap.height - top)
            val pageHeightPoints = (sliceHeightPx * 72f / 203f).toInt().coerceAtLeast(1)
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(widthPoints, pageHeightPoints, pageNumber).create()
            )
            val src = Rect(0, top, bitmap.width, top + sliceHeightPx)
            val dst = RectF(0f, 0f, widthPoints.toFloat(), pageHeightPoints.toFloat())
            page.canvas.drawBitmap(bitmap, src, dst, null)
            document.finishPage(page)
            top += sliceHeightPx
            pageNumber++
        }

        val file = File(exportsDir(), uniqueSafeName(fileName, "pdf"))
        return try {
            FileOutputStream(file).use { output ->
                document.writeTo(output)
                output.flush()
            }
            check(file.exists() && file.length() > 0L) { "تعذر التحقق من ملف PDF المُصدّر" }
            file
        } finally {
            document.close()
        }
    }

    fun savePdf(bitmap: Bitmap, fileName: String): Uri =
        toVerifiedFileProviderUri(savePdfFile(bitmap, fileName))

    fun saveCsvFile(fileName: String, rows: List<List<String>>): File {
        val csvBody = buildString {
            append('\uFEFF')
            rows.forEach { cols ->
                append(
                    cols.joinToString(",") { cell ->
                        val escaped = cell.replace("\"", "\"\"")
                        "\"$escaped\""
                    }
                )
                append('\n')
            }
        }
        val file = File(exportsDir(), uniqueSafeName(fileName, "csv"))
        file.writeBytes(csvBody.toByteArray(Charsets.UTF_8))
        check(file.exists() && file.length() > 0L) { "تعذر التحقق من ملف CSV المُصدّر" }
        return file
    }

    fun saveCsv(fileName: String, rows: List<List<String>>): Uri =
        toVerifiedFileProviderUri(saveCsvFile(fileName, rows))

    fun exportWithDetails(
        bitmap: Bitmap?,
        csvRows: List<List<String>>?,
        fileName: String,
        format: String
    ): ExportedDocument = when (format.uppercase()) {
        "PNG" -> {
            val f = savePngFile(requireNotNull(bitmap), fileName)
            ExportedDocument(toVerifiedFileProviderUri(f), f, "image/png", f.length())
        }
        "PDF" -> {
            val f = savePdfFile(requireNotNull(bitmap), fileName)
            ExportedDocument(toVerifiedFileProviderUri(f), f, "application/pdf", f.length())
        }
        "CSV" -> {
            val f = saveCsvFile(fileName, requireNotNull(csvRows))
            ExportedDocument(toVerifiedFileProviderUri(f), f, "text/csv", f.length())
        }
        else -> error("صيغة غير مدعومة: $format")
    }

    fun share(uri: Uri, mimeType: String, chooserTitle: String = "مشاركة المستند") {
        verifyUriNotEmpty(uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "مستند بقالة العزي", uri)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openWhatsAppChat(phone: String) {
        val international = normalizeYemeniPhone(phone)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            error("تعذر فتح تطبيق واتساب أو المتصفح")
        }
    }

    fun shareToWhatsApp(uri: Uri, mimeType: String, caption: String = "مستند بقالة العزي للمواد الغذائية") {
        verifyUriNotEmpty(uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newUri(context.contentResolver, "مستند بقالة العزي", uri)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            error("تطبيق واتساب غير مثبت على الجهاز؛ يمكنك استخدام المشاركة العامة")
        }
    }

    fun printBitmapWithAndroidPrintManager(
        bitmap: Bitmap,
        jobName: String,
        fileName: String = "print_job"
    ) {
        val pdfFile = savePdfFile(bitmap, fileName)
        printPdfWithAndroidPrintManager(pdfFile, jobName)
    }

    fun printPdfWithAndroidPrintManager(pdfFile: File, jobName: String) {
        check(pdfFile.exists() && pdfFile.length() > 0L) { "ملف الطباعة غير موجود أو فارغ" }
        val activity = findActivity(context)
        val printManager = (activity ?: context).getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("خدمة طباعة نظام أندرويد غير متوفرة")

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true || destination == null) {
                    callback?.onWriteCancelled()
                    return
                }
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (error: Throwable) {
                    callback?.onWriteFailed(error.message ?: "فشل كتابة ملف الطباعة")
                }
            }
        }
        printManager.print(jobName.ifBlank { "مستند بقالة العزي" }, adapter, PrintAttributes.Builder().build())
    }

    fun verifyUriNotEmpty(uri: Uri) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(16)
            val read = stream.read(buf)
            check(read > 0) { "الملف الناتج للمشاركة فارغ" }
        } ?: error("تعذر التحقق من صلاحية الملف قبل المشاركة")
    }

    fun normalizeYemeniPhone(input: String): String {
        val digits = input.map { ch ->
            when (ch) {
                in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                else -> ch
            }
        }.filter(Char::isDigit).joinToString("")

        val trimmed = when {
            digits.startsWith("00967967") -> digits.removePrefix("00967")
            digits.startsWith("967967") -> digits.removePrefix("967")
            digits.startsWith("00967") -> digits.removePrefix("00")
            digits.startsWith("967") -> digits
            digits.startsWith("0") && digits.length == 10 -> "967" + digits.drop(1)
            digits.length == 9 -> "967$digits"
            else -> error("رقم الهاتف اليمني غير صحيح")
        }
        require(trimmed.length == 12 && trimmed.startsWith("967")) {
            "رقم الهاتف اليمني غير صحيح (مثال: 777123456 أو +967777123456)"
        }
        return trimmed
    }

    private tailrec fun findActivity(ctx: Context): Activity? = when (ctx) {
        is Activity -> ctx
        is ContextWrapper -> findActivity(ctx.baseContext)
        else -> null
    }

    private fun uniqueSafeName(value: String, extension: String): String {
        val clean = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(42).ifBlank { "alazzi_doc" }
        val base = clean.removeSuffix(".$extension")
        return "$base.$extension"
    }
}
