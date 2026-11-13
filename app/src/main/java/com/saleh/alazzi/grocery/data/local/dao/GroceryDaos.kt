package com.saleh.alazzi.grocery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.saleh.alazzi.grocery.data.local.entity.*

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: CustomerEntity)
    @Query("SELECT COUNT(*) FROM customers WHERE (phone IS NOT NULL AND phone=:phone) OR (phone IS NULL AND :phone IS NULL AND name=:name COLLATE NOCASE)")
    suspend fun duplicateCount(name: String, phone: String?): Int
    @Query("SELECT COUNT(*) FROM customers WHERE id!=:excludeId AND ((phone IS NOT NULL AND phone=:phone) OR (phone IS NULL AND :phone IS NULL AND name=:name COLLATE NOCASE))")
    suspend fun duplicateCountExcluding(excludeId: String, name: String, phone: String?): Int
    @Query("UPDATE customers SET name=:name, phone=:phone, address=:address, notes=:notes, updatedAt=:now WHERE id=:id")
    suspend fun updateProfile(id: String, name: String, phone: String?, address: String?, notes: String?, now: Long): Int
    @Query("UPDATE customers SET isArchived=:archived, updatedAt=:now WHERE id=:id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long): Int
    @Query("SELECT * FROM customers ORDER BY isArchived ASC, createdAt DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers")
    suspend fun readAllSync(): List<CustomerEntity>
    @Query("SELECT * FROM customers WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): CustomerEntity?
    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<CustomerEntity>>
    @Query("UPDATE customers SET balance=:balance, updatedAt=:now WHERE id=:id")
    suspend fun setBalance(id: String, balance: Long, now: Long): Int
    @Query("""
        SELECT c.id AS customerId,
               COALESCE((SELECT SUM(s.total) FROM sales_invoices s WHERE s.customerId=c.id AND s.status='POSTED'), 0) AS totalSales,
               COALESCE((SELECT SUM(ct.amount) FROM customer_transactions ct WHERE ct.customerId=c.id AND ct.type='PAYMENT'), 0) AS totalPayments,
               (SELECT MAX(ct.createdAt) FROM customer_transactions ct WHERE ct.customerId=c.id) AS lastTransactionAt
        FROM customers c
    """)
    fun observeAggregates(): Flow<List<CustomerAggregateRow>>
}

@Dao
interface SupplierDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: SupplierEntity)
    @Query("SELECT COUNT(*) FROM suppliers WHERE (phone IS NOT NULL AND phone=:phone) OR (phone IS NULL AND :phone IS NULL AND name=:name COLLATE NOCASE)")
    suspend fun duplicateCount(name: String, phone: String?): Int
    @Query("SELECT COUNT(*) FROM suppliers WHERE id!=:excludeId AND ((phone IS NOT NULL AND phone=:phone) OR (phone IS NULL AND :phone IS NULL AND name=:name COLLATE NOCASE))")
    suspend fun duplicateCountExcluding(excludeId: String, name: String, phone: String?): Int
    @Query("UPDATE suppliers SET name=:name, phone=:phone, address=:address, notes=:notes, updatedAt=:now WHERE id=:id")
    suspend fun updateProfile(id: String, name: String, phone: String?, address: String?, notes: String?, now: Long): Int
    @Query("UPDATE suppliers SET isArchived=:archived, updatedAt=:now WHERE id=:id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long): Int
    @Query("SELECT * FROM suppliers ORDER BY isArchived ASC, createdAt DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SupplierEntity>>
    @Query("SELECT * FROM suppliers")
    suspend fun readAllSync(): List<SupplierEntity>
    @Query("SELECT * FROM suppliers WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): SupplierEntity?
    @Query("SELECT * FROM suppliers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<SupplierEntity>>
    @Query("UPDATE suppliers SET balance=:balance, updatedAt=:now WHERE id=:id")
    suspend fun setBalance(id: String, balance: Long, now: Long): Int
    @Query("""
        SELECT sp.id AS supplierId,
               COALESCE((SELECT SUM(p.total) FROM purchase_invoices p WHERE p.supplierId=sp.id AND p.status='POSTED'), 0) AS totalPurchases,
               COALESCE((SELECT SUM(st.amount) FROM supplier_transactions st WHERE st.supplierId=sp.id AND st.type='PAYMENT'), 0) AS totalPayments,
               (SELECT MAX(st.createdAt) FROM supplier_transactions st WHERE st.supplierId=sp.id) AS lastTransactionAt
        FROM suppliers sp
    """)
    fun observeAggregates(): Flow<List<SupplierAggregateRow>>
}

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: ItemEntity)
    @Query("SELECT COUNT(*) FROM items WHERE name=:name COLLATE NOCASE OR (barcode IS NOT NULL AND barcode=:barcode)")
    suspend fun duplicateCount(name: String, barcode: String?): Int
    @Query("SELECT COUNT(*) FROM items WHERE id!=:excludeId AND (name=:name COLLATE NOCASE OR (barcode IS NOT NULL AND barcode=:barcode))")
    suspend fun duplicateCountExcluding(excludeId: String, name: String, barcode: String?): Int
    @Query("SELECT * FROM items WHERE isActive=1 ORDER BY createdAt DESC, name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items ORDER BY isActive DESC, createdAt DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items")
    suspend fun readAllSync(): List<ItemEntity>
    @Query("SELECT * FROM items WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): ItemEntity?
    @Query("SELECT * FROM items WHERE barcode=:barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ItemEntity?
    @Query("SELECT * FROM items WHERE isActive=1 AND (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') ORDER BY name LIMIT 50")
    fun search(query: String): Flow<List<ItemEntity>>
    @Query("UPDATE items SET name=:name, barcode=:barcode, purchasePrice=:purchasePrice, salePrice=:salePrice, lowStockThresholdMilli=:lowStockThresholdMilli, categoryId=:categoryId, unitId=:unitId, updatedAt=:now WHERE id=:id")
    suspend fun updateDetails(
        id: String,
        name: String,
        barcode: String?,
        purchasePrice: Long,
        salePrice: Long,
        lowStockThresholdMilli: Long,
        categoryId: String?,
        unitId: String?,
        now: Long
    ): Int
    @Query("UPDATE items SET isActive=:active, updatedAt=:now WHERE id=:id")
    suspend fun setActive(id: String, active: Boolean, now: Long): Int
    @Query("SELECT (SELECT COUNT(*) FROM sales_invoice_items WHERE itemId=:itemId) + (SELECT COUNT(*) FROM purchase_invoice_items WHERE itemId=:itemId)")
    suspend fun invoiceUsageCount(itemId: String): Int
    @Query("UPDATE items SET quantityMilli=:quantity, updatedAt=:now WHERE id=:id")
    suspend fun setQuantity(id: String, quantity: Long, now: Long): Int
    @Query("UPDATE items SET quantityMilli=:quantity, purchasePrice=:purchasePrice, updatedAt=:now WHERE id=:id")
    suspend fun setQuantityAndPurchasePrice(id: String, quantity: Long, purchasePrice: Long, now: Long): Int
    @Query("SELECT * FROM items WHERE isActive=1 AND quantityMilli<=lowStockThresholdMilli ORDER BY quantityMilli ASC")
    fun observeLowStock(): Flow<List<ItemEntity>>
}

@Dao
interface SalesInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(invoice: SalesInvoiceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLines(lines: List<SalesInvoiceItemEntity>)
    @Update suspend fun update(invoice: SalesInvoiceEntity): Int
    @Query("DELETE FROM sales_invoice_items WHERE invoiceId=:invoiceId")
    suspend fun deleteLines(invoiceId: String): Int
    @Query("SELECT * FROM sales_invoices WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): SalesInvoiceEntity?
    @Query("SELECT * FROM sales_invoices WHERE id=:id LIMIT 1")
    fun observeById(id: String): Flow<SalesInvoiceEntity?>
    @Query("SELECT * FROM sales_invoices WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): SalesInvoiceEntity?
    @Query("SELECT * FROM sales_invoices ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<SalesInvoiceEntity>>
    @Query("SELECT * FROM sales_invoice_items WHERE invoiceId=:invoiceId")
    suspend fun lines(invoiceId: String): List<SalesInvoiceItemEntity>
    @Query("SELECT * FROM sales_invoice_items WHERE invoiceId=:invoiceId")
    fun observeLines(invoiceId: String): Flow<List<SalesInvoiceItemEntity>>
    @Query("SELECT COALESCE(MAX(CAST(invoiceNumber AS INTEGER)), 0) FROM sales_invoices")
    suspend fun maxInvoiceNumber(): Long
    @Query("SELECT * FROM sales_invoices WHERE customerId=:customerId ORDER BY createdAt DESC")
    fun observeForCustomer(customerId: String): Flow<List<SalesInvoiceEntity>>
    @Query("SELECT * FROM sales_invoices WHERE customerId=:customerId AND remaining>0 AND status='POSTED' ORDER BY createdAt ASC")
    fun observeOpenForCustomer(customerId: String): Flow<List<SalesInvoiceEntity>>
    @Query("UPDATE sales_invoices SET paid=:paid, remaining=:remaining WHERE id=:id")
    suspend fun updatePaymentAmounts(id: String, paid: Long, remaining: Long): Int

    @Query("SELECT COALESCE(SUM(total),0) FROM sales_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to")
    fun observeSalesTotal(from: Long, to: Long): Flow<Long>
    @Query("SELECT COUNT(*) FROM sales_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to")
    fun observeSalesCount(from: Long, to: Long): Flow<Long>
    @Query("SELECT COALESCE(SUM(si.lineTotal - ((si.unitCostSnapshot * si.quantityMilli + 500) / 1000) - CASE WHEN s.subtotal>0 THEN ((s.discount * si.lineTotal + s.subtotal / 2) / s.subtotal) ELSE 0 END),0) FROM sales_invoice_items si INNER JOIN sales_invoices s ON s.id=si.invoiceId WHERE s.status='POSTED' AND s.createdAt>=:from AND s.createdAt<:to")
    fun observeGrossProfit(from: Long, to: Long): Flow<Long>

    @Query("""
        SELECT
            (SELECT COALESCE(SUM(total),0) FROM sales_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to) AS sales,
            (SELECT COALESCE(SUM(total),0) FROM purchase_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to) AS purchases,
            (SELECT COALESCE(SUM(si.lineTotal - ((si.unitCostSnapshot * si.quantityMilli + 500) / 1000) - CASE WHEN s.subtotal>0 THEN ((s.discount * si.lineTotal + s.subtotal / 2) / s.subtotal) ELSE 0 END),0) FROM sales_invoice_items si INNER JOIN sales_invoices s ON s.id=si.invoiceId WHERE s.status='POSTED' AND s.createdAt>=:from AND s.createdAt<:to) AS grossProfit,
            (SELECT COUNT(*) FROM sales_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to) AS salesInvoices,
            (SELECT COALESCE(SUM(total),0) FROM returns WHERE returnType='SALE' AND createdAt>=:from AND createdAt<:to) AS salesReturns,
            (SELECT COALESCE(SUM(total),0) FROM returns WHERE returnType='PURCHASE' AND createdAt>=:from AND createdAt<:to) AS purchaseReturns,
            (SELECT COALESCE(SUM(amount),0) FROM expenses WHERE isReversed=0 AND createdAt>=:from AND createdAt<:to) AS expenses
    """)
    fun observeFinancialSummary(from: Long, to: Long): Flow<FinancialReportSummary>

    @Query("""
        SELECT
            (SELECT COALESCE(SUM(total),0) FROM sales_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to) AS sales,
            (SELECT COUNT(*) FROM sales_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to) AS salesCount,
            (SELECT COALESCE(SUM(total),0) FROM purchase_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to) AS purchases,
            (SELECT COUNT(*) FROM purchase_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to) AS purchaseCount,
            (SELECT COALESCE(SUM(total),0) FROM returns WHERE returnType='SALE' AND createdAt>=:from AND createdAt<:to) AS salesReturns,
            (SELECT COALESCE(SUM(total),0) FROM returns WHERE returnType='PURCHASE' AND createdAt>=:from AND createdAt<:to) AS purchaseReturns,
            (SELECT COALESCE(SUM(((si.unitCostSnapshot * si.quantityMilli + 500) / 1000)),0) FROM sales_invoice_items si INNER JOIN sales_invoices s ON s.id=si.invoiceId WHERE s.status='POSTED' AND s.createdAt>=:from AND s.createdAt<:to) AS cogs,
            (SELECT COALESCE(SUM(((si.unitCostSnapshot * ri.quantityMilli + 500) / 1000)),0) FROM return_items ri INNER JOIN returns r ON r.id=ri.returnId INNER JOIN sales_invoice_items si ON si.id=ri.originalInvoiceItemId WHERE r.returnType='SALE' AND r.createdAt>=:from AND r.createdAt<:to) AS returnedCogs,
            (SELECT COUNT(*) FROM sales_invoice_items si INNER JOIN sales_invoices s ON s.id=si.invoiceId WHERE s.status='POSTED' AND s.createdAt>=:from AND s.createdAt<:to AND si.unitCostSnapshot=0) AS missingCostItemsCount,
            (SELECT COALESCE(SUM(amount),0) FROM expenses WHERE isReversed=0 AND createdAt>=:from AND createdAt<:to) AS expenses,
            (SELECT COALESCE(SUM(amount),0) FROM cash_transactions WHERE direction='IN' AND createdAt>=:from AND createdAt<:to) AS cashIn,
            (SELECT COALESCE(SUM(amount),0) FROM cash_transactions WHERE direction='OUT' AND createdAt>=:from AND createdAt<:to) AS cashOut,
            (SELECT COALESCE(SUM(amount),0) FROM transfers WHERE direction='IN' AND createdAt>=:from AND createdAt<:to) AS transfersIn,
            (SELECT COALESCE(SUM(amount),0) FROM transfers WHERE direction='OUT' AND createdAt>=:from AND createdAt<:to) AS transfersOut,
            (SELECT COALESCE(SUM(fees),0) FROM transfers WHERE createdAt>=:from AND createdAt<:to) AS transferFees,
            (SELECT COALESCE(SUM(amount),0) FROM cash_transactions WHERE sourceType='CUSTOMER_PAYMENT' AND createdAt>=:from AND createdAt<:to) AS customerReceipts,
            (SELECT COALESCE(SUM(amount),0) FROM cash_transactions WHERE sourceType='SUPPLIER_PAYMENT' AND createdAt>=:from AND createdAt<:to) AS supplierPayments
    """)
    fun observeDetailedSummary(from: Long, to: Long): Flow<DetailedFinancialSummary>

    @Query("""
        SELECT i.id AS itemId,
               i.name AS itemName,
               COALESCE(SUM(CASE WHEN s.id IS NOT NULL THEN si.quantityMilli ELSE 0 END), 0) AS soldQuantityMilli,
               COALESCE(SUM(CASE WHEN s.id IS NOT NULL THEN si.lineTotal ELSE 0 END), 0) AS soldRevenue,
               i.quantityMilli AS currentQuantityMilli
        FROM items i
        LEFT JOIN sales_invoice_items si ON si.itemId=i.id
        LEFT JOIN sales_invoices s ON s.id=si.invoiceId AND s.status='POSTED' AND s.createdAt>=:from AND s.createdAt<:to
        WHERE i.isActive=1
        GROUP BY i.id, i.name, i.quantityMilli
        ORDER BY soldQuantityMilli DESC, soldRevenue DESC
    """)
    fun observeItemSalesSummary(from: Long, to: Long): Flow<List<ItemMovementSummary>>

    @Query("UPDATE sales_invoices SET status=:status WHERE id=:id AND status='POSTED'")
    suspend fun changePostedStatus(id: String, status: String): Int
}

@Dao
interface PurchaseInvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(invoice: PurchaseInvoiceEntity)
    @Query("SELECT * FROM purchase_invoices WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): PurchaseInvoiceEntity?
    @Query("SELECT * FROM purchase_invoices WHERE id=:id LIMIT 1")
    fun observeById(id: String): Flow<PurchaseInvoiceEntity?>
    @Query("UPDATE purchase_invoices SET status=:status WHERE id=:id AND status='POSTED'")
    suspend fun changePostedStatus(id: String, status: String): Int
    @Query("UPDATE purchase_invoices SET paid=:paid, remaining=:remaining WHERE id=:id")
    suspend fun updatePaymentAmounts(id: String, paid: Long, remaining: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLines(lines: List<PurchaseInvoiceItemEntity>)
    @Query("SELECT * FROM purchase_invoices ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<PurchaseInvoiceEntity>>
    @Query("SELECT * FROM purchase_invoices WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): PurchaseInvoiceEntity?
    @Query("SELECT * FROM purchase_invoice_items WHERE invoiceId=:invoiceId")
    suspend fun lines(invoiceId: String): List<PurchaseInvoiceItemEntity>
    @Query("SELECT * FROM purchase_invoice_items WHERE invoiceId=:invoiceId")
    fun observeLines(invoiceId: String): Flow<List<PurchaseInvoiceItemEntity>>
    @Query("SELECT COALESCE(MAX(CAST(invoiceNumber AS INTEGER)), 0) FROM purchase_invoices")
    suspend fun maxInvoiceNumber(): Long
    @Update suspend fun update(invoice: PurchaseInvoiceEntity): Int
    @Query("DELETE FROM purchase_invoice_items WHERE invoiceId=:invoiceId")
    suspend fun deleteLines(invoiceId: String): Int
    @Query("SELECT * FROM purchase_invoices WHERE supplierId=:supplierId ORDER BY createdAt DESC")
    fun observeForSupplier(supplierId: String): Flow<List<PurchaseInvoiceEntity>>
    @Query("SELECT * FROM purchase_invoices WHERE supplierId=:supplierId AND remaining>0 AND status='POSTED' ORDER BY createdAt ASC")
    fun observeOpenForSupplier(supplierId: String): Flow<List<PurchaseInvoiceEntity>>

    @Query("SELECT COALESCE(SUM(total),0) FROM purchase_invoices WHERE status='POSTED' AND createdAt>=:from AND createdAt<:to")
    fun observePurchaseTotal(from: Long, to: Long): Flow<Long>
}

@Dao
interface InvoicePaymentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: InvoicePaymentEntity)
    @Update suspend fun update(value: InvoicePaymentEntity): Int
    @Query("SELECT * FROM invoice_payments WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): InvoicePaymentEntity?
    @Query("SELECT * FROM invoice_payments WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): InvoicePaymentEntity?
    @Query("SELECT * FROM invoice_payments WHERE invoiceType=:invoiceType AND invoiceId=:invoiceId ORDER BY createdAt DESC")
    fun observeForInvoice(invoiceType: String, invoiceId: String): Flow<List<InvoicePaymentEntity>>
    @Query("SELECT * FROM invoice_payments WHERE invoiceType=:invoiceType AND invoiceId=:invoiceId ORDER BY createdAt ASC")
    suspend fun readForInvoiceSync(invoiceType: String, invoiceId: String): List<InvoicePaymentEntity>
    @Query("SELECT * FROM invoice_payments ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<InvoicePaymentEntity>>
}

@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomer(value: CustomerTransactionEntity)
    @Update suspend fun updateCustomer(value: CustomerTransactionEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSupplier(value: SupplierTransactionEntity)
    @Update suspend fun updateSupplier(value: SupplierTransactionEntity): Int
    @Query("SELECT * FROM customer_transactions WHERE id=:id LIMIT 1")
    suspend fun findCustomerById(id: String): CustomerTransactionEntity?
    @Query("SELECT * FROM supplier_transactions WHERE id=:id LIMIT 1")
    suspend fun findSupplierById(id: String): SupplierTransactionEntity?
    @Query("SELECT * FROM customer_transactions WHERE customerId=:id ORDER BY createdAt DESC")
    fun observeCustomer(id: String): Flow<List<CustomerTransactionEntity>>
    @Query("SELECT * FROM customer_transactions WHERE customerId=:id ORDER BY createdAt ASC")
    suspend fun readCustomerChronologicalSync(id: String): List<CustomerTransactionEntity>
    @Query("SELECT * FROM supplier_transactions WHERE supplierId=:id ORDER BY createdAt DESC")
    fun observeSupplier(id: String): Flow<List<SupplierTransactionEntity>>
    @Query("SELECT * FROM supplier_transactions WHERE supplierId=:id ORDER BY createdAt ASC")
    suspend fun readSupplierChronologicalSync(id: String): List<SupplierTransactionEntity>
    @Query("SELECT * FROM customer_transactions WHERE operationId=:operationId LIMIT 1")
    suspend fun findCustomerOperation(operationId: String): CustomerTransactionEntity?
    @Query("SELECT * FROM supplier_transactions WHERE operationId=:operationId LIMIT 1")
    suspend fun findSupplierOperation(operationId: String): SupplierTransactionEntity?
    @Query("SELECT * FROM customer_transactions WHERE sourceType=:sourceType AND sourceId=:sourceId LIMIT 1")
    suspend fun findCustomerBySource(sourceType: String, sourceId: String): CustomerTransactionEntity?
    @Query("SELECT * FROM supplier_transactions WHERE sourceType=:sourceType AND sourceId=:sourceId LIMIT 1")
    suspend fun findSupplierBySource(sourceType: String, sourceId: String): SupplierTransactionEntity?
}

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMovement(value: StockMovementEntity)
    @Query("SELECT * FROM stock_movements WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): StockMovementEntity?
    @Query("SELECT * FROM stock_movements WHERE itemId=:itemId ORDER BY createdAt DESC")
    fun observeMovements(itemId: String): Flow<List<StockMovementEntity>>
    @Query("SELECT * FROM stock_movements WHERE itemId=:itemId ORDER BY createdAt ASC")
    suspend fun readMovementsChronologicalSync(itemId: String): List<StockMovementEntity>
    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentMovements(limit: Int): Flow<List<StockMovementEntity>>
    @Query("SELECT * FROM stock_movements WHERE sourceType=:sourceType AND sourceId=:sourceId")
    suspend fun findBySource(sourceType: String, sourceId: String): List<StockMovementEntity>
}

@Dao
interface CashDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: CashTransactionEntity)
    @Query("SELECT * FROM cash_transactions ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun observePage(limit: Int, offset: Int): Flow<List<CashTransactionEntity>>
    @Query("SELECT * FROM cash_transactions WHERE createdAt>=:from AND createdAt<:to ORDER BY createdAt DESC")
    fun observeByDateRange(from: Long, to: Long): Flow<List<CashTransactionEntity>>
    @Query("SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0) FROM cash_transactions")
    fun observeBalance(): Flow<Long>
    @Query("SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0) FROM cash_transactions")
    suspend fun readBalanceSync(): Long
    @Query("SELECT * FROM cash_transactions WHERE idempotencyKey=:key LIMIT 1")
    suspend fun findByIdempotencyKey(key: String): CashTransactionEntity?

    @Query("SELECT * FROM cash_transactions WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): CashTransactionEntity?
    @Query("SELECT * FROM cash_transactions WHERE sourceType=:sourceType AND sourceId=:sourceId LIMIT 1")
    suspend fun findBySource(sourceType: String, sourceId: String): CashTransactionEntity?
    @Query("UPDATE cash_transactions SET amount=:amount, direction=:direction, note=:note WHERE id=:id")
    suspend fun updateEntry(id: String, direction: String, amount: Long, note: String?): Int
    @Query("DELETE FROM cash_transactions WHERE id=:id")
    suspend fun deleteById(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReconciliation(value: CashReconciliationEntity)
    @Query("SELECT * FROM cash_reconciliations WHERE operationId=:operationId LIMIT 1")
    suspend fun findReconciliationByOperationId(operationId: String): CashReconciliationEntity?
    @Query("SELECT * FROM cash_reconciliations ORDER BY createdAt DESC")
    fun observeReconciliations(): Flow<List<CashReconciliationEntity>>
}

@Dao
interface ReturnDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: ReturnEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertItems(values: List<ReturnItemEntity>)
    @Query("SELECT * FROM returns WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): ReturnEntity?
    @Query("SELECT COALESCE(SUM(ri.quantityMilli),0) FROM return_items ri INNER JOIN returns r ON r.id=ri.returnId WHERE r.originalInvoiceId=:invoiceId AND ri.originalInvoiceItemId=:invoiceItemId")
    suspend fun returnedQuantity(invoiceId: String, invoiceItemId: String): Long
    @Query("SELECT COALESCE(SUM(ri.amount),0) FROM return_items ri INNER JOIN returns r ON r.id=ri.returnId WHERE r.originalInvoiceId=:invoiceId AND ri.originalInvoiceItemId=:invoiceItemId")
    suspend fun returnedAmount(invoiceId: String, invoiceItemId: String): Long
    @Query("SELECT COALESCE(SUM(total),0) FROM returns WHERE originalInvoiceId=:invoiceId")
    suspend fun totalReturnedForInvoice(invoiceId: String): Long
    @Query("SELECT COUNT(*) FROM returns WHERE originalInvoiceId=:invoiceId")
    suspend fun countForInvoice(invoiceId: String): Int
    @Query("SELECT * FROM returns WHERE originalInvoiceId=:invoiceId ORDER BY createdAt DESC")
    fun observeForInvoice(invoiceId: String): Flow<List<ReturnEntity>>
    @Query("SELECT * FROM returns ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReturnEntity>>

    @Query("SELECT COALESCE(SUM(total),0) FROM returns WHERE returnType='SALE' AND createdAt>=:from AND createdAt<:to")
    fun observeSalesReturnsTotal(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(total),0) FROM returns WHERE returnType='PURCHASE' AND createdAt>=:from AND createdAt<:to")
    fun observePurchaseReturnsTotal(from: Long, to: Long): Flow<Long>
}

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: ExpenseEntity)
    @Update suspend fun updateEntity(value: ExpenseEntity): Int
    @Query("SELECT * FROM expenses WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): ExpenseEntity?
    @Query("SELECT * FROM expenses WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): ExpenseEntity?
    @Query("UPDATE expenses SET category=:category, amount=:amount, description=:description, updatedAt=:updatedAt WHERE id=:id")
    suspend fun update(id: String, category: String, amount: Long, description: String?, updatedAt: Long): Int
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE isReversed=0 AND createdAt>=:from AND createdAt<:to")
    fun observeTotal(from: Long, to: Long): Flow<Long>
}

@Dao
interface TransferDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: TransferEntity)
    @Update suspend fun updateEntity(value: TransferEntity): Int
    @Query("SELECT * FROM transfers WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): TransferEntity?
    @Query("SELECT * FROM transfers WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): TransferEntity?
    @Query("UPDATE transfers SET status=:status, note=:note WHERE id=:id")
    suspend fun updateStatus(id: String, status: String, note: String?): Int
    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>
}

@Dao
interface ReferenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategory(value: CategoryEntity)
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertUnit(value: UnitEntity)
    @Query("SELECT * FROM units ORDER BY name COLLATE NOCASE ASC")
    fun observeUnits(): Flow<List<UnitEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNote(value: NoteEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSetting(value: AppSettingEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertActivity(value: ActivityLogEntity)
    @Query("SELECT * FROM activity_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeActivityLogs(limit: Int): Flow<List<ActivityLogEntity>>
    @Query("SELECT * FROM activity_logs WHERE entityId=:entityId ORDER BY createdAt DESC")
    fun observeActivityForEntity(entityId: String): Flow<List<ActivityLogEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertUser(value: UserEntity)
    @Query("SELECT * FROM users WHERE username=:username LIMIT 1")
    suspend fun findUser(username: String): UserEntity?
    @Query("SELECT * FROM users WHERE id=:id LIMIT 1")
    suspend fun findUserById(id: String): UserEntity?
    @Query("UPDATE users SET role=:role, isActive=:isActive WHERE id=:id")
    suspend fun updateUserAccess(id: String, role: String, isActive: Boolean): Int
    @Query("UPDATE users SET role=:role, permissions=:permissions, isActive=:isActive WHERE id=:id")
    suspend fun updateUserAccessAndPermissions(id: String, role: String, permissions: String?, isActive: Boolean): Int
    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    fun observeUsers(): Flow<List<UserEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBackup(value: BackupHistoryEntity)
    @Query("SELECT * FROM backup_history ORDER BY createdAt DESC LIMIT 20")
    fun observeBackupHistory(): Flow<List<BackupHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReturn(value: ReturnEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReturnItems(values: List<ReturnItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun updateNote(value: NoteEntity)
    @Query("UPDATE notes SET isArchived=:archived, updatedAt=:updatedAt WHERE id=:id")
    suspend fun setNoteArchived(id: String, archived: Boolean, updatedAt: Long): Int
    @Query("DELETE FROM notes WHERE id=:id")
    suspend fun deleteNote(id: String): Int
    @Query("SELECT * FROM notes ORDER BY isArchived ASC, isPinned DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM app_settings WHERE key=:key LIMIT 1")
    suspend fun setting(key: String): AppSettingEntity?
    @Query("SELECT * FROM app_settings ORDER BY key ASC")
    fun observeSettings(): Flow<List<AppSettingEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertErrorLog(value: AppErrorLogEntity)
    @Query("SELECT * FROM app_error_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeErrorLogs(limit: Int): Flow<List<AppErrorLogEntity>>
    @Query("DELETE FROM app_error_logs")
    suspend fun clearErrorLogs(): Int
}

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEntry(entry: JournalEntryEntity)
    @Update suspend fun updateEntry(entry: JournalEntryEntity): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLines(lines: List<JournalEntryLineEntity>)
    @Query("DELETE FROM journal_entry_lines WHERE entryId=:entryId")
    suspend fun deleteLinesForEntry(entryId: String): Int
    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC, CAST(entryNumber AS INTEGER) DESC")
    fun observeAllEntries(): Flow<List<JournalEntryEntity>>
    @Query("SELECT * FROM journal_entry_lines ORDER BY id ASC")
    fun observeAllLines(): Flow<List<JournalEntryLineEntity>>
    @Query("SELECT * FROM journal_entry_lines WHERE entryId=:entryId")
    fun observeLinesForEntry(entryId: String): Flow<List<JournalEntryLineEntity>>
    @Query("SELECT * FROM journal_entry_lines WHERE entryId=:entryId")
    suspend fun readLinesForEntry(entryId: String): List<JournalEntryLineEntity>
    @Query("SELECT * FROM journal_entries WHERE id=:id LIMIT 1")
    suspend fun findById(id: String): JournalEntryEntity?
    @Query("SELECT * FROM journal_entries WHERE operationId=:operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): JournalEntryEntity?
    @Query("SELECT * FROM journal_entries WHERE sourceType=:sourceType AND sourceId=:sourceId AND status!='REVERSED' ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLatestBySource(sourceType: String, sourceId: String): JournalEntryEntity?
    @Query("SELECT COALESCE(MAX(CAST(entryNumber AS INTEGER)), 0) FROM journal_entries")
    suspend fun maxEntryNumber(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPeriod(period: AccountingPeriodEntity)
    @Update suspend fun updatePeriod(period: AccountingPeriodEntity): Int
    @Query("SELECT * FROM accounting_periods ORDER BY endAt DESC")
    fun observePeriods(): Flow<List<AccountingPeriodEntity>>
    @Query("SELECT * FROM accounting_periods WHERE isClosed=1 AND :timestamp>=startAt AND :timestamp<=endAt LIMIT 1")
    suspend fun findClosedPeriodAt(timestamp: Long): AccountingPeriodEntity?
}
