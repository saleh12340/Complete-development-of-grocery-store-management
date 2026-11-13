package com.saleh.alazzi.grocery.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.data.repository.ReturnLineInput
import com.saleh.alazzi.grocery.printing.ReceiptData
import com.saleh.alazzi.grocery.printing.ReceiptItem
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.components.*
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import java.math.BigDecimal
import java.util.UUID

private enum class InvoiceFilter(val label: String) {
    ALL("الكل"),
    PAID("مدفوعة"),
    REMAINING("آجل / متبقي"),
    VOID("ملغاة")
}

@Composable
fun SalesInvoiceHistoryScreen(
    viewModel: GroceryViewModel,
    currency: String,
    onOpenInvoice: (String) -> Unit,
    onBack: () -> Unit
) {
    val invoices by viewModel.salesInvoices.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customerNames = remember(customers) { customers.associate { it.id to it.name } }
    val colors = LocalAlAzziColors.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(InvoiceFilter.ALL) }

    val filtered = remember(invoices, customerNames, query, filter) {
        invoices.filter { inv ->
            val party = inv.customerId?.let { customerNames[it] } ?: "عميل نقدي"
            val matchQ = query.isBlank() || inv.invoiceNumber.contains(query, true) || party.contains(query, true)
            val matchF = when (filter) {
                InvoiceFilter.ALL -> true
                InvoiceFilter.PAID -> inv.status == "POSTED" && inv.remaining == 0L
                InvoiceFilter.REMAINING -> inv.status == "POSTED" && inv.remaining > 0L
                InvoiceFilter.VOID -> inv.status == "VOIDED"
            }
            matchQ && matchF
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("sales_history_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader("سجل فواتير المبيعات", "عدد الفواتير: ${filtered.size}", onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("بحث برقم الفاتورة أو اسم العميل") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InvoiceFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateView("لا توجد فواتير مطابقة", "أنشئ فاتورة بيع جديدة أو عدّل الفلتر")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { inv ->
                    val party = inv.customerId?.let { customerNames[it] } ?: "عميل نقدي"
                    AlAzziCard(onClick = { onOpenInvoice(inv.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فاتورة #${inv.invoiceNumber} — $party", fontWeight = FontWeight.Bold)
                                Text(Formatters.formatDateTime(inv.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Formatters.formatMoney(inv.total, currency), fontWeight = FontWeight.Bold)
                                when {
                                    inv.status == "VOIDED" -> StatusBadge("ملغاة", colors.debtRed, colors.debtRedContainer)
                                    inv.remaining > 0 -> StatusBadge("متبقي ${Formatters.formatMoney(inv.remaining, currency)}", colors.debtRed, colors.debtRedContainer)
                                    else -> StatusBadge("مدفوعة", colors.paidGreen, colors.paidGreenContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseInvoiceHistoryScreen(
    viewModel: GroceryViewModel,
    currency: String,
    onOpenInvoice: (String) -> Unit,
    onBack: () -> Unit
) {
    val invoices by viewModel.purchaseInvoices.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val supplierNames = remember(suppliers) { suppliers.associate { it.id to it.name } }
    val colors = LocalAlAzziColors.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(InvoiceFilter.ALL) }

    val filtered = remember(invoices, supplierNames, query, filter) {
        invoices.filter { inv ->
            val party = inv.supplierId?.let { supplierNames[it] } ?: "شراء نقدي"
            val matchQ = query.isBlank() || inv.invoiceNumber.contains(query, true) || party.contains(query, true)
            val matchF = when (filter) {
                InvoiceFilter.ALL -> true
                InvoiceFilter.PAID -> inv.status == "POSTED" && inv.remaining == 0L
                InvoiceFilter.REMAINING -> inv.status == "POSTED" && inv.remaining > 0L
                InvoiceFilter.VOID -> inv.status == "VOIDED"
            }
            matchQ && matchF
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("purchase_history_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader("سجل فواتير المشتريات", "عدد الفواتير: ${filtered.size}", onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("بحث برقم الفاتورة أو اسم المورد") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InvoiceFilter.entries.filter { it != InvoiceFilter.VOID }.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateView("لا توجد فواتير شراء مطابقة", "أنشئ فاتورة شراء جديدة أو عدّل البحث")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { inv ->
                    val party = inv.supplierId?.let { supplierNames[it] } ?: "شراء نقدي"
                    AlAzziCard(onClick = { onOpenInvoice(inv.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فاتورة شراء #${inv.invoiceNumber} — $party", fontWeight = FontWeight.Bold)
                                Text(Formatters.formatDateTime(inv.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Formatters.formatMoney(inv.total, currency), fontWeight = FontWeight.Bold)
                                if (inv.remaining > 0) {
                                    StatusBadge("متبقي ${Formatters.formatMoney(inv.remaining, currency)}", colors.warningAmber, colors.warningAmberContainer)
                                } else {
                                    StatusBadge("مسددة", colors.paidGreen, colors.paidGreenContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SalesInvoiceDetailScreen(
    viewModel: GroceryViewModel,
    invoiceId: String,
    storeName: String,
    currency: String,
    savedPrinterAddress: String?,
    savedPaperWidth: Int,
    onBack: () -> Unit
) {
    val invoice by viewModel.salesInvoice(invoiceId).collectAsStateWithLifecycle(null)
    val lines by viewModel.salesInvoiceLines(invoiceId).collectAsStateWithLifecycle(emptyList())
    val payments by viewModel.salesInvoicePayments(invoiceId).collectAsStateWithLifecycle(emptyList())
    val returns by viewModel.salesInvoiceReturns(invoiceId).collectAsStateWithLifecycle(emptyList())
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isManager = session?.role == UserRoles.MANAGER
    val canPay = isManager || session?.role == UserRoles.CASHIER
    val colors = LocalAlAzziColors.current

    var showPayDialog by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showVoidDialog by remember { mutableStateOf(false) }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var opId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    val inv = invoice
    if (inv == null) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            SectionHeader("تفاصيل فاتورة البيع", onBack = onBack)
            LoadingStateView()
        }
        return
    }
    val customer = inv.customerId?.let { id -> customers.firstOrNull { it.id == id } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("sales_invoice_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "فاتورة بيع #${inv.invoiceNumber}",
                subtitle = "${inv.storeNameSnapshot ?: storeName}  •  ${Formatters.formatDateTime(inv.createdAt)}",
                onBack = onBack
            )
        }

        item {
            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("العميل: ${customer?.name ?: "عميل نقدي"}", fontWeight = FontWeight.Bold)
                        when {
                            inv.status == "VOIDED" -> StatusBadge("ملغاة", colors.debtRed, colors.debtRedContainer)
                            inv.remaining == 0L -> StatusBadge("مدفوعة بالكامل", colors.paidGreen, colors.paidGreenContainer)
                            inv.paid == 0L -> StatusBadge("آجل", colors.debtRed, colors.debtRedContainer)
                            else -> StatusBadge("سداد جزئي", colors.warningAmber, colors.warningAmberContainer)
                        }
                    }
                    inv.storePhoneSnapshot?.takeIf { it.isNotBlank() }?.let {
                        Text("هاتف المتجر: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inv.notes?.takeIf { it.isNotBlank() }?.let {
                        Text("ملاحظات: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإجمالي: ${Formatters.formatMoney(inv.total, currency)}", fontWeight = FontWeight.Bold)
                        Text("الواصل: ${Formatters.formatMoney(inv.paid, currency)}", color = colors.paidGreen)
                        Text("المتبقي: ${Formatters.formatMoney(inv.remaining, currency)}", color = if (inv.remaining > 0) colors.debtRed else colors.paidGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showReceiptDialog = true }) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("طباعة / PDF / واتساب")
                }
                if (canPay && inv.status == "POSTED" && inv.remaining > 0) {
                    FilledTonalButton(onClick = { opId = UUID.randomUUID().toString(); showPayDialog = true }) {
                        Text("تسجيل دفعة للفاتورة")
                    }
                }
                if (isManager && inv.status == "POSTED") {
                    OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showReturnDialog = true }) {
                        Text("تسجيل مرتجع")
                    }
                    OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showVoidDialog = true }) {
                        Text("إلغاء الفاتورة", color = colors.debtRed)
                    }
                }
            }
        }

        item { Text("أصناف الفاتورة (${lines.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        itemsIndexed(lines, key = { _, it -> it.id }) { idx, line ->
            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${idx + 1}", modifier = Modifier.width(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Text(line.itemNameSnapshot, modifier = Modifier.weight(1.8f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Formatters.formatQty(line.quantityMilli), modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(line.unitPrice.toString(), modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(Formatters.formatMoney(line.lineTotal, currency), modifier = Modifier.weight(1.1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (payments.isNotEmpty()) {
            item { Text("الدفعات المرتبطة بالفاتورة (${payments.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(payments, key = { it.id }) { p ->
                AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(p.note ?: "دفعة سداد", fontWeight = FontWeight.SemiBold)
                            Text(Formatters.formatDateTime(p.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Formatters.formatMoney(p.amount, currency), fontWeight = FontWeight.Bold, color = colors.paidGreen)
                    }
                }
            }
        }

        if (returns.isNotEmpty()) {
            item { Text("المرتجعات المسجلة (${returns.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(returns, key = { it.id }) { ret ->
                AlAzziCard(containerColor = colors.warningAmberContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("مرتجع بيع", fontWeight = FontWeight.Bold)
                            ret.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(Formatters.formatDateTime(ret.createdAt), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(Formatters.formatMoney(ret.total, currency), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPayDialog) {
        AmountReasonDialog(
            title = "تسجيل دفعة على الفاتورة #${inv.invoiceNumber}",
            amountLabel = "المبلغ المدفوع (المتبقي: ${inv.remaining} $currency)",
            noteLabel = "ملاحظة الدفعة",
            initialAmount = inv.remaining.toString(),
            onDismiss = { showPayDialog = false },
            onConfirm = { amount, note ->
                viewModel.paySalesInvoice(inv.id, amount, note, opId)
                showPayDialog = false
            }
        )
    }

    if (showReturnDialog) {
        PartialReturnDialog(
            title = "مرتجع فاتورة بيع #${inv.invoiceNumber}",
            items = lines.map { Triple(it.id, it.itemNameSnapshot, Formatters.formatQty(it.quantityMilli)) },
            onDismiss = { showReturnDialog = false },
            onSubmit = { returnLines, note ->
                viewModel.returnSale(inv.id, returnLines, note, opId) { ok -> if (ok) showReturnDialog = false }
            }
        )
    }

    if (showVoidDialog) {
        ConfirmReasonDialog(
            title = "إلغاء فاتورة البيع #${inv.invoiceNumber}",
            description = "سيتم عكس حركة المخزون والعميل والصندوق بالكامل مع الاحتفاظ بسجل الفاتورة كملغاة لأغراض التدقيق.",
            reasonLabel = "سبب الإلغاء (مطلوب)",
            onDismiss = { showVoidDialog = false },
            onConfirm = { reason ->
                viewModel.voidSalesInvoice(inv.id, reason, opId)
                showVoidDialog = false
            }
        )
    }

    if (showReceiptDialog) {
        ReceiptActionDialog(
            receipt = ReceiptData(
                storeName = inv.storeNameSnapshot ?: storeName,
                invoiceType = "فاتورة مبيعات",
                invoiceNumber = inv.invoiceNumber,
                dateTime = Formatters.formatDateTime(inv.createdAt),
                partyName = customer?.name,
                items = lines.map { ReceiptItem(it.itemNameSnapshot, Formatters.formatQty(it.quantityMilli), it.unitPrice, it.lineTotal) },
                subtotal = inv.subtotal,
                discount = inv.discount,
                total = inv.total,
                paid = inv.paid,
                remaining = inv.remaining,
                accountBalance = customer?.balance,
                storePhone = inv.storePhoneSnapshot,
                storeAddress = inv.storeAddressSnapshot,
                footer = inv.storeFooterSnapshot,
                paymentStatus = if (inv.status == "VOIDED") "ملغاة" else null,
                currencyLabel = currency
            ),
            partyPhone = customer?.phone,
            savedPrinterAddress = savedPrinterAddress,
            savedPaperWidth = savedPaperWidth,
            onDismiss = { showReceiptDialog = false }
        )
    }
}

@Composable
fun PurchaseInvoiceDetailScreen(
    viewModel: GroceryViewModel,
    invoiceId: String,
    storeName: String,
    currency: String,
    savedPrinterAddress: String?,
    savedPaperWidth: Int,
    onBack: () -> Unit
) {
    val invoice by viewModel.purchaseInvoice(invoiceId).collectAsStateWithLifecycle(null)
    val lines by viewModel.purchaseInvoiceLines(invoiceId).collectAsStateWithLifecycle(emptyList())
    val payments by viewModel.purchaseInvoicePayments(invoiceId).collectAsStateWithLifecycle(emptyList())
    val returns by viewModel.purchaseInvoiceReturns(invoiceId).collectAsStateWithLifecycle(emptyList())
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isManager = session?.role == UserRoles.MANAGER
    val colors = LocalAlAzziColors.current

    var showPayDialog by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var opId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    val inv = invoice
    if (inv == null) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            SectionHeader("تفاصيل فاتورة الشراء", onBack = onBack)
            LoadingStateView()
        }
        return
    }
    val supplier = inv.supplierId?.let { id -> suppliers.firstOrNull { it.id == id } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("purchase_invoice_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "فاتورة شراء #${inv.invoiceNumber}",
                subtitle = "${inv.storeNameSnapshot ?: storeName}  •  ${Formatters.formatDateTime(inv.createdAt)}",
                onBack = onBack
            )
        }

        item {
            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المورد: ${supplier?.name ?: "شراء نقدي"}", fontWeight = FontWeight.Bold)
                        StatusBadge(
                            text = if (inv.remaining == 0L) "مسددة" else "متبقي ${Formatters.formatMoney(inv.remaining, currency)}",
                            textColor = if (inv.remaining == 0L) colors.paidGreen else colors.warningAmber,
                            backgroundColor = if (inv.remaining == 0L) colors.paidGreenContainer else colors.warningAmberContainer
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإجمالي: ${Formatters.formatMoney(inv.total, currency)}", fontWeight = FontWeight.Bold)
                        Text("المدفوع: ${Formatters.formatMoney(inv.paid, currency)}", color = colors.paidGreen)
                        Text("المتبقي: ${Formatters.formatMoney(inv.remaining, currency)}")
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showReceiptDialog = true }) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("طباعة / PDF / مشاركة")
                }
                if (isManager && inv.status == "POSTED" && inv.remaining > 0) {
                    FilledTonalButton(onClick = { opId = UUID.randomUUID().toString(); showPayDialog = true }) {
                        Text("تسجيل سداد للفاتورة")
                    }
                }
                if (isManager && inv.status == "POSTED") {
                    OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showReturnDialog = true }) {
                        Text("مرتجع شراء")
                    }
                }
            }
        }

        item { Text("أصناف الشراء (${lines.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        itemsIndexed(lines, key = { _, it -> it.id }) { idx, line ->
            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${idx + 1}", modifier = Modifier.width(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Text(line.itemNameSnapshot, modifier = Modifier.weight(1.8f), fontWeight = FontWeight.SemiBold)
                    Text(Formatters.formatQty(line.quantityMilli), modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(line.unitCost.toString(), modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(Formatters.formatMoney(line.lineTotal, currency), modifier = Modifier.weight(1.1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (payments.isNotEmpty()) {
            item { Text("الدفعات المرتبطة (${payments.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(payments, key = { it.id }) { p ->
                AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(p.note ?: "دفعة شراء", fontWeight = FontWeight.SemiBold)
                            Text(Formatters.formatDateTime(p.createdAt), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(Formatters.formatMoney(p.amount, currency), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (returns.isNotEmpty()) {
            item { Text("مرتجعات الشراء (${returns.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(returns, key = { it.id }) { ret ->
                AlAzziCard(containerColor = colors.warningAmberContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ret.note ?: "مرتجع شراء")
                        Text(Formatters.formatMoney(ret.total, currency), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPayDialog) {
        AmountReasonDialog(
            title = "سداد فاتورة الشراء #${inv.invoiceNumber}",
            amountLabel = "المبلغ المدفوع (المتبقي: ${inv.remaining} $currency)",
            noteLabel = "ملاحظة الدفعة",
            initialAmount = inv.remaining.toString(),
            onDismiss = { showPayDialog = false },
            onConfirm = { amount, note ->
                viewModel.payPurchaseInvoice(inv.id, amount, note, opId)
                showPayDialog = false
            }
        )
    }

    if (showReturnDialog) {
        PartialReturnDialog(
            title = "مرتجع فاتورة شراء #${inv.invoiceNumber}",
            items = lines.map { Triple(it.id, it.itemNameSnapshot, Formatters.formatQty(it.quantityMilli)) },
            onDismiss = { showReturnDialog = false },
            onSubmit = { returnLines, note ->
                viewModel.returnPurchase(inv.id, returnLines, note, opId) { ok -> if (ok) showReturnDialog = false }
            }
        )
    }

    if (showReceiptDialog) {
        ReceiptActionDialog(
            receipt = ReceiptData(
                storeName = inv.storeNameSnapshot ?: storeName,
                invoiceType = "فاتورة مشتريات",
                invoiceNumber = inv.invoiceNumber,
                dateTime = Formatters.formatDateTime(inv.createdAt),
                partyName = supplier?.name,
                items = lines.map { ReceiptItem(it.itemNameSnapshot, Formatters.formatQty(it.quantityMilli), it.unitCost, it.lineTotal) },
                subtotal = inv.subtotal,
                discount = inv.discount,
                total = inv.total,
                paid = inv.paid,
                remaining = inv.remaining,
                accountBalance = supplier?.balance,
                storePhone = inv.storePhoneSnapshot,
                storeAddress = inv.storeAddressSnapshot,
                footer = inv.storeFooterSnapshot,
                currencyLabel = currency
            ),
            partyPhone = supplier?.phone,
            savedPrinterAddress = savedPrinterAddress,
            savedPaperWidth = savedPaperWidth,
            onDismiss = { showReceiptDialog = false }
        )
    }
}

@Composable
private fun PartialReturnDialog(
    title: String,
    items: List<Triple<String, String, String>>,
    onDismiss: () -> Unit,
    onSubmit: (List<ReturnLineInput>, String) -> Unit
) {
    val qtyMap = remember(items) { mutableStateMapOf<String, String>() }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(items, key = { it.first }) { (lineId, name, origQty) ->
                        OutlinedTextField(
                            value = qtyMap[lineId].orEmpty(),
                            onValueChange = { qtyMap[lineId] = Formatters.filterDecimalInput(it) },
                            label = { Text("$name (الأصلي: $origQty)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("سبب المرتجع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = items.mapNotNull { (lineId, _, _) ->
                    val q = Formatters.parseQuantityMilli(qtyMap[lineId].orEmpty()) ?: return@mapNotNull null
                    ReturnLineInput(lineId, q)
                }
                if (parsed.isNotEmpty()) onSubmit(parsed, note)
            }) { Text("اعتماد المرتجع") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
