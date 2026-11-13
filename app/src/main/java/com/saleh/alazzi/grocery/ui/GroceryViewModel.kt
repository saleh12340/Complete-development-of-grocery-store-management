package com.saleh.alazzi.grocery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.*
import com.saleh.alazzi.grocery.data.repository.AccountingRepository
import com.saleh.alazzi.grocery.data.repository.CreateInvoiceInput
import com.saleh.alazzi.grocery.data.repository.EditInvoiceInput
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import com.saleh.alazzi.grocery.data.repository.IntegrityAuditReport
import com.saleh.alazzi.grocery.data.repository.JournalLineInput
import com.saleh.alazzi.grocery.data.repository.ReturnLineInput
import com.saleh.alazzi.grocery.data.repository.SaveInvoiceResult
import com.saleh.alazzi.grocery.security.PinCredential
import com.saleh.alazzi.grocery.security.PinHasher
import com.saleh.alazzi.grocery.security.SessionManager
import com.saleh.alazzi.grocery.security.UserPermissions
import com.saleh.alazzi.grocery.security.hasPermission
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar
import java.util.UUID

enum class ReportPeriod(val label: String) {
    TODAY("اليوم"),
    WEEK("الأسبوع"),
    MONTH("الشهر"),
    ALL("الكل"),
    CUSTOM("مخصص")
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("SYSTEM", "تلقائي (النظام)"),
    LIGHT("LIGHT", "الوضع الفاتح"),
    DARK("DARK", "الوضع الداكن")
}

object UserRoles {
    const val MANAGER = "MANAGER"
    const val CASHIER = "CASHIER"
    const val LIMITED = "LIMITED"

    fun label(role: String): String = when (role) {
        MANAGER -> "مدير"
        CASHIER -> "كاشير"
        LIMITED -> "مستخدم محدود"
        else -> role
    }
}

