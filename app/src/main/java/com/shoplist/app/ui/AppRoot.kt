package com.shoplist.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shoplist.app.ui.common.AddProductDialog
import com.shoplist.app.ui.history.HistoryScreen
import com.shoplist.app.ui.home.HomeScreen
import com.shoplist.app.ui.products.ProductsScreen
import com.shoplist.app.ui.settings.SettingsScreen
import com.shoplist.app.ui.shopping.ShopScreen
import com.shoplist.app.ui.stores.BranchScreen
import com.shoplist.app.ui.stores.StoreDetailScreen
import com.shoplist.app.ui.stores.StoresScreen
import com.shoplist.app.ui.theme.ShopListTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "בית", Icons.Default.Home),
    Tab("shop", "קנייה", Icons.Default.ShoppingCart),
    Tab("stores", "סופרים", Icons.Default.Place),
    Tab("products", "מוצרים", Icons.Default.List),
    Tab("settings", "הגדרות", Icons.Default.Settings)
)

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(startRoute: String?, onRouteHandled: () -> Unit, vm: AppViewModel = viewModel()) {
    val mode by vm.themeMode.collectAsState()
    ShopListTheme(mode) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        var showAdd by remember { mutableStateOf(false) }

        LaunchedEffect(startRoute) {
            if (startRoute != null) {
                nav.goTab(startRoute)
                onRouteHandled()
            }
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (tabs.any { it.route == route }) {
                        NavigationBar {
                            tabs.forEach { t ->
                                NavigationBarItem(
                                    selected = route == t.route,
                                    onClick = { nav.goTab(t.route) },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) }
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (route in listOf("home", "shop", "stores", "products")) {
                        FloatingActionButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, contentDescription = "הוסף מוצר לרשימה")
                        }
                    }
                }
            ) { padding ->
                NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                    composable("home") {
                        HomeScreen(vm, onOpenShop = { nav.goTab("shop") }, onAddClick = { showAdd = true })
                    }
                    composable("shop") {
                        ShopScreen(
                            vm,
                            onGoStores = { nav.goTab("stores") },
                            onHistory = { nav.navigate("history") }
                        )
                    }
                    composable("stores") { StoresScreen(vm, onOpenStore = { nav.navigate("store/$it") }) }
                    composable("products") { ProductsScreen(vm) }
                    composable("settings") { SettingsScreen(vm, onHistory = { nav.navigate("history") }) }
                    composable("history") { HistoryScreen(vm, onBack = { nav.popBackStack() }) }
                    composable(
                        "store/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry ->
                        StoreDetailScreen(
                            vm,
                            storeId = entry.arguments?.getLong("id") ?: -1L,
                            onBack = { nav.popBackStack() },
                            onOpenBranch = { nav.navigate("branch/$it") }
                        )
                    }
                    composable(
                        "branch/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType })
                    ) { entry ->
                        BranchScreen(
                            vm,
                            branchId = entry.arguments?.getLong("id") ?: -1L,
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
            }
        }

        if (showAdd) {
            AddProductDialog(
                onSubmit = {
                    vm.add(it)
                    showAdd = false
                },
                onDismiss = { showAdd = false }
            )
        }
    }
}
