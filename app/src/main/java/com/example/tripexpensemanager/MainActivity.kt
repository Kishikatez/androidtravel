@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.tripexpensemanager

import android.os.Bundle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType
import retrofit2.Retrofit
import retrofit2.http.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

private val ComponentActivity.userStore by preferencesDataStore("user_session")
private const val API_BASE_URL = "https://devoted-perfection-production-e006.up.railway.app/"

@Serializable data class User(val userId: String, val name: String, val phone: String)
@Serializable data class UserCreate(val name: String, val phone: String)
@Serializable data class TripCreate(val tripName: String, val budgetPerPerson: Double, val peopleCount: Int, val userId: String)
@Serializable data class TripSettingsUpdate(val peopleCount: Int)
@Serializable data class TripSummary(val tripId: String, val tripName: String, val memberCount: Int, val peopleCount: Int, val totalBudget: Double, val totalSpent: Double, val remaining: Double, val createdBy: String)
@Serializable data class TripJoin(val tripId: String, val userId: String)
@Serializable data class Expense(val expenseId: String = "", val name: String, val amount: Double, val description: String = "", val paidByName: String, val timestamp: String = "", val clientExpenseId: String? = null)
@Serializable data class ExpenseCreate(val name: String, val amount: Double, val description: String, val paidByUserId: String, val paidByName: String, val timestamp: String? = null, val clientExpenseId: String? = null)
@Serializable data class ExpenseUpdate(val name: String, val amount: Double, val description: String = "")
@Serializable data class Member(val userId: String, val name: String, val isAdmin: Boolean)

interface TripApi {
    @POST("users") suspend fun createUser(@Body user: UserCreate): User
    @POST("trips") suspend fun createTrip(@Body trip: TripCreate): TripSummary
    @PATCH("trips/{id}/settings") suspend fun updateTripSettings(@Path("id") id: String, @Query("user_id") userId: String, @Body settings: TripSettingsUpdate): TripSummary
    @POST("trips/join") suspend fun joinTrip(@Body trip: TripJoin): TripSummary
    @GET("trips/{id}") suspend fun getTrip(@Path("id") id: String): TripSummary
    @GET("trips/{id}/expenses") suspend fun getExpenses(@Path("id") id: String): List<Expense>
    @POST("trips/{id}/expenses") suspend fun addExpense(@Path("id") id: String, @Body expense: ExpenseCreate): Expense
    @PUT("trips/{tripId}/expenses/{expenseId}") suspend fun updateExpense(@Path("tripId") tripId: String, @Path("expenseId") expenseId: String, @Query("user_id") userId: String, @Body expense: ExpenseUpdate): Expense
    @DELETE("trips/{tripId}/expenses/{expenseId}") suspend fun deleteExpense(@Path("tripId") tripId: String, @Path("expenseId") expenseId: String, @Query("user_id") userId: String)
    @GET("trips/{id}/members") suspend fun getMembers(@Path("id") id: String): List<Member>
    @DELETE("trips/{tripId}/members/{userId}") suspend fun leaveTrip(@Path("tripId") tripId: String, @Path("userId") userId: String)
}

private val api: TripApi = Retrofit.Builder()
    .baseUrl(API_BASE_URL)
    .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory(MediaType.parse("application/json")!!))
    .build().create(TripApi::class.java)

