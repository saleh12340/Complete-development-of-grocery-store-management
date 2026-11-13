package com.saleh.alazzi.grocery.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saleh.alazzi.grocery.data.backup.GroceryBackupManager
import com.saleh.alazzi.grocery.ui.screens.*

private data class TopNavTab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun GroceryApp(
    viewModel: GroceryViewModel,
    backupManager: GroceryBackupManager
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val settings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val settingsMap = remember(settings) { settings.associate { it.key to it.value } }
    val storeName = settingsMap["store_name"]?.takeIf { it.isNotBlank() } ?: "بقالة العزي للمواد الغذائية"
    val storePhone = settingsMap["store_phone"].orEmpty()
    val storeAddress = settingsMap["store_address"].orEmpty()
    val storeFooter = settingsMap["receipt_footer"].orEmpty()
    val currency = settingsMap["store_currency"]?.takeIf { it.isNotBlank() } ?: "ريال"
    val themeMode = ThemeMode.entries.firstOrNull { it.key == settingsMap["theme_mode"] } ?: ThemeMode.SYSTEM
    val savedPrinterAddress = settingsMap["printer_address"]?.takeIf { it.isNotBlank() }
    val savedPrinterName = settingsMap["printer_name"]?.takeIf { it.isNotBlank() }
    val savedPaperWidth = settingsMap["printer_width"]?.toIntOrNull()?.takeIf { it == 384 || it == 576 } ?: 384

    if (users.isEmpty()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                AdminSetupScreen(onSetup = viewModel::createAdminUser)
            }
        }
        return
    }

    if (session == null) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                LoginScreen(storeName = storeName, onLogin = viewModel::login)
            }
        }
        return
    }

    val role = session!!.role
    val isManager = role == UserRoles.MANAGER
    val isCashierOrManager = role == UserRoles.MANAGER || role == UserRoles.CASHIER

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val tabs = remember(role) {
        buildList {
            add(TopNavTab("dashboard", "الرئيسية", Icons.Default.Dashboard))
            add(TopNavTab("customers", "العملاء", Icons.Default.People))
            add(TopNavTab("invoices", "الفواتير", Icons.AutoMirrored.Filled.ReceiptLong))
            add(TopNavTab("inventory", "المخزون", Icons.Default.Inventory2))
            if (isCashierOrManager) {
                add(TopNavTab("cash", "الصندوق", Icons.Default.AccountBalanceWallet))
            } else {
                add(TopNavTab("reports", "التقارير", Icons.Default.Assessment))
            }
        }
    }

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail && currentRoute in tabs.map { it.route }) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useRail) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "dashboard",
                    modifier = Modifier.weight(1f)
                ) {
                    composable("dashboard") {
                        DashboardScreen(
                            viewModel = viewModel,
                            storeName = storeName,
                            currency = currency,
                            onNavigate = { route -> navController.navigate(route) },
                            onOpenSaleDetail = { id -> navController.navigate("sales-invoice/$id") },
                            onOpenPurchaseDetail = { id -> navController.navigate("purchase-invoice/$id") },
                            onLogout = viewModel::logout
                        )
                    }

                    composable("customers") {
                        CustomersScreen(
                            viewModel = viewModel,
                            currency = currency,
                            onOpenCustomer = { id -> navController.navigate("customer/$id") },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        "customer/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { backStack ->
                        CustomerAccountScreen(
                            viewModel = viewModel,
                            customerId = backStack.arguments?.getString("id").orEmpty(),
                            currency = currency,
                            onOpenSaleInvoice = { invId -> navController.navigate("sales-invoice/$invId") },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("suppliers") {
                        if (isManager) {
                            SuppliersScreen(
                                viewModel = viewModel,
                                currency = currency,
                                onOpenSupplier = { id -> navController.navigate("supplier/$id") },
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable(
                        "supplier/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { backStack ->
                        if (isManager) {
                            SupplierAccountScreen(
                                viewModel = viewModel,
                                supplierId = backStack.arguments?.getString("id").orEmpty(),
                                currency = currency,
                                onOpenPurchaseInvoice = { invId -> navController.navigate("purchase-invoice/$invId") },
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("invoices") {
                        InvoiceHubScreen(
                            viewModel = viewModel,
                            onNewSale = { navController.navigate("new-sale") },
                            onNewPurchase = { navController.navigate("new-purchase") },
                            onSalesHistory = { navController.navigate("sales-history") },
                            onPurchaseHistory = { navController.navigate("purchase-history") },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("new-sale") {
                        if (isCashierOrManager) {
                            InvoiceEntryScreen(
                                viewModel = viewModel,
                                isSale = true,
                                storeName = storeName,
                                storePhone = storePhone,
                                storeAddress = storeAddress,
                                storeFooter = storeFooter,
                                currency = currency,
                                savedPrinterAddress = savedPrinterAddress,
                                savedPaperWidth = savedPaperWidth,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("new-purchase") {
                        if (isManager) {
                            InvoiceEntryScreen(
                                viewModel = viewModel,
                                isSale = false,
                                storeName = storeName,
                                storePhone = storePhone,
                                storeAddress = storeAddress,
                                storeFooter = storeFooter,
                                currency = currency,
                                savedPrinterAddress = savedPrinterAddress,
                                savedPaperWidth = savedPaperWidth,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("sales-history") {
                        SalesInvoiceHistoryScreen(
                            viewModel = viewModel,
                            currency = currency,
                            onOpenInvoice = { id -> navController.navigate("sales-invoice/$id") },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("purchase-history") {
                        if (isManager) {
                            PurchaseInvoiceHistoryScreen(
                                viewModel = viewModel,
                                currency = currency,
                                onOpenInvoice = { id -> navController.navigate("purchase-invoice/$id") },
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable(
                        "sales-invoice/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { backStack ->
                        SalesInvoiceDetailScreen(
                            viewModel = viewModel,
                            invoiceId = backStack.arguments?.getString("id").orEmpty(),
                            storeName = storeName,
                            currency = currency,
                            savedPrinterAddress = savedPrinterAddress,
                            savedPaperWidth = savedPaperWidth,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        "purchase-invoice/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { backStack ->
                        if (isManager) {
                            PurchaseInvoiceDetailScreen(
                                viewModel = viewModel,
                                invoiceId = backStack.arguments?.getString("id").orEmpty(),
                                storeName = storeName,
                                currency = currency,
                                savedPrinterAddress = savedPrinterAddress,
                                savedPaperWidth = savedPaperWidth,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("inventory") {
                        InventoryScreen(
                            viewModel = viewModel,
                            currency = currency,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("cash") {
                        if (isCashierOrManager) {
                            CashDrawerScreen(
                                viewModel = viewModel,
                                currency = currency,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("reports") {
                        ReportsScreen(
                            viewModel = viewModel,
                            storeName = storeName,
                            currency = currency,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("expenses") {
                        if (isManager) {
                            ExpensesScreen(viewModel = viewModel, currency = currency, onBack = { navController.popBackStack() })
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("transfers") {
                        if (isManager) {
                            TransfersScreen(viewModel = viewModel, currency = currency, onBack = { navController.popBackStack() })
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("notes") {
                        NotesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }

                    composable("printer") {
                        PrinterScreen(
                            viewModel = viewModel,
                            storeName = storeName,
                            savedPrinterAddress = savedPrinterAddress,
                            savedPrinterName = savedPrinterName,
                            savedPaperWidth = savedPaperWidth,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("backup") {
                        if (isManager) {
                            BackupScreen(viewModel = viewModel, backupManager = backupManager, onBack = { navController.popBackStack() })
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("users") {
                        if (isManager) {
                            UsersScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("audit") {
                        if (isManager) {
                            AuditAndDiagnosticsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }

                    composable("settings") {
                        if (isManager) {
                            StoreSettingsScreen(
                                viewModel = viewModel,
                                initialName = storeName,
                                initialPhone = storePhone,
                                initialAddress = storeAddress,
                                initialFooter = storeFooter,
                                initialCurrency = currency,
                                initialTheme = themeMode,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            AccessDeniedFallback { navController.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessDeniedFallback(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            com.saleh.alazzi.grocery.ui.components.SectionHeader("صلاحية غير متاحة", onBack = onBack)
            com.saleh.alazzi.grocery.ui.components.ErrorStateView(
                title = "الوصول مقيد",
                detail = "هذه الشاشة مخصصة لصلاحية المدير فقط"
            )
        }
    }
}
