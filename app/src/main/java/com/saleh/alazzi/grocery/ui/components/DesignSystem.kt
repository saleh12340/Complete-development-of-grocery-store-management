package com.saleh.alazzi.grocery.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saleh.alazzi.grocery.printing.BluetoothPrinterClient
import com.saleh.alazzi.grocery.printing.InvoiceExporter
import com.saleh.alazzi.grocery.printing.ReceiptData
import com.saleh.alazzi.grocery.printing.ReceiptRenderer
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val enLocale = Locale.US

    fun formatMoney(amount: Long, currency: String = "ريال"): String {
        val formatted = NumberFormat.getNumberInstance(enLocale).format(amount)
        return if (currency.isBlank()) formatted else "$formatted $currency"
    }

    fun formatBigMoney(amount: java.math.BigInteger, currency: String = "ريال"): String {
        val formatted = NumberFormat.getNumberInstance(enLocale).format(amount)
        return if (currency.isBlank()) formatted else "$formatted $currency"
    }

    fun formatQty(quantityMilli: Long): String =
        BigDecimal.valueOf(quantityMilli, 3).stripTrailingZeros().toPlainString()

    fun formatDateTime(millis: Long): String =
        SimpleDateFormat("yyyy/MM/dd HH:mm", enLocale).format(Date(millis))

    fun formatDate(millis: Long): String =
        SimpleDateFormat("yyyy/MM/dd", enLocale).format(Date(millis))

    fun normalizeDigits(value: String): String = value.map { ch ->
        when (ch) {
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            else -> ch
        }
    }.joinToString("")

    fun normalizeWholeInput(value: String): String =
        normalizeDigits(value).replace(",", "").replace("٬", "").trim()

    fun normalizeDecimalInput(value: String): String =
        normalizeDigits(value).replace(",", "").replace("٬", "").replace("٫", ".").trim()

    fun parseWholeAmount(value: String): Long? = runCatching {
        normalizeWholeInput(value).toLong()
    }.getOrNull()?.takeIf { it >= 0 }

    fun parseQuantityMilli(value: String): Long? = runCatching {
        BigDecimal(normalizeDecimalInput(value))
            .multiply(BigDecimal("1000"))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()?.takeIf { it > 0 }

    fun parseNonNegativeQuantityMilli(value: String): Long? = runCatching {
        val cleaned = normalizeDecimalInput(value).ifBlank { "0" }
        BigDecimal(cleaned)
            .multiply(BigDecimal("1000"))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()?.takeIf { it >= 0 }

    fun filterWholeNumberInput(input: String): String =
        input.filter { ch -> ch.isDigit() || ch in "٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹,٬" }

    fun filterDecimalInput(input: String): String =
        input.filter { ch -> ch.isDigit() || ch in "٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹.,٫" }
}

@Composable
fun AlAzziCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = LocalAlAzziColors.current.borderSubtle
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    AlAzziCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun QuickActionChipButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, LocalAlAzziColors.current.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyStateView(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Default.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorStateView(title: String, detail: String, modifier: Modifier = Modifier) {
    val colors = LocalAlAzziColors.current
    AlAzziCard(modifier = modifier.fillMaxWidth(), containerColor = colors.debtRedContainer) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = colors.debtRed)
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = colors.debtRed)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun LoadingStateView(message: String = "جارٍ التحميل...") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ReceiptActionDialog(
    receipt: ReceiptData,
    partyPhone: String?,
    savedPrinterAddress: String?,
    savedPaperWidth: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exporter = remember(context) { InvoiceExporter(context) }
    val printerClient = remember(context) { BluetoothPrinterClient(context) }
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        onDispose { previewBitmap?.recycle() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${receipt.invoiceType} #${receipt.invoiceNumber}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("الإجمالي: ${Formatters.formatMoney(receipt.total, receipt.currencyLabel)}   •   المتبقي: ${Formatters.formatMoney(receipt.remaining, receipt.currencyLabel)}")
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }

                if (showPreview && previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "معاينة الفاتورة",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .border(1.dp, LocalAlAzziColors.current.borderSubtle, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }

                FilledTonalButton(
                    onClick = {
                        runCatching {
                            previewBitmap?.recycle()
                            previewBitmap = ReceiptRenderer.render(receipt, savedPaperWidth)
                            showPreview = true
                        }.onFailure { statusMessage = it.message ?: "تعذر تجهيز المعاينة" }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showPreview) "تحديث المعاينة" else "معاينة الفاتورة / PDF")
                }

                Button(
                    enabled = !busy,
                    onClick = {
                        val address = savedPrinterAddress?.takeIf { it.isNotBlank() }
                        if (address == null) {
                            statusMessage = "لم يتم اختيار طابعة Bluetooth بعد. انتقل إلى شاشة الطابعة لاختيار وحفظ الطابعة."
                            return@Button
                        }
                        scope.launch {
                            busy = true
                            statusMessage = "جارٍ الإرسال إلى الطابعة..."
                            val bitmap = ReceiptRenderer.render(receipt, savedPaperWidth)
                            try {
                                printerClient.print(bitmap, address, savedPaperWidth)
                                statusMessage = "تم إرسال أمر الطباعة إلى الطابعة"
                            } catch (err: Throwable) {
                                statusMessage = err.message ?: "تعذر الاتصال بالطابعة"
                            } finally {
                                bitmap.recycle()
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("طباعة حرارية (${if (savedPaperWidth == 576) "80mm" else "58mm"})")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val bmp = ReceiptRenderer.render(receipt, 576)
                                try { exporter.savePdf(bmp, "Invoice_${receipt.invoiceNumber}") } finally { bmp.recycle() }
                            }.onSuccess { statusMessage = "تم حفظ ملف PDF في المستندات" }
                                .onFailure { statusMessage = it.message ?: "تعذر حفظ PDF" }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("حفظ PDF") }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val bmp = ReceiptRenderer.render(receipt, 576)
                                try { exporter.savePng(bmp, "Invoice_${receipt.invoiceNumber}") } finally { bmp.recycle() }
                            }.onSuccess { statusMessage = "تم حفظ الصورة في المعرض" }
                                .onFailure { statusMessage = it.message ?: "تعذر حفظ الصورة" }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("حفظ صورة") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            runCatching {
                                val bmp = ReceiptRenderer.render(receipt, 576)
                                val uri = try { exporter.savePdf(bmp, "Invoice_${receipt.invoiceNumber}") } finally { bmp.recycle() }
                                exporter.share(uri, "application/pdf", "مشاركة فاتورة PDF")
                            }.onFailure { statusMessage = it.message ?: "تعذرت مشاركة PDF" }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مشاركة PDF")
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                val bmp = ReceiptRenderer.render(receipt, 576)
                                val uri = try { exporter.savePng(bmp, "Invoice_${receipt.invoiceNumber}") } finally { bmp.recycle() }
                                exporter.share(uri, "image/png", "مشاركة صورة الفاتورة")
                            }.onFailure { statusMessage = it.message ?: "تعذرت مشاركة الصورة" }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("مشاركة صورة") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            runCatching {
                                val bmp = ReceiptRenderer.render(receipt, 576)
                                val uri = try { exporter.savePng(bmp, "Invoice_${receipt.invoiceNumber}") } finally { bmp.recycle() }
                                exporter.shareToWhatsApp(uri, "image/png")
                            }.onFailure { statusMessage = "تعذر فتح WhatsApp؛ استخدم زر المشاركة العام" }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("إرسال واتساب") }

                    if (!partyPhone.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                runCatching { exporter.openWhatsAppChat(partyPhone) }
                                    .onFailure { statusMessage = it.message ?: "رقم الهاتف غير صالح لفتح واتساب" }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("محادثة العميل") }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("إغلاق") } }
    )
}
