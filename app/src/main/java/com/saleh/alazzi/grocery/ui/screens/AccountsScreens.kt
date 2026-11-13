package com.saleh.alazzi.grocery.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.data.local.entity.CustomerEntity
import com.saleh.alazzi.grocery.data.local.entity.SupplierEntity
import com.saleh.alazzi.grocery.printing.InvoiceExporter
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.components.*
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import java.util.UUID

private enum class PartyFilter(val label: String) {
    ACTIVE("النشطون"),
    DEBT("عليهم رصيد"),
    ZERO("المسددون"),
    ARCHIVED("المؤرشفون")
}

private enum class PartySort(val label: String) {
    RECENT("الأحدث حركة"),
    HIGHEST_BALANCE("الأعلى رصيداً"),
    NAME("الاسم")
}

@Composable
fun CustomersScreen(
    viewModel: GroceryViewModel,
    currency: String,
    onOpenCustomer: (String) -> Unit,
    onBack: () -> Unit
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val aggregates by viewModel.customerAggregates.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val canAdd = session?.role == UserRoles.MANAGER || session?.role == UserRoles.CASHIER
    val colors = LocalAlAzziColors.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PartyFilter.ACTIVE) }
    var sort by remember { mutableStateOf(PartySort.RECENT) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(customers, aggregates, query, filter, sort) {
        customers
            .filter { c ->
                val matchesQuery = query.isBlank() ||
                    c.name.contains(query, ignoreCase = true) ||
                    (c.phone ?: "").contains(query)
                val matchesFilter = when (filter) {
                    PartyFilter.ACTIVE -> !c.isArchived
                    PartyFilter.DEBT -> !c.isArchived && c.balance > 0
                    PartyFilter.ZERO -> !c.isArchived && c.balance == 0L
                    PartyFilter.ARCHIVED -> c.isArchived
                }
                matchesQuery && matchesFilter
            }
            .let { list ->
                when (sort) {
                    PartySort.RECENT -> list.sortedByDescending { aggregates[it.id]?.lastTransactionAt ?: it.updatedAt }
                    PartySort.HIGHEST_BALANCE -> list.sortedByDescending { it.balance }
                    PartySort.NAME -> list.sortedBy { it.name.lowercase() }
                }
            }
    }

    val totalDebt = remember(customers) { customers.filter { !it.isArchived }.sumOf { it.balance } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("customers_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "العملاء والذمم",
            subtitle = "إجمالي ديون العملاء: ${Formatters.formatMoney(totalDebt, currency)}",
            actionLabel = if (canAdd) "إضافة عميل" else null,
            onAction = if (canAdd) ({ showAddDialog = true }) else null,
            onBack = onBack
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("بحث باسم العميل أو رقم الهاتف") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("customer_search_input")
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PartyFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) }
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ترتيب:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PartySort.entries.forEach { s ->
                AssistChip(
                    onClick = { sort = s },
                    label = { Text(s.label, fontWeight = if (sort == s) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateView("لا يوجد عملاء مطابقون", "غيّر البحث أو أضف عميلاً جديداً")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { customer ->
                    val agg = aggregates[customer.id]
                    AlAzziCard(
                        onClick = { onOpenCustomer(customer.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_card_${customer.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(customer.phone ?: "بدون هاتف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusBadge(
                                    text = Formatters.formatMoney(customer.balance, currency),
                                    textColor = if (customer.balance > 0) colors.debtRed else colors.paidGreen,
                                    backgroundColor = if (customer.balance > 0) colors.debtRedContainer else colors.paidGreenContainer
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("مبيعات: ${Formatters.formatMoney(agg?.totalSales ?: 0L, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("سداد: ${Formatters.formatMoney(agg?.totalPayments ?: 0L, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (customer.isArchived) {
                                    StatusBadge("مؤرشف", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PartyFormDialog(
            title = "إضافة عميل جديد",
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, address, notes ->
                viewModel.addCustomer(name, phone, address, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CustomerAccountScreen(
    viewModel: GroceryViewModel,
    customerId: String,
    currency: String,
    onOpenSaleInvoice: (String) -> Unit,
    onBack: () -> Unit
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customer = customers.firstOrNull { it.id == customerId }
    val transactions by viewModel.customerTransactions(customerId).collectAsStateWithLifecycle(emptyList())
    val invoices by viewModel.customerSalesInvoices(customerId).collectAsStateWithLifecycle(emptyList())
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isManager = session?.role == UserRoles.MANAGER
    val canPay = isManager || session?.role == UserRoles.CASHIER
    val colors = LocalAlAzziColors.current
    val context = LocalContext.current
    val exporter = remember(context) { InvoiceExporter(context) }

    var showPayDialog by remember { mutableStateOf(false) }
    var showDebitDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var opId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    if (customer == null) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            SectionHeader("كشف حساب العميل", onBack = onBack)
            ErrorStateView("العميل غير موجود", "ربما تم حذف أو تعديل بيانات هذا العميل")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("customer_account_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = customer.name,
            subtitle = listOfNotNull(customer.phone, customer.address).joinToString(" • ").ifBlank { "كشف حساب مستقل" },
            onBack = onBack
        )

        AlAzziCard(
            containerColor = if (customer.balance > 0) colors.debtRedContainer else colors.paidGreenContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("الرصيد المستحق على العميل", style = MaterialTheme.typography.bodySmall)
                    Text(
                        Formatters.formatMoney(customer.balance, currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (customer.balance > 0) colors.debtRed else colors.paidGreen
                    )
                }
                if (!customer.phone.isNullOrBlank()) {
                    FilledTonalButton(onClick = { runCatching { exporter.openWhatsAppChat(customer.phone) } }) {
                        Icon(Icons.Default.Chat, contentDescription = "واتساب", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("واتساب")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canPay) {
                Button(onClick = { opId = UUID.randomUUID().toString(); showPayDialog = true }) {
                    Text("تسجيل دفعة / قبض")
                }
            }
            if (isManager) {
                OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showDebitDialog = true }) {
                    Text("إضافة دين")
                }
                OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showAdjustDialog = true }) {
                    Text("تصحيح رصيد")
                }
                if (customer.balance > 0) {
                    OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showSettleDialog = true }) {
                        Text("تسوية كاملة")
                    }
                }
            }
            if (canPay) {
                OutlinedButton(onClick = { showEditDialog = true }) { Text("تعديل البيانات") }
            }
            if (isManager) {
                TextButton(onClick = { viewModel.setCustomerArchived(customer.id, !customer.isArchived) }) {
                    Text(if (customer.isArchived) "استعادة" else "أرشفة")
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("كشف الحساب (${transactions.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("الفواتير (${invoices.size})") })
        }

        if (selectedTab == 0) {
            if (transactions.isEmpty()) {
                EmptyStateView("لا توجد قيود مالية بعد", "ستظهر هنا كل الفواتير والدفعات والتعديلات")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions, key = { it.id }) { tx ->
                        val isInvoiceLink = tx.sourceType == "SALES_INVOICE" && !tx.sourceId.isNullOrBlank()
                        AlAzziCard(
                            onClick = if (isInvoiceLink) ({ onOpenSaleInvoice(tx.sourceId!!) }) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(ledgerTypeLabel(tx.type), fontWeight = FontWeight.Bold)
                                    tx.note?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        Formatters.formatDateTime(tx.createdAt) + if (isInvoiceLink) "  •  اضغط لفتح الفاتورة" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        Formatters.formatMoney(tx.amount, currency),
                                        fontWeight = FontWeight.Bold,
                                        color = if (tx.type in listOf("PAYMENT", "RETURN", "SETTLEMENT")) colors.paidGreen else colors.debtRed
                                    )
                                    Text("الرصيد بعد: ${Formatters.formatMoney(tx.balanceAfter, currency)}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (invoices.isEmpty()) {
                EmptyStateView("لا توجد فواتير لهذا العميل", "يمكن إنشاء فاتورة بيع جديدة وربطها بهذا العميل")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(invoices, key = { it.id }) { inv ->
                        AlAzziCard(onClick = { onOpenSaleInvoice(inv.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("فاتورة بيع #${inv.invoiceNumber}", fontWeight = FontWeight.Bold)
                                    Text(Formatters.formatDateTime(inv.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Formatters.formatMoney(inv.total, currency), fontWeight = FontWeight.Bold)
                                    if (inv.status == "VOIDED") {
                                        StatusBadge("ملغاة", colors.debtRed, colors.debtRedContainer)
                                    } else if (inv.remaining > 0) {
                                        StatusBadge("متبقي ${Formatters.formatMoney(inv.remaining, currency)}", colors.debtRed, colors.debtRedContainer)
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

    if (showPayDialog) {
        AmountReasonDialog(
            title = "تسجيل دفعة / قبض من ${customer.name}",
            amountLabel = "المبلغ المستلم ($currency)",
            noteLabel = "ملاحظة السداد",
            initialAmount = customer.balance.takeIf { it > 0 }?.toString().orEmpty(),
            onDismiss = { showPayDialog = false },
            onConfirm = { amount, note ->
                viewModel.receiveCustomerPayment(customer.id, amount, note, opId)
                showPayDialog = false
            }
        )
    }

    if (showDebitDialog) {
        AmountReasonDialog(
            title = "إضافة دين يدوي على ${customer.name}",
            amountLabel = "مبلغ الدين ($currency)",
            noteLabel = "السبب / البيان (مطلوب)",
            requireNote = true,
            onDismiss = { showDebitDialog = false },
            onConfirm = { amount, note ->
                viewModel.addCustomerDebit(customer.id, amount, note, opId)
                showDebitDialog = false
            }
        )
    }

    if (showAdjustDialog) {
        AmountReasonDialog(
            title = "تصحيح رصيد العميل (مدير)",
            amountLabel = "الرصيد الجديد الصحيح ($currency)",
            noteLabel = "سبب التصحيح المحاسبي (مطلوب)",
            initialAmount = customer.balance.toString(),
            allowZero = true,
            requireNote = true,
            onDismiss = { showAdjustDialog = false },
            onConfirm = { newBalance, reason ->
                viewModel.adjustCustomerBalance(customer.id, newBalance, reason, opId)
                showAdjustDialog = false
            }
        )
    }

    if (showSettleDialog) {
        ConfirmReasonDialog(
            title = "تسوية حساب العميل",
            description = "سيتم تصفير الرصيد (${Formatters.formatMoney(customer.balance, currency)}) بقيد تسوية رسمي مع الاحتفاظ الكامل بكل الفواتير والقيود التاريخية دون إنشاء حركة صندوق وهمية.",
            reasonLabel = "سبب التسوية (مطلوب)",
            onDismiss = { showSettleDialog = false },
            onConfirm = { reason ->
                viewModel.settleCustomerBalance(customer.id, reason, opId)
                showSettleDialog = false
            }
        )
    }

    if (showEditDialog) {
        PartyFormDialog(
            title = "تعديل بيانات العميل",
            initialName = customer.name,
            initialPhone = customer.phone.orEmpty(),
            initialAddress = customer.address.orEmpty(),
            initialNotes = customer.notes.orEmpty(),
            onDismiss = { showEditDialog = false },
            onSave = { name, phone, address, notes ->
                viewModel.updateCustomer(customer.id, name, phone, address, notes)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun SuppliersScreen(
    viewModel: GroceryViewModel,
    currency: String,
    onOpenSupplier: (String) -> Unit,
    onBack: () -> Unit
) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val aggregates by viewModel.supplierAggregates.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isManager = session?.role == UserRoles.MANAGER
    val colors = LocalAlAzziColors.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PartyFilter.ACTIVE) }
    var sort by remember { mutableStateOf(PartySort.RECENT) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(suppliers, aggregates, query, filter, sort) {
        suppliers
            .filter { s ->
                val matchesQuery = query.isBlank() ||
                    s.name.contains(query, ignoreCase = true) ||
                    (s.phone ?: "").contains(query)
                val matchesFilter = when (filter) {
                    PartyFilter.ACTIVE -> !s.isArchived
                    PartyFilter.DEBT -> !s.isArchived && s.balance > 0
                    PartyFilter.ZERO -> !s.isArchived && s.balance == 0L
                    PartyFilter.ARCHIVED -> s.isArchived
                }
                matchesQuery && matchesFilter
            }
            .let { list ->
                when (sort) {
                    PartySort.RECENT -> list.sortedByDescending { aggregates[it.id]?.lastTransactionAt ?: it.updatedAt }
                    PartySort.HIGHEST_BALANCE -> list.sortedByDescending { it.balance }
                    PartySort.NAME -> list.sortedBy { it.name.lowercase() }
                }
            }
    }

    val totalDue = remember(suppliers) { suppliers.filter { !it.isArchived }.sumOf { it.balance } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("suppliers_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "الموردون والمستحقات",
            subtitle = "إجمالي المستحقات للموردين: ${Formatters.formatMoney(totalDue, currency)}",
            actionLabel = if (isManager) "إضافة مورد" else null,
            onAction = if (isManager) ({ showAddDialog = true }) else null,
            onBack = onBack
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("بحث باسم المورد أو رقم الهاتف") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PartyFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateView("لا يوجد موردون مطابقون", "أضف مورداً جديداً أو عدّل الفلتر")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { supplier ->
                    val agg = aggregates[supplier.id]
                    AlAzziCard(onClick = { onOpenSupplier(supplier.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(supplier.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(supplier.phone ?: "بدون هاتف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusBadge(
                                    text = Formatters.formatMoney(supplier.balance, currency),
                                    textColor = if (supplier.balance > 0) colors.warningAmber else colors.paidGreen,
                                    backgroundColor = if (supplier.balance > 0) colors.warningAmberContainer else colors.paidGreenContainer
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("مشتريات: ${Formatters.formatMoney(agg?.totalPurchases ?: 0L, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("مدفوعات: ${Formatters.formatMoney(agg?.totalPayments ?: 0L, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PartyFormDialog(
            title = "إضافة مورد جديد",
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, address, notes ->
                viewModel.addSupplier(name, phone, address, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun SupplierAccountScreen(
    viewModel: GroceryViewModel,
    supplierId: String,
    currency: String,
    onOpenPurchaseInvoice: (String) -> Unit,
    onBack: () -> Unit
) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val supplier = suppliers.firstOrNull { it.id == supplierId }
    val transactions by viewModel.supplierTransactions(supplierId).collectAsStateWithLifecycle(emptyList())
    val invoices by viewModel.supplierPurchaseInvoices(supplierId).collectAsStateWithLifecycle(emptyList())
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isManager = session?.role == UserRoles.MANAGER
    val colors = LocalAlAzziColors.current

    var showPayDialog by remember { mutableStateOf(false) }
    var showDebitDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var opId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    if (supplier == null) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            SectionHeader("كشف حساب المورد", onBack = onBack)
            ErrorStateView("المورد غير موجود", "تحقق من قائمة الموردين")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("supplier_account_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = supplier.name,
            subtitle = listOfNotNull(supplier.phone, supplier.address).joinToString(" • ").ifBlank { "حساب مورد مستقل" },
            onBack = onBack
        )

        AlAzziCard(
            containerColor = if (supplier.balance > 0) colors.warningAmberContainer else colors.paidGreenContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("المستحق للمورد", style = MaterialTheme.typography.bodySmall)
                Text(
                    Formatters.formatMoney(supplier.balance, currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (supplier.balance > 0) colors.warningAmber else colors.paidGreen
                )
            }
        }

        if (isManager) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { opId = UUID.randomUUID().toString(); showPayDialog = true }) { Text("سداد للمورد") }
                OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showDebitDialog = true }) { Text("مستحق إضافي") }
                OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showAdjustDialog = true }) { Text("تصحيح رصيد") }
                if (supplier.balance > 0) {
                    OutlinedButton(onClick = { opId = UUID.randomUUID().toString(); showSettleDialog = true }) { Text("تسوية كاملة") }
                }
                OutlinedButton(onClick = { showEditDialog = true }) { Text("تعديل") }
                TextButton(onClick = { viewModel.setSupplierArchived(supplier.id, !supplier.isArchived) }) {
                    Text(if (supplier.isArchived) "استعادة" else "أرشفة")
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("كشف الحساب (${transactions.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("فواتير الشراء (${invoices.size})") })
        }

        if (selectedTab == 0) {
            if (transactions.isEmpty()) {
                EmptyStateView("لا توجد قيود للمورد", "ستظهر فواتير الشراء والمدفوعات هنا")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(transactions, key = { it.id }) { tx ->
                        val isInvoiceLink = tx.sourceType == "PURCHASE_INVOICE" && !tx.sourceId.isNullOrBlank()
                        AlAzziCard(
                            onClick = if (isInvoiceLink) ({ onOpenPurchaseInvoice(tx.sourceId!!) }) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ledgerTypeLabel(tx.type), fontWeight = FontWeight.Bold)
                                    tx.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    Text(
                                        Formatters.formatDateTime(tx.createdAt) + if (isInvoiceLink) "  •  اضغط لفتح الفاتورة" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Formatters.formatMoney(tx.amount, currency), fontWeight = FontWeight.Bold)
                                    Text("الرصيد بعد: ${Formatters.formatMoney(tx.balanceAfter, currency)}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (invoices.isEmpty()) {
                EmptyStateView("لا توجد فواتير شراء", "أنشئ فاتورة شراء واربطها بهذا المورد")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(invoices, key = { it.id }) { inv ->
                        AlAzziCard(onClick = { onOpenPurchaseInvoice(inv.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("فاتورة شراء #${inv.invoiceNumber}", fontWeight = FontWeight.Bold)
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

    if (showPayDialog) {
        AmountReasonDialog(
            title = "تسجيل سداد للمورد ${supplier.name}",
            amountLabel = "المبلغ المدفوع ($currency)",
            noteLabel = "ملاحظة السداد",
            initialAmount = supplier.balance.takeIf { it > 0 }?.toString().orEmpty(),
            onDismiss = { showPayDialog = false },
            onConfirm = { amount, note ->
                viewModel.paySupplier(supplier.id, amount, note, opId)
                showPayDialog = false
            }
        )
    }

    if (showDebitDialog) {
        AmountReasonDialog(
            title = "إضافة مستحق للمورد ${supplier.name}",
            amountLabel = "المبلغ ($currency)",
            noteLabel = "السبب (مطلوب)",
            requireNote = true,
            onDismiss = { showDebitDialog = false },
            onConfirm = { amount, note ->
                viewModel.addSupplierDebit(supplier.id, amount, note, opId)
                showDebitDialog = false
            }
        )
    }

    if (showAdjustDialog) {
        AmountReasonDialog(
            title = "تصحيح رصيد المورد (مدير)",
            amountLabel = "الرصيد الجديد الصحيح ($currency)",
            noteLabel = "سبب التصحيح (مطلوب)",
            initialAmount = supplier.balance.toString(),
            allowZero = true,
            requireNote = true,
            onDismiss = { showAdjustDialog = false },
            onConfirm = { newBalance, reason ->
                viewModel.adjustSupplierBalance(supplier.id, newBalance, reason, opId)
                showAdjustDialog = false
            }
        )
    }

    if (showSettleDialog) {
        ConfirmReasonDialog(
            title = "تسوية حساب المورد",
            description = "سيتم تصفير رصيد المورد (${Formatters.formatMoney(supplier.balance, currency)}) بقيد تسوية رسمي مع حفظ السجل التاريخي.",
            reasonLabel = "سبب التسوية (مطلوب)",
            onDismiss = { showSettleDialog = false },
            onConfirm = { reason ->
                viewModel.settleSupplierBalance(supplier.id, reason, opId)
                showSettleDialog = false
            }
        )
    }

    if (showEditDialog) {
        PartyFormDialog(
            title = "تعديل بيانات المورد",
            initialName = supplier.name,
            initialPhone = supplier.phone.orEmpty(),
            initialAddress = supplier.address.orEmpty(),
            initialNotes = supplier.notes.orEmpty(),
            onDismiss = { showEditDialog = false },
            onSave = { name, phone, address, notes ->
                viewModel.updateSupplier(supplier.id, name, phone, address, notes)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun PartyFormDialog(
    title: String,
    initialName: String = "",
    initialPhone: String = "",
    initialAddress: String = "",
    initialNotes: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, phone, address, notes) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AmountReasonDialog(
    title: String,
    amountLabel: String,
    noteLabel: String,
    initialAmount: String = "",
    allowZero: Boolean = false,
    requireNote: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var amountText by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf("") }
    val parsed = Formatters.parseWholeAmount(amountText)
    val validAmount = parsed != null && (allowZero || parsed > 0)
    val validNote = !requireNote || note.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = Formatters.filterWholeNumberInput(it) },
                    label = { Text(amountLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(noteLabel) }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = validAmount && validNote,
                onClick = { onConfirm(parsed!!, note) }
            ) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun ConfirmReasonDialog(
    title: String,
    description: String,
    reasonLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(reasonLabel) }
                )
            }
        },
        confirmButton = {
            Button(enabled = reason.isNotBlank(), onClick = { onConfirm(reason) }) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun ledgerTypeLabel(type: String): String = when (type) {
    "INVOICE" -> "فاتورة"
    "PAYMENT" -> "دفعة / سداد"
    "DEBIT" -> "دين / مستحق يدوي"
    "RETURN" -> "مرتجع"
    "SETTLEMENT" -> "تسوية حساب"
    "CORRECTION" -> "تصحيح رصيد (مدير)"
    else -> type
}
