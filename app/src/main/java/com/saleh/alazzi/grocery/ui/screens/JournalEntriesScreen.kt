package com.saleh.alazzi.grocery.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.data.local.entity.AccountingPeriodEntity
import com.saleh.alazzi.grocery.data.local.entity.JournalEntryEntity
import com.saleh.alazzi.grocery.data.local.entity.JournalEntryLineEntity
import com.saleh.alazzi.grocery.data.repository.ChartAccounts
import com.saleh.alazzi.grocery.data.repository.JournalLineInput
import com.saleh.alazzi.grocery.printing.JournalVoucherData
import com.saleh.alazzi.grocery.printing.JournalVoucherLine
import com.saleh.alazzi.grocery.printing.ReceiptRenderer
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.components.*
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import java.util.Calendar
import java.util.UUID

private data class DraftJournalLine(
    val id: String = UUID.randomUUID().toString(),
    val accountCode: String = ChartAccounts.CODE_CASH,
    val accountName: String = ChartAccounts.NAME_CASH,
    val debitText: String = "",
    val creditText: String = "",
    val memo: String = ""
)

@Composable
fun JournalEntriesScreen(
    viewModel: GroceryViewModel,
    storeName: String,
    currency: String,
    savedPrinterAddress: String?,
    savedPrinterName: String?,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val entries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val allLines by viewModel.journalEntryLines.collectAsStateWithLifecycle()
    val periods by viewModel.accountingPeriods.collectAsStateWithLifecycle()
    val settings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val settingsMap = remember(settings) { settings.associate { it.key to it.value } }
    val colors = LocalAlAzziColors.current
    val isManager = session?.role == UserRoles.MANAGER

    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPeriodsDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var editingEntry by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var entryToReverse by remember { mutableStateOf<JournalEntryEntity?>(null) }
    var entryToPrint by remember { mutableStateOf<JournalEntryEntity?>(null) }

    val linesByEntry = remember(allLines) { allLines.groupBy { it.entryId } }

    val filtered = remember(entries, query, typeFilter, statusFilter) {
        entries.filter { entry ->
            val qOk = query.isBlank() ||
                entry.entryNumber.contains(query, true) ||
                entry.description.contains(query, true) ||
                entry.debitAccount.contains(query, true) ||
                entry.creditAccount.contains(query, true) ||
                (entry.createdByUserName?.contains(query, true) == true)
            val tOk = typeFilter == "ALL" || entry.entryType == typeFilter
            val sOk = statusFilter == "ALL" || entry.status == statusFilter
            qOk && tOk && sOk
        }
    }

    val postedTotal = remember(entries) { entries.filter { it.status == "POSTED" }.sumOf { it.amount } }
    val reversedCount = remember(entries) { entries.count { it.status == "REVERSED" } }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "القيود المحاسبية والفترات",
            subtitle = "${entries.size} قيد • مرحل: ${Formatters.formatMoney(postedTotal, currency)}",
            actionLabel = if (isManager) "قيد جديد" else null,
            onAction = { showCreateDialog = true },
            onBack = onBack
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("بحث بالرقم، البيان، الحساب أو المستخدم...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("journal_search")
            )
            if (isManager) {
                FilledTonalButton(
                    onClick = { showPeriodsDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("manage_periods_btn")
                ) {
                    Icon(Icons.Default.LockClock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("الفترات (${periods.count { it.isClosed }})")
                }
            }
        }

        val typeOptions = listOf(
            "ALL" to "الكل",
            "SALE" to "مبيعات",
            "PURCHASE" to "مشتريات",
            "RECEIPT" to "قبض",
            "PAYMENT" to "صرف",
            "EXPENSE" to "مصروف",
            "TRANSFER" to "حوالة",
            "MANUAL" to "يدوي",
            "REVERSAL" to "عكسي"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(typeOptions) { (key, label) ->
                FilterChip(
                    selected = typeFilter == key,
                    onClick = { typeFilter = key },
                    label = { Text(label) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = statusFilter == "ALL", onClick = { statusFilter = "ALL" }, label = { Text("جميع الحالات (${entries.size})") })
            FilterChip(selected = statusFilter == "POSTED", onClick = { statusFilter = "POSTED" }, label = { Text("مرحل") })
            FilterChip(selected = statusFilter == "REVERSED", onClick = { statusFilter = "REVERSED" }, label = { Text("معكوس ($reversedCount)") })
        }

        if (filtered.isEmpty()) {
            EmptyStateView("لا توجد قيود مطابقة", "ستظهر القيود المحاسبية التلقائية واليدوية هنا")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { entry ->
                    val entryLines = linesByEntry[entry.id].orEmpty()
                    AlAzziCard(
                        onClick = { selectedEntry = entry },
                        modifier = Modifier.fillMaxWidth().testTag("journal_card_${entry.entryNumber}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("قيد #${entry.entryNumber}  •  ${journalTypeArabic(entry.entryType)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                StatusBadge(
                                    text = if (entry.status == "REVERSED") "معكوس" else "مرحل",
                                    textColor = if (entry.status == "REVERSED") colors.debtRed else colors.paidGreen,
                                    backgroundColor = if (entry.status == "REVERSED") colors.debtRedContainer else colors.paidGreenContainer
                                )
                            }
                            Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                            Text("من حـ/ ${entry.debitAccount}   ←   إلى حـ/ ${entry.creditAccount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Formatters.formatMoney(entry.amount, currency), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                Text("${Formatters.formatDateTime(entry.createdAt)} • ${entryLines.size} أسطر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        val entryLines = linesByEntry[entry.id].orEmpty()
        val auditLogs by remember(entry.id) { viewModel.entityAuditLogs(entry.id) }
            .collectAsStateWithLifecycle(emptyList())
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text("تفاصيل القيد #${entry.entryNumber}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("النوع: ${journalTypeArabic(entry.entryType)}  •  الحالة: ${if (entry.status == "REVERSED") "معكوس" else "مرحل"}")
                    Text("البيان: ${entry.description}", fontWeight = FontWeight.Bold)
                    Text("الإجمالي: ${Formatters.formatMoney(entry.amount, currency)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("التاريخ: ${Formatters.formatDateTime(entry.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    entry.createdByUserName?.let { Text("أنشأه: $it", style = MaterialTheme.typography.bodySmall) }
                    entry.updatedByUserName?.let { Text("عدّله: $it (${entry.updatedAt?.let(Formatters::formatDateTime) ?: ""})", style = MaterialTheme.typography.bodySmall) }
                    entry.notes?.takeIf { it.isNotBlank() }?.let { Text("ملاحظات: $it", style = MaterialTheme.typography.bodySmall) }

                    HorizontalDivider()
                    Text("تفصيل القيد المزدوج:", fontWeight = FontWeight.Bold)
                    entryLines.forEach { line ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(line.accountName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (line.debit > 0) "مدين ${line.debit}" else "دائن ${line.credit}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (line.debit > 0) colors.paidGreen else colors.debtRed
                            )
                        }
                    }
                    if (auditLogs.isNotEmpty()) {
                        HorizontalDivider()
                        Text("سجل التعديلات:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        auditLogs.take(5).forEach {
                            Text("• ${Formatters.formatDateTime(it.createdAt)}: ${it.details}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { entryToPrint = entry; selectedEntry = null }) { Text("طباعة/مشاركة") }
                    if (isManager && entry.status == "POSTED") {
                        TextButton(onClick = { editingEntry = entry; selectedEntry = null }) { Text("تعديل") }
                        TextButton(onClick = { entryToReverse = entry; selectedEntry = null }) {
                            Text("عكس", color = colors.debtRed)
                        }
                    }
                    TextButton(onClick = { selectedEntry = null }) { Text("إغلاق") }
                }
            }
        )
    }

    if (showCreateDialog) {
        JournalEntryEditorDialog(
            title = "إنشاء قيد محاسبي مزدوج",
            initialEntry = null,
            initialLines = emptyList(),
            requireReason = false,
            onDismiss = { showCreateDialog = false },
            onSave = { type, desc, lines, notes, _ ->
                viewModel.createManualJournalEntry(type, desc, lines, notes, UUID.randomUUID().toString())
                showCreateDialog = false
            }
        )
    }

    editingEntry?.let { entry ->
        JournalEntryEditorDialog(
            title = "تعديل القيد #${entry.entryNumber}",
            initialEntry = entry,
            initialLines = linesByEntry[entry.id].orEmpty(),
            requireReason = true,
            onDismiss = { editingEntry = null },
            onSave = { type, desc, lines, notes, reason ->
                viewModel.updateJournalEntry(entry.id, type, desc, lines, notes, reason)
                editingEntry = null
            }
        )
    }

    entryToReverse?.let { entry ->
        PinConfirmDialog(
            title = "عكس القيد المحاسبي #${entry.entryNumber}",
            description = "سيتم إنشاء قيد عكسي متوازن بقيمة ${Formatters.formatMoney(entry.amount, currency)} وتعليم هذا القيد كمعكوس.",
            confirmLabel = "إنشاء القيد العكسي",
            requireReason = true,
            viewModel = viewModel,
            onDismiss = { entryToReverse = null },
            onConfirm = { reason ->
                viewModel.reverseJournalEntry(entry.id, reason, UUID.randomUUID().toString())
                entryToReverse = null
            }
        )
    }

    entryToPrint?.let { entry ->
        val lines = linesByEntry[entry.id].orEmpty()
        val voucher = JournalVoucherData(
            storeName = storeName,
            entryNumber = entry.entryNumber,
            dateTime = Formatters.formatDateTime(entry.createdAt),
            entryType = journalTypeArabic(entry.entryType),
            status = if (entry.status == "REVERSED") "معكوس" else "مرحل",
            description = entry.description,
            amount = entry.amount,
            createdBy = entry.createdByUserName,
            notes = entry.notes,
            lines = lines.map { JournalVoucherLine(it.accountName, it.debit, it.credit, it.memo) },
            currencyLabel = currency
        )
        DocumentPrintDialog(
            title = "سند قيد #${entry.entryNumber}",
            defaultFileName = "journal_${entry.entryNumber}",
            viewModel = viewModel,
            initialSettings = viewModel.currentPrintSettings(settingsMap),
            savedPrinterAddress = savedPrinterAddress,
            savedPrinterName = savedPrinterName,
            currency = currency,
            whatsappCaption = "سند قيد محاسبي #${entry.entryNumber} — ${entry.description}",
            renderBitmap = { ps -> ReceiptRenderer.renderJournalVoucher(voucher, ps) },
            onDismiss = { entryToPrint = null }
        )
    }

    if (showPeriodsDialog) {
        AccountingPeriodsDialog(
            periods = periods,
            viewModel = viewModel,
            onDismiss = { showPeriodsDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalEntryEditorDialog(
    title: String,
    initialEntry: JournalEntryEntity?,
    initialLines: List<JournalEntryLineEntity>,
    requireReason: Boolean,
    onDismiss: () -> Unit,
    onSave: (type: String, description: String, lines: List<JournalLineInput>, notes: String?, reason: String) -> Unit
) {
    var entryType by remember { mutableStateOf(initialEntry?.entryType ?: "MANUAL") }
    var description by remember { mutableStateOf(initialEntry?.description.orEmpty()) }
    var notes by remember { mutableStateOf(initialEntry?.notes.orEmpty()) }
    var reason by remember { mutableStateOf("") }
    var draftLines by remember {
        mutableStateOf(
            if (initialLines.isNotEmpty()) {
                initialLines.map {
                    DraftJournalLine(
                        accountCode = it.accountCode,
                        accountName = it.accountName,
                        debitText = if (it.debit > 0) it.debit.toString() else "",
                        creditText = if (it.credit > 0) it.credit.toString() else "",
                        memo = it.memo.orEmpty()
                    )
                }
            } else {
                listOf(
                    DraftJournalLine(accountCode = ChartAccounts.CODE_CASH, accountName = ChartAccounts.NAME_CASH),
                    DraftJournalLine(accountCode = ChartAccounts.CODE_EXPENSES, accountName = ChartAccounts.NAME_EXPENSES)
                )
            }
        )
    }

    val totalDebit = draftLines.sumOf { Formatters.parseWholeAmount(it.debitText) ?: 0L }
    val totalCredit = draftLines.sumOf { Formatters.parseWholeAmount(it.creditText) ?: 0L }
    val isBalanced = totalDebit > 0L && totalDebit == totalCredit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("بيان / وصف القيد") },
                    modifier = Modifier.fillMaxWidth().testTag("journal_desc_input")
                )
                draftLines.forEachIndexed { idx, line ->
                    var menuOpen by remember(line.id) { mutableStateOf(false) }
                    AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("السطر ${idx + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (draftLines.size > 2) {
                                    IconButton(onClick = { draftLines = draftLines.filterNot { it.id == line.id } }) {
                                        Icon(Icons.Default.Delete, "حذف سطر")
                                    }
                                }
                            }
                            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = !menuOpen }) {
                                OutlinedTextField(
                                    value = "${line.accountCode} - ${line.accountName}",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("الحساب") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    ChartAccounts.selectableAccounts.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text("$code - $name") },
                                            onClick = {
                                                draftLines = draftLines.map {
                                                    if (it.id == line.id) it.copy(accountCode = code, accountName = name) else it
                                                }
                                                menuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = line.debitText,
                                    onValueChange = { raw ->
                                        val clean = Formatters.filterWholeNumberInput(raw)
                                        draftLines = draftLines.map {
                                            if (it.id == line.id) it.copy(debitText = clean, creditText = if (clean.isNotBlank()) "" else it.creditText) else it
                                        }
                                    },
                                    label = { Text("مدين") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("journal_debit_$idx")
                                )
                                OutlinedTextField(
                                    value = line.creditText,
                                    onValueChange = { raw ->
                                        val clean = Formatters.filterWholeNumberInput(raw)
                                        draftLines = draftLines.map {
                                            if (it.id == line.id) it.copy(creditText = clean, debitText = if (clean.isNotBlank()) "" else it.debitText) else it
                                        }
                                    },
                                    label = { Text("دائن") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("journal_credit_$idx")
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { draftLines = draftLines + DraftJournalLine() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة سطر حساب")
                }
                Text(
                    text = "المدين: $totalDebit  •  الدائن: $totalCredit  •  ${if (isBalanced) "متوازن ✓" else "غير متوازن"}",
                    color = if (isBalanced) LocalAlAzziColors.current.paidGreen else LocalAlAzziColors.current.debtRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireReason) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("سبب التعديل (إلزامي)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isBalanced && description.isNotBlank() && (!requireReason || reason.isNotBlank()),
                onClick = {
                    val mapped = draftLines.map {
                        JournalLineInput(
                            accountCode = it.accountCode,
                            accountName = it.accountName,
                            debit = Formatters.parseWholeAmount(it.debitText) ?: 0L,
                            credit = Formatters.parseWholeAmount(it.creditText) ?: 0L,
                            memo = it.memo.ifBlank { null }
                        )
                    }.filter { it.debit > 0 || it.credit > 0 }
                    onSave(entryType, description.trim(), mapped, notes.trim().ifBlank { null }, reason.trim())
                },
                modifier = Modifier.testTag("journal_save_btn")
            ) { Text("حفظ وترحيل القيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AccountingPeriodsDialog(
    periods: List<AccountingPeriodEntity>,
    viewModel: GroceryViewModel,
    onDismiss: () -> Unit
) {
    var periodName by remember { mutableStateOf("إقفال حتى اليوم (${Formatters.formatDate(System.currentTimeMillis())})") }
    var daysBackText by remember { mutableStateOf("30") }
    var pendingToggle by remember { mutableStateOf<AccountingPeriodEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الفترات المحاسبية المغلقة") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("يمنع الإغلاق إضافة أو تعديل أو عكس العمليات الواقعة داخل الفترة المغلقة.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = periodName,
                    onValueChange = { periodName = it },
                    label = { Text("اسم الفترة") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = daysBackText,
                    onValueChange = { daysBackText = Formatters.filterWholeNumberInput(it) },
                    label = { Text("تشمل آخر (يوم)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val days = (daysBackText.toIntOrNull() ?: 30).coerceIn(1, 3650)
                        val end = System.currentTimeMillis()
                        val start = Calendar.getInstance().apply {
                            timeInMillis = end
                            add(Calendar.DAY_OF_YEAR, -days)
                        }.timeInMillis
                        viewModel.closeAccountingPeriod(periodName, start, end)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("close_period_btn")
                ) { Text("إغلاق الفترة المحددة") }

                HorizontalDivider()
                if (periods.isEmpty()) {
                    Text("لا توجد فترات مغلقة حالياً", style = MaterialTheme.typography.bodySmall)
                } else {
                    periods.forEach { p ->
                        AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold)
                                    Text("${Formatters.formatDate(p.startAt)} ← ${Formatters.formatDate(p.endAt)}", style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { pendingToggle = p }) {
                                    Text(if (p.isClosed) "إعادة فتح" else "إغلاق")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )

    pendingToggle?.let { p ->
        PinConfirmDialog(
            title = if (p.isClosed) "إعادة فتح الفترة" else "إغلاق الفترة",
            description = "الفترة: ${p.name}",
            confirmLabel = "تأكيد",
            requireReason = true,
            viewModel = viewModel,
            onDismiss = { pendingToggle = null },
            onConfirm = { reason ->
                viewModel.toggleAccountingPeriodClosed(p, !p.isClosed, reason)
                pendingToggle = null
            }
        )
    }
}

private fun journalTypeArabic(type: String): String = when (type) {
    "SALE" -> "قيد مبيعات"
    "PURCHASE" -> "قيد مشتريات"
    "RECEIPT" -> "سند قبض"
    "PAYMENT" -> "سند صرف"
    "EXPENSE" -> "مصروف"
    "TRANSFER" -> "حوالة"
    "RETURN" -> "مرتجع"
    "ADJUSTMENT" -> "تسوية"
    "MANUAL" -> "قيد يدوي"
    "REVERSAL" -> "قيد عكسي"
    else -> type
}
