package com.saleh.alazzi.grocery.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.data.local.entity.ExpenseEntity
import com.saleh.alazzi.grocery.data.local.entity.NoteEntity
import com.saleh.alazzi.grocery.printing.BluetoothPrinterClient
import com.saleh.alazzi.grocery.printing.PairedPrinter
import com.saleh.alazzi.grocery.printing.ReceiptData
import com.saleh.alazzi.grocery.printing.ReceiptItem
import com.saleh.alazzi.grocery.printing.ReceiptRenderer
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.components.*
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ExpensesScreen(
    viewModel: GroceryViewModel,
    currency: String,
    onBack: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val colors = LocalAlAzziColors.current

    val presetCategories = listOf("الكل", "إيجار", "كهرباء ومياه", "رواتب", "نقل وتوصيل", "صيانة", "أخرى")
    var selectedCat by remember { mutableStateOf("الكل") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var opId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    val filtered = remember(expenses, selectedCat) {
        if (selectedCat == "الكل") expenses else expenses.filter { it.category == selectedCat }
    }
    val totalFiltered = remember(filtered) { filtered.sumOf { it.amount } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("expenses_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "المصروفات التشغيلية",
            subtitle = "الإجمالي المعروض: ${Formatters.formatMoney(totalFiltered, currency)}",
            actionLabel = "تسجيل مصروف",
            onAction = { opId = UUID.randomUUID().toString(); showAddDialog = true },
            onBack = onBack
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presetCategories.forEach { cat ->
                FilterChip(selected = selectedCat == cat, onClick = { selectedCat = cat }, label = { Text(cat) })
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateView("لا توجد مصروفات", "اضغط تسجيل مصروف لإضافة أول مصروف")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { exp ->
                    AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.category, fontWeight = FontWeight.Bold)
                                exp.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text(Formatters.formatDateTime(exp.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Formatters.formatMoney(exp.amount, currency), fontWeight = FontWeight.Bold, color = colors.debtRed)
                            IconButton(onClick = { editingExpense = exp }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل المصروف")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var category by remember { mutableStateOf("أخرى") }
        var amount by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        val parsed = Formatters.parseWholeAmount(amount)
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("تسجيل مصروف جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        presetCategories.drop(1).forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = Formatters.filterWholeNumberInput(it) },
                        label = { Text("المبلغ ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("الوصف / التفاصيل") })
                }
            },
            confirmButton = {
                Button(enabled = parsed != null && parsed > 0, onClick = {
                    viewModel.recordExpense(category, parsed!!, desc, opId)
                    showAddDialog = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
        )
    }

    editingExpense?.let { exp ->
        var category by remember { mutableStateOf(exp.category) }
        var amount by remember { mutableStateOf(exp.amount.toString()) }
        var desc by remember { mutableStateOf(exp.description.orEmpty()) }
        var reason by remember { mutableStateOf("") }
        val parsed = Formatters.parseWholeAmount(amount)
        AlertDialog(
            onDismissRequest = { editingExpense = null },
            title = { Text("تعديل المصروف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("التصنيف") }, singleLine = true)
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = Formatters.filterWholeNumberInput(it) },
                        label = { Text("المبلغ الصحيح ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("الوصف") })
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("سبب التعديل (مطلوب)") })
                }
            },
            confirmButton = {
                Button(enabled = parsed != null && parsed > 0 && reason.isNotBlank(), onClick = {
                    viewModel.updateExpense(exp.id, category, parsed!!, desc, reason)
                    editingExpense = null
                }) { Text("تحديث") }
            },
            dismissButton = { TextButton(onClick = { editingExpense = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun TransfersScreen(
    viewModel: GroceryViewModel,
    currency: String,
    onBack: () -> Unit
) {
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val colors = LocalAlAzziColors.current

    var showAddDialog by remember { mutableStateOf(false) }
    var opId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("transfers_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "الحوالات المالية",
            subtitle = "سجل الحوالات الواردة والصادرة",
            actionLabel = "حوالة جديدة",
            onAction = { opId = UUID.randomUUID().toString(); showAddDialog = true },
            onBack = onBack
        )

        if (transfers.isEmpty()) {
            EmptyStateView("لا توجد حوالات مسجلة", "أضف حوالة صادرة أو واردة لتوثيقها في الصندوق")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(transfers, key = { it.id }) { t ->
                    AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (if (t.direction == "IN") "واردة من: " else "صادرة إلى: ") + t.recipientName,
                                    fontWeight = FontWeight.Bold
                                )
                                StatusBadge(
                                    text = transferStatusLabel(t.status),
                                    textColor = if (t.status == "COMPLETED") colors.paidGreen else colors.warningAmber,
                                    backgroundColor = if (t.status == "COMPLETED") colors.paidGreenContainer else colors.warningAmberContainer
                                )
                            }
                            val meta = listOfNotNull(
                                t.senderName?.takeIf { it.isNotBlank() }?.let { "المرسل: $it" },
                                "هاتف: ${t.phone}",
                                "الرسوم: ${Formatters.formatMoney(t.fees, currency)}"
                            ).joinToString(" • ")
                            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${Formatters.formatMoney(t.amount, currency)} (الصافي: ${Formatters.formatMoney(t.netAmount, currency)})",
                                    fontWeight = FontWeight.Bold,
                                    color = if (t.direction == "IN") colors.paidGreen else colors.debtRed
                                )
                                if (t.status == "PENDING") {
                                    TextButton(onClick = { viewModel.updateTransferStatus(t.id, "COMPLETED", "تم الاستلام") }) {
                                        Text("تأكيد الاكتمال")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var direction by remember { mutableStateOf("OUT") }
        var sender by remember { mutableStateOf("") }
        var recipient by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var fees by remember { mutableStateOf("0") }
        var status by remember { mutableStateOf("COMPLETED") }
        var note by remember { mutableStateOf("") }
        val parsedAmount = Formatters.parseWholeAmount(amount)
        val parsedFees = Formatters.parseWholeAmount(fees) ?: 0L

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("تسجيل حوالة مالية") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = direction == "OUT", onClick = { direction = "OUT" }, label = { Text("حوالة صادرة") })
                            FilterChip(selected = direction == "IN", onClick = { direction = "IN" }, label = { Text("حوالة واردة") })
                        }
                    }
                    item { OutlinedTextField(value = sender, onValueChange = { sender = it }, label = { Text("اسم المرسل") }, singleLine = true) }
                    item { OutlinedTextField(value = recipient, onValueChange = { recipient = it }, label = { Text("اسم المستلم") }, singleLine = true) }
                    item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = Formatters.filterWholeNumberInput(it) },
                                label = { Text("المبلغ ($currency)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = fees,
                                onValueChange = { fees = Formatters.filterWholeNumberInput(it) },
                                label = { Text("العمولة") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = status == "COMPLETED", onClick = { status = "COMPLETED" }, label = { Text("مكتملة") })
                            FilterChip(selected = status == "PENDING", onClick = { status = "PENDING" }, label = { Text("قيد الانتظار") })
                        }
                    }
                    item { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظات") }) }
                }
            },
            confirmButton = {
                Button(
                    enabled = recipient.isNotBlank() && phone.isNotBlank() && parsedAmount != null && parsedAmount > 0,
                    onClick = {
                        viewModel.recordTransfer(recipient, phone, parsedAmount!!, parsedFees, direction, note, opId, sender, status)
                        showAddDialog = false
                    }
                ) { Text("حفظ الحوالة") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun NotesScreen(
    viewModel: GroceryViewModel,
    onBack: () -> Unit
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val colors = LocalAlAzziColors.current

    val categories = listOf("الكل", "عام", "عملاء", "موردين", "مهام", "مؤرشف")
    var selectedCat by remember { mutableStateOf("الكل") }
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }

    val filtered = remember(notes, selectedCat, search) {
        notes.filter { n ->
            val qOk = search.isBlank() || n.title.contains(search, true) || n.body.contains(search, true)
            val cOk = when (selectedCat) {
                "الكل" -> !n.isArchived
                "مؤرشف" -> n.isArchived
                else -> !n.isArchived && n.category == selectedCat
            }
            qOk && cOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("notes_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "الملاحظات والتنبيهات",
            subtitle = "تثبيت وبحث وتصنيف الملاحظات",
            actionLabel = "ملاحظة جديدة",
            onAction = { showAdd = true },
            onBack = onBack
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("بحث في الملاحظات") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { cat ->
                FilterChip(selected = selectedCat == cat, onClick = { selectedCat = cat }, label = { Text(cat) })
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateView("لا توجد ملاحظات", "أضف ملاحظة لمتابعة المهام والديون")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { n ->
                    AlAzziCard(
                        containerColor = if (n.isPinned) colors.warningAmberContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(n.title.ifBlank { "بدون عنوان" }, fontWeight = FontWeight.Bold)
                                    Text("${n.category} • ${Formatters.formatDateTime(n.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.toggleNotePin(n) }) {
                                    Icon(Icons.Default.PushPin, contentDescription = "تثبيت", tint = if (n.isPinned) colors.warningAmber else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { editingNote = n }) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل")
                                }
                            }
                            if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { viewModel.toggleNoteArchive(n) }) {
                                    Text(if (n.isArchived) "استعادة" else "أرشفة")
                                }
                                TextButton(onClick = { viewModel.deleteNote(n.id) }) {
                                    Text("حذف", color = colors.debtRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NoteDialog(
            title = "إضافة ملاحظة",
            onDismiss = { showAdd = false },
            onSave = { t, b, c -> viewModel.addNote(t, b, c); showAdd = false }
        )
    }

    editingNote?.let { note ->
        NoteDialog(
            title = "تعديل الملاحظة",
            initialTitle = note.title,
            initialBody = note.body,
            initialCategory = note.category,
            onDismiss = { editingNote = null },
            onSave = { t, b, c -> viewModel.updateNote(note, t, b, c); editingNote = null }
        )
    }
}

@Composable
private fun NoteDialog(
    title: String,
    initialTitle: String = "",
    initialBody: String = "",
    initialCategory: String = "عام",
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var t by remember { mutableStateOf(initialTitle) }
    var b by remember { mutableStateOf(initialBody) }
    var c by remember { mutableStateOf(initialCategory) }
    val cats = listOf("عام", "عملاء", "موردين", "مهام")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    cats.forEach { cat -> FilterChip(selected = c == cat, onClick = { c = cat }, label = { Text(cat) }) }
                }
                OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("العنوان") }, singleLine = true)
                OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("المحتوى") })
            }
        },
        confirmButton = {
            Button(enabled = t.isNotBlank() || b.isNotBlank(), onClick = { onSave(t, b, c) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun PrinterScreen(
    viewModel: GroceryViewModel,
    storeName: String,
    savedPrinterAddress: String?,
    savedPrinterName: String?,
    savedPaperWidth: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val client = remember(context) { BluetoothPrinterClient(context) }
    val scope = rememberCoroutineScope()

    var printers by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }
    var selectedAddress by remember(savedPrinterAddress) { mutableStateOf(savedPrinterAddress.orEmpty()) }
    var selectedName by remember(savedPrinterName) { mutableStateOf(savedPrinterName.orEmpty()) }
    var paperWidth by remember(savedPaperWidth) { mutableIntStateOf(savedPaperWidth) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun loadPaired() {
        scope.launch {
            runCatching { client.pairedPrinters() }
                .onSuccess {
                    printers = it
                    status = if (it.isEmpty()) "لا توجد طابعات مقترنة في إعدادات بلوتوث الهاتف" else "تم العثور على ${it.size} أجهزة مقترنة"
                }
                .onFailure { status = it.message ?: "تعذر قراءة أجهزة Bluetooth" }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadPaired() else status = "تم رفض إذن Bluetooth القريب"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("printer_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader("الطابعة الحرارية Bluetooth", "حفظ الطابعة الافتراضية ومقاس الورق", onBack = onBack)

        AlAzziCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الطابعة المحفوظة: ${selectedName.ifBlank { selectedAddress.ifBlank { "لم يتم اختيار طابعة" } }}", fontWeight = FontWeight.Bold)
                Text("مقاس الورق:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = paperWidth == 384, onClick = { paperWidth = 384 }, label = { Text("58mm (384 dots)") })
                    FilterChip(selected = paperWidth == 576, onClick = { paperWidth = 576 }, label = { Text("80mm (576 dots)") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        } else {
                            loadPaired()
                        }
                    }, modifier = Modifier.weight(1f)) { Text("بحث الطابعات") }

                    FilledTonalButton(
                        enabled = selectedAddress.isNotBlank(),
                        onClick = { viewModel.savePrinterSettings(selectedAddress, selectedName, paperWidth) },
                        modifier = Modifier.weight(1f)
                    ) { Text("حفظ كافتراضية") }
                }

                if (selectedAddress.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    status = "جارٍ اختبار الاتصال..."
                                    runCatching { client.testConnection(selectedAddress) }
                                        .onSuccess { status = it }
                                        .onFailure { status = it.message ?: "فشل اختبار الاتصال" }
                                    busy = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("اختبار الاتصال") }

                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    status = "جارٍ طباعة فاتورة تجريبية..."
                                    val bmp = ReceiptRenderer.render(
                                        ReceiptData(
                                            storeName = storeName,
                                            invoiceType = "اختبار طباعة",
                                            invoiceNumber = "TEST-001",
                                            dateTime = Formatters.formatDateTime(System.currentTimeMillis()),
                                            partyName = "اختبار عربي RTL",
                                            items = listOf(ReceiptItem("سكر أبيض 10 كجم", "2", 4500, 9000)),
                                            subtotal = 9000,
                                            discount = 0,
                                            total = 9000,
                                            paid = 9000,
                                            remaining = 0
                                        ),
                                        paperWidth
                                    )
                                    try {
                                        client.print(bmp, selectedAddress, paperWidth)
                                        status = "تم إرسال طباعة الاختبار بنجاح"
                                    } catch (e: Throwable) {
                                        status = e.message ?: "تعذر إرسال طباعة الاختبار"
                                    } finally {
                                        bmp.recycle()
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("طباعة تجريبية") }
                    }
                }
                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (printers.isNotEmpty()) {
            Text("الأجهزة المقترنة (اضغط للاختيار):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(printers, key = { it.address }) { p ->
                    AlAzziCard(
                        containerColor = if (selectedAddress == p.address) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        onClick = {
                            selectedAddress = p.address
                            selectedName = p.name
                            viewModel.savePrinterSettings(p.address, p.name, paperWidth)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(p.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selectedAddress == p.address) Icon(Icons.Default.CheckCircle, contentDescription = "محددة", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun transferStatusLabel(status: String): String = when (status) {
    "COMPLETED" -> "مكتملة"
    "PENDING" -> "قيد الانتظار"
    "CANCELLED" -> "ملغاة"
    else -> status
}
