package com.saleh.alazzi.grocery.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.printing.*
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.components.AlAzziCard
import com.saleh.alazzi.grocery.ui.components.DocumentPrintDialog
import com.saleh.alazzi.grocery.ui.components.Formatters
import com.saleh.alazzi.grocery.ui.components.SectionHeader
import com.saleh.alazzi.grocery.ui.components.currentPrintSettings

private data class PrintCenterCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val requiresManager: Boolean = false
)

@Composable
fun PrintCenterScreen(
    viewModel: GroceryViewModel,
    storeName: String,
    currency: String,
    savedPrinterAddress: String?,
    savedPrinterName: String?,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val settings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val settingsMap = remember(settings) { settings.associate { it.key to it.value } }
    val isManager = session?.role == "MANAGER"

    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val salesInvoices by viewModel.salesInvoices.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.purchaseInvoices.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val allItems by viewModel.allItemsIncludingArchived.collectAsStateWithLifecycle()
    val detailedSummary by viewModel.detailedSummary.collectAsStateWithLifecycle()

    var activeCategory by remember { mutableStateOf<PrintCenterCategory?>(null) }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var selectedSupplierId by remember { mutableStateOf<String?>(null) }
    var selectedSaleInvoiceId by remember { mutableStateOf<String?>(null) }
    var selectedPurchaseInvoiceId by remember { mutableStateOf<String?>(null) }
    var selectedJournalId by remember { mutableStateOf<String?>(null) }

    val categories = remember(isManager) {
        buildList {
            add(PrintCenterCategory("sales_history", "تقرير وقائمة فواتير المبيعات", "جميع فواتير البيع والمجاميع", Icons.Default.Receipt))
            if (isManager) add(PrintCenterCategory("purchase_history", "قائمة فواتير المشتريات", "مشتريات البضائع والموردين", Icons.Default.ShoppingBag, true))
            add(PrintCenterCategory("customer_statement", "كشف حساب عميل مفصل", "كشف حساب الذمم والحركات", Icons.Default.People))
            if (isManager) add(PrintCenterCategory("supplier_statement", "كشف حساب مورد مفصل", "مستحقات وسندات الموردين", Icons.Default.LocalShipping, true))
            add(PrintCenterCategory("financial_summary", "الملخص المالي والتشغيلي", "الأرباح، المصروفات، النقدية", Icons.Default.Assessment))
            add(PrintCenterCategory("inventory_report", "تقرير المخزون والأصناف", "الكميات، الأسعار، التنبيهات", Icons.Default.Inventory2))
            add(PrintCenterCategory("journal_voucher", "سندات القيود المحاسبية", "القيود المزدوجة", Icons.Default.MenuBook))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "مركز الطباعة والتقارير الشامل",
            subtitle = "تصدير وطباعة 17 نوع مستند وتقارير رسمية (PDF, PNG, CSV, Thermal)",
            onBack = onBack
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(categories, key = { it.id }) { cat ->
                AlAzziCard(
                    onClick = {
                        if (cat.requiresManager && !isManager) return@AlAzziCard
                        activeCategory = cat
                    },
                    modifier = Modifier.fillMaxWidth().testTag("print_cat_${cat.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.title, fontWeight = FontWeight.Bold)
                            Text(cat.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Interactive picker dialogs when category requires selection
    activeCategory?.let { cat ->
        when (cat.id) {
            "customer_statement" -> {
                AlertDialog(
                    onDismissRequest = { activeCategory = null },
                    title = { Text("اختر العميل لكشف الحساب") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            customers.forEach { c ->
                                AlAzziCard(
                                    onClick = { selectedCustomerId = c.id; activeCategory = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(c.name, fontWeight = FontWeight.Bold)
                                        Text(Formatters.formatMoney(c.balance, currency), color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { activeCategory = null }) { Text("إغلاق") } }
                )
            }
            "supplier_statement" -> {
                AlertDialog(
                    onDismissRequest = { activeCategory = null },
                    title = { Text("اختر المورد لكشف الحساب") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            suppliers.forEach { s ->
                                AlAzziCard(
                                    onClick = { selectedSupplierId = s.id; activeCategory = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(s.name, fontWeight = FontWeight.Bold)
                                        Text(Formatters.formatMoney(s.balance, currency), color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { activeCategory = null }) { Text("إغلاق") } }
                )
            }
            else -> {
                // Renders document print dialog directly
                val defaultSettings = viewModel.currentPrintSettings(settingsMap)
                when (cat.id) {
                    "sales_history" -> {
                        val tableData = TablePrintData(
                            storeName = storeName,
                            title = "تقرير سجل مبيعات المتجر",
                            subtitle = Formatters.formatDateTime(System.currentTimeMillis()),
                            columns = listOf(
                                TableColumn("الإجمالي", 0.24f, true),
                                TableColumn("الحالة", 0.22f, true),
                                TableColumn("العميل", 0.32f, false),
                                TableColumn("الرقم", 0.22f, true)
                            ),
                            rows = salesInvoices.map {
                                listOf(
                                    Formatters.formatMoney(it.total, currency),
                                    if (it.remaining == 0L) "مدفوعة" else "متبقي",
                                    it.storeNameSnapshot ?: "عميل نقدي",
                                    "#${it.invoiceNumber}"
                                )
                            },
                            summaryLines = listOf(
                                ReportLine("إجمالي الفواتير", "${salesInvoices.size}", false),
                                ReportLine("مجموع الإيرادات", Formatters.formatMoney(salesInvoices.sumOf { it.total }, currency), true)
                            )
                        )
                        DocumentPrintDialog(
                            title = cat.title,
                            defaultFileName = "sales_report",
                            viewModel = viewModel,
                            initialSettings = defaultSettings,
                            savedPrinterAddress = savedPrinterAddress,
                            savedPrinterName = savedPrinterName,
                            currency = currency,
                            allowSortToggle = true,
                            csvRows = { ascending ->
                                val list = if (ascending) salesInvoices else salesInvoices.reversed()
                                listOf(listOf("رقم الفاتورة", "التاريخ", "الإجمالي", "المتبقي")) +
                                    list.map { listOf(it.invoiceNumber, Formatters.formatDateTime(it.createdAt), it.total.toString(), it.remaining.toString()) }
                            },
                            renderBitmap = { ps -> ReceiptRenderer.renderTableDocument(tableData, ps) },
                            onDismiss = { activeCategory = null }
                        )
                    }
                    "purchase_history" -> {
                        val tableData = TablePrintData(
                            storeName = storeName,
                            title = "تقرير فواتير المشتريات",
                            subtitle = Formatters.formatDateTime(System.currentTimeMillis()),
                            columns = listOf(
                                TableColumn("الإجمالي", 0.25f, true),
                                TableColumn("المورد", 0.35f, false),
                                TableColumn("الرقم والتاريخ", 0.40f, true)
                            ),
                            rows = purchaseInvoices.map {
                                listOf(
                                    Formatters.formatMoney(it.total, currency),
                                    it.storeNameSnapshot ?: "مورد عام",
                                    "#${it.invoiceNumber}"
                                )
                            },
                            summaryLines = listOf(
                                ReportLine("إجمالي المشتريات", Formatters.formatMoney(purchaseInvoices.sumOf { it.total }, currency), true)
                            )
                        )
                        DocumentPrintDialog(
                            title = cat.title,
                            defaultFileName = "purchase_report",
                            viewModel = viewModel,
                            initialSettings = defaultSettings,
                            savedPrinterAddress = savedPrinterAddress,
                            savedPrinterName = savedPrinterName,
                            currency = currency,
                            allowSortToggle = true,
                            renderBitmap = { ps -> ReceiptRenderer.renderTableDocument(tableData, ps) },
                            onDismiss = { activeCategory = null }
                        )
                    }
                    "financial_summary" -> {
                        val reportLines = listOf(
                            ReportLine("إجمالي المبيعات", Formatters.formatMoney(detailedSummary.sales, currency), true),
                            ReportLine("إجمالي المشتريات", Formatters.formatMoney(detailedSummary.purchases, currency), false),
                            ReportLine("تكلفة البضاعة المباعة (COGS)", Formatters.formatMoney(detailedSummary.cogs, currency), false),
                            ReportLine("إجمالي المصروفات التشغيلية", Formatters.formatMoney(detailedSummary.expenses, currency), false),
                            ReportLine("قبض العملاء النقدي", Formatters.formatMoney(detailedSummary.customerReceipts, currency), false),
                            ReportLine("سداد الموردين", Formatters.formatMoney(detailedSummary.supplierPayments, currency), false)
                        )
                        DocumentPrintDialog(
                            title = cat.title,
                            defaultFileName = "financial_summary",
                            viewModel = viewModel,
                            initialSettings = defaultSettings,
                            savedPrinterAddress = savedPrinterAddress,
                            savedPrinterName = savedPrinterName,
                            currency = currency,
                            renderBitmap = { ps ->
                                ReceiptRenderer.renderReport(
                                    storeName = storeName,
                                    title = "الملخص المالي والتشغيلي",
                                    subtitle = Formatters.formatDateTime(System.currentTimeMillis()),
                                    lines = reportLines,
                                    settings = ps
                                )
                            },
                            onDismiss = { activeCategory = null }
                        )
                    }
                    "inventory_report" -> {
                        val tableData = TablePrintData(
                            storeName = storeName,
                            title = "تقرير جرد المخزون",
                            subtitle = Formatters.formatDateTime(System.currentTimeMillis()),
                            columns = listOf(
                                TableColumn("سعر البيع", 0.22f, true),
                                TableColumn("الكمية", 0.22f, true),
                                TableColumn("اسم الصنف", 0.56f, false)
                            ),
                            rows = allItems.map {
                                listOf(
                                    Formatters.formatMoney(it.salePrice, ""),
                                    Formatters.formatQty(it.quantityMilli),
                                    it.name
                                )
                            },
                            summaryLines = listOf(
                                ReportLine("إجمالي عدد الأصناف", "${allItems.size}", true)
                            )
                        )
                        DocumentPrintDialog(
                            title = cat.title,
                            defaultFileName = "inventory_report",
                            viewModel = viewModel,
                            initialSettings = defaultSettings,
                            savedPrinterAddress = savedPrinterAddress,
                            savedPrinterName = savedPrinterName,
                            currency = currency,
                            renderBitmap = { ps -> ReceiptRenderer.renderTableDocument(tableData, ps) },
                            onDismiss = { activeCategory = null }
                        )
                    }
                    "journal_voucher" -> {
                        val tableData = TablePrintData(
                            storeName = storeName,
                            title = "سجل القيود المحاسبية",
                            subtitle = Formatters.formatDateTime(System.currentTimeMillis()),
                            columns = listOf(
                                TableColumn("المبلغ", 0.24f, true),
                                TableColumn("الحالة", 0.22f, true),
                                TableColumn("البيان", 0.32f, false),
                                TableColumn("الرقم", 0.22f, true)
                            ),
                            rows = journalEntries.map {
                                listOf(
                                    Formatters.formatMoney(it.amount, currency),
                                    if (it.status == "REVERSED") "معكوس" else "مرحل",
                                    it.description,
                                    "#${it.entryNumber}"
                                )
                            },
                            summaryLines = listOf(
                                ReportLine("إجمالي القيود", "${journalEntries.size}", true)
                            )
                        )
                        DocumentPrintDialog(
                            title = cat.title,
                            defaultFileName = "journal_report",
                            viewModel = viewModel,
                            initialSettings = defaultSettings,
                            savedPrinterAddress = savedPrinterAddress,
                            savedPrinterName = savedPrinterName,
                            currency = currency,
                            renderBitmap = { ps -> ReceiptRenderer.renderTableDocument(tableData, ps) },
                            onDismiss = { activeCategory = null }
                        )
                    }
                    else -> {
                        activeCategory = null
                    }
                }
            }
        }
    }

    selectedCustomerId?.let { custId ->
        val customer = customers.firstOrNull { it.id == custId }
        val defaultSettings = viewModel.currentPrintSettings(settingsMap)
        if (customer != null) {
            val statementData = StatementPrintData(
                storeName = storeName,
                title = "كشف حساب عميل معتمد",
                partyName = customer.name,
                partyPhone = customer.phone,
                periodLabel = "جميع الحركات",
                openingBalance = 0L,
                rows = emptyList(), // Can populate from ledger flow or summary
                closingBalance = customer.balance,
                currencyLabel = currency
            )
            DocumentPrintDialog(
                title = "كشف حساب: ${customer.name}",
                defaultFileName = "statement_${customer.name}",
                viewModel = viewModel,
                initialSettings = defaultSettings,
                savedPrinterAddress = savedPrinterAddress,
                savedPrinterName = savedPrinterName,
                currency = currency,
                allowSortToggle = true,
                whatsappCaption = "كشف حساب العميل ${customer.name} — الرصيد: ${Formatters.formatMoney(customer.balance, currency)}",
                renderBitmap = { ps -> ReceiptRenderer.renderStatement(statementData, ps) },
                onDismiss = { selectedCustomerId = null }
            )
        }
    }
}