class MainViewModel(
    private val loadUser: suspend () -> User?,
    private val saveUser: suspend (User) -> Unit,
    private val loadTrip: suspend () -> TripSummary?,
    private val saveTrip: suspend (TripSummary?) -> Unit,
    private val loadExpenses: suspend () -> List<Expense>,
    private val saveExpenses: suspend (List<Expense>) -> Unit,
    private val loadPending: suspend () -> List<ExpenseCreate>,
    private val savePending: suspend (List<ExpenseCreate>) -> Unit,
    private val clearTripSession: suspend () -> Unit
) : ViewModel() {
    var user by mutableStateOf<User?>(null); private set
    var trip by mutableStateOf<TripSummary?>(null); private set
    var expenses by mutableStateOf<List<Expense>>(emptyList()); private set
    var members by mutableStateOf<List<Member>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var initialized by mutableStateOf(false); private set
    private val syncMutex = Mutex()

    init { viewModelScope.launch { user = loadUser(); trip = loadTrip(); expenses = loadExpenses(); initialized = true; while (true) { delay(15000); if (trip != null) syncData() } } }
    fun clearError() { error = null }
    private suspend fun request(block: suspend () -> Unit) { loading = true; error = null; try { block() } catch (exception: Exception) { error = exception.message ?: "Network unavailable" } finally { loading = false } }
    fun register(name: String, phone: String, onDone: () -> Unit) = viewModelScope.launch { request { val result = api.createUser(UserCreate(name.trim(), phone.trim())); saveUser(result); user = result; onDone() } }
    fun createTrip(name: String, budget: String, peopleCount: String, onDone: () -> Unit) = viewModelScope.launch { request { trip = api.createTrip(TripCreate(name.trim(), budget.toDouble(), peopleCount.toInt(), user!!.userId)); saveTrip(trip); syncData(); onDone() } }
    fun updatePeopleCount(peopleCount: String) = viewModelScope.launch { request { trip = api.updateTripSettings(trip!!.tripId, user!!.userId, TripSettingsUpdate(peopleCount.toInt())); saveTrip(trip) } }
    fun joinTrip(code: String, onDone: () -> Unit) = viewModelScope.launch { request { trip = api.joinTrip(TripJoin(code.trim().uppercase(), user!!.userId)); saveTrip(trip); syncData(); onDone() } }
    private suspend fun syncData() = syncMutex.withLock {
        val current = trip ?: return
        var pending = loadPending()
        pending.forEach { queued ->
            try {
                api.addExpense(current.tripId, queued)
                pending = pending - queued
            } catch (_: Exception) { return@forEach }
        }
        try {
            trip = api.getTrip(current.tripId)
            val serverExpenses = api.getExpenses(current.tripId)
            val pendingIds = pending.mapNotNull { it.clientExpenseId }.toSet()
            expenses = serverExpenses + expenses.filter { it.expenseId.startsWith("local-") && it.clientExpenseId in pendingIds }
        } catch (_: Exception) { }
        saveTrip(trip)
        saveExpenses(expenses)
        savePending(pending)
    }
    fun refreshData() = viewModelScope.launch { request { syncData() } }
    fun loadMembers() = viewModelScope.launch { request { members = api.getMembers(trip!!.tripId) } }
    fun addExpense(name: String, amount: String, description: String, onDone: () -> Unit) = viewModelScope.launch {
        request {
            val clientId = UUID.randomUUID().toString()
            val queued = ExpenseCreate(name.trim(), amount.toDouble(), description.trim(), user!!.userId, user!!.name, Instant.now().toString(), clientId)
            expenses = expenses + Expense("local-$clientId", queued.name, queued.amount, queued.description, queued.paidByName, queued.timestamp ?: "", clientId)
            saveExpenses(expenses); savePending(loadPending() + queued)
            syncData(); onDone()
        }
    }
    fun updateExpense(expense: Expense, name: String, amount: String, description: String, onDone: () -> Unit) = viewModelScope.launch {
        request {
            val value = amount.toDouble()
            if (expense.expenseId.startsWith("local-")) {
                val clientId = expense.clientExpenseId
                val pending = loadPending().map { queued -> if (queued.clientExpenseId == clientId) queued.copy(name = name.trim(), amount = value, description = description.trim()) else queued }
                expenses = expenses.map { current -> if (current.expenseId == expense.expenseId) current.copy(name = name.trim(), amount = value, description = description.trim()) else current }
                savePending(pending)
            } else {
                val updated = api.updateExpense(trip!!.tripId, expense.expenseId, user!!.userId, ExpenseUpdate(name.trim(), value, description.trim()))
                expenses = expenses.map { current -> if (current.expenseId == updated.expenseId) updated else current }
            }
            saveExpenses(expenses); onDone()
        }
    }
    fun deleteExpense(expense: Expense, onDone: () -> Unit) = viewModelScope.launch {
        request {
            if (!expense.expenseId.startsWith("local-")) api.deleteExpense(trip!!.tripId, expense.expenseId, user!!.userId)
            savePending(loadPending().filter { it.clientExpenseId != expense.clientExpenseId })
            expenses = expenses.filter { it.expenseId != expense.expenseId }
            saveExpenses(expenses); onDone()
        }
    }
    fun leaveTrip(onDone: () -> Unit) = viewModelScope.launch { request { api.leaveTrip(trip!!.tripId, user!!.userId); trip = null; expenses = emptyList(); saveTrip(null); clearTripSession(); onDone() } }
}

