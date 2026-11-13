package com.saleh.alazzi.grocery.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.printing.BluetoothPrinterClient
import com.saleh.alazzi.grocery.printing.InvoiceExporter
import com.saleh.alazzi.grocery.printing.OperationVoucherData
import com.saleh.alazzi.grocery.printing.PairedPrinter
import com.saleh.alazzi.grocery.printing.PrintSettings
import com.saleh.alazzi.grocery.printing.ReceiptRenderer
import com.saleh.alazzi.grocery.security.UserPermissions
import com.saleh.alazzi.grocery.security.hasPermission
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun GroceryViewModel.currentPrintSettings(settingsMap: Map<String, String>): PrintSettings {
    val width = settingsMap["printer_width"]?.toIntOrNull()?.takeIf { it == 384 || it == 576 } ?: 576
    val copies = settingsMap["print_copies"]?.toIntOrNull()?.coerceIn(1, 5) ?: 1
    val fontScale = settingsMap["print_font_scale"]?.toFloatOrNull()?.coerceIn(0.8f, 1.3f) ?: 1.0f
    val margin = settingsMap["print_margin"]?.toIntOrNull()?.coerceIn(4, 32) ?: 12
    val showLogo = settingsMap["print_show_logo"]?.toBooleanStrictOrNull() ?: true
    val showDateTime = settingsMap["print_show_datetime"]?.toBooleanStrictOrNull() ?: true
    return PrintSettings(
        paperWidthDots = width,
        fontScale = fontScale,
        marginPx = margin,
        showLogo = showLogo,
        showDateTime = showDateTime,
        copies = copies,
        ascendingOrder = false
    )
}

