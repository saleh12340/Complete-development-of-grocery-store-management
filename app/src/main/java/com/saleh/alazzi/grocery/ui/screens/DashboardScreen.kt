package com.saleh.alazzi.grocery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.components.AlAzziCard
import com.saleh.alazzi.grocery.ui.components.Formatters
import com.saleh.alazzi.grocery.ui.components.MetricTile
import com.saleh.alazzi.grocery.ui.components.QuickActionChipButton
import com.saleh.alazzi.grocery.ui.components.StatusBadge
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors

data class NavSectionItem(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val managerOnly: Boolean = false
)

@Composable
fun DashboardScreen(
    viewModel: GroceryViewModel,
    storeName: String,
    currency: String,
    onNavigate: (String) -> Unit,
    onOpenSaleDetail: (String) -> Unit,
    onOpenPurchaseDetail: (String) -> Unit,
    onLogout: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val salesToday by viewModel.salesToday.collectAsStateWithLifecycle()
    val purchasesToday by viewModel.purchasesToday.collectAsStateWithLifecycle()
    val invoicesToday by viewModel.invoicesToday.collectAsStateWithLifecycle()
    val grossProfitToday by viewModel.grossProfitToday.collectAsStateWithLifecycle()
    val cashBalance by viewModel.cashBalance.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val lowStockItems by viewModel.lowStockItems.collectAsStateWithLifecycle()
    val salesInvoices by viewModel.salesInvoices.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.purchaseInvoices.collectAsStateWithLifecycle()
    val cashTransactions by viewModel.cashTransactions.collectAsStateWithLifecycle()

    val isManager = session?.role == UserRoles.MANAGER
    val isCashierOrManager = session?.role == UserRoles.MANAGER || session?.role == UserRoles.CASHIER
    val colors = LocalAlAzziColors.current

    val customerDebtTotal = remember(customers) { customers.filter { !it.isArchived }.sumOf { it.balance } }
    val supplierDueTotal = remember(suppliers) { suppliers.filter { !it.isArchived }.sumOf { it.balance } }

    val sections = remember(isManager, isCashierOrManager) {
        buildList {
            add(NavSectionItem("customers", "العملاء والذمم", "الحسابات، الكشوفات والسداد", Icons.Default.People))
            if (isManager) add(NavSectionItem("suppliers", "الموردون", "فواتير الشراء والمستحقات", Icons.Default.LocalShipping, true))
            add(NavSectionItem("invoices", "مركز الفواتير", "المبيعات والمشتريات والسجل", Icons.AutoMirrored.Filled.ReceiptLong))
            add(NavSectionItem("inventory", "المخزون والأصناف", "الكميات، التنبيهات والتكلفة", Icons.Default.Inventory2))
            if (isCashierOrManager) add(NavSectionItem("cash", "الصندوق النقدي", "الحركات النقدية وجرد الدرج", Icons.Default.AccountBalanceWallet))
            add(NavSectionItem("reports", "التقارير والمطابقة", "20 مؤشر مالي ومحاسبي", Icons.Default.Assessment))
            if (isManager) add(NavSectionItem("expenses", "المصروفات", "تسجيل وتعديل المصروفات", Icons.Default.MoneyOff, true))
            if (isManager) add(NavSectionItem("transfers", "الحوالات", "الواردة والصادرة والعمولات", Icons.Default.SwapHoriz, true))
            add(NavSectionItem("notes", "الملاحظات", "تنبيهات المتجر والديون", Icons.AutoMirrored.Filled.NoteAdd))
            add(NavSectionItem("journal", "القيود المحاسبية", "القيود المزدوجة وإغلاق الفترات", Icons.Default.MenuBook, true))
            add(NavSectionItem("print-center", "مركز الطباعة والتقارير", "طباعة وتصدير 17 نوع مستند", Icons.Default.Print))
            if (isManager) add(NavSectionItem("backup", "النسخ الاحتياطي", "تشفير، فحص واستعادة", Icons.Default.CloudUpload, true))
            if (isManager) add(NavSectionItem("users", "المستخدمون", "الصلاحيات والوصول", Icons.Default.AdminPanelSettings, true))
            if (isManager) add(NavSectionItem("audit", "التدقيق والتشخيص", "سجل العمليات والأخطاء", Icons.Default.FactCheck, true))
            if (isManager) add(NavSectionItem("settings", "إعدادات المتجر", "الهوية، العملة والمظهر", Icons.Default.Settings, true))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.horizontalGradient(
                            listOf(colors.headerNavy, colors.headerNavy.copy(alpha = 0.88f))
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = storeName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onHeaderNavy
                        )
                        Text(
                            text = "${session?.displayName ?: "مستخدم"}  •  ${UserRoles.label(session?.role.orEmpty())}  •  ${Formatters.formatDate(System.currentTimeMillis())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onHeaderNavy.copy(alpha = 0.86f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        var showSwitchUser by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showSwitchUser = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("switch_user_button")
                        ) {
                            Icon(
                                Icons.Default.SwitchAccount,
                                contentDescription = "تبديل المستخدم",
                                tint = colors.onHeaderNavy
                            )
                        }
                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("logout_button")
                        ) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "تسجيل الخروج",
                                tint = colors.onHeaderNavy
                            )
                        }

                        if (showSwitchUser) {
                            var switchUsername by remember { mutableStateOf("") }
                            var switchPin by remember { mutableStateOf("") }
                            var switchError by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { showSwitchUser = false },
                                title = { Text("تبديل المستخدم السريع") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = switchUsername,
                                            onValueChange = { switchUsername = it },
                                            label = { Text("اسم المستخدم") },
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = switchPin,
                                            onValueChange = { switchPin = it },
                                            label = { Text("رمز PIN") },
                                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                            singleLine = true
                                        )
                                        if (switchError.isNotBlank()) {
                                            Text(switchError, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        enabled = switchUsername.isNotBlank() && switchPin.isNotBlank(),
                                        onClick = {
                                            viewModel.switchUser(switchUsername, switchPin) { ok ->
                                                if (ok) showSwitchUser = false else switchError = "بيانات الدخول غير صحيحة"
                                            }
                                        }
                                    ) { Text("تبديل الحساب") }
                                },
                                dismissButton = { TextButton(onClick = { showSwitchUser = false }) { Text("إلغاء") } }
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricTile(
                        label = "مبيعات اليوم",
                        value = Formatters.formatMoney(salesToday, currency),
                        accent = colors.paidGreen,
                        subtitle = "$invoicesToday فاتورة",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("sales-history") }
                    )
                    MetricTile(
                        label = "رصيد الصندوق",
                        value = Formatters.formatMoney(cashBalance, currency),
                        accent = if (cashBalance >= 0) MaterialTheme.colorScheme.primary else colors.debtRed,
                        subtitle = "الصافي الدفتري",
                        modifier = Modifier.weight(1f),
                        onClick = { if (isCashierOrManager) onNavigate("cash") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricTile(
                        label = "ديون العملاء",
                        value = Formatters.formatMoney(customerDebtTotal, currency),
                        accent = if (customerDebtTotal > 0) colors.debtRed else colors.paidGreen,
                        subtitle = "${customers.count { !it.isArchived && it.balance > 0 }} عميل مدين",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("customers") }
                    )
                    if (isManager) {
                        MetricTile(
                            label = "مستحقات الموردين",
                            value = Formatters.formatMoney(supplierDueTotal, currency),
                            accent = if (supplierDueTotal > 0) colors.warningAmber else colors.paidGreen,
                            subtitle = "مشتريات اليوم: ${Formatters.formatMoney(purchasesToday, currency)}",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("suppliers") }
                        )
                    } else {
                        MetricTile(
                            label = "الربح التقديري اليوم",
                            value = Formatters.formatMoney(grossProfitToday, currency),
                            accent = colors.paidGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("reports") }
                        )
                    }
                }
            }
        }

        if (lowStockItems.isNotEmpty()) {
            item {
                AlAzziCard(
                    containerColor = colors.warningAmberContainer,
                    onClick = { onNavigate("inventory") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = colors.warningAmber)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "تنبيه نقص المخزون (${lowStockItems.size} صنف)",
                                fontWeight = FontWeight.Bold,
                                color = colors.warningAmber
                            )
                            Text(
                                lowStockItems.take(3).joinToString(" • ") { "${it.name} (${Formatters.formatQty(it.quantityMilli)})" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusBadge("عرض", colors.warningAmber, MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }

        if (isCashierOrManager) {
            item {
                Text("اختصارات العمليات السريعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickActionChipButton(
                            label = "فاتورة بيع",
                            icon = Icons.Default.AddShoppingCart,
                            modifier = Modifier.weight(1f).testTag("quick_new_sale"),
                            containerColor = colors.paidGreenContainer,
                            contentColor = colors.paidGreen,
                            onClick = { onNavigate("new-sale") }
                        )
                        if (isManager) {
                            QuickActionChipButton(
                                label = "فاتورة شراء",
                                icon = Icons.Default.ShoppingBag,
                                modifier = Modifier.weight(1f).testTag("quick_new_purchase"),
                                onClick = { onNavigate("new-purchase") }
                            )
                        } else {
                            QuickActionChipButton(
                                label = "سجل العملاء",
                                icon = Icons.Default.People,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate("customers") }
                            )
                        }
                    }
                    if (isManager) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            QuickActionChipButton(
                                label = "مصروف",
                                icon = Icons.Default.MoneyOff,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                onClick = { onNavigate("expenses") }
                            )
                            QuickActionChipButton(
                                label = "حوالة",
                                icon = Icons.Default.SwapHoriz,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                onClick = { onNavigate("transfers") }
                            )
                            QuickActionChipButton(
                                label = "تقارير",
                                icon = Icons.Default.Assessment,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                onClick = { onNavigate("reports") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("أقسام النظام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        val rows = sections.chunked(2)
        items(rows) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { sec ->
                    AlAzziCard(
                        onClick = { onNavigate(sec.route) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("section_${sec.route}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(sec.icon, contentDescription = sec.title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sec.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (salesInvoices.isNotEmpty() || purchaseInvoices.isNotEmpty() || cashTransactions.isNotEmpty()) {
            item {
                Text("أحدث العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(salesInvoices.take(4), key = { "s_${it.id}" }) { inv ->
                AlAzziCard(
                    onClick = { onOpenSaleDetail(inv.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("فاتورة بيع #${inv.invoiceNumber}", fontWeight = FontWeight.Bold)
                            Text(Formatters.formatDateTime(inv.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Formatters.formatMoney(inv.total, currency), fontWeight = FontWeight.Bold, color = colors.paidGreen)
                            if (inv.status == "VOIDED") {
                                StatusBadge("ملغاة", colors.debtRed, colors.debtRedContainer)
                            } else if (inv.remaining > 0) {
                                StatusBadge("متبقي ${Formatters.formatMoney(inv.remaining, currency)}", colors.debtRed, colors.debtRedContainer)
                            } else {
                                StatusBadge("مدفوعة", colors.paidGreen, colors.paidGreenContainer)
                            }
                        }
                    }
                }
            }
            if (isManager) {
                items(purchaseInvoices.take(2), key = { "p_${it.id}" }) { inv ->
                    AlAzziCard(
                        onClick = { onOpenPurchaseDetail(inv.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فاتورة شراء #${inv.invoiceNumber}", fontWeight = FontWeight.Bold)
                                Text(Formatters.formatDateTime(inv.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Formatters.formatMoney(inv.total, currency), fontWeight = FontWeight.Bold)
                                if (inv.remaining > 0) {
                                    StatusBadge("متبقي ${Formatters.formatMoney(inv.remaining, currency)}", colors.warningAmber, colors.warningAmberContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