class MainViewModelFactory(private val activity: ComponentActivity) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
        loadUser = { val preferences = activity.userStore.data.first(); val id = preferences[stringPreferencesKey("id")]; if (id == null) null else User(id, preferences[stringPreferencesKey("name")] ?: "", preferences[stringPreferencesKey("phone")] ?: "") },
        saveUser = { user -> activity.userStore.edit { it[stringPreferencesKey("id")] = user.userId; it[stringPreferencesKey("name")] = user.name; it[stringPreferencesKey("phone")] = user.phone } },
        loadTrip = { activity.userStore.data.first()[stringPreferencesKey("trip")]?.let { Json.decodeFromString<TripSummary>(it) } },
        saveTrip = { trip -> activity.userStore.edit { preferences -> if (trip == null) preferences.remove(stringPreferencesKey("trip")) else preferences[stringPreferencesKey("trip")] = Json.encodeToString(trip) } },
        loadExpenses = { activity.userStore.data.first()[stringPreferencesKey("expenses")]?.let { Json.decodeFromString<List<Expense>>(it) } ?: emptyList() },
        saveExpenses = { expenses -> activity.userStore.edit { it[stringPreferencesKey("expenses")] = Json.encodeToString(expenses) } },
        loadPending = { activity.userStore.data.first()[stringPreferencesKey("pending_expenses")]?.let { Json.decodeFromString<List<ExpenseCreate>>(it) } ?: emptyList() },
        savePending = { pending -> activity.userStore.edit { it[stringPreferencesKey("pending_expenses")] = Json.encodeToString(pending) } },
        clearTripSession = { activity.userStore.edit { it.remove(stringPreferencesKey("expenses")); it.remove(stringPreferencesKey("pending_expenses")) } }
    ) as T
}

private enum class Screen { HOME, CREATE, JOIN, DASHBOARD, EXPENSE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { installSplashScreen(); super.onCreate(savedInstanceState); setContent { val model: MainViewModel = viewModel(factory = MainViewModelFactory(this)); TripApp(model) } }
}

@Composable fun TripApp(model: MainViewModel) {
    val nav = rememberNavController()
    var showBrandSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1400); showBrandSplash = false }
    if (showBrandSplash) {
        BrandSplash()
        return
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B5B), secondary = Color(0xFFE7794D), background = Color(0xFFF7F8F4))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!model.initialized) return@Surface
            val start = if (model.user == null) "setup" else if (model.trip != null) "dashboard" else "home"
            NavHost(nav, startDestination = start) {
                composable("setup") { SetupScreen(model) { nav.navigate("home") { popUpTo("setup") { inclusive = true } } } }
                composable("home") { HomeScreen(model, { nav.navigate("create") }, { nav.navigate("join") }) }
                composable("create") { FormScaffold("Create a trip", nav) { CreateTripScreen(model) { nav.navigate("dashboard") { popUpTo("home") } } } }
                composable("join") { FormScaffold("Join a trip", nav) { JoinTripScreen(model) { nav.navigate("dashboard") { popUpTo("home") } } } }
                composable("dashboard") { DashboardScreen(model, { nav.navigate("expense") }, { model.loadMembers(); nav.navigate("members") }) { nav.navigate("home") { popUpTo("dashboard") { inclusive = true } } } }
                composable("expense") { FormScaffold("Add expense", nav) { AddExpenseScreen(model) { nav.popBackStack() } } }
                composable("members") { MembersScreen(model, nav) }
            }
        }
    }
}

