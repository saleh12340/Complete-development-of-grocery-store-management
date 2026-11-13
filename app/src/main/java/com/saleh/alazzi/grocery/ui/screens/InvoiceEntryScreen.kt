package com.saleh.alazzi.grocery.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import com.saleh.alazzi.grocery.data.repository.CreateInvoiceInput
import com.saleh.alazzi.grocery.data.repository.InvoiceLineInput
import com.saleh.alazzi.grocery.domain.money.MoneyMath
import com.saleh.alazzi.grocery.domain.voice.ParsedVoiceLine
import com.saleh.alazzi.grocery.domain.voice.VoiceInvoiceParser
import com.saleh.alazzi.grocery.printing.ReceiptData
import com.saleh.alazzi.grocery.printing.ReceiptItem
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.components.*
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import java.math.BigDecimal
import java.util.UUID

private data class DraftInvoiceLine(
    val item: ItemEntity,
    val quantityMilli: Long,
    val unitPrice: Long,
    val lineTotal: Long
)

private enum class PaymentMode(val label: String, val methodCode: String) {
    CASH("نقدي", "CASH"),
    CREDIT("آجل", "CREDIT"),
    PARTIAL("سداد جزئي", "PARTIAL")
}

@Composable
fun InvoiceHubScreen(
    viewModel: GroceryViewModel,
    onNewSale: () -> Unit,
    onNewPurchase: () -> Unit,
    onSalesHistory: () -> Unit,
    onPurchaseHistory: () -> Unit,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val salesCount = viewModel.salesInvoices.collectAsStateWithLifecycle().value.size
    val purchaseCount = viewModel.purchaseInvoices.collectAsStateWithLifecycle().value.size
    val isManager = session?.role == UserRoles.MANAGER
    val canSell = isManager || session?.role == UserRoles.CASHIER
    val colors = LocalAlAzziColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("invoice_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("مركز الفواتير", "إنشاء ومراجعة فواتير البيع والشراء", onBack = onBack)

        if (canSell) {
            AlAzziCard(
                containerColor = colors.paidGreenContainer,
                onClick = onNewSale,
                modifier = Modifier.fillMaxWidth().testTag("hub_new_sale")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = colors.paidGreen, modifier = Modifier.size(30.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("فاتورة بيع جديدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.paidGreen)
                        Text("إدخال ذكي: الإجمالي ثم العدد ثم الصنف مع الإدخال الصوتي والطباعة", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (isManager) {
            AlAzziCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = onNewPurchase,
                modifier = Modifier.fillMaxWidth().testTag("hub_new_purchase")
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("فاتورة شراء جديدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("تحديث المخزون ومتوسط التكلفة المرجح تلقائياً", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        AlAzziCard(onClick = onSalesHistory, modifier = Modifier.fillMaxWidth().testTag("hub_sales_history")) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("سجل فواتير المبيعات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("عدد الفواتير المحفوظة: $salesCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
            }
        }

        if (isManager) {
            AlAzziCard(onClick = onPurchaseHistory, modifier = Modifier.fillMaxWidth().testTag("hub_purchase_history")) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("سجل فواتير المشتريات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("عدد الفواتير المحفوظة: $purchaseCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronLeft, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun InvoiceEntryScreen(
    viewModel: GroceryViewModel,
    isSale: Boolean,
    storeName: String,
    storePhone: String?,
    storeAddress: String?,
    storeFooter: String?,
    currency: String,
    savedPrinterAddress: String?,
    savedPaperWidth: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val salesInvoices by viewModel.salesInvoices.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.purchaseInvoices.collectAsStateWithLifecycle()
    val colors = LocalAlAzziColors.current

    val nextInvoiceNumber = remember(isSale, salesInvoices, purchaseInvoices) {
        val max = if (isSale) {
            salesInvoices.maxOfOrNull { it.invoiceNumber.toLongOrNull() ?: 0L } ?: 0L
        } else {
            purchaseInvoices.maxOfOrNull { it.invoiceNumber.toLongOrNull() ?: 0L } ?: 0L
        }
        (max + 1L).toString().padStart(8, '0')
    }

    var paymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    var selectedPartyId by remember { mutableStateOf<String?>(null) }
    var partySearch by remember { mutableStateOf("") }
    var showPartyPicker by remember { mutableStateOf(false) }

    // Line Entry State (Strict Order: Total -> Qty -> Item)
    var lineTotalInput by remember { mutableStateOf("") }
    var qtyInput by remember { mutableStateOf("1") }
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showItemPicker by remember { mutableStateOf(false) }
    var itemSearchQuery by remember { mutableStateOf("") }

    var draftLines by remember { mutableStateOf<List<DraftInvoiceLine>>(emptyList()) }
    var discountInput by remember { mutableStateOf("0") }
    var paidInput by remember { mutableStateOf("0") }
    var notesInput by remember { mutableStateOf("") }
    var operationId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var isSaving by remember { mutableStateOf(false) }

    // Voice & Receipt Dialog State
    var showVoiceFallbackDialog by remember { mutableStateOf(false) }
    var voiceTranscriptDraft by remember { mutableStateOf("") }
    var parsedVoicePreview by remember { mutableStateOf<ParsedVoiceLine?>(null) }
    var voiceStatusHint by remember { mutableStateOf<String?>(null) }
    var receiptToPrint by remember { mutableStateOf<ReceiptData?>(null) }
    var receiptPartyPhone by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcript = matches?.firstOrNull().orEmpty()
            if (transcript.isNotBlank()) {
                parsedVoicePreview = VoiceInvoiceParser.parse(transcript, items)
            } else {
                voiceStatusHint = "لم يتم التقاط نص واضح؛ يمكنك إدخال النص يدوياً"
                showVoiceFallbackDialog = true
            }
        }
    }

    fun startSpeechCapture() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "قل مثلاً: سكر 12000 الكمية 2")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            voiceStatusHint = "محرك التعرف الصوتي غير متوفر على هذا الجهاز — يمكنك كتابة الإملاء هنا لتحليله تلقائياً"
            showVoiceFallbackDialog = true
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startSpeechCapture()
        } else {
            voiceStatusHint = "تم رفض إذن الميكروفون؛ يمكنك الإدخال كتابياً أو استخدام التحليل النصي السريع"
            showVoiceFallbackDialog = true
        }
    }

    val parsedQtyMilli = Formatters.parseQuantityMilli(qtyInput)
    val parsedLineTotal = Formatters.parseWholeAmount(lineTotalInput)
    val derivedUnitPrice: Long? = remember(parsedLineTotal, parsedQtyMilli) {
        if (parsedLineTotal != null && parsedQtyMilli != null && parsedQtyMilli > 0) {
            runCatching { MoneyMath.unitPriceFromLineTotal(parsedLineTotal, parsedQtyMilli) }
                .getOrElse {
                    ((parsedLineTotal.toBigDecimal().multiply(BigDecimal("1000")))
                        .divide(BigDecimal.valueOf(parsedQtyMilli), 0, java.math.RoundingMode.HALF_UP))
                        .toLong()
                }
        } else null
    }
    val exactLineTotal = remember(parsedQtyMilli, derivedUnitPrice) {
        if (parsedQtyMilli != null && derivedUnitPrice != null) {
            MoneyMath.lineTotal(parsedQtyMilli, derivedUnitPrice)
        } else 0L
    }

    val subtotal = remember(draftLines) { draftLines.sumOf { it.lineTotal } }
    val discount = Formatters.parseWholeAmount(discountInput) ?: 0L
    val invoiceTotal = (subtotal - discount).coerceAtLeast(0L)
    val effectivePaid = when (paymentMode) {
        PaymentMode.CASH -> invoiceTotal
        PaymentMode.CREDIT -> 0L
        PaymentMode.PARTIAL -> (Formatters.parseWholeAmount(paidInput) ?: 0L).coerceIn(0L, invoiceTotal)
    }
    val remaining = (invoiceTotal - effectivePaid).coerceAtLeast(0L)

    val activePartyName = remember(isSale, selectedPartyId, customers, suppliers) {
        if (isSale) customers.firstOrNull { it.id == selectedPartyId }?.name
        else suppliers.firstOrNull { it.id == selectedPartyId }?.name
    }

    fun applyLineToTable() {
        val item = selectedItem ?: return
        val qtyMilli = parsedQtyMilli ?: return
        val unitPrice = derivedUnitPrice ?: return
        val lineTotal = MoneyMath.lineTotal(qtyMilli, unitPrice)

        val current = draftLines.toMutableList()
        val editIdx = editingIndex
        if (editIdx != null && editIdx in current.indices) {
            current[editIdx] = DraftInvoiceLine(item, qtyMilli, unitPrice, lineTotal)
        } else {
            val existingIdx = current.indexOfFirst { it.item.id == item.id }
            if (existingIdx >= 0) {
                val old = current[existingIdx]
                val mergedQty = old.quantityMilli + qtyMilli
                val mergedTotal = MoneyMath.lineTotal(mergedQty, unitPrice)
                current[existingIdx] = DraftInvoiceLine(item, mergedQty, unitPrice, mergedTotal)
            } else {
                current += DraftInvoiceLine(item, qtyMilli, unitPrice, lineTotal)
            }
        }
        draftLines = current
        editingIndex = null
        lineTotalInput = ""
        qtyInput = "1"
        selectedItem = null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag(if (isSale) "new_sale_screen" else "new_purchase_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = if (isSale) "فاتورة بيع جديدة" else "فاتورة شراء جديدة",
                subtitle = "$storeName  •  رقم #$nextInvoiceNumber  •  ${Formatters.formatDate(System.currentTimeMillis())}",
                onBack = onBack
            )
        }

        // Invoice Header Card (Mode & Party)
        item {
            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        PaymentMode.entries.forEach { mode ->
                            FilterChip(
                                selected = paymentMode == mode,
                                onClick = { paymentMode = mode },
                                label = { Text(mode.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = { showPartyPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("party_picker_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PersonSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isSale) "العميل" else "المورد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    activePartyName ?: if (isSale) "عميل نقدي مباشر (بدون حساب)" else "شراء نقدي مباشر (اختر مورد للآجل)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (selectedPartyId != null) {
                                IconButton(onClick = { selectedPartyId = null }) {
                                    Icon(Icons.Default.Clear, contentDescription = "إزالة الاختيار")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Item Entry Card (Required Order: Total -> Qty -> Item Name + Smart Arabic Voice Input)
        item {
            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (editingIndex != null) "تعديل الصنف المحدد" else "إضافة صنف للفاتورة",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) startSpeechCapture() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.testTag("voice_input_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "إدخال صوتي ذكي", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إدخال صوتي")
                        }
                    }

                    // 1. الإجمالي (Total) first
                    OutlinedTextField(
                        value = lineTotalInput,
                        onValueChange = { lineTotalInput = Formatters.filterWholeNumberInput(it) },
                        label = { Text("1. الإجمالي ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("line_total_input")
                    )

                    // 2. الكمية (Qty) second
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = Formatters.filterDecimalInput(it) },
                        label = { Text("2. الكمية / العدد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("line_qty_input")
                    )

                    // 3. اسم الصنف (Item) third
                    OutlinedCard(
                        onClick = { showItemPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("line_item_picker_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("3. اسم الصنف", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    selectedItem?.let { "${it.name} (متوفر: ${Formatters.formatQty(it.quantityMilli)})" }
                                        ?: "اضغط لاختيار الصنف أو البحث بالباركود",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    }

                    if (derivedUnitPrice != null && parsedQtyMilli != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("سعر الواحدة: ${Formatters.formatMoney(derivedUnitPrice, currency)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("صافي السطر: ${Formatters.formatMoney(exactLineTotal, currency)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            enabled = selectedItem != null && parsedQtyMilli != null && parsedLineTotal != null,
                            onClick = { applyLineToTable() },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("add_line_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (editingIndex != null) "تحديث السطر" else "إضافة للجدول")
                        }
                        if (editingIndex != null) {
                            OutlinedButton(onClick = {
                                editingIndex = null
                                lineTotalInput = ""
                                qtyInput = "1"
                                selectedItem = null
                            }) { Text("إلغاء") }
                        }
                    }
                }
            }
        }

        // RTL Table Header & Rows (# | التفاصيل | العدد | سعر الواحدة | الإجمالي)
        item {
            Text("جدول أصناف الفاتورة (${draftLines.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (draftLines.isEmpty()) {
            item {
                EmptyStateView("لا توجد أصناف في الفاتورة", "أدخل الإجمالي والعدد واختر الصنف لإضافته للجدول")
            }
        } else {
            item {
                AlAzziCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text("#", modifier = Modifier.width(26.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        Text("التفاصيل", modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("العدد", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        Text("سعر الواحدة", modifier = Modifier.weight(1.0f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        Text("الإجمالي", modifier = Modifier.weight(1.0f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                        Spacer(Modifier.width(64.dp))
                    }
                }
            }

            itemsIndexed(draftLines) { index, line ->
                AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", modifier = Modifier.width(26.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Text(line.item.name, modifier = Modifier.weight(1.8f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text(Formatters.formatQty(line.quantityMilli), modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Text(line.unitPrice.toString(), modifier = Modifier.weight(1.0f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Text(line.lineTotal.toString(), modifier = Modifier.weight(1.0f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        IconButton(
                            onClick = {
                                editingIndex = index
                                selectedItem = line.item
                                qtyInput = Formatters.formatQty(line.quantityMilli)
                                lineTotalInput = line.lineTotal.toString()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل السطر", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                draftLines = draftLines.toMutableList().also { it.removeAt(index) }
                                if (editingIndex == index) editingIndex = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف السطر", tint = colors.debtRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Totals & Save Card
        item {
            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = discountInput,
                            onValueChange = { discountInput = Formatters.filterWholeNumberInput(it) },
                            label = { Text("الخصم ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (paymentMode == PaymentMode.PARTIAL) {
                            OutlinedTextField(
                                value = paidInput,
                                onValueChange = { paidInput = Formatters.filterWholeNumberInput(it) },
                                label = { Text("المبلغ المدفوع ($currency)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("ملاحظات الفاتورة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإجمالي الصافي", fontWeight = FontWeight.Bold)
                        Text(Formatters.formatMoney(invoiceTotal, currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الواصل: ${Formatters.formatMoney(effectivePaid, currency)}", color = colors.paidGreen, fontWeight = FontWeight.SemiBold)
                        Text("المتبقي: ${Formatters.formatMoney(remaining, currency)}", color = if (remaining > 0) colors.debtRed else colors.paidGreen, fontWeight = FontWeight.Bold)
                    }

                    val partyRequiredWarning = remaining > 0 && selectedPartyId == null
                    if (partyRequiredWarning) {
                        Text(
                            if (isSale) "الفاتورة الآجلة أو الجزئية تتطلب اختيار عميل أولاً"
                            else "فاتورة الشراء الآجلة أو الجزئية تتطلب اختيار مورد أولاً",
                            color = colors.debtRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        enabled = !isSaving && draftLines.isNotEmpty() && !partyRequiredWarning,
                        onClick = {
                            isSaving = true
                            val snapshotLines = draftLines
                            val input = CreateInvoiceInput(
                                operationId = operationId,
                                customerOrSupplierId = selectedPartyId,
                                lines = snapshotLines.map { InvoiceLineInput(it.item.id, it.quantityMilli, it.unitPrice) },
                                discount = discount,
                                paid = effectivePaid,
                                paymentMethod = paymentMode.methodCode,
                                notes = notesInput.trim().ifBlank { null }
                            )
                            val partyObj = if (isSale) customers.firstOrNull { it.id == selectedPartyId } else null
                            val supObj = if (!isSale) suppliers.firstOrNull { it.id == selectedPartyId } else null
                            val callback: (com.saleh.alazzi.grocery.data.repository.SaveInvoiceResult) -> Unit = { saved ->
                                receiptPartyPhone = partyObj?.phone ?: supObj?.phone
                                val newPartyBalance = when {
                                    isSale && partyObj != null -> partyObj.balance + remaining
                                    !isSale && supObj != null -> supObj.balance + remaining
                                    else -> null
                                }
                                receiptToPrint = ReceiptData(
                                    storeName = storeName,
                                    invoiceType = if (isSale) "فاتورة مبيعات" else "فاتورة مشتريات",
                                    invoiceNumber = saved.invoiceNumber,
                                    dateTime = Formatters.formatDateTime(System.currentTimeMillis()),
                                    partyName = partyObj?.name ?: supObj?.name,
                                    items = snapshotLines.map {
                                        ReceiptItem(
                                            name = it.item.name,
                                            quantity = Formatters.formatQty(it.quantityMilli),
                                            unitPrice = it.unitPrice,
                                            total = it.lineTotal
                                        )
                                    },
                                    subtotal = subtotal,
                                    discount = discount,
                                    total = invoiceTotal,
                                    paid = effectivePaid,
                                    remaining = remaining,
                                    accountBalance = newPartyBalance,
                                    storePhone = storePhone,
                                    storeAddress = storeAddress,
                                    footer = storeFooter,
                                    currencyLabel = currency
                                )
                                draftLines = emptyList()
                                discountInput = "0"
                                paidInput = "0"
                                notesInput = ""
                                operationId = UUID.randomUUID().toString()
                            }
                            if (isSale) {
                                viewModel.createSale(input, onFinished = { isSaving = false }, onSaved = callback)
                            } else {
                                viewModel.createPurchase(input, onFinished = { isSaving = false }, onSaved = callback)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_invoice_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSaving) "جارٍ الحفظ..." else "حفظ الفاتورة وإصدار الإيصال")
                    }
                }
            }
        }
    }

    if (showItemPicker) {
        AlertDialog(
            onDismissRequest = { showItemPicker = false },
            title = { Text("اختيار صنف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    OutlinedTextField(
                        value = itemSearchQuery,
                        onValueChange = { itemSearchQuery = it },
                        label = { Text("ابحث بالاسم أو الباركود") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val filteredItems = remember(items, itemSearchQuery) {
                        items.filter {
                            itemSearchQuery.isBlank() ||
                                it.name.contains(itemSearchQuery, ignoreCase = true) ||
                                (it.barcode ?: "").contains(itemSearchQuery, ignoreCase = true)
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredItems, key = { it.id }) { item ->
                            AlAzziCard(
                                onClick = {
                                    selectedItem = item
                                    val qty = Formatters.parseQuantityMilli(qtyInput) ?: 1000L
                                    if (lineTotalInput.isBlank()) {
                                        val unitPrice = if (isSale) item.salePrice else item.purchasePrice
                                        if (unitPrice > 0) {
                                            lineTotalInput = MoneyMath.lineTotal(qty, unitPrice).toString()
                                        }
                                    }
                                    showItemPicker = false
                                    itemSearchQuery = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "متوفر: ${Formatters.formatQty(item.quantityMilli)} • بيع: ${item.salePrice} • شراء: ${item.purchasePrice}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showItemPicker = false }) { Text("إغلاق") } }
        )
    }

    if (showPartyPicker) {
        AlertDialog(
            onDismissRequest = { showPartyPicker = false },
            title = { Text(if (isSale) "اختيار العميل" else "اختيار المورد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = partySearch,
                        onValueChange = { partySearch = it },
                        label = { Text("بحث بالاسم أو الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isSale) {
                            val list = customers.filter { !it.isArchived && (partySearch.isBlank() || it.name.contains(partySearch, true) || (it.phone ?: "").contains(partySearch)) }
                            items(list, key = { it.id }) { c ->
                                AlAzziCard(onClick = { selectedPartyId = c.id; showPartyPicker = false }, modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(c.name, fontWeight = FontWeight.Bold)
                                        Text(Formatters.formatMoney(c.balance, currency), color = if (c.balance > 0) colors.debtRed else colors.paidGreen)
                                    }
                                }
                            }
                        } else {
                            val list = suppliers.filter { !it.isArchived && (partySearch.isBlank() || it.name.contains(partySearch, true) || (it.phone ?: "").contains(partySearch)) }
                            items(list, key = { it.id }) { s ->
                                AlAzziCard(onClick = { selectedPartyId = s.id; showPartyPicker = false }, modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(s.name, fontWeight = FontWeight.Bold)
                                        Text(Formatters.formatMoney(s.balance, currency))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPartyPicker = false }) { Text("إغلاق") } }
        )
    }

    if (showVoiceFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceFallbackDialog = false },
            title = { Text("الإدخال الصوتي / الإملاء السريع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    voiceStatusHint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.warningAmber) }
                    OutlinedTextField(
                        value = voiceTranscriptDraft,
                        onValueChange = { voiceTranscriptDraft = it },
                        label = { Text("مثال: سكر 12000 الكمية 2") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = voiceTranscriptDraft.isNotBlank(),
                    onClick = {
                        parsedVoicePreview = VoiceInvoiceParser.parse(voiceTranscriptDraft, items)
                        showVoiceFallbackDialog = false
                    }
                ) { Text("تحليل النص") }
            },
            dismissButton = { TextButton(onClick = { showVoiceFallbackDialog = false }) { Text("إغلاق") } }
        )
    }

    parsedVoicePreview?.let { parsed ->
        AlertDialog(
            onDismissRequest = { parsedVoicePreview = null },
            title = { Text("تأكيد نتائج الإدخال الصوتي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("النص الملتقط: ${parsed.rawTranscript}", style = MaterialTheme.typography.bodySmall)
                    Text("البحث عن الصنف: ${parsed.itemQuery.ifBlank { "-" }}")
                    Text(
                        "الصنف المطابق: ${parsed.matchedItem?.name ?: "لم يطابق صنفاً — اختره يدوياً بعد الاعتماد"}",
                        fontWeight = FontWeight.Bold,
                        color = if (parsed.matchedItem != null) colors.paidGreen else colors.warningAmber
                    )
                    Text("الإجمالي المستخرج: ${parsed.totalAmountInput.ifBlank { "غير محدد" }}")
                    Text("الكمية المستخرجة: ${parsed.quantityInput}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (parsed.totalAmountInput.isNotBlank()) lineTotalInput = parsed.totalAmountInput
                    qtyInput = parsed.quantityInput.ifBlank { "1" }
                    parsed.matchedItem?.let { selectedItem = it }
                    parsedVoicePreview = null
                }) { Text("تعبئة الحقول") }
            },
            dismissButton = { TextButton(onClick = { parsedVoicePreview = null }) { Text("إلغاء") } }
        )
    }

    receiptToPrint?.let { receipt ->
        ReceiptActionDialog(
            receipt = receipt,
            partyPhone = receiptPartyPhone,
            savedPrinterAddress = savedPrinterAddress,
            savedPaperWidth = savedPaperWidth,
            onDismiss = { receiptToPrint = null }
        )
    }
}