data class ReportSummary(
    val sales: Long = 0,
    val purchases: Long = 0,
    val grossProfit: Long = 0,
    val salesInvoices: Long = 0,
    val salesReturns: Long = 0,
    val purchaseReturns: Long = 0,
    val expenses: Long = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class GroceryViewModel(
    private val database: GroceryDatabase,
    private val repository: GroceryRepository,
    private val sessionManager: SessionManager? = null
) : ViewModel() {
    private val accounting = AccountingRepository(database)

    val customers = database.customers().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customerAggregates = database.customers().observeAggregates()
        .map { list -> list.associateBy { it.customerId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val suppliers = database.suppliers().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val supplierAggregates = database.suppliers().observeAggregates()
        .map { list -> list.associateBy { it.supplierId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val items = database.items().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allItemsIncludingArchived = database.items().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lowStockItems = database.items().observeLowStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = database.references().observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val units = database.references().observeUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenses = database.expenses().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transfers = database.transfers().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notes = database.references().observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val users = database.references().observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val storeSettings = database.references().observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activityLogs = database.references().observeActivityLogs(150)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val errorLogs = database.references().observeErrorLogs(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val backupHistory = database.references().observeBackupHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _session = MutableStateFlow<UserEntity?>(null)
    val session = _session.asStateFlow()

    private val _restoredRoute = MutableStateFlow("dashboard")
    val restoredRoute = _restoredRoute.asStateFlow()

    private val _sessionInitialized = MutableStateFlow(sessionManager == null)
    val sessionInitialized = _sessionInitialized.asStateFlow()

    val journalEntries = database.journal().observeAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val journalEntryLines = database.journal().observeAllLines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accountingPeriods = database.journal().observePeriods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (sessionManager != null) {
            viewModelScope.launch {
                runCatching {
                    val persisted = sessionManager.loadValidSession()
                    if (persisted != null) {
                        val user = database.references().findUserById(persisted.userId)
                        if (user != null && user.isActive) {
                            _restoredRoute.value = persisted.lastRoute.ifBlank { "dashboard" }
                            _session.value = user
                        } else {
                            sessionManager.clearSession()
                        }
                    }
                }
                _sessionInitialized.value = true
            }
        }
    }

    val cashBalance = database.cash().observeBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val cashTransactions = database.cash().observePage(300, 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cashReconciliations = database.cash().observeReconciliations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val todayRange = todayRange()
    val salesToday = database.salesInvoices().observeSalesTotal(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val purchasesToday = database.purchaseInvoices().observePurchaseTotal(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val invoicesToday = database.salesInvoices().observeSalesCount(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val grossProfitToday = database.salesInvoices().observeGrossProfit(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val salesInvoices = database.salesInvoices().observePage(300, 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val purchaseInvoices = database.purchaseInvoices().observePage(300, 0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentReturns = database.returns().observeRecent(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentStockMovements = database.stock().observeRecentMovements(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _reportPeriod = MutableStateFlow(ReportPeriod.TODAY)
    val reportPeriod = _reportPeriod.asStateFlow()

    private val _customReportRange = MutableStateFlow(todayRange)
    val customReportRange = _customReportRange.asStateFlow()

    private val activeRangeFlow = combine(_reportPeriod, _customReportRange) { period, custom ->
        if (period == ReportPeriod.CUSTOM) custom else reportRange(period)
    }

    val reportSummary = activeRangeFlow.flatMapLatest { (from, to) ->
        database.salesInvoices().observeFinancialSummary(from, to).map { row ->
            ReportSummary(
                sales = row.sales,
                purchases = row.purchases,
                grossProfit = row.grossProfit,
                salesInvoices = row.salesInvoices,
                salesReturns = row.salesReturns,
                purchaseReturns = row.purchaseReturns,
                expenses = row.expenses
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportSummary())

    val detailedSummary = activeRangeFlow.flatMapLatest { (from, to) ->
        database.salesInvoices().observeDetailedSummary(from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailedFinancialSummary())

    val itemSalesSummary = activeRangeFlow.flatMapLatest { (from, to) ->
        database.salesInvoices().observeItemSalesSummary(from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _integrityAudit = MutableStateFlow<IntegrityAuditReport?>(null)
    val integrityAudit = _integrityAudit.asStateFlow()

    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun setReportPeriod(period: ReportPeriod) {
        _reportPeriod.value = period
    }

    fun setCustomReportRange(fromMillis: Long, toMillis: Long) {
        val start = minOf(fromMillis, toMillis)
        val end = maxOf(fromMillis, toMillis)
        _customReportRange.value = start to end
        _reportPeriod.value = ReportPeriod.CUSTOM
    }

    fun runIntegrityAudit() {
        viewModelScope.launch {
            runCatching {
                requireManager("فحص مطابقة الأرصدة للمدير فقط")
                repository.runIntegrityAudit()
            }.onSuccess { report ->
                _integrityAudit.value = report
                eventChannel.send(
                    if (report.isHealthy) "المطابقة سليمة 100% — لا توجد فروقات"
                    else "تم رصد ${report.mismatches.size} اختلاف في المطابقة"
                )
            }.onFailure {
                logError("IntegrityAudit", it)
                eventChannel.send(messageFor(it, "تعذر تنفيذ فحص المطابقة"))
            }
        }
    }

    fun saveStoreSettings(
        storeName: String,
        phone: String,
        address: String,
        receiptFooter: String,
        currency: String = "ريال",
        themeMode: ThemeMode = ThemeMode.SYSTEM
    ) {
        viewModelScope.launch {
            runCatching {
                requireManager("إعدادات المتجر للمدير فقط")
                val cleanName = storeName.trim()
                require(cleanName.isNotBlank()) { "اكتب اسم المتجر" }
                val now = System.currentTimeMillis()
                database.withTransaction {
                    val values = mapOf(
                        "store_name" to cleanName,
                        "store_phone" to phone.trim(),
                        "store_address" to address.trim(),
                        "receipt_footer" to receiptFooter.trim(),
                        "store_currency" to currency.trim().ifBlank { "ريال" },
                        "theme_mode" to themeMode.key
                    )
                    values.forEach { (key, value) ->
                        database.references().putSetting(AppSettingEntity(key, value, now))
                    }
                    database.references().insertActivity(
                        ActivityLogEntity(
                            id = UUID.randomUUID().toString(),
                            actorId = _session.value?.id,
                            action = "UPDATE_STORE_SETTINGS",
                            entityType = "SETTINGS",
                            entityId = "store",
                            details = "تحديث إعدادات المتجر: $cleanName",
                            createdAt = now,
                            actorName = _session.value?.displayName
                        )
                    )
                }
            }.onSuccess { eventChannel.send("تم حفظ إعدادات المتجر") }
                .onFailure {
                    logError("SaveSettings", it)
                    eventChannel.send(messageFor(it, "تعذر حفظ إعدادات المتجر"))
                }
        }
    }

    fun savePrinterSettings(
        address: String?,
        printerName: String?,
        paperWidthDots: Int,
        copies: Int = 1,
        fontScale: Float = 1.0f,
        marginPx: Int = 12,
        showLogo: Boolean = true,
        showDateTime: Boolean = true
    ) {
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                database.withTransaction {
                    database.references().putSetting(AppSettingEntity("printer_address", address.orEmpty(), now))
                    database.references().putSetting(AppSettingEntity("printer_name", printerName.orEmpty(), now))
                    database.references().putSetting(AppSettingEntity("printer_width", paperWidthDots.toString(), now))
                    database.references().putSetting(AppSettingEntity("print_copies", copies.coerceIn(1, 5).toString(), now))
                    database.references().putSetting(AppSettingEntity("print_font_scale", fontScale.coerceIn(0.8f, 1.3f).toString(), now))
                    database.references().putSetting(AppSettingEntity("print_margin", marginPx.coerceIn(4, 32).toString(), now))
                    database.references().putSetting(AppSettingEntity("print_show_logo", showLogo.toString(), now))
                    database.references().putSetting(AppSettingEntity("print_show_datetime", showDateTime.toString(), now))
                }
            }.onSuccess { eventChannel.send("تم حفظ إعدادات الطباعة والطابعة") }
                .onFailure {
                    logError("SavePrinter", it)
                    eventChannel.send(messageFor(it, "تعذر حفظ إعدادات الطابعة"))
                }
        }
    }

    fun saveSessionTimeoutHours(hours: Int) {
        viewModelScope.launch {
            runCatching {
                requireManager("تغيير مهلة الجلسة للمدير فقط")
                val safe = hours.coerceIn(1, 720)
                database.references().putSetting(AppSettingEntity("session_timeout_hours", safe.toString(), System.currentTimeMillis()))
                _session.value?.let { sessionManager?.saveSession(it, _restoredRoute.value, safe) }
            }.onSuccess { eventChannel.send("تم تحديث مهلة بقاء الجلسة ($hours ساعة)") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر حفظ مهلة الجلسة")) }
        }
    }

    fun addCustomer(name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_VIEW_CUSTOMERS, "لا تملك صلاحية إضافة عملاء")
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم العميل" }
                val cleanPhone = phone.trim().ifBlank { null }
                require(database.customers().duplicateCount(cleanName, cleanPhone) == 0) { "العميل مسجل مسبقاً" }
                val now = System.currentTimeMillis()
                database.customers().insert(
                    CustomerEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        phone = cleanPhone,
                        address = address.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null },
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }.onSuccess { eventChannel.send("تمت إضافة العميل") }
                .onFailure {
                    logError("AddCustomer", it)
                    eventChannel.send(messageFor(it, "تعذر إضافة العميل"))
                }
        }
    }

    fun updateCustomer(id: String, name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_VIEW_CUSTOMERS, "لا تملك صلاحية تعديل بيانات العملاء")
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم العميل" }
                val cleanPhone = phone.trim().ifBlank { null }
                require(database.customers().duplicateCountExcluding(id, cleanName, cleanPhone) == 0) { "الاسم أو الهاتف مستخدم لعميل آخر" }
                check(database.customers().updateProfile(id, cleanName, cleanPhone, address.trim().ifBlank { null }, notes.trim().ifBlank { null }, System.currentTimeMillis()) == 1)
            }.onSuccess { eventChannel.send("تم تحديث بيانات العميل") }
                .onFailure {
                    logError("UpdateCustomer", it)
                    eventChannel.send(messageFor(it, "تعذر تحديث بيانات العميل"))
                }
        }
    }

    fun setCustomerArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            runCatching {
                requireManager("أرشفة العملاء للمدير فقط")
                val c = database.customers().findById(id) ?: error("العميل غير موجود")
                if (archived) require(c.balance == 0L) { "لا يمكن أرشفة عميل لديه رصيد مستحق (${c.balance})" }
                check(database.customers().setArchived(id, archived, System.currentTimeMillis()) == 1)
            }.onSuccess { eventChannel.send(if (archived) "تمت أرشفة العميل" else "تمت استعادة العميل") }
                .onFailure {
                    logError("ArchiveCustomer", it)
                    eventChannel.send(messageFor(it, "تعذر تحديث حالة العميل"))
                }
        }
    }

    fun addSupplier(name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إدارة الموردين للمدير فقط")
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم المورد" }
                val cleanPhone = phone.trim().ifBlank { null }
                require(database.suppliers().duplicateCount(cleanName, cleanPhone) == 0) { "المورد مسجل مسبقاً" }
                val now = System.currentTimeMillis()
                database.suppliers().insert(
                    SupplierEntity(
                        id = UUID.randomUUID().toString(),
                        name = cleanName,
                        phone = cleanPhone,
                        address = address.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null },
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }.onSuccess { eventChannel.send("تمت إضافة المورد") }
                .onFailure {
                    logError("AddSupplier", it)
                    eventChannel.send(messageFor(it, "تعذر إضافة المورد"))
                }
        }
    }

    fun updateSupplier(id: String, name: String, phone: String, address: String, notes: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إدارة الموردين للمدير فقط")
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم المورد" }
                val cleanPhone = phone.trim().ifBlank { null }
                require(database.suppliers().duplicateCountExcluding(id, cleanName, cleanPhone) == 0) { "الاسم أو الهاتف مستخدم لمورد آخر" }
                check(database.suppliers().updateProfile(id, cleanName, cleanPhone, address.trim().ifBlank { null }, notes.trim().ifBlank { null }, System.currentTimeMillis()) == 1)
            }.onSuccess { eventChannel.send("تم تحديث بيانات المورد") }
                .onFailure {
                    logError("UpdateSupplier", it)
                    eventChannel.send(messageFor(it, "تعذر تحديث بيانات المورد"))
                }
        }
    }

    fun setSupplierArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            runCatching {
                requireManager("أرشفة الموردين للمدير فقط")
                val s = database.suppliers().findById(id) ?: error("المورد غير موجود")
                if (archived) require(s.balance == 0L) { "لا يمكن أرشفة مورد لديه مستحقات (${s.balance})" }
                check(database.suppliers().setArchived(id, archived, System.currentTimeMillis()) == 1)
            }.onSuccess { eventChannel.send(if (archived) "تمت أرشفة المورد" else "تمت استعادة المورد") }
                .onFailure {
                    logError("ArchiveSupplier", it)
                    eventChannel.send(messageFor(it, "تعذر تحديث حالة المورد"))
                }
        }
    }

    fun addItem(
        name: String,
        barcode: String,
        quantity: String,
        purchasePrice: String,
        salePrice: String,
        lowStockThreshold: String = "0",
        categoryId: String? = null,
        unitId: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_EDIT_INVENTORY, "لا تملك صلاحية إضافة الأصناف")
                database.withTransaction {
                    val cleanName = name.trim()
                    require(cleanName.isNotEmpty()) { "اكتب اسم الصنف" }
                    val cleanBarcode = barcode.trim().ifBlank { null }
                    require(database.items().duplicateCount(cleanName, cleanBarcode) == 0) { "الصنف أو الباركود مسجل مسبقاً" }
                    val now = System.currentTimeMillis()
                    val normalizedQty = normalizeDecimalText(quantity).ifBlank { "0" }
                    val normalizedLow = normalizeDecimalText(lowStockThreshold).ifBlank { "0" }
                    val normalizedBuy = normalizeWholeText(purchasePrice).ifBlank { "0" }
                    val normalizedSell = normalizeWholeText(salePrice).ifBlank { "0" }

                    val quantityMilli = parseMilliSafe(normalizedQty, "الكمية غير صحيحة")
                    val lowMilli = parseMilliSafe(normalizedLow, "حد التنبيه غير صحيح")
                    val buy = normalizedBuy.toLongOrNull() ?: error("سعر الشراء غير صحيح")
                    val sell = normalizedSell.toLongOrNull() ?: error("سعر البيع غير صحيح")
                    require(quantityMilli >= 0 && lowMilli >= 0 && buy >= 0 && sell >= 0) { "لا تقبل القيم السالبة" }
                    val itemId = UUID.randomUUID().toString()
                    database.items().insert(
                        ItemEntity(
                            id = itemId,
                            name = cleanName,
                            barcode = cleanBarcode,
                            categoryId = categoryId,
                            unitId = unitId,
                            quantityMilli = quantityMilli,
                            lowStockThresholdMilli = lowMilli,
                            purchasePrice = buy,
                            salePrice = sell,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    if (quantityMilli > 0) {
                        database.stock().insertMovement(
                            StockMovementEntity(
                                id = UUID.randomUUID().toString(),
                                operationId = "opening:$itemId",
                                itemId = itemId,
                                movementType = "OPENING",
                                quantityInMilli = quantityMilli,
                                quantityOutMilli = 0,
                                balanceBeforeMilli = 0,
                                balanceAfterMilli = quantityMilli,
                                sourceType = "OPENING_BALANCE",
                                sourceId = itemId,
                                note = "رصيد افتتاحي عند إضافة الصنف",
                                createdAt = now
                            )
                        )
                    }
                }
            }.onSuccess {
                eventChannel.send("تمت إضافة الصنف وتسجيل رصيده الافتتاحي")
                onComplete(true)
            }.onFailure {
                logError("AddItem", it)
                eventChannel.send(messageFor(it, "تعذر إضافة الصنف"))
                onComplete(false)
            }
        }
    }

    fun updateItem(
        id: String,
        name: String,
        barcode: String,
        purchasePrice: String,
        salePrice: String,
        lowStockThreshold: String,
        categoryId: String? = null,
        unitId: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_EDIT_INVENTORY, "لا تملك صلاحية تعديل الأصناف")
                val cleanName = name.trim()
                require(cleanName.isNotEmpty()) { "اكتب اسم الصنف" }
                val cleanBarcode = barcode.trim().ifBlank { null }
                require(database.items().duplicateCountExcluding(id, cleanName, cleanBarcode) == 0) { "اسم الصنف أو الباركود مستخدم لصنف آخر" }
                val buy = normalizeWholeText(purchasePrice).ifBlank { "0" }.toLongOrNull() ?: error("سعر الشراء غير صحيح")
                val sell = normalizeWholeText(salePrice).ifBlank { "0" }.toLongOrNull() ?: error("سعر البيع غير صحيح")
                val lowMilli = parseMilliSafe(normalizeDecimalText(lowStockThreshold).ifBlank { "0" }, "حد التنبيه غير صحيح")
                require(buy >= 0 && sell >= 0 && lowMilli >= 0) { "لا تقبل القيم السالبة" }
                check(database.items().updateDetails(id, cleanName, cleanBarcode, buy, sell, lowMilli, categoryId, unitId, System.currentTimeMillis()) == 1)
            }.onSuccess {
                eventChannel.send("تم تحديث بيانات الصنف")
                onComplete(true)
            }.onFailure {
                logError("UpdateItem", it)
                eventChannel.send(messageFor(it, "تعذر تحديث الصنف"))
                onComplete(false)
            }
        }
    }

    fun setItemActive(id: String, active: Boolean) {
        viewModelScope.launch {
            runCatching {
                requireManager("أرشفة الأصناف للمدير فقط")
                check(database.items().setActive(id, active, System.currentTimeMillis()) == 1)
            }.onSuccess { eventChannel.send(if (active) "تمت استعادة الصنف" else "تمت أرشفة الصنف مع حفظ سجلات الفواتير") }
                .onFailure {
                    logError("ArchiveItem", it)
                    eventChannel.send(messageFor(it, "تعذر تحديث حالة الصنف"))
                }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إدارة التصنيفات للمدير فقط")
                val clean = name.trim()
                require(clean.isNotBlank()) { "اكتب اسم التصنيف" }
                database.references().insertCategory(CategoryEntity(UUID.randomUUID().toString(), clean, System.currentTimeMillis()))
            }.onSuccess { eventChannel.send("تمت إضافة التصنيف") }
                .onFailure { eventChannel.send(messageFor(it, "التصنيف مسجل مسبقاً")) }
        }
    }

    fun addUnit(name: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إدارة الوحدات للمدير فقط")
                val clean = name.trim()
                require(clean.isNotBlank()) { "اكتب اسم الوحدة" }
                database.references().insertUnit(UnitEntity(UUID.randomUUID().toString(), clean, System.currentTimeMillis()))
            }.onSuccess { eventChannel.send("تمت إضافة الوحدة") }
                .onFailure { eventChannel.send(messageFor(it, "الوحدة مسجلة مسبقاً")) }
        }
    }

    fun createSale(
        input: CreateInvoiceInput,
        onFinished: (Boolean) -> Unit = {},
        onSaved: (SaveInvoiceResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_SELL, "لا تملك صلاحية إنشاء فواتير البيع")
                val actor = _session.value ?: error("يجب تسجيل الدخول أولاً")
                repository.createSale(input.copy(actorId = actor.id, actorName = actor.displayName))
            }.onSuccess {
                eventChannel.send(
                    if (it.wasDuplicate) "هذه الفاتورة محفوظة مسبقاً: ${it.invoiceNumber}"
                    else "تم حفظ فاتورة البيع رقم ${it.invoiceNumber}"
                )
                onSaved(it)
                onFinished(true)
            }.onFailure {
                logError("CreateSale", it)
                eventChannel.send(messageFor(it, "تعذر حفظ فاتورة البيع"))
                onFinished(false)
            }
        }
    }

    fun editSalesInvoice(
        input: EditInvoiceInput,
        onFinished: (Boolean) -> Unit = {},
        onSaved: (SaveInvoiceResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_EDIT_OPERATIONS, "لا تملك صلاحية تعديل فواتير البيع")
                val actor = _session.value ?: error("يجب تسجيل الدخول أولاً")
                accounting.editSalesInvoice(input.copy(actorId = actor.id, actorName = actor.displayName))
            }.onSuccess {
                eventChannel.send("تم تحديث فاتورة البيع #${it.invoiceNumber} وإعادة حساب الأرصدة والمخزون")
                onSaved(it)
                onFinished(true)
            }.onFailure {
                logError("EditSale", it)
                eventChannel.send(messageFor(it, "تعذر تعديل فاتورة البيع"))
                onFinished(false)
            }
        }
    }

    fun createPurchase(
        input: CreateInvoiceInput,
        onFinished: (Boolean) -> Unit = {},
        onSaved: (SaveInvoiceResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_BUY, "لا تملك صلاحية إنشاء فواتير الشراء")
                val actor = _session.value
                repository.createPurchase(input.copy(actorId = actor?.id, actorName = actor?.displayName))
            }.onSuccess {
                eventChannel.send(
                    if (it.wasDuplicate) "فاتورة الشراء محفوظة مسبقاً: ${it.invoiceNumber}"
                    else "تم حفظ فاتورة الشراء رقم ${it.invoiceNumber}"
                )
                onSaved(it)
                onFinished(true)
            }.onFailure {
                logError("CreatePurchase", it)
                eventChannel.send(messageFor(it, "تعذر حفظ فاتورة الشراء"))
                onFinished(false)
            }
        }
    }

    fun editPurchaseInvoice(
        input: EditInvoiceInput,
        onFinished: (Boolean) -> Unit = {},
        onSaved: (SaveInvoiceResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_EDIT_OPERATIONS, "لا تملك صلاحية تعديل فواتير الشراء")
                val actor = _session.value ?: error("يجب تسجيل الدخول أولاً")
                accounting.editPurchaseInvoice(input.copy(actorId = actor.id, actorName = actor.displayName))
            }.onSuccess {
                eventChannel.send("تم تحديث فاتورة الشراء #${it.invoiceNumber} وإعادة حساب الأرصدة والمخزون")
                onSaved(it)
                onFinished(true)
            }.onFailure {
                logError("EditPurchase", it)
                eventChannel.send(messageFor(it, "تعذر تعديل فاتورة الشراء"))
                onFinished(false)
            }
        }
    }

    fun paySalesInvoice(invoiceId: String, amount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_RECEIVE_PAYMENT, "لا تملك صلاحية تسجيل دفعات الفواتير")
                val actor = _session.value
                repository.recordSalesInvoicePayment(
                    operationId = operationId,
                    invoiceId = invoiceId,
                    amount = amount,
                    note = note,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم تسجيل الدفعة وربطها بالفاتورة الأصلية") }
                .onFailure {
                    logError("PaySalesInvoice", it)
                    eventChannel.send(messageFor(it, "تعذر تسجيل الدفعة"))
                }
        }
    }

    fun payPurchaseInvoice(invoiceId: String, amount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("سداد فواتير الشراء للمدير فقط")
                val actor = _session.value
                repository.recordPurchaseInvoicePayment(
                    operationId = operationId,
                    invoiceId = invoiceId,
                    amount = amount,
                    note = note,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم تسجيل الدفعة وربطها بفاتورة الشراء") }
                .onFailure {
                    logError("PayPurchaseInvoice", it)
                    eventChannel.send(messageFor(it, "تعذر تسجيل الدفعة"))
                }
        }
    }

    fun voidSalesInvoice(invoiceId: String, reason: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إلغاء الفواتير للمدير فقط")
                val actor = _session.value
                repository.voidSalesInvoice(operationId, invoiceId, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم إلغاء الفاتورة وعكس آثارها المالية والمخزنية") }
                .onFailure {
                    logError("VoidSale", it)
                    eventChannel.send(messageFor(it, "تعذر إلغاء الفاتورة"))
                }
        }
    }

    fun settleCustomerBalance(customerId: String, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("تسوية حساب العميل للمدير فقط")
                val actor = _session.value
                repository.settleCustomerBalance(operationId, customerId, note, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تمت تسوية حساب العميل مع الاحتفاظ بالسجل التاريخي") }
                .onFailure {
                    logError("SettleCustomer", it)
                    eventChannel.send(messageFor(it, "تعذر تسوية حساب العميل"))
                }
        }
    }

    fun settleSupplierBalance(supplierId: String, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("تسوية حساب المورد للمدير فقط")
                val actor = _session.value
                repository.settleSupplierBalance(operationId, supplierId, note, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تمت تسوية حساب المورد مع الاحتفاظ بالسجل التاريخي") }
                .onFailure {
                    logError("SettleSupplier", it)
                    eventChannel.send(messageFor(it, "تعذر تسوية حساب المورد"))
                }
        }
    }

    fun adjustCustomerBalance(customerId: String, newBalance: Long, reason: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("تصحيح الرصيد بصلاحية المدير فقط")
                val actor = _session.value
                repository.adjustCustomerBalance(operationId, customerId, newBalance, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم تصحيح رصيد العميل وتوثيق السبب") }
                .onFailure {
                    logError("AdjustCustomerBalance", it)
                    eventChannel.send(messageFor(it, "تعذر تصحيح الرصيد"))
                }
        }
    }

    fun adjustSupplierBalance(supplierId: String, newBalance: Long, reason: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("تصحيح الرصيد بصلاحية المدير فقط")
                val actor = _session.value
                repository.adjustSupplierBalance(operationId, supplierId, newBalance, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم تصحيح رصيد المورد وتوثيق السبب") }
                .onFailure {
                    logError("AdjustSupplierBalance", it)
                    eventChannel.send(messageFor(it, "تعذر تصحيح الرصيد"))
                }
        }
    }

    fun addCustomerDebit(customerId: String, amount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إضافة الديون اليدوية للمدير فقط")
                val actor = _session.value
                repository.addCustomerDebit(operationId, customerId, amount, note, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تمت إضافة الدين وتحديث رصيد العميل") }
                .onFailure {
                    logError("AddCustomerDebit", it)
                    eventChannel.send(messageFor(it, "تعذر إضافة الدين"))
                }
        }
    }

    fun addSupplierDebit(supplierId: String, amount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إضافة المستحقات اليدوية للمدير فقط")
                val actor = _session.value
                repository.addSupplierDebit(operationId, supplierId, amount, note, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تمت إضافة المستحق وتحديث رصيد المورد") }
                .onFailure {
                    logError("AddSupplierDebit", it)
                    eventChannel.send(messageFor(it, "تعذر إضافة المستحق"))
                }
        }
    }

    fun receiveCustomerPayment(customerId: String, amount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_RECEIVE_PAYMENT, "لا تملك صلاحية تسجيل القبض")
                val actor = _session.value
                repository.receiveCustomerPayment(
                    operationId = operationId,
                    customerId = customerId,
                    amount = amount,
                    paymentMethod = "CASH",
                    note = note,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم تسجيل قبض العميل وتحديث الصندوق") }
                .onFailure {
                    logError("ReceiveCustomerPayment", it)
                    eventChannel.send(messageFor(it, "تعذر تسجيل القبض"))
                }
        }
    }

    fun paySupplier(supplierId: String, amount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("دفع الموردين للمدير فقط")
                val actor = _session.value
                repository.paySupplier(
                    operationId = operationId,
                    supplierId = supplierId,
                    amount = amount,
                    paymentMethod = "CASH",
                    note = note,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم تسجيل دفع المورد وتحديث الصندوق") }
                .onFailure {
                    logError("PaySupplier", it)
                    eventChannel.send(messageFor(it, "تعذر تسجيل الدفع"))
                }
        }
    }

    fun recordExpense(category: String, amount: Long, description: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("تسجيل المصروفات للمدير فقط")
                val actor = _session.value
                repository.recordExpense(
                    operationId = operationId,
                    category = category,
                    amount = amount,
                    paymentMethod = "CASH",
                    description = description,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم تسجيل المصروف وتحديث الصندوق") }
                .onFailure {
                    logError("RecordExpense", it)
                    eventChannel.send(messageFor(it, "تعذر تسجيل المصروف"))
                }
        }
    }

    fun updateExpense(expenseId: String, category: String, amount: Long, description: String, reason: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("تعديل المصروفات للمدير فقط")
                val actor = _session.value
                repository.updateExpense(expenseId, category, amount, description, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم تعديل المصروف وتحديث الصندوق") }
                .onFailure {
                    logError("UpdateExpense", it)
                    eventChannel.send(messageFor(it, "تعذر تعديل المصروف"))
                }
        }
    }

    fun recordTransfer(
        recipient: String,
        phone: String,
        amount: Long,
        fees: Long,
        direction: String,
        note: String,
        operationId: String,
        senderName: String = "",
        status: String = "COMPLETED"
    ) {
        viewModelScope.launch {
            runCatching {
                requireManager("تسجيل الحوالات للمدير فقط")
                repository.recordTransfer(
                    operationId = operationId,
                    recipientName = recipient,
                    phone = phone,
                    direction = direction,
                    amount = amount,
                    fees = fees,
                    paymentMethod = "CASH",
                    status = status,
                    note = note,
                    senderName = senderName
                )
            }.onSuccess { eventChannel.send("تم تسجيل الحوالة وتحديث الصندوق") }
                .onFailure {
                    logError("RecordTransfer", it)
                    eventChannel.send(messageFor(it, "تعذر تسجيل الحوالة"))
                }
        }
    }

    fun updateTransferStatus(transferId: String, status: String, note: String?) {
        viewModelScope.launch {
            runCatching {
                requireManager("تعديل حالة الحوالة للمدير فقط")
                check(database.transfers().updateStatus(transferId, status, note) == 1)
            }.onSuccess { eventChannel.send("تم تحديث حالة الحوالة") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تحديث حالة الحوالة")) }
        }
    }

    fun reconcileCashDrawer(actualCount: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("جرد ومطابقة الصندوق للمدير فقط")
                val actor = _session.value
                repository.reconcileCashDrawer(operationId, actualCount, note, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { rec ->
                eventChannel.send(
                    if (rec.difference == 0L) "الصندوق مطابق للرصيد الدفتري"
                    else "تم تسجيل جرد الصندوق وتسوية الفرق (${rec.difference})"
                )
            }.onFailure {
                logError("CashReconcile", it)
                eventChannel.send(messageFor(it, "تعذر حفظ جرد الصندوق"))
            }
        }
    }

    fun addNote(title: String, body: String, category: String = "عام") {
        viewModelScope.launch {
            runCatching {
                require(title.isNotBlank() || body.isNotBlank()) { "اكتب عنوان الملاحظة أو محتواها" }
                val now = System.currentTimeMillis()
                database.references().insertNote(
                    NoteEntity(UUID.randomUUID().toString(), title.trim(), body.trim(), false, now, now, category.ifBlank { "عام" }, false)
                )
            }.onSuccess { eventChannel.send("تم حفظ الملاحظة") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر حفظ الملاحظة")) }
        }
    }

    fun updateNote(note: NoteEntity, title: String, body: String, category: String) {
        viewModelScope.launch {
            runCatching {
                require(title.isNotBlank() || body.isNotBlank()) { "اكتب عنوان الملاحظة أو محتواها" }
                database.references().updateNote(
                    note.copy(
                        title = title.trim(),
                        body = body.trim(),
                        category = category.ifBlank { "عام" },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }.onSuccess { eventChannel.send("تم تعديل الملاحظة") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تعديل الملاحظة")) }
        }
    }

    fun toggleNotePin(note: NoteEntity) {
        viewModelScope.launch {
            database.references().updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleNoteArchive(note: NoteEntity) {
        viewModelScope.launch {
            database.references().setNoteArchived(note.id, !note.isArchived, System.currentTimeMillis())
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            database.references().deleteNote(id)
            eventChannel.send("تم حذف الملاحظة")
        }
    }

    fun createAdminUser(displayName: String, pin: String) {
        viewModelScope.launch {
            runCatching {
                require(database.references().observeUsers().first().isEmpty()) { "تم إعداد مستخدم مدير مسبقاً" }
                require(displayName.isNotBlank()) { "اكتب اسم المدير" }
                val credential = PinHasher.create(pin.toCharArray())
                val user = UserEntity(
                    id = UUID.randomUUID().toString(),
                    username = "admin",
                    displayName = displayName.trim(),
                    pinSalt = credential.saltBase64,
                    pinHash = credential.hashBase64,
                    role = UserRoles.MANAGER,
                    isActive = true,
                    createdAt = System.currentTimeMillis()
                )
                database.references().insertUser(user)
                _session.value = user
                sessionManager?.saveSession(user, "dashboard", configuredTimeoutHours())
            }.onSuccess { eventChannel.send("تم إعداد حساب المدير") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر إعداد حساب المدير")) }
        }
    }

    fun login(username: String, pin: String) {
        viewModelScope.launch {
            val user = database.references().findUser(username.trim())
            val valid = user != null && user.isActive &&
                PinHasher.verify(pin.toCharArray(), PinCredential(user.pinSalt, user.pinHash))
            if (valid) {
                _session.value = user
                _restoredRoute.value = "dashboard"
                sessionManager?.saveSession(user, "dashboard", configuredTimeoutHours())
                eventChannel.send("مرحباً ${user.displayName}")
            } else {
                eventChannel.send("اسم المستخدم أو PIN غير صحيح")
            }
        }
    }

    fun switchUser(username: String, pin: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val user = database.references().findUser(username.trim())
            val valid = user != null && user.isActive &&
                PinHasher.verify(pin.toCharArray(), PinCredential(user.pinSalt, user.pinHash))
            if (valid) {
                _session.value = user
                _restoredRoute.value = "dashboard"
                sessionManager?.saveSession(user, "dashboard", configuredTimeoutHours())
                eventChannel.send("تم تبديل الحساب إلى ${user.displayName}")
                onResult(true)
            } else {
                eventChannel.send("بيانات التحقق للحساب الجديد غير صحيحة")
                onResult(false)
            }
        }
    }

    fun verifyCurrentUserPin(pin: String): Boolean {
        val user = _session.value ?: return false
        return PinHasher.verify(pin.toCharArray(), PinCredential(user.pinSalt, user.pinHash))
    }

    fun saveLastRoute(route: String) {
        if (route.isBlank() || route == "login" || route == "setup" || _session.value == null) return
        _restoredRoute.value = route
        viewModelScope.launch {
            runCatching { sessionManager?.updateLastRoute(route, configuredTimeoutHours()) }
        }
    }

    fun logout() {
        _session.value = null
        _restoredRoute.value = "dashboard"
        sessionManager?.clearSession()
    }

    fun addUser(
        displayName: String,
        username: String,
        pin: String,
        role: String = UserRoles.CASHIER,
        permissions: Set<String>? = null
    ) {
        viewModelScope.launch {
            runCatching {
                requireManager("هذه الصلاحية للمدير فقط")
                require(displayName.isNotBlank() && username.isNotBlank()) { "أدخل اسم المستخدم والاسم الظاهر" }
                require(role in listOf(UserRoles.MANAGER, UserRoles.CASHIER, UserRoles.LIMITED)) { "الدور غير صالح" }
                val credential = PinHasher.create(pin.toCharArray())
                val permCsv = permissions?.takeIf { it.isNotEmpty() }?.joinToString(",")
                val user = UserEntity(
                    id = UUID.randomUUID().toString(),
                    username = username.trim(),
                    displayName = displayName.trim(),
                    pinSalt = credential.saltBase64,
                    pinHash = credential.hashBase64,
                    role = role,
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    permissions = permCsv
                )
                database.references().insertUser(user)
            }.onSuccess { eventChannel.send("تم إنشاء حساب المستخدم") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر إنشاء حساب المستخدم")) }
        }
    }

    fun updateUserAccess(userId: String, role: String, isActive: Boolean, permissions: Set<String>? = null) {
        viewModelScope.launch {
            runCatching {
                requireManager("تعديل الصلاحيات للمدير فقط")
                require(_session.value?.id != userId || (isActive && role == UserRoles.MANAGER)) {
                    "لا يمكنك تعطيل أو خفض صلاحية حسابك الحالي"
                }
                val permCsv = permissions?.takeIf { it.isNotEmpty() }?.joinToString(",")
                check(database.references().updateUserAccessAndPermissions(userId, role, permCsv, isActive) == 1)
                if (_session.value?.id == userId) {
                    database.references().findUserById(userId)?.let { _session.value = it }
                }
            }.onSuccess { eventChannel.send("تم تحديث صلاحية المستخدم") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تحديث صلاحية المستخدم")) }
        }
    }

    fun salesInvoice(id: String) = database.salesInvoices().observeById(id)
    fun salesInvoiceLines(id: String) = database.salesInvoices().observeLines(id)
    fun salesInvoicePayments(id: String) = database.invoicePayments().observeForInvoice("SALE", id)
    fun salesInvoiceReturns(id: String) = database.returns().observeForInvoice(id)
    fun customerSalesInvoices(customerId: String) = database.salesInvoices().observeForCustomer(customerId)

    fun purchaseInvoice(id: String) = database.purchaseInvoices().observeById(id)
    fun purchaseInvoiceLines(id: String) = database.purchaseInvoices().observeLines(id)
    fun purchaseInvoicePayments(id: String) = database.invoicePayments().observeForInvoice("PURCHASE", id)
    fun purchaseInvoiceReturns(id: String) = database.returns().observeForInvoice(id)
    fun supplierPurchaseInvoices(supplierId: String) = database.purchaseInvoices().observeForSupplier(supplierId)

    fun returnSale(invoiceId: String, lines: List<ReturnLineInput>, note: String, operationId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                requireManager("تسجيل المرتجعات للمدير فقط")
                val actor = _session.value
                repository.returnSale(operationId, invoiceId, lines, note, actorId = actor?.id, actorName = actor?.displayName)
            }
                .onSuccess { eventChannel.send("تم حفظ مرتجع البيع بقيمة ${it.total}"); onComplete(true) }
                .onFailure {
                    logError("ReturnSale", it)
                    eventChannel.send(messageFor(it, "تعذر حفظ مرتجع البيع"))
                    onComplete(false)
                }
        }
    }

    fun returnPurchase(invoiceId: String, lines: List<ReturnLineInput>, note: String, operationId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                requireManager("تسجيل مرتجعات الشراء للمدير فقط")
                val actor = _session.value
                repository.returnPurchase(operationId, invoiceId, lines, note, actorId = actor?.id, actorName = actor?.displayName)
            }
                .onSuccess { eventChannel.send("تم حفظ مرتجع الشراء بقيمة ${it.total}"); onComplete(true) }
                .onFailure {
                    logError("ReturnPurchase", it)
                    eventChannel.send(messageFor(it, "تعذر حفظ مرتجع الشراء"))
                    onComplete(false)
                }
        }
    }

    fun adjustStock(itemId: String, newQuantityMilli: Long, note: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("تسوية المخزون للمدير فقط")
                val actor = _session.value
                repository.adjustStock(operationId, itemId, newQuantityMilli, note, actorId = actor?.id, actorName = actor?.displayName)
            }
                .onSuccess { eventChannel.send("تمت تسوية المخزون وتسجيل الحركة") }
                .onFailure {
                    logError("AdjustStock", it)
                    eventChannel.send(messageFor(it, "تعذر تعديل المخزون"))
                }
        }
    }

    fun itemMovements(itemId: String) = database.stock().observeMovements(itemId)
    fun customerTransactions(customerId: String) = database.ledger().observeCustomer(customerId)
    fun supplierTransactions(supplierId: String) = database.ledger().observeSupplier(supplierId)
    fun entityAuditLogs(entityId: String) = database.references().observeActivityForEntity(entityId)
    fun journalLinesForEntry(entryId: String) = database.journal().observeLinesForEntry(entryId)

    fun voidPurchaseInvoice(invoiceId: String, reason: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إلغاء فواتير الشراء للمدير فقط")
                val actor = _session.value
                accounting.voidPurchaseInvoice(operationId, invoiceId, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم إلغاء فاتورة الشراء وعكس آثارها المالية والمخزنية") }
                .onFailure {
                    logError("VoidPurchase", it)
                    eventChannel.send(messageFor(it, "تعذر إلغاء فاتورة الشراء"))
                }
        }
    }

    fun updateInvoicePayment(paymentId: String, newAmount: Long, newMethod: String, newNote: String?, reason: String) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_EDIT_OPERATIONS, "لا تملك صلاحية تعديل الدفعات")
                val actor = _session.value
                accounting.updateInvoicePayment(
                    paymentId = paymentId,
                    newAmount = newAmount,
                    newMethod = newMethod,
                    newNote = newNote,
                    reason = reason,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم تعديل الدفعة وإعادة احتساب المتبقي والأرصدة") }
                .onFailure {
                    logError("UpdateInvoicePayment", it)
                    eventChannel.send(messageFor(it, "تعذر تعديل الدفعة"))
                }
        }
    }

    fun updateCustomerTransaction(txId: String, newAmount: Long, newNote: String?, reason: String) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_EDIT_OPERATIONS, "لا تملك صلاحية تعديل السندات")
                val actor = _session.value
                accounting.updateCustomerTransaction(txId, newAmount, newNote, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم تعديل العملية وإعادة حساب رصيد العميل") }
                .onFailure {
                    logError("UpdateCustomerTx", it)
                    eventChannel.send(messageFor(it, "تعذر تعديل العملية"))
                }
        }
    }

    fun reverseCustomerTransaction(txId: String, reason: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("العكس المحاسبي للمدير فقط")
                val actor = _session.value
                accounting.reverseCustomerTransaction(txId, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم عكس العملية محاسبياً وإعادة حساب الرصيد") }
                .onFailure {
                    logError("ReverseCustomerTx", it)
                    eventChannel.send(messageFor(it, "تعذر عكس العملية"))
                }
        }
    }

    fun updateSupplierTransaction(txId: String, newAmount: Long, newNote: String?, reason: String) {
        viewModelScope.launch {
            runCatching {
                requirePermission(UserPermissions.CAN_EDIT_OPERATIONS, "لا تملك صلاحية تعديل السندات")
                val actor = _session.value
                accounting.updateSupplierTransaction(txId, newAmount, newNote, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم تعديل العملية وإعادة حساب رصيد المورد") }
                .onFailure {
                    logError("UpdateSupplierTx", it)
                    eventChannel.send(messageFor(it, "تعذر تعديل العملية"))
                }
        }
    }

    fun reverseSupplierTransaction(txId: String, reason: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("العكس المحاسبي للمدير فقط")
                val actor = _session.value
                accounting.reverseSupplierTransaction(txId, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم عكس العملية محاسبياً وإعادة حساب الرصيد") }
                .onFailure {
                    logError("ReverseSupplierTx", it)
                    eventChannel.send(messageFor(it, "تعذر عكس العملية"))
                }
        }
    }

    fun reverseExpense(expenseId: String, reason: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("عكس المصروفات للمدير فقط")
                val actor = _session.value
                accounting.reverseExpense(expenseId, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم عكس المصروف وإعادة أثره للصندوق") }
                .onFailure {
                    logError("ReverseExpense", it)
                    eventChannel.send(messageFor(it, "تعذر عكس المصروف"))
                }
        }
    }

    fun updateTransfer(
        transferId: String,
        senderName: String?,
        recipientName: String,
        phone: String,
        direction: String,
        amount: Long,
        fees: Long,
        status: String,
        note: String?,
        reason: String
    ) {
        viewModelScope.launch {
            runCatching {
                requireManager("تعديل الحوالات للمدير فقط")
                val actor = _session.value
                accounting.updateTransfer(
                    transferId, senderName, recipientName, phone, direction, amount, fees, status, note, reason,
                    actorId = actor?.id, actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم تحديث الحوالة وتسوية الصندوق") }
                .onFailure {
                    logError("UpdateTransfer", it)
                    eventChannel.send(messageFor(it, "تعذر تعديل الحوالة"))
                }
        }
    }

    fun createManualJournalEntry(
        entryType: String,
        description: String,
        lines: List<JournalLineInput>,
        notes: String?,
        operationId: String
    ) {
        viewModelScope.launch {
            runCatching {
                requireManager("إنشاء القيود المحاسبية للمدير فقط")
                val actor = _session.value
                accounting.createManualJournalEntry(
                    operationId = operationId,
                    entryType = entryType,
                    description = description,
                    lines = lines,
                    notes = notes,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم ترحيل القيد المحاسبي #${it.entryNumber}") }
                .onFailure {
                    logError("CreateJournal", it)
                    eventChannel.send(messageFor(it, "تعذر إنشاء القيد المحاسبي"))
                }
        }
    }

    fun updateJournalEntry(
        entryId: String,
        entryType: String,
        description: String,
        lines: List<JournalLineInput>,
        notes: String?,
        reason: String
    ) {
        viewModelScope.launch {
            runCatching {
                requireManager("تعديل القيود المحاسبية للمدير فقط")
                val actor = _session.value
                accounting.updateJournalEntry(
                    entryId = entryId,
                    entryType = entryType,
                    description = description,
                    lines = lines,
                    notes = notes,
                    reason = reason,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم تحديث القيد المحاسبي #${it.entryNumber}") }
                .onFailure {
                    logError("UpdateJournal", it)
                    eventChannel.send(messageFor(it, "تعذر تعديل القيد المحاسبي"))
                }
        }
    }

    fun reverseJournalEntry(entryId: String, reason: String, operationId: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("عكس القيود المحاسبية للمدير فقط")
                val actor = _session.value
                accounting.reverseJournalEntry(
                    operationId = operationId,
                    entryId = entryId,
                    reason = reason,
                    actorId = actor?.id,
                    actorName = actor?.displayName
                )
            }.onSuccess { eventChannel.send("تم إنشاء القيد العكسي #${it.entryNumber}") }
                .onFailure {
                    logError("ReverseJournal", it)
                    eventChannel.send(messageFor(it, "تعذر عكس القيد المحاسبي"))
                }
        }
    }

    fun closeAccountingPeriod(name: String, startAt: Long, endAt: Long) {
        viewModelScope.launch {
            runCatching {
                requireManager("إغلاق الفترات المحاسبية للمدير فقط")
                val actor = _session.value
                accounting.closeAccountingPeriod(name, startAt, endAt, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send("تم إغلاق الفترة المحاسبية: ${it.name}") }
                .onFailure {
                    logError("ClosePeriod", it)
                    eventChannel.send(messageFor(it, "تعذر إغلاق الفترة المحاسبية"))
                }
        }
    }

    fun toggleAccountingPeriodClosed(period: AccountingPeriodEntity, closed: Boolean, reason: String) {
        viewModelScope.launch {
            runCatching {
                requireManager("إدارة الفترات المحاسبية للمدير فقط")
                val actor = _session.value
                accounting.toggleAccountingPeriodClosed(period, closed, reason, actorId = actor?.id, actorName = actor?.displayName)
            }.onSuccess { eventChannel.send(if (closed) "تم إغلاق الفترة" else "تمت إعادة فتح الفترة") }
                .onFailure { eventChannel.send(messageFor(it, "تعذر تحديث حالة الفترة")) }
        }
    }

    fun clearErrorLogs() {
        viewModelScope.launch {
            runCatching {
                requireManager("مسح سجل الأخطاء للمدير فقط")
                database.references().clearErrorLogs()
            }.onSuccess { eventChannel.send("تم مسح سجل التشخيص") }
        }
    }

    private fun configuredTimeoutHours(): Int =
        storeSettings.value.firstOrNull { it.key == "session_timeout_hours" }?.value?.toIntOrNull()
            ?: SessionManager.DEFAULT_TIMEOUT_HOURS

    private fun requireManager(message: String) {
        require(_session.value?.role == UserRoles.MANAGER) { message }
    }

    private fun requirePermission(permission: String, message: String) {
        val user = _session.value ?: throw IllegalArgumentException("يجب تسجيل الدخول أولاً")
        require(user.hasPermission(permission)) { message }
    }

    private fun logError(tag: String, error: Throwable) {
        val safeMsg = (error.message ?: error.javaClass.simpleName)
            .replace(Regex("pin|passphrase|password", RegexOption.IGNORE_CASE), "[REDACTED]")
        viewModelScope.launch {
            runCatching {
                database.references().insertErrorLog(
                    AppErrorLogEntity(
                        id = UUID.randomUUID().toString(),
                        tag = tag,
                        message = safeMsg.take(240),
                        details = error.javaClass.simpleName,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun reportRange(period: ReportPeriod): Pair<Long, Long> {
        if (period == ReportPeriod.ALL) return 0L to Long.MAX_VALUE
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        when (period) {
            ReportPeriod.TODAY, ReportPeriod.CUSTOM -> Unit
            ReportPeriod.WEEK -> {
                val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
                calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
            }
            ReportPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            ReportPeriod.ALL -> Unit
        }
        return calendar.timeInMillis to (System.currentTimeMillis() + 1L)
    }

    private fun todayRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return start to calendar.timeInMillis
    }

    private fun normalizeWholeText(raw: String): String = raw.map { ch ->
        when (ch) {
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            else -> ch
        }
    }.joinToString("").replace(",", "").replace("٬", "").trim()

    private fun normalizeDecimalText(raw: String): String =
        normalizeWholeText(raw).replace("٫", ".")

    private fun parseMilliSafe(normalized: String, errorMsg: String): Long {
        val bd = normalized.toBigDecimalOrNull() ?: error(errorMsg)
        return runCatching {
            bd.multiply(BigDecimal("1000"))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrElse { error(errorMsg) }
    }

    private fun messageFor(error: Throwable, fallback: String): String =
        error.message?.takeIf { it.isNotBlank() } ?: fallback

    class Factory(
        private val database: GroceryDatabase,
        private val repository: GroceryRepository,
        private val sessionManager: SessionManager? = null
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GroceryViewModel::class.java))
            return GroceryViewModel(database, repository, sessionManager) as T
        }
    }
}
