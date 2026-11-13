package com.saleh.alazzi.grocery.ui.screens

import android.app.DatePickerDialog
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
import com.saleh.alazzi.grocery.printing.InvoiceExporter
import com.saleh.alazzi.grocery.printing.ReceiptRenderer
import com.saleh.alazzi.grocery.printing.ReportLine
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.ReportPeriod
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.components.*
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import java.math.BigInteger
import java.util.Calendar
import java.util.UUID

@Composable
fun CashDrawerScreen(
    viewModel: GroceryViewModel,
    currency: String,
    onBack: () -> Unit
) {
    val balance by viewModel.cashBalance.collectAsStateWithLifecycle()
    val transactions by viewModel.cashTransactions.collectAsStateWithLifecycle()
    val reconciliations by viewModel.cashReconciliations.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isManager = session?.role == UserRoles.MANAGER
    val colors = LocalAlAzziColors.current

    var filterDir by remember { mutableStateOf("ALL") }
    var showReconcileDialog by remember { mutableStateOf(false) }
    var opId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    val totalIn = remember(transactions) { transactions.filter { it.direction == "IN" }.sumOf { it.amount } }
    val totalOut = remember(transactions) { transactions.filter { it.direction == "OUT" }.sumOf { it.amount } }
    val filtered = remember(transactions, filterDir) {
        when (filterDir) {
            "IN" -> transactions.filter { it.direction == "IN" }
            "OUT" -> transactions.filter { it.direction == "OUT" }
            else -> transactions
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("cash_drawer_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "الصندوق النقدي",
                subtitle = "حركة الدرج والمطابقة الفعلية",
                actionLabel = if (isManager) "جرد الدرج" else null,
                onAction = if (isManager) ({ opId = UUID.randomUUID().toString(); showReconcileDialog = true }) else null,
                onBack = onBack
            )
        }

        item {
            AlAzziCard(containerColor = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الرصيد الدفتري الحالي للصندوق", style = MaterialTheme.typography.bodySmall)
                    Text(
                        Formatters.formatMoney(balance, currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (balance >= 0) colors.paidGreen else colors.debtRed
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("إجمالي الوارد: ${Formatters.formatMoney(totalIn, currency)}", style = MaterialTheme.typography.bodySmall, color = colors.paidGreen)
                        Text("إجمالي الصادر: ${Formatters.formatMoney(totalOut, currency)}", style = MaterialTheme.typography.bodySmall, color = colors.debtRed)
                    }
                }
            }
        }

        if (reconciliations.isNotEmpty()) {
            item { Text("آخر عمليات جرد الصندوق (${reconciliations.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(reconciliations.take(3), key = { it.id }) { rec ->
                AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("فعلي: ${Formatters.formatMoney(rec.actualCount, currency)}  •  دفتري: ${Formatters.formatMoney(rec.bookBalance, currency)}", fontWeight = FontWeight.SemiBold)
                            Text("${rec.note} • ${Formatters.formatDateTime(rec.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusBadge(
                            text = if (rec.difference == 0L) "مطابق" else "فرق ${rec.difference}",
                            textColor = if (rec.difference == 0L) colors.paidGreen else colors.warningAmber,
                            backgroundColor = if (rec.difference == 0L) colors.paidGreenContainer else colors.warningAmberContainer
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filterDir == "ALL", onClick = { filterDir = "ALL" }, label = { Text("الكل (${transactions.size})") })
                FilterChip(selected = filterDir == "IN", onClick = { filterDir = "IN" }, label = { Text("وارد فقط") })
                FilterChip(selected = filterDir == "OUT", onClick = { filterDir = "OUT" }, label = { Text("صادر فقط") })
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyStateView("لا توجد حركات صندوق", "تظهر هنا كل المقبوضات والمدفوعات النقدية") }
        } else {
            items(filtered, key = { it.id }) { tx ->
                AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.note ?: cashSourceLabel(tx.sourceType), fontWeight = FontWeight.SemiBold)
                            Text("${cashSourceLabel(tx.sourceType)} • ${Formatters.formatDateTime(tx.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            (if (tx.direction == "IN") "+ " else "- ") + Formatters.formatMoney(tx.amount, currency),
                            fontWeight = FontWeight.Bold,
                            color = if (tx.direction == "IN") colors.paidGreen else colors.debtRed
                        )
                    }
                }
            }
        }
    }

    if (showReconcileDialog) {
        var actualText by remember { mutableStateOf(balance.toString()) }
        var note by remember { mutableStateOf("") }
        val actual = Formatters.parseWholeAmount(actualText)
        val diff = actual?.minus(balance)
        AlertDialog(
            onDismissRequest = { showReconcileDialog = false },
            title = { Text("جرد ومطابقة الصندوق") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الرصيد الدفتري المسجل: ${Formatters.formatMoney(balance, currency)}")
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = { actualText = Formatters.filterWholeNumberInput(it) },
                        label = { Text("المبلغ الفعلي المعدود في الدرج ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    if (diff != null) {
                        Text(
                            text = when {
                                diff == 0L -> "مطابق تماماً (لا يوجد فرق)"
                                diff > 0 -> "زيادة فعلية في الدرج: ${Formatters.formatMoney(diff, currency)}"
                                else -> "عجز في الدرج: ${Formatters.formatMoney(-diff, currency)}"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (diff == 0L) colors.paidGreen else colors.warningAmber
                        )
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة الجرد (مطلوبة)") })
                }
            },
            confirmButton = {
                Button(
                    enabled = actual != null && note.isNotBlank(),
                    onClick = {
                        viewModel.reconcileCashDrawer(actual!!, note, opId)
                        showReconcileDialog = false
                    }
                ) { Text("اعتماد الجرد") }
            },
            dismissButton = { TextButton(onClick = { showReconcileDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun ReportsScreen(
    viewModel: GroceryViewModel,
    storeName: String,
    currency: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val exporter = remember(context) { InvoiceExporter(context) }
    val period by viewModel.reportPeriod.collectAsStateWithLifecycle()
    val customRange by viewModel.customReportRange.collectAsStateWithLifecycle()
    val summary by viewModel.detailedSummary.collectAsStateWithLifecycle()
    val itemSales by viewModel.itemSalesSummary.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val items by viewModel.allItemsIncludingArchived.collectAsStateWithLifecycle()
    val cashBalance by viewModel.cashBalance.collectAsStateWithLifecycle()
    val auditReport by viewModel.integrityAudit.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isManager = session?.role == UserRoles.MANAGER
    val colors = LocalAlAzziColors.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var exportStatus by remember { mutableStateOf("") }

    val netSales = (summary.sales - summary.salesReturns).coerceAtLeast(0L)
    val netPurchases = (summary.purchases - summary.purchaseReturns).coerceAtLeast(0L)
    val netCogs = (summary.cogs - summary.returnedCogs).coerceAtLeast(0L)
    val grossTradingProfit = netSales - netCogs
    val netOperatingProfit = grossTradingProfit - summary.expenses
    val customerDebts = remember(customers) { customers.filter { !it.isArchived }.sumOf { it.balance } }
    val supplierDues = remember(suppliers) { suppliers.filter { !it.isArchived }.sumOf { it.balance } }
    val thousand = remember { BigInteger.valueOf(1_000L) }
    val halfThousand = remember { BigInteger.valueOf(500L) }
    val inventoryCostValue = remember(items) {
        items.filter { it.isActive && it.quantityMilli > 0 && it.purchasePrice >= 0 }
            .fold(BigInteger.ZERO) { acc, item ->
                acc.add(BigInteger.valueOf(item.quantityMilli).multiply(BigInteger.valueOf(item.purchasePrice)).add(halfThousand).divide(thousand))
            }
    }
    val inventoryRetailValue = remember(items) {
        items.filter { it.isActive && it.quantityMilli > 0 && it.salePrice >= 0 }
            .fold(BigInteger.ZERO) { acc, item ->
                acc.add(BigInteger.valueOf(item.quantityMilli).multiply(BigInteger.valueOf(item.salePrice)).add(halfThousand).divide(thousand))
            }
    }
    val lowStockCount = remember(items) { items.count { it.isActive && it.quantityMilli <= it.lowStockThresholdMilli } }
    val averageInvoice = if (summary.salesCount > 0) summary.sales / summary.salesCount else 0L

    val topSelling = remember(itemSales) { itemSales.filter { it.soldQuantityMilli > 0 } }
    val stagnantItems = remember(itemSales) { itemSales.filter { it.soldQuantityMilli == 0L && it.currentQuantityMilli > 0 } }

    fun pickDate(initialMillis: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val all20Metrics = remember(
        summary, netSales, netPurchases, netCogs, grossTradingProfit, netOperatingProfit,
        customerDebts, supplierDues, cashBalance, inventoryCostValue, inventoryRetailValue, lowStockCount, averageInvoice, currency
    ) {
        listOf(
            ReportLine("1. إجمالي المبيعات", Formatters.formatMoney(summary.sales, currency), true),
            ReportLine("2. مرتجع المبيعات", Formatters.formatMoney(summary.salesReturns, currency)),
            ReportLine("3. صافي المبيعات", Formatters.formatMoney(netSales, currency), true),
            ReportLine("4. عدد فواتير البيع", "${summary.salesCount} فاتورة"),
            ReportLine("5. متوسط قيمة الفاتورة", Formatters.formatMoney(averageInvoice, currency)),
            ReportLine("6. إجمالي المشتريات", Formatters.formatMoney(summary.purchases, currency)),
            ReportLine("7. مرتجع المشتريات", Formatters.formatMoney(summary.purchaseReturns, currency)),
            ReportLine("8. صافي المشتريات", Formatters.formatMoney(netPurchases, currency)),
            ReportLine("9. صافي تكلفة البضاعة المباعة (COGS)", Formatters.formatMoney(netCogs, currency)),
            ReportLine("10. مجمل الربح التجاري", Formatters.formatMoney(grossTradingProfit, currency), true),
            ReportLine("11. إجمالي المصروفات", Formatters.formatMoney(summary.expenses, currency)),
            ReportLine("12. صافي الربح التشغيلي", Formatters.formatMoney(netOperatingProfit, currency), true),
            ReportLine("13. الوارد النقدي للصندوق", Formatters.formatMoney(summary.cashIn, currency)),
            ReportLine("14. الصادر النقدي من الصندوق", Formatters.formatMoney(summary.cashOut, currency)),
            ReportLine("15. رصيد الصندوق الحالي", Formatters.formatMoney(cashBalance, currency), true),
            ReportLine("16. مقبوضات العملاء", Formatters.formatMoney(summary.customerReceipts, currency)),
            ReportLine("17. مدفوعات الموردين", Formatters.formatMoney(summary.supplierPayments, currency)),
            ReportLine("18. إجمالي ذمم العملاء", Formatters.formatMoney(customerDebts, currency)),
            ReportLine("19. إجمالي مستحقات الموردين", Formatters.formatMoney(supplierDues, currency)),
            ReportLine("20. قيمة المخزون (تكلفة / بيع)", "${Formatters.formatBigMoney(inventoryCostValue, currency)} / ${Formatters.formatBigMoney(inventoryRetailValue, currency)}")
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "التقارير المالية والمطابقة",
                subtitle = "الفترة: ${period.label}" + if (period == ReportPeriod.CUSTOM) " (${Formatters.formatDate(customRange.first)} - ${Formatters.formatDate(customRange.second)})" else "",
                onBack = onBack
            )
        }

        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { viewModel.setReportPeriod(p) },
                        label = { Text(p.label) }
                    )
                }
            }
            if (period == ReportPeriod.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    OutlinedButton(onClick = {
                        pickDate(customRange.first) { start ->
                            viewModel.setCustomReportRange(start, maxOf(start + 86_399_999L, customRange.second))
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Text("من: ${Formatters.formatDate(customRange.first)}")
                    }
                    OutlinedButton(onClick = {
                        pickDate(customRange.second) { endDay ->
                            viewModel.setCustomReportRange(customRange.first, endDay + 86_399_999L)
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Text("إلى: ${Formatters.formatDate(customRange.second)}")
                    }
                }
            }
        }

        if (summary.missingCostItemsCount > 0) {
            item {
                AlAzziCard(containerColor = colors.warningAmberContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = colors.warningAmber)
                        Text(
                            "تنبيه محاسبي: توجد ${summary.missingCostItemsCount} بنود مباعة بتكلفة شراء صفرية — يرجى مراجعة أسعار الشراء لدقة صافي الربح.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("20 مؤشر مالي") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("الأكثر مبيعاً والراكد") })
                if (isManager) {
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("المطابقة") })
                }
            }
        }

        when (selectedTab) {
            0 -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            runCatching {
                                val bmp = ReceiptRenderer.renderReport(storeName, "التقرير المالي الشامل", "الفترة: ${period.label}", all20Metrics)
                                val uri = try { exporter.savePdf(bmp, "AlAzzi_Report_${period.name}") } finally { bmp.recycle() }
                                exporter.share(uri, "application/pdf", "مشاركة التقرير PDF")
                            }.onSuccess { exportStatus = "تم تصدير التقرير PDF" }
                                .onFailure { exportStatus = it.message ?: "تعذر تصدير التقرير" }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF")
                        }
                        OutlinedButton(onClick = {
                            runCatching {
                                val rows = listOf(listOf("المؤشر", "القيمة")) + all20Metrics.map { listOf(it.label, it.value) }
                                val uri = exporter.saveCsv("AlAzzi_Report_${period.name}", rows)
                                exporter.share(uri, "text/csv", "مشاركة التقرير CSV")
                            }.onSuccess { exportStatus = "تم تصدير التقرير CSV" }
                                .onFailure { exportStatus = it.message ?: "تعذر تصدير CSV" }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CSV")
                        }
                        OutlinedButton(onClick = {
                            runCatching {
                                val bmp = ReceiptRenderer.renderReport(storeName, "التقرير المالي الشامل", "الفترة: ${period.label}", all20Metrics)
                                val uri = try { exporter.savePng(bmp, "AlAzzi_Report_${period.name}") } finally { bmp.recycle() }
                                exporter.share(uri, "image/png", "مشاركة صورة التقرير")
                            }.onSuccess { exportStatus = "تم تصدير صورة التقرير" }
                                .onFailure { exportStatus = it.message ?: "تعذر تصدير التقرير" }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("صورة")
                        }
                    }
                    if (exportStatus.isNotBlank()) {
                        Text(exportStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(all20Metrics) { metric ->
                    AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(metric.label, fontWeight = if (metric.highlight) FontWeight.Bold else FontWeight.Normal)
                            Text(
                                metric.value,
                                fontWeight = FontWeight.Bold,
                                color = if (metric.highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                item {
                    AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("مؤشرات إضافية:", fontWeight = FontWeight.Bold)
                            Text("حوالات واردة: ${Formatters.formatMoney(summary.transfersIn, currency)} • صادرة: ${Formatters.formatMoney(summary.transfersOut, currency)} • عمولات: ${Formatters.formatMoney(summary.transferFees, currency)}", style = MaterialTheme.typography.bodySmall)
                            Text("الأصناف تحت حد التنبيه: $lowStockCount صنف", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            1 -> {
                item { Text("الأصناف الأكثر مبيعاً (${topSelling.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (topSelling.isEmpty()) {
                    item { EmptyStateView("لا توجد مبيعات أصناف في هذه الفترة", "جرّب توسيع الفترة الزمنية") }
                } else {
                    items(topSelling, key = { "top_${it.itemId}" }) { row ->
                        AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.itemName, fontWeight = FontWeight.Bold)
                                    Text("المتبقي بالمخزون: ${Formatters.formatQty(row.currentQuantityMilli)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${Formatters.formatQty(row.soldQuantityMilli)} وحدة", fontWeight = FontWeight.Bold, color = colors.paidGreen)
                                    Text(Formatters.formatMoney(row.soldRevenue, currency), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item { Text("الأصناف الراكدة (لم تُبع خلال الفترة: ${stagnantItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(stagnantItems, key = { "stag_${it.itemId}" }) { row ->
                    AlAzziCard(containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.itemName, fontWeight = FontWeight.SemiBold)
                            Text("رصيد متوفر: ${Formatters.formatQty(row.currentQuantityMilli)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            2 -> {
                item {
                    Button(
                        onClick = { viewModel.runIntegrityAudit() },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("run_integrity_audit_button")
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("تشغيل فحص مطابقة الأرصدة والمخزون الآن")
                    }
                }
                auditReport?.let { rep ->
                    item {
                        AlAzziCard(
                            containerColor = if (rep.isHealthy) colors.paidGreenContainer else colors.debtRedContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (rep.isHealthy) "نتيجة المطابقة: سليمة 100% — جميع الأرصدة متطابقة"
                                    else "تم رصد ${rep.mismatches.size} فروقات تحتاج مراجعة",
                                    fontWeight = FontWeight.Bold,
                                    color = if (rep.isHealthy) colors.paidGreen else colors.debtRed
                                )
                                Text("العملاء المفحوصون: ${rep.checkedCustomers} • الموردون: ${rep.checkedSuppliers} • الأصناف: ${rep.checkedItems}", style = MaterialTheme.typography.bodySmall)
                                Text("رصيد الصندوق الدفتري: ${Formatters.formatMoney(rep.cashBookBalance, currency)} • ${Formatters.formatDateTime(rep.checkedAt)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(rep.mismatches) { mismatch ->
                        AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("${mismatch.category}: ${mismatch.label}", fontWeight = FontWeight.Bold, color = colors.debtRed)
                                Text("المتوقع: ${mismatch.expected} • الفعلي: ${mismatch.actual}", style = MaterialTheme.typography.bodySmall)
                                Text(mismatch.detail, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cashSourceLabel(sourceType: String): String = when (sourceType) {
    "SALES_INVOICE", "SALES_INVOICE_PAYMENT" -> "تحصيل فاتورة بيع"
    "PURCHASE_INVOICE", "PURCHASE_INVOICE_PAYMENT" -> "سداد فاتورة شراء"
    "CUSTOMER_PAYMENT" -> "قبض من عميل"
    "SUPPLIER_PAYMENT" -> "دفع لمورد"
    "EXPENSE" -> "مصروف تشغيلي"
    "TRANSFER" -> "حوالة مالية"
    "SALES_RETURN" -> "استرداد مرتجع بيع"
    "PURCHASE_RETURN" -> "تحصيل مرتجع شراء"
    "VOID_SALES_INVOICE" -> "عكس فاتورة ملغاة"
    "CASH_RECONCILIATION" -> "تسوية جرد الصندوق"
    else -> sourceType
}