@Composable private fun BrandSplash() {
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F8F4)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(com.example.tripexpensemanager.R.drawable.ic_fing_logo), contentDescription = null, modifier = Modifier.size(82.dp))
        Spacer(Modifier.height(18.dp))
        Text("Fing", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color(0xFF176B5B))
        Text("by kishikatez", fontSize = 14.sp, color = Color(0xFF6B756F))
    }
}

@Composable private fun SetupScreen(model: MainViewModel, done: () -> Unit) { var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; FormPage("Welcome to Trip Expense Manager", "Set up your local profile to start sharing expenses.") { Input("Your name", name) { name = it }; Input("Phone number", phone, KeyboardType.Phone) { phone = it }; PrimaryButton("Continue", name.isNotBlank() && phone.isNotBlank()) { model.register(name, phone, done) }; ErrorText(model.error) } }

@Composable private fun HomeScreen(model: MainViewModel, create: () -> Unit, join: () -> Unit) { FormPage("Ready for the trip?", "Create a shared budget or join one with a code.") { Text("Hi, ${model.user?.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(20.dp)); PrimaryButton("Create trip", true, create); OutlinedButton(onClick = join, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) { Text("Join with a trip ID") }; ErrorText(model.error) } }

@Composable private fun CreateTripScreen(model: MainViewModel, done: () -> Unit) { var name by remember { mutableStateOf("") }; var budget by remember { mutableStateOf("") }; var peopleCount by remember { mutableStateOf("1") }; Input("Trip name", name) { name = it }; Input("Budget per person (₹)", budget, KeyboardType.Number) { budget = it }; Input("Total people on the trip", peopleCount, KeyboardType.Number) { peopleCount = it }; PrimaryButton("Create trip", name.isNotBlank() && budget.toDoubleOrNull() != null && peopleCount.toIntOrNull() != null && peopleCount.toInt() > 0) { model.createTrip(name, budget, peopleCount, done) }; ErrorText(model.error) }
@Composable private fun JoinTripScreen(model: MainViewModel, done: () -> Unit) { var code by remember { mutableStateOf("") }; Input("Trip ID / join code", code) { code = it }; PrimaryButton("Join trip", code.isNotBlank()) { model.joinTrip(code, done) }; ErrorText(model.error) }
@Composable private fun AddExpenseScreen(model: MainViewModel, done: () -> Unit) { var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; Input("Expense name", name) { name = it }; Input("Amount (₹)", amount, KeyboardType.Decimal) { amount = it }; Input("Description (optional)", description) { description = it }; PrimaryButton("Add expense", name.isNotBlank() && amount.toDoubleOrNull() != null) { model.addExpense(name, amount, description, done) }; ErrorText(model.error) }

@Composable private fun DashboardScreen(model: MainViewModel, add: () -> Unit, members: () -> Unit) { val trip = model.trip; var editingPeopleCount by remember { mutableStateOf(false) }; var peopleCount by remember(trip?.peopleCount) { mutableStateOf(trip?.peopleCount?.toString() ?: "1") }; LaunchedEffect(Unit) { model.refreshData() }; if (editingPeopleCount) { AlertDialog(onDismissRequest = { editingPeopleCount = false }, title = { Text("Trip people") }, text = { Input("Total people on the trip", peopleCount, KeyboardType.Number) { peopleCount = it } }, confirmButton = { TextButton(onClick = { if (peopleCount.toIntOrNull() ?: 0 > 0) { model.updatePeopleCount(peopleCount); editingPeopleCount = false } }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editingPeopleCount = false }) { Text("Cancel") } }) }; Scaffold(floatingActionButton = { FloatingActionButton(onClick = add, containerColor = MaterialTheme.colorScheme.secondary) { Icon(Icons.Default.Add, "Add expense") } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(trip?.tripName ?: "Trip", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(trip?.tripId ?: "", color = Color.Gray) }; Row { IconButton({ model.refreshData() }) { Icon(Icons.Default.Refresh, "Refresh") }; IconButton(members) { Icon(Icons.Default.Person, "Members") } } }; Spacer(Modifier.height(8.dp)); Summary(trip); if (trip?.createdBy == model.user?.userId) { TextButton(onClick = { editingPeopleCount = true }) { Text("Change total people") } } }; item { Text("Recent expenses", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; if (model.expenses.isEmpty()) item { Text("No expenses yet. Add the first one.", color = Color.Gray) } else items(model.expenses, key = { it.expenseId }) { expense -> ExpenseRow(expense, onEdit = { name, amount, description -> model.updateExpense(expense, name, amount, description) {} }, onDelete = { model.deleteExpense(expense) {} }) }; item { Spacer(Modifier.height(80.dp)) } } } }

@Composable private fun Summary(trip: TripSummary?) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F0E8)), shape = RoundedCornerShape(20.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Budget", trip?.totalBudget); Metric("Spent", trip?.totalSpent); Metric("Left", trip?.remaining) }; Text("${trip?.peopleCount ?: 0} people planned · ${trip?.memberCount ?: 0} using app", Modifier.padding(start = 18.dp, bottom = 16.dp), color = Color(0xFF176B5B), fontWeight = FontWeight.Bold) } }
@Composable private fun Metric(label: String, value: Double?) { Column { Text(label, color = Color.Gray, fontSize = 13.sp); Text("₹${String.format("%.0f", value ?: 0.0)}", fontWeight = FontWeight.Bold, fontSize = 18.sp) } }
@Composable private fun ExpenseRow(expense: Expense, onEdit: (String, String, String) -> Unit, onDelete: () -> Unit) {
    var actions by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var name by remember(expense) { mutableStateOf(expense.name) }
    var amount by remember(expense) { mutableStateOf(expense.amount.toString()) }
    var description by remember(expense) { mutableStateOf(expense.description) }
    if (actions) AlertDialog(
        onDismissRequest = { actions = false },
        title = { Text(expense.name) },
        text = { Text("Edit or delete this expense for everyone in the trip.") },
        confirmButton = { TextButton(onClick = { actions = false; editing = true }) { Text("Edit") } },
        dismissButton = { TextButton(onClick = { actions = false; deleting = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }
    )
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Edit expense") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Input("Expense name", name) { name = it }; Input("Amount", amount, KeyboardType.Decimal) { amount = it }; Input("Description", description) { description = it } } }, confirmButton = { TextButton(enabled = name.isNotBlank() && amount.toDoubleOrNull() != null, onClick = { onEdit(name, amount, description); editing = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete expense?") }, text = { Text("This removes the expense for everyone in the trip.") }, confirmButton = { TextButton(onClick = { onDelete(); deleting = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
    ListItem(
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { actions = true }),
        headlineContent = { Text(expense.name, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(listOfNotNull(expense.paidByName, expense.description.takeIf { it.isNotBlank() }, formatTimestamp(expense.timestamp).takeIf { it.isNotBlank() }).joinToString(" · ")) },
        trailingContent = { Text("₹${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
    )
}

private fun formatTimestamp(value: String): String {
    if (value.isBlank()) return ""
    return try {
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
    } catch (_: Exception) {
        value
    }
}
@Composable private fun MembersScreen(model: MainViewModel, nav: NavHostController) { Scaffold(topBar = { TopAppBar(title = { Text("Trip members") }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding -> LazyColumn(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(model.members) { member -> ListItem(headlineContent = { Text(member.name) }, supportingContent = { Text(if (member.isAdmin) "Trip admin" else "Member") }) } } } }

@Composable private fun FormScaffold(title: String, nav: NavHostController, content: @Composable () -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding -> Column(Modifier.padding(padding).padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } } }
@Composable private fun FormPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Spacer(Modifier.height(44.dp)); Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.Gray); Spacer(Modifier.height(14.dp)); content() } }
@Composable private fun Input(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), shape = RoundedCornerShape(14.dp)) }
@Composable private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) { Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) { Text(label, fontSize = 16.sp) } }
@Composable private fun ErrorText(error: String?) { if (error != null) Text(error, color = MaterialTheme.colorScheme.error) }
@Composable private fun DashboardScreen(model: MainViewModel, add: () -> Unit, members: () -> Unit, leave: () -> Unit) { Box(Modifier.fillMaxSize()) { DashboardScreen(model, add, members); TextButton(onClick = leave, modifier = Modifier.align(Alignment.BottomCenter), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Leave trip") } } }
