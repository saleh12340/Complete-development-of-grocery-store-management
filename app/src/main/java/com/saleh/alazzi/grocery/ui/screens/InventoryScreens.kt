package com.saleh.alazzi.grocery.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.data.local.entity.CategoryEntity
import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import com.saleh.alazzi.grocery.data.local.entity.UnitEntity
import com.saleh.alazzi.grocery.domain.money.MoneyMath
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.components.*
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import java.util.UUID

private enum class InventoryFilter(val label: String) {
    ACTIVE("النشطة"),
    LOW("منخفضة"),
    OUT("نافدة"),
    ARCHIVED("مؤرشفة")
}

@Composable
fun InventoryScreen(
    viewModel: GroceryViewModel,
    currency: String,
    onBack: () -> Unit
) {
    val allItems by viewModel.allItemsIncludingArchived.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isManager = session?.role == UserRoles.MANAGER
    val colors = LocalAlAzziColors.current

    val catNames = remember(categories) { categories.associate { it.id to it.name } }
    val unitNames = remember(units) { units.associate { it.id to it.name } }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(InventoryFilter.ACTIVE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ItemEntity?>(null) }
    var adjustItem by remember { mutableStateOf<ItemEntity?>(null) }
    var historyItem by remember { mutableStateOf<ItemEntity?>(null) }
    var showRefsDialog by remember { mutableStateOf(false) }

    val filtered = remember(allItems, query, filter) {
        allItems.filter { item ->
            val qMatch = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                (item.barcode ?: "").contains(query, ignoreCase = true)
            val fMatch = when (filter) {
                InventoryFilter.ACTIVE -> item.isActive
                InventoryFilter.LOW -> item.isActive && item.quantityMilli > 0 && item.quantityMilli <= item.lowStockThresholdMilli
                InventoryFilter.OUT -> item.isActive && item.quantityMilli == 0L
                InventoryFilter.ARCHIVED -> !item.isActive
            }
            qMatch && fMatch
        }
    }

    val totalCostValue = remember(allItems) {
        allItems.filter { it.isActive && it.quantityMilli > 0 }
            .sumOf { MoneyMath.lineTotal(it.quantityMilli, it.purchasePrice) }
    }
    val totalSaleValue = remember(allItems) {
        allItems.filter { it.isActive && it.quantityMilli > 0 }
            .sumOf { MoneyMath.lineTotal(it.quantityMilli, it.salePrice) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("inventory_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "المخزون والأصناف",
            subtitle = "الأصناف النشطة: ${allItems.count { it.isActive }}",
            actionLabel = if (isManager) "صنف جديد" else null,
            onAction = if (isManager) ({ showAddDialog = true }) else null,
            onBack = onBack
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("قيمة المخزون بالتكلفة", Formatters.formatMoney(totalCostValue, currency), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            MetricTile("القيمة البيعية التقديرية", Formatters.formatMoney(totalSaleValue, currency), colors.paidGreen, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("بحث باسم الصنف أو الباركود") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("inventory_search_input")
            )
            if (isManager) {
                FilledTonalIconButton(onClick = { showRefsDialog = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.Category, contentDescription = "التصنيفات والوحدات")
                }
            }
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InventoryFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }

        if (filtered.isEmpty()) {
            EmptyStateView("لا توجد أصناف مطابقة", "أضف صنفاً جديداً أو غيّر معيار البحث")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { item ->
                    val isOut = item.quantityMilli == 0L
                    val isLow = item.quantityMilli > 0 && item.quantityMilli <= item.lowStockThresholdMilli
                    val cat = item.categoryId?.let { catNames[it] }
                    val unit = item.unitId?.let { unitNames[it] }
                    AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val meta = listOfNotNull(
                                        item.barcode?.let { "باركود: $it" },
                                        cat,
                                        unit
                                    ).joinToString(" • ")
                                    if (meta.isNotBlank()) {
                                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                StatusBadge(
                                    text = Formatters.formatQty(item.quantityMilli) + (unit?.let { " $it" } ?: ""),
                                    textColor = when {
                                        !item.isActive -> MaterialTheme.colorScheme.onSurfaceVariant
                                        isOut -> colors.debtRed
                                        isLow -> colors.warningAmber
                                        else -> colors.paidGreen
                                    },
                                    backgroundColor = when {
                                        !item.isActive -> MaterialTheme.colorScheme.surfaceVariant
                                        isOut -> colors.debtRedContainer
                                        isLow -> colors.warningAmberContainer
                                        else -> colors.paidGreenContainer
                                    }
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("شراء: ${Formatters.formatMoney(item.purchasePrice, currency)}", style = MaterialTheme.typography.bodySmall)
                                Text("بيع: ${Formatters.formatMoney(item.salePrice, currency)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("حد التنبيه: ${Formatters.formatQty(item.lowStockThresholdMilli)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { historyItem = item }) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("الحركات")
                                }
                                if (isManager) {
                                    TextButton(onClick = { editItem = item }) { Text("تعديل") }
                                    TextButton(onClick = { adjustItem = item }) { Text("جرد / تسوية") }
                                    TextButton(onClick = { viewModel.setItemActive(item.id, !item.isActive) }) {
                                        Text(if (item.isActive) "أرشفة" else "استعادة")
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
        ItemFormDialog(
            title = "إضافة صنف جديد",
            categories = categories,
            units = units,
            isEdit = false,
            onDismiss = { showAddDialog = false },
            onSave = { name, barcode, qty, buy, sell, low, catId, unitId ->
                viewModel.addItem(name, barcode, qty, buy, sell, low, catId, unitId)
                showAddDialog = false
            }
        )
    }

    editItem?.let { item ->
        ItemFormDialog(
            title = "تعديل بيانات الصنف",
            categories = categories,
            units = units,
            isEdit = true,
            initial = item,
            onDismiss = { editItem = null },
            onSave = { name, barcode, _, buy, sell, low, catId, unitId ->
                viewModel.updateItem(item.id, name, barcode, buy, sell, low, catId, unitId)
                editItem = null
            }
        )
    }

    adjustItem?.let { item ->
        var newQty by remember { mutableStateOf(Formatters.formatQty(item.quantityMilli)) }
        var note by remember { mutableStateOf("") }
        var opId by remember { mutableStateOf(UUID.randomUUID().toString()) }
        val parsed = Formatters.parseQuantityMilli(newQty) ?: if (Formatters.normalizeDigits(newQty).trim() == "0") 0L else null
        AlertDialog(
            onDismissRequest = { adjustItem = null },
            title = { Text("جرد / تسوية كمية: ${item.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الكمية الدفترية الحالية: ${Formatters.formatQty(item.quantityMilli)}")
                    OutlinedTextField(
                        value = newQty,
                        onValueChange = { newQty = Formatters.filterDecimalInput(it) },
                        label = { Text("الكمية الفعلية الجديدة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("سبب التسوية (مطلوب)") })
                }
            },
            confirmButton = {
                Button(
                    enabled = parsed != null && parsed >= 0 && note.isNotBlank(),
                    onClick = {
                        viewModel.adjustStock(item.id, parsed!!, note, opId)
                        adjustItem = null
                    }
                ) { Text("حفظ التسوية") }
            },
            dismissButton = { TextButton(onClick = { adjustItem = null }) { Text("إلغاء") } }
        )
    }

    historyItem?.let { item ->
        val movements by viewModel.itemMovements(item.id).collectAsStateWithLifecycle(emptyList())
        AlertDialog(
            onDismissRequest = { historyItem = null },
            title = { Text("سجل حركات: ${item.name}") },
            text = {
                if (movements.isEmpty()) {
                    Text("لا توجد حركات مسجلة لهذا الصنف")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(movements, key = { it.id }) { m ->
                            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(m.movementType, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (m.quantityInMilli > 0) "+${Formatters.formatQty(m.quantityInMilli)}"
                                            else "-${Formatters.formatQty(m.quantityOutMilli)}",
                                            color = if (m.quantityInMilli > 0) colors.paidGreen else colors.debtRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    m.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    Text(
                                        "الرصيد بعد: ${Formatters.formatQty(m.balanceAfterMilli)} • ${Formatters.formatDateTime(m.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyItem = null }) { Text("إغلاق") } }
        )
    }

    if (showRefsDialog) {
        var catName by remember { mutableStateOf("") }
        var unitName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRefsDialog = false },
            title = { Text("التصنيفات والوحدات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = catName, onValueChange = { catName = it }, label = { Text("تصنيف جديد") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(enabled = catName.isNotBlank(), onClick = { viewModel.addCategory(catName); catName = "" }) { Text("إضافة") }
                    }
                    if (categories.isNotEmpty()) {
                        Text("التصنيفات: ${categories.joinToString("، ") { it.name }}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = unitName, onValueChange = { unitName = it }, label = { Text("وحدة جديدة (كرتون/حبة/كيلو)") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(enabled = unitName.isNotBlank(), onClick = { viewModel.addUnit(unitName); unitName = "" }) { Text("إضافة") }
                    }
                    if (units.isNotEmpty()) {
                        Text("الوحدات: ${units.joinToString("، ") { it.name }}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRefsDialog = false }) { Text("إغلاق") } }
        )
    }
}

@Composable
private fun ItemFormDialog(
    title: String,
    categories: List<CategoryEntity>,
    units: List<UnitEntity>,
    isEdit: Boolean,
    initial: ItemEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var barcode by remember { mutableStateOf(initial?.barcode.orEmpty()) }
    var quantity by remember { mutableStateOf(initial?.let { Formatters.formatQty(it.quantityMilli) } ?: "0") }
    var buy by remember { mutableStateOf(initial?.purchasePrice?.toString() ?: "0") }
    var sell by remember { mutableStateOf(initial?.salePrice?.toString() ?: "0") }
    var low by remember { mutableStateOf(initial?.let { Formatters.formatQty(it.lowStockThresholdMilli) } ?: "5") }
    var catId by remember { mutableStateOf(initial?.categoryId) }
    var unitId by remember { mutableStateOf(initial?.unitId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الصنف") }, singleLine = true) }
                item { OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("الباركود (اختياري)") }, singleLine = true) }
                if (!isEdit) {
                    item {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = Formatters.filterDecimalInput(it) },
                            label = { Text("الكمية الافتتاحية") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = buy,
                            onValueChange = { buy = Formatters.filterWholeNumberInput(it) },
                            label = { Text("سعر الشراء") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sell,
                            onValueChange = { sell = Formatters.filterWholeNumberInput(it) },
                            label = { Text("سعر البيع") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = low,
                        onValueChange = { low = Formatters.filterDecimalInput(it) },
                        label = { Text("حد تنبيه المخزون المنخفض") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
                if (categories.isNotEmpty()) {
                    item {
                        Text("التصنيف:", style = MaterialTheme.typography.labelSmall)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            categories.forEach { c ->
                                FilterChip(selected = catId == c.id, onClick = { catId = if (catId == c.id) null else c.id }, label = { Text(c.name) })
                            }
                        }
                    }
                }
                if (units.isNotEmpty()) {
                    item {
                        Text("الوحدة:", style = MaterialTheme.typography.labelSmall)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            units.forEach { u ->
                                FilterChip(selected = unitId == u.id, onClick = { unitId = if (unitId == u.id) null else u.id }, label = { Text(u.name) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        name,
                        barcode,
                        Formatters.normalizeDigits(quantity),
                        Formatters.normalizeDigits(buy),
                        Formatters.normalizeDigits(sell),
                        Formatters.normalizeDigits(low),
                        catId,
                        unitId
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
