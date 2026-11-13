package com.saleh.alazzi.grocery.data.repository

import androidx.room.withTransaction
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.local.entity.*
import com.saleh.alazzi.grocery.domain.money.MoneyMath
import java.util.UUID
import kotlin.math.abs

object ChartAccounts {
    const val CODE_CASH = "1010"
    const val NAME_CASH = "الصندوق"
    const val CODE_BANK_WALLET = "1020"
    const val NAME_BANK_WALLET = "الحساب البنكي / المحفظة"
    const val CODE_TRANSFERS = "1030"
    const val NAME_TRANSFERS = "حساب الحوالات والعمولات"
    const val CODE_RECEIVABLE = "1110"
    const val NAME_RECEIVABLE = "العملاء (الذمم المدينة)"
    const val CODE_INVENTORY = "1210"
    const val NAME_INVENTORY = "المخزون"
    const val CODE_PAYABLE = "2010"
    const val NAME_PAYABLE = "الموردون (الذمم الدائنة)"
    const val CODE_EQUITY_ADJ = "3090"
    const val NAME_EQUITY_ADJ = "تسويات وفروقات"
    const val CODE_SALES = "4010"
    const val NAME_SALES = "المبيعات"
    const val CODE_SALES_RETURNS = "4020"
    const val NAME_SALES_RETURNS = "مردودات المبيعات"
    const val CODE_PURCHASES = "5010"
    const val NAME_PURCHASES = "المشتريات"
    const val CODE_EXPENSES = "5110"
    const val NAME_EXPENSES = "المصروفات التشغيلية"

    fun accountForMethod(method: String): Pair<String, String> =
        if (method == "CASH") CODE_CASH to NAME_CASH else CODE_BANK_WALLET to NAME_BANK_WALLET

    val selectableAccounts: List<Pair<String, String>> = listOf(
        CODE_CASH to NAME_CASH,
        CODE_BANK_WALLET to NAME_BANK_WALLET,
        CODE_TRANSFERS to NAME_TRANSFERS,
        CODE_RECEIVABLE to NAME_RECEIVABLE,
        CODE_INVENTORY to NAME_INVENTORY,
        CODE_PAYABLE to NAME_PAYABLE,
        CODE_EQUITY_ADJ to NAME_EQUITY_ADJ,
        CODE_SALES to NAME_SALES,
        CODE_SALES_RETURNS to NAME_SALES_RETURNS,
        CODE_PURCHASES to NAME_PURCHASES,
        CODE_EXPENSES to NAME_EXPENSES
    )
}

data class JournalLineInput(
    val accountCode: String,
    val accountName: String,
    val debit: Long,
    val credit: Long,
    val memo: String? = null
)

data class EditInvoiceInput(
    val invoiceId: String,
    val customerOrSupplierId: String?,
    val lines: List<InvoiceLineInput>,
    val discount: Long,
    val paid: Long,
    val paymentMethod: String,
    val notes: String?,
    val reason: String,
    val now: Long = System.currentTimeMillis(),
    val actorId: String? = null,
    val actorName: String? = null
)

class AccountingRepository(private val database: GroceryDatabase) {

    suspend fun requirePeriodOpen(timestamp: Long) {
        val closed = database.journal().findClosedPeriodAt(timestamp)
        if (closed != null) {
            throw IllegalStateException("الفترة المحاسبية (${closed.name}) مغلقة ولا يمكن الإضافة أو التعديل ضمنها")
        }
    }

