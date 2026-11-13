package com.saleh.alazzi.grocery.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saleh.alazzi.grocery.data.backup.BackupInspection
import com.saleh.alazzi.grocery.data.backup.GroceryBackupManager
import com.saleh.alazzi.grocery.ui.GroceryViewModel
import com.saleh.alazzi.grocery.ui.ThemeMode
import com.saleh.alazzi.grocery.ui.UserRoles
import com.saleh.alazzi.grocery.ui.components.*
import com.saleh.alazzi.grocery.ui.theme.LocalAlAzziColors
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(
    viewModel: GroceryViewModel,
    backupManager: GroceryBackupManager,
    onBack: () -> Unit
) {
    val history by viewModel.backupHistory.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = LocalAlAzziColors.current

    var passphrase by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var inspectionPreview by remember { mutableStateOf<BackupInspection?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching { backupManager.exportTo(uri, passphrase) }
                    .onSuccess { statusMessage = "تم إنشاء نسخة مشفرة (${it.recordCount} سجل)" }
                    .onFailure { statusMessage = it.message ?: "فشل إنشاء النسخة الاحتياطية" }
                busy = false
            }
        }
    }

    val inspectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching { backupManager.inspectBackup(uri, passphrase) }
                    .onSuccess {
                        pendingRestoreUri = uri
                        inspectionPreview = it
                    }
                    .onFailure { statusMessage = it.message ?: "تعذر قراءة أو فك تشفير ملف النسخة" }
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("backup_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader("النسخ الاحتياطي المشفر", "AES-256-GCM + فحص العلاقات قبل الاستعادة", onBack = onBack)

        AlAzziCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("كلمة مرور التشفير (8 أحرف على الأقل)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !busy && passphrase.length >= 8,
                        onClick = { exportLauncher.launch("alazzi_backup_${System.currentTimeMillis()}.json") },
                        modifier = Modifier.weight(1f)
                    ) { Text("تصدير نسخة") }

                    FilledTonalButton(
                        enabled = !busy && passphrase.length >= 8,
                        onClick = { inspectLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("فحص واستعادة") }
                }
                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text("سجل عمليات النسخ (${history.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history, key = { it.id }) { h ->
                AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(h.result, fontWeight = FontWeight.Bold)
                            Text("إصدار ${h.formatVersion} • ${Formatters.formatDateTime(h.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(h.checksumSha256.take(10), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    inspectionPreview?.let { info ->
        val uri = pendingRestoreUri
        AlertDialog(
            onDismissRequest = { inspectionPreview = null; pendingRestoreUri = null },
            title = { Text("نتائج فحص النسخة قبل الاستعادة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("تاريخ النسخة: ${Formatters.formatDateTime(info.createdAt)}")
                    Text("إصدار المخطط: v${info.formatVersion}  •  التطبيق: ${info.appVersion}")
                    Text("العملاء: ${info.customersCount} • الموردون: ${info.suppliersCount} • الأصناف: ${info.itemsCount}")
                    Text("فواتير البيع: ${info.salesInvoicesCount} • فواتير الشراء: ${info.purchaseInvoicesCount}")
                    Text("إجمالي السجلات: ${info.recordCount}")
                    if (!info.relationsValid) {
                        Text("تحذيرات العلاقات: ${info.relationWarnings.joinToString("؛ ")}", color = colors.debtRed, fontWeight = FontWeight.Bold)
                    } else {
                        Text("جميع العلاقات وتوقيع SHA-256 سليمة — سيتم حفظ نسخة أمان تلقائية قبل الاستبدال.", color = colors.paidGreen)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = info.relationsValid && uri != null && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            runCatching { backupManager.restoreFrom(uri!!, passphrase, confirmed = true) }
                                .onSuccess { statusMessage = "تمت استعادة ${it.recordCount} سجل بنجاح مع حفظ نسخة أمان تلقائية" }
                                .onFailure { statusMessage = it.message ?: "فشلت الاستعادة" }
                            inspectionPreview = null
                            pendingRestoreUri = null
                            busy = false
                        }
                    }
                ) { Text("تأكيد الاستعادة") }
            },
            dismissButton = { TextButton(onClick = { inspectionPreview = null; pendingRestoreUri = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun UsersScreen(
    viewModel: GroceryViewModel,
    onBack: () -> Unit
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val colors = LocalAlAzziColors.current

    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("users_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "المستخدمون والصلاحيات",
            subtitle = "المدير، الكاشير، والمستخدم المحدود",
            actionLabel = "مستخدم جديد",
            onAction = { showAdd = true },
            onBack = onBack
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.id }) { u ->
                AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${u.displayName} (@${u.username})", fontWeight = FontWeight.Bold)
                            Text("الدور: ${UserRoles.label(u.role)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (u.id != session?.id) {
                            Switch(
                                checked = u.isActive,
                                onCheckedChange = { active -> viewModel.updateUserAccess(u.id, u.role, active) }
                            )
                        } else {
                            StatusBadge("الحساب الحالي", colors.paidGreen, colors.paidGreenContainer)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var displayName by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(UserRoles.CASHIER) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("إنشاء حساب مستخدم") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("الاسم الظاهر") }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("اسم الدخول (Username)") }, singleLine = true)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("رمز PIN (6 أرقام فأكثر)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = role == UserRoles.MANAGER, onClick = { role = UserRoles.MANAGER }, label = { Text("مدير") })
                        FilterChip(selected = role == UserRoles.CASHIER, onClick = { role = UserRoles.CASHIER }, label = { Text("كاشير") })
                        FilterChip(selected = role == UserRoles.LIMITED, onClick = { role = UserRoles.LIMITED }, label = { Text("محدود") })
                    }
                }
            },
            confirmButton = {
                Button(enabled = displayName.isNotBlank() && username.isNotBlank() && pin.length >= 6, onClick = {
                    viewModel.addUser(displayName, username, pin, role)
                    showAdd = false
                }) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun AuditAndDiagnosticsScreen(
    viewModel: GroceryViewModel,
    onBack: () -> Unit
) {
    val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
    val errorLogs by viewModel.errorLogs.collectAsStateWithLifecycle()
    val colors = LocalAlAzziColors.current
    var tab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("audit_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "التدقيق وسجل الأخطاء",
            subtitle = "توثيق التغييرات الحساسة والتشخيص المحلي",
            actionLabel = if (tab == 1 && errorLogs.isNotEmpty()) "مسح الأخطاء" else null,
            onAction = if (tab == 1 && errorLogs.isNotEmpty()) ({ viewModel.clearErrorLogs() }) else null,
            onBack = onBack
        )

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("سجل العمليات (${activityLogs.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("سجل الأخطاء (${errorLogs.size})") })
        }

        if (tab == 0) {
            if (activityLogs.isEmpty()) {
                EmptyStateView("لا توجد سجلات تدقيق", "تُسجل هنا العمليات المالية والحساسة")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(activityLogs, key = { it.id }) { log ->
                        AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(log.action, fontWeight = FontWeight.Bold)
                                    Text(log.actorName ?: "النظام", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                log.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                if (log.previousValue != null || log.newValue != null) {
                                    Text("قبل: ${log.previousValue ?: "-"} -> بعد: ${log.newValue ?: "-"}", style = MaterialTheme.typography.labelSmall)
                                }
                                log.reason?.takeIf { it.isNotBlank() }?.let {
                                    Text("السبب: $it", style = MaterialTheme.typography.labelSmall, color = colors.warningAmber)
                                }
                                Text(Formatters.formatDateTime(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            if (errorLogs.isEmpty()) {
                EmptyStateView("سجل الأخطاء نظيف", "لم تُسجل أية أخطاء تشغيلية")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(errorLogs, key = { it.id }) { err ->
                        AlAzziCard(containerColor = colors.debtRedContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${err.tag}: ${err.message}", fontWeight = FontWeight.Bold, color = colors.debtRed)
                                Text("${err.details ?: ""} • ${Formatters.formatDateTime(err.createdAt)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreSettingsScreen(
    viewModel: GroceryViewModel,
    initialName: String,
    initialPhone: String,
    initialAddress: String,
    initialFooter: String,
    initialCurrency: String,
    initialTheme: ThemeMode,
    onBack: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var address by remember(initialAddress) { mutableStateOf(initialAddress) }
    var footer by remember(initialFooter) { mutableStateOf(initialFooter) }
    var currency by remember(initialCurrency) { mutableStateOf(initialCurrency) }
    var theme by remember(initialTheme) { mutableStateOf(initialTheme) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("إعدادات المتجر والمظهر", "تظهر بيانات المتجر في رأس وتذييل الفواتير", onBack = onBack) }

        item {
            AlAzziCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المتجر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("هاتف المتجر") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("العملة (مثل: ريال)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = footer, onValueChange = { footer = it }, label = { Text("تذييل الفاتورة") }, modifier = Modifier.fillMaxWidth())

                    Text("مظهر التطبيق:", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(selected = theme == m, onClick = { theme = m }, label = { Text(m.label) })
                        }
                    }

                    Button(
                        enabled = name.isNotBlank(),
                        onClick = { viewModel.saveStoreSettings(name, phone, address, footer, currency, theme) },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("save_settings_button")
                    ) { Text("حفظ الإعدادات") }
                }
            }
        }
    }
}

@Composable
fun AdminSetupScreen(
    onSetup: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("المدير العام") }
    var pin by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        AlAzziCard(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("بقالة العزي للمواد الغذائية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("إعداد حساب المدير لأول مرة", style = MaterialTheme.typography.titleMedium)
                Text("سيتم حفظ رمز الدخول مشفراً محلياً عبر PBKDF2-HMAC-SHA256، ويُستخدم username: admin للدخول لاحقاً.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المدير") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("admin_name_input"))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("رمز PIN (6 أرقام أو أحرف على الأقل)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_pin_input")
                )
                Button(
                    enabled = name.isNotBlank() && pin.length >= 6,
                    onClick = { onSetup(name, pin) },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("create_admin_button")
                ) { Text("إنشاء حساب المدير والبدء") }
            }
        }
    }
}

@Composable
fun LoginScreen(
    storeName: String,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("admin") }
    var pin by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        AlAzziCard(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Column {
                        Text(storeName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("نظام إدارة المبيعات والمحاسبة والمخزون", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("اسم المستخدم") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("login_username_input"))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("رمز الدخول PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("login_pin_input")
                )
                Button(
                    enabled = username.isNotBlank() && pin.isNotBlank(),
                    onClick = { onLogin(username, pin) },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("login_button")
                ) { Text("تسجيل الدخول") }
            }
        }
    }
}