@Composable
fun DocumentPrintDialog(
    title: String,
    defaultFileName: String,
    viewModel: GroceryViewModel,
    initialSettings: PrintSettings,
    savedPrinterAddress: String?,
    savedPrinterName: String? = null,
    currency: String = "ريال",
    allowSortToggle: Boolean = false,
    csvRows: ((ascending: Boolean) -> List<List<String>>)? = null,
    whatsappCaption: String = title,
    renderBitmap: (PrintSettings) -> Bitmap,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exporter = remember(context) { InvoiceExporter(context) }
    val printerClient = remember(context) { BluetoothPrinterClient(context) }
    val scope = rememberCoroutineScope()

    var settings by remember(initialSettings) { mutableStateOf(initialSettings) }
    var selectedAddress by remember(savedPrinterAddress) { mutableStateOf(savedPrinterAddress) }
    var selectedName by remember(savedPrinterName) { mutableStateOf(savedPrinterName) }
    var pairedPrinters by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }
    var showPrinterPicker by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(settings) {
        val next = runCatching { renderBitmap(settings) }.getOrNull()
        val prev = previewBitmap
        previewBitmap = next
        if (prev != null && prev != next && !prev.isRecycled) prev.recycle()
    }

    DisposableEffect(Unit) {
        onDispose {
            previewBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                previewBitmap?.takeIf { !it.isRecycled }?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "معاينة الطباعة",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .border(1.dp, LocalAlAzziColors.current.borderSubtle, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = settings.paperWidthDots == 576,
                        onClick = { settings = settings.copy(paperWidthDots = 576) },
                        label = { Text("80mm") }
                    )
                    FilterChip(
                        selected = settings.paperWidthDots == 384,
                        onClick = { settings = settings.copy(paperWidthDots = 384) },
                        label = { Text("58mm") }
                    )
                    FilterChip(
                        selected = showSettingsPanel,
                        onClick = { showSettingsPanel = !showSettingsPanel },
                        label = { Text("النسخ والخط") },
                        leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) }
                    )
                }

                if (allowSortToggle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("الترتيب الزمني للسطور", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !settings.ascendingOrder,
                                onClick = { settings = settings.copy(ascendingOrder = false) },
                                label = { Text("الأحدث أولاً") }
                            )
                            FilterChip(
                                selected = settings.ascendingOrder,
                                onClick = { settings = settings.copy(ascendingOrder = true) },
                                label = { Text("تصاعدي") }
                            )
                        }
                    }
                }

                if (showSettingsPanel) {
                    AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("عدد النسخ: ${settings.copies}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (1..5).forEach { count ->
                                        FilterChip(
                                            selected = settings.copies == count,
                                            onClick = { settings = settings.copy(copies = count) },
                                            label = { Text("$count") }
                                        )
                                    }
                                }
                            }
                            Text("حجم الخط: ${(settings.fontScale * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = settings.fontScale,
                                onValueChange = { settings = settings.copy(fontScale = it) },
                                valueRange = 0.85f..1.25f
                            )
                            Text("الهوامش الجانبية: ${settings.marginPx} px", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = settings.marginPx.toFloat(),
                                onValueChange = { settings = settings.copy(marginPx = it.roundToInt()) },
                                valueRange = 4f..28f
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = settings.showLogo, onCheckedChange = { settings = settings.copy(showLogo = it) })
                                Text("إظهار شعار البقالة", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.weight(1f))
                                Checkbox(checked = settings.showDateTime, onCheckedChange = { settings = settings.copy(showDateTime = it) })
                                Text("التاريخ/الوقت", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.savePrinterSettings(
                                        address = selectedAddress,
                                        printerName = selectedName,
                                        paperWidthDots = settings.paperWidthDots,
                                        copies = settings.copies,
                                        fontScale = settings.fontScale,
                                        marginPx = settings.marginPx,
                                        showLogo = settings.showLogo,
                                        showDateTime = settings.showDateTime
                                    )
                                    statusMessage = "تم حفظ تفضيلات الطباعة"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("حفظ الإعدادات كافتراضي")
                            }
                        }
                    }
                }

                Button(
                    enabled = !busy,
                    onClick = {
                        val address = selectedAddress?.takeIf { it.isNotBlank() }
                        if (address == null) {
                            scope.launch {
                                runCatching { printerClient.pairedPrinters() }
                                    .onSuccess { list ->
                                        pairedPrinters = list
                                        if (list.isEmpty()) {
                                            statusMessage = "لا توجد طابعة Bluetooth مقترنة؛ قم بإقران الطابعة أولاً أو استخدم طباعة أندرويد"
                                        } else {
                                            showPrinterPicker = true
                                        }
                                    }
                                    .onFailure { statusMessage = it.message ?: "تعذر قراءة طابعات Bluetooth" }
                            }
                            return@Button
                        }
                        scope.launch {
                            busy = true
                            statusMessage = "جارٍ الإرسال للطابعة (${settings.copies} نسخة)..."
                            val bmp = renderBitmap(settings)
                            try {
                                printerClient.print(bmp, address, settings.paperWidthDots, settings.copies)
                                statusMessage = "تمت الطباعة بنجاح ($selectedName)"
                            } catch (err: Throwable) {
                                statusMessage = err.message ?: "تعذر الطباعة الحرارية"
                            } finally {
                                if (!bmp.isRecycled) bmp.recycle()
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("doc_print_bluetooth")
                ) {
                    Icon(Icons.Default.Print, null)
                    Spacer(Modifier.width(8.dp))
                    Text("طباعة حرارية ESC/POS (${settings.copies}x)")
                }

                FilledTonalButton(
                    enabled = !busy,
                    onClick = {
                        runCatching {
                            val bmp = renderBitmap(settings)
                            exporter.printBitmapWithAndroidPrintManager(bmp, title, defaultFileName)
                            if (!bmp.isRecycled) bmp.recycle()
                            statusMessage = "تم إرسال المستند إلى خدمة طباعة أندرويد"
                        }.onFailure { statusMessage = it.message ?: "تعذر بدء طباعة نظام أندرويد" }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("doc_print_android")
                ) {
                    Icon(Icons.Default.LocalPrintshop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("طباعة عبر نظام أندرويد (PrintManager)")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        enabled = !busy,
                        onClick = {
                            runCatching {
                                val bmp = renderBitmap(settings)
                                val doc = exporter.exportWithDetails(bmp, null, defaultFileName, "PDF")
                                if (!bmp.isRecycled) bmp.recycle()
                                exporter.share(doc.uri, doc.mimeType, "مشاركة PDF - $title")
                                statusMessage = "تم إنشاء PDF (${doc.byteLength} بايت)"
                            }.onFailure { statusMessage = it.message ?: "تعذر تصدير PDF" }
                        },
                        modifier = Modifier.weight(1f).testTag("doc_export_pdf")
                    ) {
                        Text("PDF ومشاركة")
                    }
                    FilledTonalButton(
                        enabled = !busy,
                        onClick = {
                            runCatching {
                                val bmp = renderBitmap(settings)
                                val doc = exporter.exportWithDetails(bmp, null, defaultFileName, "PDF")
                                if (!bmp.isRecycled) bmp.recycle()
                                exporter.shareToWhatsApp(doc.uri, doc.mimeType, whatsappCaption)
                                statusMessage = "تم تجهيز PDF لواتساب"
                            }.onFailure { statusMessage = it.message ?: "تعذر الإرسال عبر واتساب" }
                        },
                        modifier = Modifier.weight(1f).testTag("doc_share_whatsapp")
                    ) {
                        Text("واتساب")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            runCatching {
                                val bmp = renderBitmap(settings)
                                val doc = exporter.exportWithDetails(bmp, null, defaultFileName, "PNG")
                                if (!bmp.isRecycled) bmp.recycle()
                                exporter.share(doc.uri, doc.mimeType, "مشاركة صورة - $title")
                                statusMessage = "تم إنشاء صورة PNG (${doc.byteLength} بايت)"
                            }.onFailure { statusMessage = it.message ?: "تعذر تصدير PNG" }
                        },
                        modifier = Modifier.weight(1f).testTag("doc_export_png")
                    ) {
                        Text("صورة PNG")
                    }
                    if (csvRows != null) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                runCatching {
                                    val doc = exporter.exportWithDetails(null, csvRows(settings.ascendingOrder), defaultFileName, "CSV")
                                    exporter.share(doc.uri, doc.mimeType, "مشاركة جدول CSV - $title")
                                    statusMessage = "تم تصدير جدول CSV (${doc.byteLength} بايت)"
                                }.onFailure { statusMessage = it.message ?: "تعذر تصدير CSV" }
                            },
                            modifier = Modifier.weight(1f).testTag("doc_export_csv")
                        ) {
                            Text("جدول CSV")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )

    if (showPrinterPicker) {
        AlertDialog(
            onDismissRequest = { showPrinterPicker = false },
            title = { Text("اختر طابعة Bluetooth") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pairedPrinters.forEach { p ->
                        AlAzziCard(
                            onClick = {
                                selectedAddress = p.address
                                selectedName = p.name
                                viewModel.savePrinterSettings(
                                    address = p.address,
                                    printerName = p.name,
                                    paperWidthDots = settings.paperWidthDots,
                                    copies = settings.copies,
                                    fontScale = settings.fontScale,
                                    marginPx = settings.marginPx,
                                    showLogo = settings.showLogo,
                                    showDateTime = settings.showDateTime
                                )
                                showPrinterPicker = false
                                statusMessage = "تم اختيار الطابعة ${p.name}؛ اضغط طباعة حرارية الآن"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(p.address, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrinterPicker = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun PinConfirmDialog(
    title: String,
    description: String,
    confirmLabel: String = "تأكيد العملية",
    requireReason: Boolean = true,
    initialReason: String = "",
    viewModel: GroceryViewModel,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(initialReason) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, style = MaterialTheme.typography.bodySmall)
                if (requireReason) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it; errorMsg = "" },
                        label = { Text("سبب التعديل / العكس (إلزامي)") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().testTag("pin_confirm_reason")
                    )
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; errorMsg = "" },
                    label = { Text("رمز التحقق (PIN) للمستخدم الحالي") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pin_confirm_input")
                )
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanReason = reason.trim()
                    if (requireReason && cleanReason.isBlank()) {
                        errorMsg = "أدخل سبب التعديل أو العكس أولاً"
                        return@Button
                    }
                    if (!viewModel.verifyCurrentUserPin(pin)) {
                        errorMsg = "رمز التحقق غير صحيح"
                        return@Button
                    }
                    onConfirm(cleanReason)
                },
                modifier = Modifier.testTag("pin_confirm_submit")
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

enum class ManagedOperationKind {
    CUSTOMER_TX,
    SUPPLIER_TX,
    EXPENSE,
    TRANSFER,
    CASH_READONLY
}

data class ManagedOperationData(
    val id: String,
    val kind: ManagedOperationKind,
    val title: String,
    val reference: String,
    val amount: Long,
    val partyOrCategory: String,
    val paymentMethod: String,
    val balanceBefore: Long? = null,
    val balanceAfter: Long? = null,
    val createdAt: Long,
    val updatedAt: Long? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val status: String = "مرحل",
    val isReversed: Boolean = false,
    val notes: String? = null,
    val canEdit: Boolean = true,
    val canReverse: Boolean = true
)

@Composable
fun OperationManagementDialog(
    operation: ManagedOperationData,
    storeName: String,
    currency: String,
    viewModel: GroceryViewModel,
    savedPrinterAddress: String?,
    savedPrinterName: String?,
    onEditConfirmed: (newAmount: Long, newNote: String, reason: String) -> Unit,
    onReverseConfirmed: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val settings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val auditLogs by remember(operation.id) { viewModel.entityAuditLogs(operation.id) }
        .collectAsStateWithLifecycle(emptyList())

    val settingsMap = remember(settings) { settings.associate { it.key to it.value } }
    val canEditOps = session?.hasPermission(UserPermissions.CAN_EDIT_OPERATIONS) == true
    val isManager = session?.role == UserRoles.MANAGER
    val colors = LocalAlAzziColors.current

    var showEditForm by remember { mutableStateOf(false) }
    var amountText by remember(operation.id) { mutableStateOf(operation.amount.toString()) }
    var noteText by remember(operation.id) { mutableStateOf(operation.notes.orEmpty()) }
    var editReasonText by remember { mutableStateOf("") }
    var showPinForReverse by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(operation.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("البيان / الحساب: ${operation.partyOrCategory}", fontWeight = FontWeight.Bold)
                        Text("المرجع: ${operation.reference}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "المبلغ: ${Formatters.formatMoney(operation.amount, currency)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("الوسيلة: ${operation.paymentMethod}   •   الحالة: ${operation.status}", style = MaterialTheme.typography.bodySmall)
                        operation.balanceBefore?.let {
                            Text("الرصيد قبل: ${Formatters.formatMoney(it, currency)}", style = MaterialTheme.typography.bodySmall)
                        }
                        operation.balanceAfter?.let {
                            Text("الرصيد بعد: ${Formatters.formatMoney(it, currency)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Text("التاريخ: ${Formatters.formatDateTime(operation.createdAt)}", style = MaterialTheme.typography.bodySmall)
                        operation.createdBy?.takeIf { it.isNotBlank() }?.let {
                            Text("أنشأها: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        operation.updatedBy?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "آخر تعديل: $it (${operation.updatedAt?.let { t -> Formatters.formatDateTime(t) } ?: ""})",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.warningAmber
                            )
                        }
                        operation.notes?.takeIf { it.isNotBlank() }?.let {
                            Text("ملاحظات: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showPrintDialog = true },
                        modifier = Modifier.weight(1f).testTag("op_print_voucher")
                    ) {
                        Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("طباعة ومشاركة")
                    }
                    if (!operation.isReversed && operation.canEdit && canEditOps) {
                        FilledTonalButton(
                            onClick = { showEditForm = !showEditForm },
                            modifier = Modifier.weight(1f).testTag("op_toggle_edit")
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تعديل")
                        }
                    }
                }

                if (!operation.isReversed && operation.canReverse && isManager) {
                    OutlinedButton(
                        onClick = { showPinForReverse = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.debtRed),
                        modifier = Modifier.fillMaxWidth().testTag("op_reverse_button")
                    ) {
                        Icon(Icons.Default.Undo, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("عكس محاسبي وإعادة الرصيد")
                    }
                }

                if (showEditForm) {
                    AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("تعديل العملية وإعادة حساب الأرصدة", fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = Formatters.filterWholeNumberInput(it) },
                                label = { Text("المبلغ الصحيح") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("op_edit_amount")
                            )
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                label = { Text("الملاحظات") },
                                modifier = Modifier.fillMaxWidth().testTag("op_edit_note")
                            )
                            OutlinedTextField(
                                value = editReasonText,
                                onValueChange = { editReasonText = it },
                                label = { Text("سبب التعديل (إلزامي)") },
                                modifier = Modifier.fillMaxWidth().testTag("op_edit_reason")
                            )
                            Button(
                                onClick = {
                                    val amt = Formatters.parseWholeAmount(amountText) ?: return@Button
                                    val cleanReason = editReasonText.trim()
                                    if (amt <= 0 || cleanReason.isBlank()) return@Button
                                    onEditConfirmed(amt, noteText.trim(), cleanReason)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("op_edit_save")
                            ) {
                                Text("حفظ التعديل")
                            }
                        }
                    }
                }

                if (auditLogs.isNotEmpty()) {
                    Text("سجل التعديلات على هذه العملية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    auditLogs.take(5).forEach { log ->
                        Text(
                            "• ${Formatters.formatDateTime(log.createdAt)}: ${log.details}${log.actorName?.let { " ($it)" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )

    if (showPinForReverse) {
        PinConfirmDialog(
            title = "تأكيد العكس المحاسبي",
            description = "سيتم عكس أثر العملية (${Formatters.formatMoney(operation.amount, currency)}) وإعادة احتساب الأرصدة بدقة مع بقاء السجل التاريخي.",
            confirmLabel = "عكس العملية",
            requireReason = true,
            viewModel = viewModel,
            onDismiss = { showPinForReverse = false },
            onConfirm = { reason ->
                showPinForReverse = false
                onReverseConfirmed(reason)
                onDismiss()
            }
        )
    }

    if (showPrintDialog) {
        val voucher = OperationVoucherData(
            storeName = storeName,
            voucherTitle = operation.title,
            reference = operation.reference,
            dateTime = Formatters.formatDateTime(operation.createdAt),
            partyOrCategory = operation.partyOrCategory,
            amount = operation.amount,
            paymentMethod = operation.paymentMethod,
            balanceBefore = operation.balanceBefore,
            balanceAfter = operation.balanceAfter,
            createdBy = operation.createdBy,
            updatedBy = operation.updatedBy,
            status = operation.status,
            notes = operation.notes,
            currencyLabel = currency
        )
        DocumentPrintDialog(
            title = operation.title,
            defaultFileName = "voucher_${operation.reference}",
            viewModel = viewModel,
            initialSettings = viewModel.currentPrintSettings(settingsMap),
            savedPrinterAddress = savedPrinterAddress,
            savedPrinterName = savedPrinterName,
            currency = currency,
            whatsappCaption = "${operation.title} — ${operation.partyOrCategory} — ${Formatters.formatMoney(operation.amount, currency)}",
            renderBitmap = { ps -> ReceiptRenderer.renderOperationVoucher(voucher, ps) },
            onDismiss = { showPrintDialog = false }
        )
    }
}