    suspend fun postAutoJournal(
        operationId: String,
        entryType: String,
        description: String,
        sourceType: String,
        sourceId: String?,
        lines: List<JournalLineInput>,
        createdAt: Long,
        actorId: String?,
        actorName: String?,
        notes: String? = null
    ): JournalEntryEntity? {
        if (lines.isEmpty()) return null
        database.journal().findByOperationId(operationId)?.let { return it }
        val totalDebit = lines.sumOf { it.debit }
        val totalCredit = lines.sumOf { it.credit }
        require(totalDebit == totalCredit) { "القيد المحاسبي التلقائي غير متوازن ($totalDebit != $totalCredit)" }
        if (totalDebit <= 0L) return null

        val entryNumber = (database.journal().maxEntryNumber() + 1L).toString().padStart(6, '0')
        val entryId = UUID.randomUUID().toString()
        val debitNames = lines.filter { it.debit > 0 }.joinToString(" + ") { it.accountName }.ifBlank { "-" }
        val creditNames = lines.filter { it.credit > 0 }.joinToString(" + ") { it.accountName }.ifBlank { "-" }
        val entry = JournalEntryEntity(
            id = entryId,
            entryNumber = entryNumber,
            operationId = operationId,
            entryType = entryType,
            debitAccount = debitNames,
            creditAccount = creditNames,
            amount = totalDebit,
            description = description,
            sourceType = sourceType,
            sourceId = sourceId,
            createdByUserId = actorId,
            createdByUserName = actorName,
            status = "POSTED",
            notes = notes,
            createdAt = createdAt
        )
        database.journal().insertEntry(entry)
        database.journal().insertLines(
            lines.map { line ->
                JournalEntryLineEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = entryId,
                    accountCode = line.accountCode,
                    accountName = line.accountName,
                    debit = line.debit,
                    credit = line.credit,
                    memo = line.memo
                )
            }
        )
        return entry
    }

    suspend fun createManualJournalEntry(
        operationId: String,
        entryType: String,
        description: String,
        lines: List<JournalLineInput>,
        notes: String? = null,
        createdAt: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ): JournalEntryEntity = database.withTransaction {
        require(operationId.isNotBlank() && description.isNotBlank()) { "وصف القيد مطلوب" }
        requirePeriodOpen(createdAt)
        database.journal().findByOperationId(operationId)?.let { return@withTransaction it }
        validateJournalLines(lines)
        val entry = postAutoJournal(
            operationId = operationId,
            entryType = entryType.ifBlank { "MANUAL" },
            description = description.trim(),
            sourceType = "MANUAL_JOURNAL",
            sourceId = null,
            lines = lines,
            createdAt = createdAt,
            actorId = actorId,
            actorName = actorName,
            notes = notes?.trim()?.ifBlank { null }
        ) ?: error("تعذر إنشاء القيد المحاسبي")

        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "CREATE_JOURNAL_ENTRY",
                entityType = "JOURNAL_ENTRY",
                entityId = entry.id,
                details = "إنشاء قيد محاسبي #${entry.entryNumber}: ${entry.description}",
                createdAt = createdAt,
                actorName = actorName,
                newValue = entry.amount.toString(),
                reason = notes
            )
        )
        entry
    }

    suspend fun updateJournalEntry(
        entryId: String,
        entryType: String,
        description: String,
        lines: List<JournalLineInput>,
        notes: String? = null,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ): JournalEntryEntity = database.withTransaction {
        require(description.isNotBlank() && reason.isNotBlank()) { "الوصف وسبب التعديل مطلوبان" }
        val existing = database.journal().findById(entryId) ?: error("القيد المحاسبي غير موجود")
        require(existing.status != "REVERSED") { "لا يمكن تعديل قيد معكوس" }
        requirePeriodOpen(existing.createdAt)
        requirePeriodOpen(now)
        validateJournalLines(lines)

        val totalDebit = lines.sumOf { it.debit }
        val debitNames = lines.filter { it.debit > 0 }.joinToString(" + ") { it.accountName }
        val creditNames = lines.filter { it.credit > 0 }.joinToString(" + ") { it.accountName }
        val updated = existing.copy(
            entryType = entryType.ifBlank { existing.entryType },
            debitAccount = debitNames,
            creditAccount = creditNames,
            amount = totalDebit,
            description = description.trim(),
            notes = notes?.trim()?.ifBlank { null },
            updatedByUserId = actorId,
            updatedByUserName = actorName,
            updatedAt = now
        )
        check(database.journal().updateEntry(updated) == 1)
        database.journal().deleteLinesForEntry(entryId)
        database.journal().insertLines(
            lines.map { line ->
                JournalEntryLineEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = entryId,
                    accountCode = line.accountCode,
                    accountName = line.accountName,
                    debit = line.debit,
                    credit = line.credit,
                    memo = line.memo
                )
            }
        )
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "UPDATE_JOURNAL_ENTRY",
                entityType = "JOURNAL_ENTRY",
                entityId = entryId,
                details = "تعديل القيد #${existing.entryNumber}",
                createdAt = now,
                actorName = actorName,
                previousValue = "${existing.amount} (${existing.debitAccount} -> ${existing.creditAccount})",
                newValue = "$totalDebit ($debitNames -> $creditNames)",
                reason = reason
            )
        )
        updated
    }

    suspend fun reverseJournalEntry(
        operationId: String,
        entryId: String,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ): JournalEntryEntity = database.withTransaction {
        require(operationId.isNotBlank() && reason.isNotBlank()) { "سبب العكس المحاسبي مطلوب" }
        val existing = database.journal().findById(entryId) ?: error("القيد المحاسبي غير موجود")
        require(existing.status != "REVERSED") { "القيد معكوس بالفعل" }
        requirePeriodOpen(existing.createdAt)
        requirePeriodOpen(now)

        val oldLines = database.journal().readLinesForEntry(entryId)
        val reversedLines = if (oldLines.isNotEmpty()) {
            oldLines.map { line ->
                JournalLineInput(
                    accountCode = line.accountCode,
                    accountName = line.accountName,
                    debit = line.credit,
                    credit = line.debit,
                    memo = "عكس: ${line.memo ?: existing.description}"
                )
            }
        } else {
            listOf(
                JournalLineInput(ChartAccounts.CODE_EQUITY_ADJ, existing.creditAccount, existing.amount, 0L, "عكس"),
                JournalLineInput(ChartAccounts.CODE_EQUITY_ADJ, existing.debitAccount, 0L, existing.amount, "عكس")
            )
        }

        val entryNumber = (database.journal().maxEntryNumber() + 1L).toString().padStart(6, '0')
        val reversalId = UUID.randomUUID().toString()
        val reversal = JournalEntryEntity(
            id = reversalId,
            entryNumber = entryNumber,
            operationId = operationId,
            entryType = "REVERSAL",
            debitAccount = existing.creditAccount,
            creditAccount = existing.debitAccount,
            amount = existing.amount,
            description = "قيد عكسي للقيد #${existing.entryNumber}: ${reason.trim()}",
            sourceType = "JOURNAL_REVERSAL",
            sourceId = existing.id,
            createdByUserId = actorId,
            createdByUserName = actorName,
            status = "POSTED",
            reversedEntryId = existing.id,
            notes = reason.trim(),
            createdAt = now
        )
        database.journal().insertEntry(reversal)
        database.journal().insertLines(
            reversedLines.map { line ->
                JournalEntryLineEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = reversalId,
                    accountCode = line.accountCode,
                    accountName = line.accountName,
                    debit = line.debit,
                    credit = line.credit,
                    memo = line.memo
                )
            }
        )
        check(
            database.journal().updateEntry(
                existing.copy(
                    status = "REVERSED",
                    reversalEntryId = reversalId,
                    updatedByUserId = actorId,
                    updatedByUserName = actorName,
                    updatedAt = now
                )
            ) == 1
        )
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "REVERSE_JOURNAL_ENTRY",
                entityType = "JOURNAL_ENTRY",
                entityId = existing.id,
                details = "عكس محاسبي للقيد #${existing.entryNumber} برقم #${reversal.entryNumber}",
                createdAt = now,
                actorName = actorName,
                previousValue = "POSTED (${existing.amount})",
                newValue = "REVERSED -> #$entryNumber",
                reason = reason.trim()
            )
        )
        reversal
    }

    suspend fun closeAccountingPeriod(
        name: String,
        startAt: Long,
        endAt: Long,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ): AccountingPeriodEntity = database.withTransaction {
        require(name.isNotBlank() && endAt >= startAt) { "بيانات الفترة المحاسبية غير صحيحة" }
        val period = AccountingPeriodEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            startAt = startAt,
            endAt = endAt,
            isClosed = true,
            closedByUserId = actorId,
            closedByUserName = actorName,
            closedAt = now
        )
        database.journal().insertPeriod(period)
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "CLOSE_PERIOD",
                entityType = "ACCOUNTING_PERIOD",
                entityId = period.id,
                details = "إغلاق الفترة المحاسبية: ${period.name}",
                createdAt = now,
                actorName = actorName
            )
        )
        period
    }

    suspend fun toggleAccountingPeriodClosed(
        period: AccountingPeriodEntity,
        closed: Boolean,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(reason.isNotBlank()) { "أدخل سبب تغيير حالة الفترة المحاسبية" }
        check(
            database.journal().updatePeriod(
                period.copy(
                    isClosed = closed,
                    closedByUserId = actorId,
                    closedByUserName = actorName,
                    closedAt = if (closed) now else null
                )
            ) == 1
        )
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = if (closed) "CLOSE_PERIOD" else "REOPEN_PERIOD",
                entityType = "ACCOUNTING_PERIOD",
                entityId = period.id,
                details = "${if (closed) "إغلاق" else "إعادة فتح"} الفترة: ${period.name}",
                createdAt = now,
                actorName = actorName,
                reason = reason.trim()
            )
        )
    }

    suspend fun editSalesInvoice(input: EditInvoiceInput): SaveInvoiceResult = database.withTransaction {
        require(input.reason.isNotBlank()) { "أدخل سبب تعديل الفاتورة" }
        require(input.lines.isNotEmpty()) { "أضف صنفاً واحداً على الأقل" }
        require(input.lines.map { it.itemId }.distinct().size == input.lines.size) { "ادمج الكميات المكررة للصنف نفسه" }
        require(input.paid >= 0) { "المبلغ المدفوع غير صحيح" }

        val existing = database.salesInvoices().findById(input.invoiceId) ?: error("الفاتورة غير موجودة")
        require(existing.status == "POSTED") { "لا يمكن تعديل فاتورة ملغاة" }
        requirePeriodOpen(existing.createdAt)
        requirePeriodOpen(input.now)
        require(database.returns().countForInvoice(existing.id) == 0) {
            "تحتوي الفاتورة على مرتجعات سابقة؛ قم بمراجعة المرتجعات قبل التعديل"
        }

        val oldLines = database.salesInvoices().lines(existing.id)
        val payments = database.invoicePayments().readForInvoiceSync("SALE", existing.id)
        val extraPaymentsTotal = payments.drop(1).sumOf { it.amount }
        val newInitialPaid = input.paid
        val newTotalPaid = Math.addExact(newInitialPaid, extraPaymentsTotal)

        val prepared = input.lines.map { line ->
            val item = database.items().findById(line.itemId) ?: error("الصنف غير موجود: ${line.itemId}")
            require(line.quantityMilli > 0) { "الكمية يجب أن تكون أكبر من صفر" }
            Triple(line, item, MoneyMath.lineTotal(line.quantityMilli, line.unitPrice))
        }
        val subtotal = prepared.fold(0L) { acc, row -> Math.addExact(acc, row.third) }
        val total = MoneyMath.total(listOf(subtotal), input.discount)
        require(newTotalPaid <= total) { "إجمالي المدفوع ($newTotalPaid) يتجاوز إجمالي الفاتورة المعدّل ($total)" }
        val remaining = Math.subtractExact(total, newTotalPaid)

        if (input.customerOrSupplierId == null) {
            require(remaining == 0L) { "الفاتورة الآجلة تتطلب اختيار عميل" }
        }
        val newCustomer = input.customerOrSupplierId?.let {
            database.customers().findById(it) ?: error("العميل غير موجود")
        }

        // Update stock cleanly via compensating movements so inventory audit invariant stays intact.
        val oldQtyByItem = oldLines.groupBy { it.itemId }.mapValues { (_, list) -> list.sumOf { it.quantityMilli } }
        val newQtyByItem = prepared.associate { (line, _, _) -> line.itemId to line.quantityMilli }
        val allItemIds = (oldQtyByItem.keys + newQtyByItem.keys).distinct()
        allItemIds.forEachIndexed { idx, itemId ->
            val oldQ = oldQtyByItem[itemId] ?: 0L
            val newQ = newQtyByItem[itemId] ?: 0L
            val deltaSold = newQ - oldQ // positive -> more sold -> deduct from stock
            if (deltaSold != 0L) {
                val item = database.items().findById(itemId) ?: error("الصنف غير موجود")
                val before = item.quantityMilli
                val after = before - deltaSold
                require(after >= 0L) { "المخزون غير كافٍ لتعديل كمية: ${item.name}" }
                check(database.items().setQuantity(item.id, after, input.now) == 1)
                database.stock().insertMovement(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "sale-edit:${existing.id}:${input.now}:$idx:${UUID.randomUUID()}",
                        itemId = item.id,
                        movementType = if (deltaSold > 0) "SALE_EDIT_OUT" else "SALE_EDIT_IN",
                        quantityInMilli = if (deltaSold < 0) abs(deltaSold) else 0L,
                        quantityOutMilli = if (deltaSold > 0) deltaSold else 0L,
                        balanceBeforeMilli = before,
                        balanceAfterMilli = after,
                        sourceType = "SALES_INVOICE_EDIT",
                        sourceId = existing.id,
                        note = "تعديل فاتورة بيع #${existing.invoiceNumber}: ${input.reason}",
                        createdAt = input.now
                    )
                )
            }
        }

        // Replace invoice header and lines while preserving invoiceNumber and createdAt.
        val oldCostByItem = oldLines.associate { it.itemId to it.unitCostSnapshot }
        val newLineEntities = prepared.map { (line, item, lineTotal) ->
            SalesInvoiceItemEntity(
                id = UUID.randomUUID().toString(),
                invoiceId = existing.id,
                itemId = item.id,
                itemNameSnapshot = item.name,
                quantityMilli = line.quantityMilli,
                unitPrice = line.unitPrice,
                unitCostSnapshot = oldCostByItem[item.id] ?: item.purchasePrice,
                lineTotal = lineTotal
            )
        }
        database.salesInvoices().deleteLines(existing.id)
        database.salesInvoices().insertLines(newLineEntities)
        val updatedInvoice = existing.copy(
            customerId = newCustomer?.id,
            subtotal = subtotal,
            discount = input.discount,
            total = total,
            paid = newTotalPaid,
            remaining = remaining,
            paymentMethod = input.paymentMethod,
            notes = input.notes?.trim()?.ifBlank { null },
            updatedByUserId = input.actorId,
            updatedByUserName = input.actorName,
            updatedAt = input.now
        )
        check(database.salesInvoices().update(updatedInvoice) == 1)

        // Update customer ledgers & recalculate chronological customer balances.
        val affectedCustomers = mutableSetOf<String>()
        existing.customerId?.let(affectedCustomers::add)
        newCustomer?.id?.let(affectedCustomers::add)

        val saleTx = database.ledger().findCustomerBySource("SALES_INVOICE", existing.id)
        val initPayTx = database.ledger().findCustomerBySource("SALES_INVOICE_PAYMENT", existing.id)
        if (newCustomer != null) {
            if (saleTx != null) {
                database.ledger().updateCustomer(
                    saleTx.copy(
                        customerId = newCustomer.id,
                        amount = total,
                        note = "فاتورة بيع رقم ${existing.invoiceNumber} (معدّلة)",
                        updatedByUserId = input.actorId,
                        updatedByUserName = input.actorName,
                        updatedAt = input.now
                    )
                )
            } else {
                database.ledger().insertCustomer(
                    CustomerTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "sale-edit-ledger:${existing.id}:${UUID.randomUUID()}",
                        customerId = newCustomer.id,
                        type = "SALE",
                        amount = total,
                        balanceAfter = 0L,
                        sourceType = "SALES_INVOICE",
                        sourceId = existing.id,
                        note = "فاتورة بيع رقم ${existing.invoiceNumber}",
                        createdAt = existing.createdAt,
                        createdByUserId = existing.createdByUserId,
                        createdByUserName = existing.createdByUserName,
                        updatedByUserId = input.actorId,
                        updatedByUserName = input.actorName,
                        updatedAt = input.now
                    )
                )
            }
            if (initPayTx != null) {
                database.ledger().updateCustomer(
                    initPayTx.copy(
                        customerId = newCustomer.id,
                        amount = newInitialPaid,
                        isReversed = newInitialPaid == 0L,
                        note = "دفعة مع فاتورة ${existing.invoiceNumber}",
                        updatedByUserId = input.actorId,
                        updatedByUserName = input.actorName,
                        updatedAt = input.now
                    )
                )
            } else if (newInitialPaid > 0L) {
                database.ledger().insertCustomer(
                    CustomerTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "sale-edit-pay-ledger:${existing.id}:${UUID.randomUUID()}",
                        customerId = newCustomer.id,
                        type = "PAYMENT",
                        amount = newInitialPaid,
                        balanceAfter = 0L,
                        sourceType = "SALES_INVOICE_PAYMENT",
                        sourceId = existing.id,
                        note = "دفعة مع فاتورة ${existing.invoiceNumber}",
                        createdAt = existing.createdAt,
                        updatedByUserId = input.actorId,
                        updatedByUserName = input.actorName,
                        updatedAt = input.now
                    )
                )
            }
        } else {
            if (saleTx != null) {
                database.ledger().updateCustomer(
                    saleTx.copy(isReversed = true, updatedByUserId = input.actorId, updatedByUserName = input.actorName, updatedAt = input.now)
                )
            }
            if (initPayTx != null) {
                database.ledger().updateCustomer(
                    initPayTx.copy(isReversed = true, updatedByUserId = input.actorId, updatedByUserName = input.actorName, updatedAt = input.now)
                )
            }
        }
        affectedCustomers.forEach { recalculateCustomerLedger(it, input.now) }

        // Update first payment record if present and record cash delta.
        val firstPay = payments.firstOrNull()
        val oldInitialPaid = firstPay?.amount ?: (existing.paid - extraPaymentsTotal).coerceAtLeast(0L)
        if (firstPay != null) {
            database.invoicePayments().update(
                firstPay.copy(
                    partyId = newCustomer?.id,
                    amount = newInitialPaid,
                    paymentMethod = input.paymentMethod,
                    updatedByUserId = input.actorId,
                    updatedByUserName = input.actorName,
                    updatedAt = input.now
                )
            )
        } else if (newInitialPaid > 0L) {
            database.invoicePayments().insert(
                InvoicePaymentEntity(
                    id = UUID.randomUUID().toString(),
                    operationId = "sale-edit-invpay:${existing.id}:${UUID.randomUUID()}",
                    invoiceType = "SALE",
                    invoiceId = existing.id,
                    partyId = newCustomer?.id,
                    amount = newInitialPaid,
                    paymentMethod = input.paymentMethod,
                    note = "دفعة مع الفاتورة #${existing.invoiceNumber}",
                    createdAt = existing.createdAt,
                    updatedByUserId = input.actorId,
                    updatedByUserName = input.actorName,
                    updatedAt = input.now
                )
            )
        }

        val cashDiff = newInitialPaid - oldInitialPaid
        if (cashDiff != 0L) {
            database.cash().insert(
                CashTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = "sale-edit-cash:${existing.id}:${input.now}:${UUID.randomUUID()}",
                    direction = if (cashDiff > 0) "IN" else "OUT",
                    amount = abs(cashDiff),
                    sourceType = "SALES_INVOICE_EDIT",
                    sourceId = existing.id,
                    paymentMethod = input.paymentMethod,
                    note = "تعديل مدفوع فاتورة بيع #${existing.invoiceNumber}: ${input.reason}",
                    createdAt = input.now,
                    createdByUserId = input.actorId,
                    createdByUserName = input.actorName
                )
            )
        }

        val totalDiff = total - existing.total
        if (totalDiff != 0L || cashDiff != 0L) {
            val (cashCode, cashName) = ChartAccounts.accountForMethod(input.paymentMethod)
            val adjAmount = maxOf(abs(totalDiff), abs(cashDiff))
            if (adjAmount > 0L) {
                postAutoJournal(
                    operationId = "journal:sale-edit:${existing.id}:${input.now}:${UUID.randomUUID()}",
                    entryType = "ADJUSTMENT",
                    description = "تعديل فاتورة بيع #${existing.invoiceNumber}: ${input.reason}",
                    sourceType = "SALES_INVOICE_EDIT",
                    sourceId = existing.id,
                    lines = listOf(
                        JournalLineInput(
                            if (cashDiff >= 0) cashCode else ChartAccounts.CODE_SALES,
                            if (cashDiff >= 0) cashName else ChartAccounts.NAME_SALES,
                            adjAmount,
                            0L,
                            input.reason
                        ),
                        JournalLineInput(
                            if (cashDiff >= 0) ChartAccounts.CODE_SALES else cashCode,
                            if (cashDiff >= 0) ChartAccounts.NAME_SALES else cashName,
                            0L,
                            adjAmount,
                            input.reason
                        )
                    ),
                    createdAt = input.now,
                    actorId = input.actorId,
                    actorName = input.actorName
                )
            }
        }

        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = input.actorId,
                action = "EDIT_SALES_INVOICE",
                entityType = "SALES_INVOICE",
                entityId = existing.id,
                details = "تعديل فاتورة بيع #${existing.invoiceNumber} من ${existing.total} إلى $total",
                createdAt = input.now,
                actorName = input.actorName,
                previousValue = "total=${existing.total}, paid=${existing.paid}",
                newValue = "total=$total, paid=$newTotalPaid",
                reason = input.reason
            )
        )
        SaveInvoiceResult(existing.id, existing.invoiceNumber, false)
    }

    suspend fun editPurchaseInvoice(input: EditInvoiceInput): SaveInvoiceResult = database.withTransaction {
        require(input.reason.isNotBlank()) { "أدخل سبب تعديل فاتورة الشراء" }
        require(input.lines.isNotEmpty()) { "أضف صنفاً واحداً على الأقل" }
        require(input.lines.map { it.itemId }.distinct().size == input.lines.size) { "ادمج الكميات المكررة للصنف نفسه" }
        require(input.paid >= 0) { "المبلغ المدفوع غير صحيح" }

        val existing = database.purchaseInvoices().findById(input.invoiceId) ?: error("فاتورة الشراء غير موجودة")
        require(existing.status == "POSTED") { "لا يمكن تعديل فاتورة ملغاة" }
        requirePeriodOpen(existing.createdAt)
        requirePeriodOpen(input.now)
        require(database.returns().countForInvoice(existing.id) == 0) {
            "تحتوي فاتورة الشراء على مرتجعات سابقة؛ قم بمراجعة المرتجعات قبل التعديل"
        }

        val oldLines = database.purchaseInvoices().lines(existing.id)
        val payments = database.invoicePayments().readForInvoiceSync("PURCHASE", existing.id)
        val extraPaymentsTotal = payments.drop(1).sumOf { it.amount }
        val newInitialPaid = input.paid
        val newTotalPaid = Math.addExact(newInitialPaid, extraPaymentsTotal)

        val prepared = input.lines.map { line ->
            val item = database.items().findById(line.itemId) ?: error("الصنف غير موجود: ${line.itemId}")
            require(line.quantityMilli > 0) { "الكمية يجب أن تكون أكبر من صفر" }
            Triple(line, item, MoneyMath.lineTotal(line.quantityMilli, line.unitPrice))
        }
        val subtotal = prepared.fold(0L) { acc, row -> Math.addExact(acc, row.third) }
        val total = MoneyMath.total(listOf(subtotal), input.discount)
        require(newTotalPaid <= total) { "إجمالي المدفوع يتجاوز إجمالي الفاتورة المعدّل" }
        val remaining = Math.subtractExact(total, newTotalPaid)

        if (input.customerOrSupplierId == null) {
            require(remaining == 0L) { "الشراء الآجل يتطلب اختيار مورد" }
        }
        val newSupplier = input.customerOrSupplierId?.let {
            database.suppliers().findById(it) ?: error("المورد غير موجود")
        }

        val oldQtyByItem = oldLines.groupBy { it.itemId }.mapValues { (_, list) -> list.sumOf { it.quantityMilli } }
        val newQtyByItem = prepared.associate { (line, _, _) -> line.itemId to line.quantityMilli }
        val newCostByItem = prepared.associate { (line, _, _) -> line.itemId to line.unitPrice }
        val allItemIds = (oldQtyByItem.keys + newQtyByItem.keys).distinct()
        allItemIds.forEachIndexed { idx, itemId ->
            val oldQ = oldQtyByItem[itemId] ?: 0L
            val newQ = newQtyByItem[itemId] ?: 0L
            val deltaBought = newQ - oldQ // positive -> add to stock, negative -> deduct from stock
            if (deltaBought != 0L) {
                val item = database.items().findById(itemId) ?: error("الصنف غير موجود")
                val before = item.quantityMilli
                val after = before + deltaBought
                require(after >= 0L) { "المخزون الحالي لا يكفي لإنقاص كمية الشراء للصنف: ${item.name}" }
                val avgCost = if (deltaBought > 0) {
                    MoneyMath.weightedAverageCost(before, item.purchasePrice, deltaBought, newCostByItem[itemId] ?: item.purchasePrice)
                } else {
                    item.purchasePrice
                }
                check(database.items().setQuantityAndPurchasePrice(item.id, after, avgCost, input.now) == 1)
                database.stock().insertMovement(
                    StockMovementEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "purchase-edit:${existing.id}:${input.now}:$idx:${UUID.randomUUID()}",
                        itemId = item.id,
                        movementType = if (deltaBought > 0) "PURCHASE_EDIT_IN" else "PURCHASE_EDIT_OUT",
                        quantityInMilli = if (deltaBought > 0) deltaBought else 0L,
                        quantityOutMilli = if (deltaBought < 0) abs(deltaBought) else 0L,
                        balanceBeforeMilli = before,
                        balanceAfterMilli = after,
                        sourceType = "PURCHASE_INVOICE_EDIT",
                        sourceId = existing.id,
                        note = "تعديل فاتورة شراء #${existing.invoiceNumber}: ${input.reason}",
                        createdAt = input.now
                    )
                )
            }
        }

        database.purchaseInvoices().deleteLines(existing.id)
        database.purchaseInvoices().insertLines(
            prepared.map { (line, item, lineTotal) ->
                PurchaseInvoiceItemEntity(
                    id = UUID.randomUUID().toString(),
                    invoiceId = existing.id,
                    itemId = item.id,
                    itemNameSnapshot = item.name,
                    quantityMilli = line.quantityMilli,
                    unitCost = line.unitPrice,
                    lineTotal = lineTotal
                )
            }
        )
        check(
            database.purchaseInvoices().update(
                existing.copy(
                    supplierId = newSupplier?.id,
                    subtotal = subtotal,
                    discount = input.discount,
                    total = total,
                    paid = newTotalPaid,
                    remaining = remaining,
                    paymentMethod = input.paymentMethod,
                    notes = input.notes?.trim()?.ifBlank { null },
                    updatedByUserId = input.actorId,
                    updatedByUserName = input.actorName,
                    updatedAt = input.now
                )
            ) == 1
        )

        val affectedSuppliers = mutableSetOf<String>()
        existing.supplierId?.let(affectedSuppliers::add)
        newSupplier?.id?.let(affectedSuppliers::add)

        val purchaseTx = database.ledger().findSupplierBySource("PURCHASE_INVOICE", existing.id)
        val initPayTx = database.ledger().findSupplierBySource("PURCHASE_INVOICE_PAYMENT", existing.id)
        if (newSupplier != null) {
            if (purchaseTx != null) {
                database.ledger().updateSupplier(
                    purchaseTx.copy(
                        supplierId = newSupplier.id,
                        amount = total,
                        note = "فاتورة شراء رقم ${existing.invoiceNumber} (معدّلة)",
                        updatedByUserId = input.actorId,
                        updatedByUserName = input.actorName,
                        updatedAt = input.now
                    )
                )
            } else {
                database.ledger().insertSupplier(
                    SupplierTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "purchase-edit-ledger:${existing.id}:${UUID.randomUUID()}",
                        supplierId = newSupplier.id,
                        type = "PURCHASE",
                        amount = total,
                        balanceAfter = 0L,
                        sourceType = "PURCHASE_INVOICE",
                        sourceId = existing.id,
                        note = "فاتورة شراء رقم ${existing.invoiceNumber}",
                        createdAt = existing.createdAt,
                        updatedByUserId = input.actorId,
                        updatedByUserName = input.actorName,
                        updatedAt = input.now
                    )
                )
            }
            if (initPayTx != null) {
                database.ledger().updateSupplier(
                    initPayTx.copy(
                        supplierId = newSupplier.id,
                        amount = newInitialPaid,
                        isReversed = newInitialPaid == 0L,
                        updatedByUserId = input.actorId,
                        updatedByUserName = input.actorName,
                        updatedAt = input.now
                    )
                )
            } else if (newInitialPaid > 0L) {
                database.ledger().insertSupplier(
                    SupplierTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        operationId = "purchase-edit-pay-ledger:${existing.id}:${UUID.randomUUID()}",
                        supplierId = newSupplier.id,
                        type = "PAYMENT",
                        amount = newInitialPaid,
                        balanceAfter = 0L,
                        sourceType = "PURCHASE_INVOICE_PAYMENT",
                        sourceId = existing.id,
                        note = "دفعة مع فاتورة ${existing.invoiceNumber}",
                        createdAt = existing.createdAt,
                        updatedByUserId = input.actorId,
                        updatedByUserName = input.actorName,
                        updatedAt = input.now
                    )
                )
            }
        } else {
            if (purchaseTx != null) database.ledger().updateSupplier(purchaseTx.copy(isReversed = true, updatedAt = input.now))
            if (initPayTx != null) database.ledger().updateSupplier(initPayTx.copy(isReversed = true, updatedAt = input.now))
        }
        affectedSuppliers.forEach { recalculateSupplierLedger(it, input.now) }

        val firstPay = payments.firstOrNull()
        val oldInitialPaid = firstPay?.amount ?: (existing.paid - extraPaymentsTotal).coerceAtLeast(0L)
        if (firstPay != null) {
            database.invoicePayments().update(
                firstPay.copy(
                    partyId = newSupplier?.id,
                    amount = newInitialPaid,
                    paymentMethod = input.paymentMethod,
                    updatedByUserId = input.actorId,
                    updatedByUserName = input.actorName,
                    updatedAt = input.now
                )
            )
        }
        val cashDiff = newInitialPaid - oldInitialPaid
        if (cashDiff != 0L) {
            database.cash().insert(
                CashTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = "purchase-edit-cash:${existing.id}:${input.now}:${UUID.randomUUID()}",
                    direction = if (cashDiff > 0) "OUT" else "IN",
                    amount = abs(cashDiff),
                    sourceType = "PURCHASE_INVOICE_EDIT",
                    sourceId = existing.id,
                    paymentMethod = input.paymentMethod,
                    note = "تعديل مدفوع فاتورة شراء #${existing.invoiceNumber}: ${input.reason}",
                    createdAt = input.now,
                    createdByUserId = input.actorId,
                    createdByUserName = input.actorName
                )
            )
        }
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = input.actorId,
                action = "EDIT_PURCHASE_INVOICE",
                entityType = "PURCHASE_INVOICE",
                entityId = existing.id,
                details = "تعديل فاتورة شراء #${existing.invoiceNumber} من ${existing.total} إلى $total",
                createdAt = input.now,
                actorName = input.actorName,
                previousValue = "total=${existing.total}, paid=${existing.paid}",
                newValue = "total=$total, paid=$newTotalPaid",
                reason = input.reason
            )
        )
        SaveInvoiceResult(existing.id, existing.invoiceNumber, false)
    }

    suspend fun voidPurchaseInvoice(
        operationId: String,
        invoiceId: String,
        reason: String,
        createdAt: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(operationId.isNotBlank() && reason.isNotBlank()) { "أدخل سبب إلغاء فاتورة الشراء" }
        val invoice = database.purchaseInvoices().findById(invoiceId) ?: error("فاتورة الشراء غير موجودة")
        if (invoice.status == "VOIDED") return@withTransaction
        requirePeriodOpen(invoice.createdAt)
        requirePeriodOpen(createdAt)
        require(database.returns().countForInvoice(invoiceId) == 0) {
            "تحتوي فاتورة الشراء على مرتجعات سابقة؛ استخدم شاشة المرتجع بدلاً من الإلغاء"
        }
        val lines = database.purchaseInvoices().lines(invoiceId)
        check(database.purchaseInvoices().changePostedStatus(invoiceId, "VOIDED") == 1)

        lines.forEachIndexed { index, line ->
            val item = database.items().findById(line.itemId) ?: error("الصنف غير موجود")
            val before = item.quantityMilli
            require(before >= line.quantityMilli) { "المخزون غير كافٍ لإلغاء شراء الصنف: ${item.name}" }
            val after = Math.subtractExact(before, line.quantityMilli)
            check(database.items().setQuantity(item.id, after, createdAt) == 1)
            database.stock().insertMovement(
                StockMovementEntity(
                    id = UUID.randomUUID().toString(),
                    operationId = "$operationId:void-p-stock:$index",
                    itemId = item.id,
                    movementType = "PURCHASE_VOID",
                    quantityInMilli = 0,
                    quantityOutMilli = line.quantityMilli,
                    balanceBeforeMilli = before,
                    balanceAfterMilli = after,
                    sourceType = "PURCHASE_INVOICE_VOID",
                    sourceId = invoiceId,
                    note = "إلغاء فاتورة شراء #${invoice.invoiceNumber}: $reason",
                    createdAt = createdAt
                )
            )
        }
        val supplierId = invoice.supplierId
        if (supplierId != null) {
            val supplier = database.suppliers().findById(supplierId) ?: error("المورد غير موجود")
            val reduction = minOf(supplier.balance, invoice.remaining)
            val newBalance = MoneyMath.reduceBalance(supplier.balance, reduction)
            database.ledger().insertSupplier(
                SupplierTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    operationId = "$operationId:void-supplier",
                    supplierId = supplierId,
                    type = "VOID",
                    amount = invoice.remaining,
                    balanceAfter = newBalance,
                    sourceType = "PURCHASE_INVOICE_VOID",
                    sourceId = invoiceId,
                    note = "عكس فاتورة شراء ملغاة #${invoice.invoiceNumber}: $reason",
                    createdAt = createdAt,
                    balanceBefore = supplier.balance,
                    createdByUserId = actorId,
                    createdByUserName = actorName
                )
            )
            check(database.suppliers().setBalance(supplierId, newBalance, createdAt) == 1)
        }
        if (invoice.paid > 0) {
            database.cash().insert(
                CashTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = "$operationId:void-cash",
                    direction = "IN",
                    amount = invoice.paid,
                    sourceType = "PURCHASE_INVOICE_VOID",
                    sourceId = invoiceId,
                    paymentMethod = invoice.paymentMethod,
                    note = "استرداد نقدي لإلغاء فاتورة شراء #${invoice.invoiceNumber}: $reason",
                    createdAt = createdAt,
                    createdByUserId = actorId,
                    createdByUserName = actorName
                )
            )
        }
        val (cashCode, cashName) = ChartAccounts.accountForMethod(invoice.paymentMethod)
        postAutoJournal(
            operationId = "journal:$operationId",
            entryType = "REVERSAL",
            description = "عكس فاتورة شراء ملغاة #${invoice.invoiceNumber}",
            sourceType = "PURCHASE_INVOICE_VOID",
            sourceId = invoiceId,
            lines = buildList {
                if (invoice.paid > 0) add(JournalLineInput(cashCode, cashName, invoice.paid, 0L, reason))
                if (invoice.remaining > 0) add(JournalLineInput(ChartAccounts.CODE_PAYABLE, ChartAccounts.NAME_PAYABLE, invoice.remaining, 0L, reason))
                add(JournalLineInput(ChartAccounts.CODE_PURCHASES, ChartAccounts.NAME_PURCHASES, 0L, invoice.total, reason))
            },
            createdAt = createdAt,
            actorId = actorId,
            actorName = actorName,
            notes = reason
        )
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "VOID_PURCHASE_INVOICE",
                entityType = "PURCHASE_INVOICE",
                entityId = invoiceId,
                details = "إلغاء فاتورة شراء #${invoice.invoiceNumber}",
                createdAt = createdAt,
                actorName = actorName,
                previousValue = "POSTED (${invoice.total})",
                newValue = "VOIDED",
                reason = reason
            )
        )
    }

    suspend fun updateInvoicePayment(
        paymentId: String,
        newAmount: Long,
        newMethod: String,
        newNote: String?,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(newAmount > 0 && reason.isNotBlank()) { "أدخل مبلغاً صحيحاً وسبب التعديل" }
        val payment = database.invoicePayments().findById(paymentId) ?: error("الدفعة غير موجودة")
        requirePeriodOpen(payment.createdAt)
        requirePeriodOpen(now)

        val delta = newAmount - payment.amount
        val allPayments = database.invoicePayments().readForInvoiceSync(payment.invoiceType, payment.invoiceId)
        val otherPaymentsTotal = allPayments.filter { it.id != payment.id }.sumOf { it.amount }

        if (payment.invoiceType == "SALE") {
            val invoice = database.salesInvoices().findById(payment.invoiceId) ?: error("الفاتورة غير موجودة")
            require(invoice.status == "POSTED") { "لا يمكن تعديل دفعة لفاتورة ملغاة" }
            val newPaid = Math.addExact(otherPaymentsTotal, newAmount)
            require(newPaid <= invoice.total) { "إجمالي الدفعات يتجاوز إجمالي الفاتورة" }
            val newRemaining = Math.subtractExact(invoice.total, newPaid)
            check(database.salesInvoices().updatePaymentAmounts(invoice.id, newPaid, newRemaining) == 1)

            val ledgerRow = database.ledger().findCustomerOperation("${payment.operationId}:ledger")
            if (ledgerRow != null && invoice.customerId != null) {
                database.ledger().updateCustomer(
                    ledgerRow.copy(
                        amount = newAmount,
                        note = newNote?.ifBlank { null } ?: ledgerRow.note,
                        updatedByUserId = actorId,
                        updatedByUserName = actorName,
                        updatedAt = now
                    )
                )
                recalculateCustomerLedger(invoice.customerId, now)
            }
            if (delta != 0L) {
                database.cash().insert(
                    CashTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        idempotencyKey = "pay-edit-cash:$paymentId:$now:${UUID.randomUUID()}",
                        direction = if (delta > 0) "IN" else "OUT",
                        amount = abs(delta),
                        sourceType = "INVOICE_PAYMENT_EDIT",
                        sourceId = invoice.id,
                        paymentMethod = newMethod,
                        note = "تعديل دفعة فاتورة #${invoice.invoiceNumber}: $reason",
                        createdAt = now,
                        createdByUserId = actorId,
                        createdByUserName = actorName
                    )
                )
                val (cashCode, cashName) = ChartAccounts.accountForMethod(newMethod)
                postAutoJournal(
                    operationId = "journal:pay-edit:$paymentId:$now:${UUID.randomUUID()}",
                    entryType = "ADJUSTMENT",
                    description = "تعديل دفعة فاتورة بيع #${invoice.invoiceNumber}",
                    sourceType = "INVOICE_PAYMENT_EDIT",
                    sourceId = paymentId,
                    lines = listOf(
                        JournalLineInput(
                            if (delta > 0) cashCode else ChartAccounts.CODE_RECEIVABLE,
                            if (delta > 0) cashName else ChartAccounts.NAME_RECEIVABLE,
                            abs(delta),
                            0L,
                            reason
                        ),
                        JournalLineInput(
                            if (delta > 0) ChartAccounts.CODE_RECEIVABLE else cashCode,
                            if (delta > 0) ChartAccounts.NAME_RECEIVABLE else cashName,
                            0L,
                            abs(delta),
                            reason
                        )
                    ),
                    createdAt = now,
                    actorId = actorId,
                    actorName = actorName
                )
            }
        } else {
            val invoice = database.purchaseInvoices().findById(payment.invoiceId) ?: error("فاتورة الشراء غير موجودة")
            require(invoice.status == "POSTED") { "لا يمكن تعديل دفعة لفاتورة ملغاة" }
            val newPaid = Math.addExact(otherPaymentsTotal, newAmount)
            require(newPaid <= invoice.total) { "إجمالي الدفعات يتجاوز إجمالي الفاتورة" }
            val newRemaining = Math.subtractExact(invoice.total, newPaid)
            check(database.purchaseInvoices().updatePaymentAmounts(invoice.id, newPaid, newRemaining) == 1)

            val ledgerRow = database.ledger().findSupplierOperation("${payment.operationId}:ledger")
            if (ledgerRow != null && invoice.supplierId != null) {
                database.ledger().updateSupplier(
                    ledgerRow.copy(
                        amount = newAmount,
                        note = newNote?.ifBlank { null } ?: ledgerRow.note,
                        updatedByUserId = actorId,
                        updatedByUserName = actorName,
                        updatedAt = now
                    )
                )
                recalculateSupplierLedger(invoice.supplierId, now)
            }
            if (delta != 0L) {
                database.cash().insert(
                    CashTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        idempotencyKey = "ppay-edit-cash:$paymentId:$now:${UUID.randomUUID()}",
                        direction = if (delta > 0) "OUT" else "IN",
                        amount = abs(delta),
                        sourceType = "INVOICE_PAYMENT_EDIT",
                        sourceId = invoice.id,
                        paymentMethod = newMethod,
                        note = "تعديل دفعة فاتورة شراء #${invoice.invoiceNumber}: $reason",
                        createdAt = now,
                        createdByUserId = actorId,
                        createdByUserName = actorName
                    )
                )
            }
        }

        check(
            database.invoicePayments().update(
                payment.copy(
                    amount = newAmount,
                    paymentMethod = newMethod,
                    note = newNote?.trim()?.ifBlank { null } ?: payment.note,
                    updatedByUserId = actorId,
                    updatedByUserName = actorName,
                    updatedAt = now
                )
            ) == 1
        )
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "EDIT_INVOICE_PAYMENT",
                entityType = "INVOICE_PAYMENT",
                entityId = payment.invoiceId,
                details = "تعديل دفعة من ${payment.amount} إلى $newAmount",
                createdAt = now,
                actorName = actorName,
                previousValue = payment.amount.toString(),
                newValue = newAmount.toString(),
                reason = reason
            )
        )
    }

    suspend fun updateCustomerTransaction(
        txId: String,
        newAmount: Long,
        newNote: String?,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(newAmount > 0 && reason.isNotBlank()) { "أدخل المبلغ وسبب التعديل" }
        val tx = database.ledger().findCustomerById(txId) ?: error("القيد غير موجود")
        require(!tx.isReversed) { "لا يمكن تعديل عملية معكوسة" }
        require(tx.sourceType in setOf("CUSTOMER_PAYMENT", "MANUAL_DEBIT")) {
            "لإعديل الفواتير المرتبطة استخدم شاشة تعديل الفاتورة"
        }
        requirePeriodOpen(tx.createdAt)
        requirePeriodOpen(now)

        val delta = newAmount - tx.amount
        check(
            database.ledger().updateCustomer(
                tx.copy(
                    amount = newAmount,
                    note = newNote?.trim()?.ifBlank { null } ?: tx.note,
                    updatedByUserId = actorId,
                    updatedByUserName = actorName,
                    updatedAt = now
                )
            ) == 1
        )
        recalculateCustomerLedger(tx.customerId, now)

        if (tx.sourceType == "CUSTOMER_PAYMENT" && delta != 0L) {
            database.cash().insert(
                CashTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = "cust-tx-edit:$txId:$now:${UUID.randomUUID()}",
                    direction = if (delta > 0) "IN" else "OUT",
                    amount = abs(delta),
                    sourceType = "CUSTOMER_PAYMENT_EDIT",
                    sourceId = tx.customerId,
                    paymentMethod = "CASH",
                    note = "تعديل سند قبض عميل: $reason",
                    createdAt = now,
                    createdByUserId = actorId,
                    createdByUserName = actorName
                )
            )
            postAutoJournal(
                operationId = "journal:cust-edit:$txId:$now:${UUID.randomUUID()}",
                entryType = "ADJUSTMENT",
                description = "تعديل سند قبض عميل: $reason",
                sourceType = "CUSTOMER_PAYMENT_EDIT",
                sourceId = tx.id,
                lines = listOf(
                    JournalLineInput(
                        if (delta > 0) ChartAccounts.CODE_CASH else ChartAccounts.CODE_RECEIVABLE,
                        if (delta > 0) ChartAccounts.NAME_CASH else ChartAccounts.NAME_RECEIVABLE,
                        abs(delta),
                        0L,
                        reason
                    ),
                    JournalLineInput(
                        if (delta > 0) ChartAccounts.CODE_RECEIVABLE else ChartAccounts.CODE_CASH,
                        if (delta > 0) ChartAccounts.NAME_RECEIVABLE else ChartAccounts.NAME_CASH,
                        0L,
                        abs(delta),
                        reason
                    )
                ),
                createdAt = now,
                actorId = actorId,
                actorName = actorName
            )
        }
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "EDIT_CUSTOMER_TX",
                entityType = "CUSTOMER",
                entityId = tx.customerId,
                details = "تعديل عملية عميل (${tx.type}) من ${tx.amount} إلى $newAmount",
                createdAt = now,
                actorName = actorName,
                previousValue = tx.amount.toString(),
                newValue = newAmount.toString(),
                reason = reason
            )
        )
    }

    suspend fun reverseCustomerTransaction(
        txId: String,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(reason.isNotBlank()) { "أدخل سبب العكس المحاسبي" }
        val tx = database.ledger().findCustomerById(txId) ?: error("القيد غير موجود")
        require(!tx.isReversed) { "العملية معكوسة مسبقاً" }
        require(tx.sourceType in setOf("CUSTOMER_PAYMENT", "MANUAL_DEBIT", "MANUAL_SETTLEMENT", "BALANCE_CORRECTION")) {
            "عكس فواتير البيع يتم عبر الإلغاء أو المرتجع"
        }
        requirePeriodOpen(tx.createdAt)
        requirePeriodOpen(now)

        check(
            database.ledger().updateCustomer(
                tx.copy(
                    isReversed = true,
                    updatedByUserId = actorId,
                    updatedByUserName = actorName,
                    updatedAt = now
                )
            ) == 1
        )
        recalculateCustomerLedger(tx.customerId, now)

        if (tx.sourceType == "CUSTOMER_PAYMENT") {
            database.cash().insert(
                CashTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = "cust-tx-rev:$txId:$now:${UUID.randomUUID()}",
                    direction = "OUT",
                    amount = tx.amount,
                    sourceType = "CUSTOMER_PAYMENT_REVERSAL",
                    sourceId = tx.customerId,
                    paymentMethod = "CASH",
                    note = "عكس سند قبض عميل: $reason",
                    createdAt = now,
                    createdByUserId = actorId,
                    createdByUserName = actorName
                )
            )
            postAutoJournal(
                operationId = "journal:cust-rev:$txId:$now:${UUID.randomUUID()}",
                entryType = "REVERSAL",
                description = "عكس سند قبض عميل: $reason",
                sourceType = "CUSTOMER_PAYMENT_REVERSAL",
                sourceId = tx.id,
                lines = listOf(
                    JournalLineInput(ChartAccounts.CODE_RECEIVABLE, ChartAccounts.NAME_RECEIVABLE, tx.amount, 0L, reason),
                    JournalLineInput(ChartAccounts.CODE_CASH, ChartAccounts.NAME_CASH, 0L, tx.amount, reason)
                ),
                createdAt = now,
                actorId = actorId,
                actorName = actorName
            )
        }
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "REVERSE_CUSTOMER_TX",
                entityType = "CUSTOMER",
                entityId = tx.customerId,
                details = "عكس عملية عميل بقيمة ${tx.amount}",
                createdAt = now,
                actorName = actorName,
                previousValue = tx.amount.toString(),
                newValue = "REVERSED",
                reason = reason
            )
        )
    }

    suspend fun updateSupplierTransaction(
        txId: String,
        newAmount: Long,
        newNote: String?,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(newAmount > 0 && reason.isNotBlank()) { "أدخل المبلغ وسبب التعديل" }
        val tx = database.ledger().findSupplierById(txId) ?: error("القيد غير موجود")
        require(!tx.isReversed) { "لا يمكن تعديل عملية معكوسة" }
        require(tx.sourceType in setOf("SUPPLIER_PAYMENT", "MANUAL_DEBIT")) {
            "لتعديل فواتير الشراء استخدم شاشة تعديل الفاتورة"
        }
        requirePeriodOpen(tx.createdAt)
        requirePeriodOpen(now)

        val delta = newAmount - tx.amount
        check(
            database.ledger().updateSupplier(
                tx.copy(
                    amount = newAmount,
                    note = newNote?.trim()?.ifBlank { null } ?: tx.note,
                    updatedByUserId = actorId,
                    updatedByUserName = actorName,
                    updatedAt = now
                )
            ) == 1
        )
        recalculateSupplierLedger(tx.supplierId, now)

        if (tx.sourceType == "SUPPLIER_PAYMENT" && delta != 0L) {
            database.cash().insert(
                CashTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = "sup-tx-edit:$txId:$now:${UUID.randomUUID()}",
                    direction = if (delta > 0) "OUT" else "IN",
                    amount = abs(delta),
                    sourceType = "SUPPLIER_PAYMENT_EDIT",
                    sourceId = tx.supplierId,
                    paymentMethod = "CASH",
                    note = "تعديل سند دفع مورد: $reason",
                    createdAt = now,
                    createdByUserId = actorId,
                    createdByUserName = actorName
                )
            )
            postAutoJournal(
                operationId = "journal:sup-edit:$txId:$now:${UUID.randomUUID()}",
                entryType = "ADJUSTMENT",
                description = "تعديل سند دفع مورد: $reason",
                sourceType = "SUPPLIER_PAYMENT_EDIT",
                sourceId = tx.id,
                lines = listOf(
                    JournalLineInput(
                        if (delta > 0) ChartAccounts.CODE_PAYABLE else ChartAccounts.CODE_CASH,
                        if (delta > 0) ChartAccounts.NAME_PAYABLE else ChartAccounts.NAME_CASH,
                        abs(delta),
                        0L,
                        reason
                    ),
                    JournalLineInput(
                        if (delta > 0) ChartAccounts.CODE_CASH else ChartAccounts.CODE_PAYABLE,
                        if (delta > 0) ChartAccounts.NAME_CASH else ChartAccounts.NAME_PAYABLE,
                        0L,
                        abs(delta),
                        reason
                    )
                ),
                createdAt = now,
                actorId = actorId,
                actorName = actorName
            )
        }
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "EDIT_SUPPLIER_TX",
                entityType = "SUPPLIER",
                entityId = tx.supplierId,
                details = "تعديل عملية مورد (${tx.type}) من ${tx.amount} إلى $newAmount",
                createdAt = now,
                actorName = actorName,
                previousValue = tx.amount.toString(),
                newValue = newAmount.toString(),
                reason = reason
            )
        )
    }

    suspend fun reverseSupplierTransaction(
        txId: String,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(reason.isNotBlank()) { "أدخل سبب العكس المحاسبي" }
        val tx = database.ledger().findSupplierById(txId) ?: error("القيد غير موجود")
        require(!tx.isReversed) { "العملية معكوسة مسبقاً" }
        require(tx.sourceType in setOf("SUPPLIER_PAYMENT", "MANUAL_DEBIT", "MANUAL_SETTLEMENT", "BALANCE_CORRECTION")) {
            "عكس فواتير الشراء يتم عبر الإلغاء أو المرتجع"
        }
        requirePeriodOpen(tx.createdAt)
        requirePeriodOpen(now)

        check(
            database.ledger().updateSupplier(
                tx.copy(
                    isReversed = true,
                    updatedByUserId = actorId,
                    updatedByUserName = actorName,
                    updatedAt = now
                )
            ) == 1
        )
        recalculateSupplierLedger(tx.supplierId, now)

        if (tx.sourceType == "SUPPLIER_PAYMENT") {
            database.cash().insert(
                CashTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = "sup-tx-rev:$txId:$now:${UUID.randomUUID()}",
                    direction = "IN",
                    amount = tx.amount,
                    sourceType = "SUPPLIER_PAYMENT_REVERSAL",
                    sourceId = tx.supplierId,
                    paymentMethod = "CASH",
                    note = "عكس سند دفع مورد: $reason",
                    createdAt = now,
                    createdByUserId = actorId,
                    createdByUserName = actorName
                )
            )
            postAutoJournal(
                operationId = "journal:sup-rev:$txId:$now:${UUID.randomUUID()}",
                entryType = "REVERSAL",
                description = "عكس سند دفع مورد: $reason",
                sourceType = "SUPPLIER_PAYMENT_REVERSAL",
                sourceId = tx.id,
                lines = listOf(
                    JournalLineInput(ChartAccounts.CODE_CASH, ChartAccounts.NAME_CASH, tx.amount, 0L, reason),
                    JournalLineInput(ChartAccounts.CODE_PAYABLE, ChartAccounts.NAME_PAYABLE, 0L, tx.amount, reason)
                ),
                createdAt = now,
                actorId = actorId,
                actorName = actorName
            )
        }
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "REVERSE_SUPPLIER_TX",
                entityType = "SUPPLIER",
                entityId = tx.supplierId,
                details = "عكس عملية مورد بقيمة ${tx.amount}",
                createdAt = now,
                actorName = actorName,
                previousValue = tx.amount.toString(),
                newValue = "REVERSED",
                reason = reason
            )
        )
    }

    suspend fun reverseExpense(
        expenseId: String,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(reason.isNotBlank()) { "أدخل سبب عكس المصروف" }
        val existing = database.expenses().findById(expenseId) ?: error("المصروف غير موجود")
        require(!existing.isReversed) { "المصروف معكوس مسبقاً" }
        requirePeriodOpen(existing.createdAt)
        requirePeriodOpen(now)

        check(
            database.expenses().updateEntity(
                existing.copy(
                    isReversed = true,
                    updatedByUserId = actorId,
                    updatedByUserName = actorName,
                    updatedAt = now
                )
            ) == 1
        )
        database.cash().insert(
            CashTransactionEntity(
                id = UUID.randomUUID().toString(),
                idempotencyKey = "exp-rev:$expenseId:$now:${UUID.randomUUID()}",
                direction = "IN",
                amount = existing.amount,
                sourceType = "EXPENSE_REVERSAL",
                sourceId = expenseId,
                paymentMethod = existing.paymentMethod,
                note = "عكس مصروف (${existing.category}): $reason",
                createdAt = now,
                createdByUserId = actorId,
                createdByUserName = actorName
            )
        )
        val (cashCode, cashName) = ChartAccounts.accountForMethod(existing.paymentMethod)
        postAutoJournal(
            operationId = "journal:exp-rev:$expenseId:$now:${UUID.randomUUID()}",
            entryType = "REVERSAL",
            description = "عكس مصروف (${existing.category}): $reason",
            sourceType = "EXPENSE_REVERSAL",
            sourceId = expenseId,
            lines = listOf(
                JournalLineInput(cashCode, cashName, existing.amount, 0L, reason),
                JournalLineInput(ChartAccounts.CODE_EXPENSES, ChartAccounts.NAME_EXPENSES, 0L, existing.amount, reason)
            ),
            createdAt = now,
            actorId = actorId,
            actorName = actorName
        )
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "REVERSE_EXPENSE",
                entityType = "EXPENSE",
                entityId = expenseId,
                details = "عكس مصروف ${existing.category} بقيمة ${existing.amount}",
                createdAt = now,
                actorName = actorName,
                previousValue = existing.amount.toString(),
                newValue = "REVERSED",
                reason = reason
            )
        )
    }

    suspend fun updateTransfer(
        transferId: String,
        senderName: String?,
        recipientName: String,
        phone: String,
        direction: String,
        amount: Long,
        fees: Long,
        status: String,
        note: String?,
        reason: String,
        now: Long = System.currentTimeMillis(),
        actorId: String? = null,
        actorName: String? = null
    ) = database.withTransaction {
        require(recipientName.isNotBlank() && amount > 0 && fees >= 0 && reason.isNotBlank()) {
            "بيانات الحوالة أو سبب التعديل غير مكتملة"
        }
        val existing = database.transfers().findById(transferId) ?: error("الحوالة غير موجودة")
        require(existing.status != "REVERSED") { "لا يمكن تعديل حوالة معكوسة" }
        requirePeriodOpen(existing.createdAt)
        requirePeriodOpen(now)

        val newNet = if (direction == "OUT") Math.addExact(amount, fees) else {
            require(fees <= amount) { "الرسوم أكبر من مبلغ الحوالة" }
            Math.subtractExact(amount, fees)
        }
        val oldSigned = if (existing.direction == "IN") existing.netAmount else -existing.netAmount
        val newSigned = if (status == "CANCELLED") 0L else if (direction == "IN") newNet else -newNet
        val cashDelta = newSigned - oldSigned
        if (cashDelta != 0L) {
            database.cash().insert(
                CashTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    idempotencyKey = "tr-edit:$transferId:$now:${UUID.randomUUID()}",
                    direction = if (cashDelta > 0) "IN" else "OUT",
                    amount = abs(cashDelta),
                    sourceType = "TRANSFER_EDIT",
                    sourceId = transferId,
                    paymentMethod = "CASH",
                    note = "تعديل حوالة (${recipientName.trim()}): $reason",
                    createdAt = now,
                    createdByUserId = actorId,
                    createdByUserName = actorName
                )
            )
        }
        check(
            database.transfers().updateEntity(
                existing.copy(
                    senderName = senderName?.trim()?.ifBlank { null },
                    recipientName = recipientName.trim(),
                    phone = phone.filter(Char::isDigit),
                    direction = direction,
                    amount = amount,
                    fees = fees,
                    netAmount = newNet,
                    status = status,
                    note = note?.trim()?.ifBlank { null },
                    updatedByUserId = actorId,
                    updatedByUserName = actorName,
                    updatedAt = now
                )
            ) == 1
        )
        database.references().insertActivity(
            ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                actorId = actorId,
                action = "EDIT_TRANSFER",
                entityType = "TRANSFER",
                entityId = transferId,
                details = "تعديل حوالة ${recipientName.trim()}",
                createdAt = now,
                actorName = actorName,
                previousValue = existing.netAmount.toString(),
                newValue = newNet.toString(),
                reason = reason
            )
        )
    }

    suspend fun recalculateCustomerLedger(customerId: String, now: Long) {
        val txs = database.ledger().readCustomerChronologicalSync(customerId)
        var running = 0L
        txs.forEach { tx ->
            val before = running
            if (!tx.isReversed) {
                running = when (tx.type) {
                    "SALE", "DEBIT", "ADJUSTMENT_ADD" -> MoneyMath.addBalance(running, tx.amount)
                    "PAYMENT", "RETURN", "VOID", "SETTLEMENT", "ADJUSTMENT_SUB" ->
                        (running - tx.amount).coerceAtLeast(0L)
                    else -> running
                }
            }
            if (tx.balanceAfter != running || tx.balanceBefore != before) {
                database.ledger().updateCustomer(tx.copy(balanceBefore = before, balanceAfter = running))
            }
        }
        database.customers().setBalance(customerId, running, now)
    }

    suspend fun recalculateSupplierLedger(supplierId: String, now: Long) {
        val txs = database.ledger().readSupplierChronologicalSync(supplierId)
        var running = 0L
        txs.forEach { tx ->
            val before = running
            if (!tx.isReversed) {
                running = when (tx.type) {
                    "PURCHASE", "DEBIT", "ADJUSTMENT_ADD" -> MoneyMath.addBalance(running, tx.amount)
                    "PAYMENT", "RETURN", "VOID", "SETTLEMENT", "ADJUSTMENT_SUB" ->
                        (running - tx.amount).coerceAtLeast(0L)
                    else -> running
                }
            }
            if (tx.balanceAfter != running || tx.balanceBefore != before) {
                database.ledger().updateSupplier(tx.copy(balanceBefore = before, balanceAfter = running))
            }
        }
        database.suppliers().setBalance(supplierId, running, now)
    }

    private fun validateJournalLines(lines: List<JournalLineInput>) {
        require(lines.size >= 2) { "يجب أن يحتوي القيد المحاسبي على سطرين على الأقل" }
        var totalDebit = 0L
        var totalCredit = 0L
        lines.forEach { line ->
            require(line.accountName.isNotBlank()) { "اسم الحساب مطلوب في كل سطر" }
            require(line.debit >= 0L && line.credit >= 0L) { "القيم يجب ألا تكون سالبة" }
            require((line.debit > 0L) xor (line.credit > 0L)) { "كل سطر يجب أن يكون مديناً أو دائناً فقط" }
            totalDebit = Math.addExact(totalDebit, line.debit)
            totalCredit = Math.addExact(totalCredit, line.credit)
        }
        require(totalDebit > 0L && totalDebit == totalCredit) {
            "القيد غير متوازن: مجموع المدين ($totalDebit) لا يساوي مجموع الدائن ($totalCredit)"
        }
    }
}
