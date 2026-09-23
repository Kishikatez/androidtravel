package com.example.tripexpensemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import androidx.compose.ui.text.input.KeyboardType

private val ComponentActivity.userStore by preferencesDataStore("user_session")
private const val API_BASE_URL = "http://10.0.2.2:8000/"

@Serializable data class User(val userId: String, val name: String, val phone: String)
@Serializable data class UserCreate(val name: String, val phone: String)
@Serializable data class TripCreate(val tripName: String, val budgetPerPerson: Double, val userId: String)
@Serializable data class TripJoin(val tripId: String, val userId: String)
@Serializable data class TripSummary(val tripId: String, val tripName: String, val memberCount: Int, val totalBudget: Double, val totalSpent: Double, val remaining: Double)
@Serializable data class Expense(val expenseId: String = "", val name: String, val amount: Double, val description: String = "", val paidByName: String, val timestamp: String = "")
@Serializable data class ExpenseCreate(val name: String, val amount: Double, val description: String, val paidByUserId: String, val paidByName: String)
@Serializable data class Member(val userId: String, val name: String, val isAdmin: Boolean)

interface TripApi {
    @POST("users") suspend fun createUser(@Body user: UserCreate): User
    @POST("trips") suspend fun createTrip(@Body trip: TripCreate): TripSummary
    @POST("trips/join") suspend fun joinTrip(@Body trip: TripJoin): TripSummary
    @GET("trips/{id}") suspend fun getTrip(@Path("id") id: String): TripSummary
    @GET("trips/{id}/expenses") suspend fun getExpenses(@Path("id") id: String): List<Expense>
    @POST("trips/{id}/expenses") suspend fun addExpense(@Path("id") id: String, @Body expense: ExpenseCreate): Expense
    @GET("trips/{id}/members") suspend fun getMembers(@Path("id") id: String): List<Member>
}

private val api: TripApi = Retrofit.Builder()
    .baseUrl(API_BASE_URL)
    .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
    .build().create(TripApi::class.java)

class MainViewModel(private val loadUser: suspend () -> User?, private val saveUser: suspend (User) -> Unit) : ViewModel() {
    var user by mutableStateOf<User?>(null); private set
    var trip by mutableStateOf<TripSummary?>(null); private set
    var expenses by mutableStateOf<List<Expense>>(emptyList()); private set
    var members by mutableStateOf<List<Member>>(emptyList()); private set
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set

    init { viewModelScope.launch { user = loadUser() } }
    fun clearError() { error = null }
    private suspend fun request(block: suspend () -> Unit) { loading = true; error = null; try { block() } catch (exception: Exception) { error = exception.message ?: "Network unavailable" } finally { loading = false } }
    fun register(name: String, phone: String, onDone: () -> Unit) = viewModelScope.launch { request { val result = api.createUser(UserCreate(name.trim(), phone.trim())); saveUser(result); user = result; onDone() } }
    fun createTrip(name: String, budget: String, onDone: () -> Unit) = viewModelScope.launch { request { trip = api.createTrip(TripCreate(name.trim(), budget.toDouble(), user!!.userId)); refreshData(); onDone() } }
    fun joinTrip(code: String, onDone: () -> Unit) = viewModelScope.launch { request { trip = api.joinTrip(TripJoin(code.trim().uppercase(), user!!.userId)); refreshData(); onDone() } }
    fun refreshData() = viewModelScope.launch { request { val current = trip ?: return@request; trip = api.getTrip(current.tripId); expenses = api.getExpenses(current.tripId) } }
    fun loadMembers() = viewModelScope.launch { request { members = api.getMembers(trip!!.tripId) } }
    fun addExpense(name: String, amount: String, description: String, onDone: () -> Unit) = viewModelScope.launch { request { val current = trip!!; api.addExpense(current.tripId, ExpenseCreate(name.trim(), amount.toDouble(), description.trim(), user!!.userId, user!!.name)); refreshData(); onDone() } }
}

class MainViewModelFactory(private val activity: ComponentActivity) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
        loadUser = { val preferences = activity.userStore.data.first(); val id = preferences[stringPreferencesKey("id")]; if (id == null) null else User(id, preferences[stringPreferencesKey("name")] ?: "", preferences[stringPreferencesKey("phone")] ?: "") },
        saveUser = { user -> activity.userStore.edit { it[stringPreferencesKey("id")] = user.userId; it[stringPreferencesKey("name")] = user.name; it[stringPreferencesKey("phone")] = user.phone } }
    ) as T
}

private enum class Screen { HOME, CREATE, JOIN, DASHBOARD, EXPENSE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { val model: MainViewModel = viewModel(factory = MainViewModelFactory(this)); TripApp(model) } }
}

@Composable fun TripApp(model: MainViewModel) {
    val nav = rememberNavController()
    val start by remember(model.user) { mutableStateOf(if (model.user == null) "setup" else "home") }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B5B), secondary = Color(0xFFE7794D), background = Color(0xFFF7F8F4))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(nav, startDestination = start) {
                composable("setup") { SetupScreen(model) { nav.navigate("home") { popUpTo("setup") { inclusive = true } } } }
                composable("home") { HomeScreen(model, { nav.navigate("create") }, { nav.navigate("join") }) }
                composable("create") { FormScaffold("Create a trip", nav) { CreateTripScreen(model) { nav.navigate("dashboard") { popUpTo("home") } } } }
                composable("join") { FormScaffold("Join a trip", nav) { JoinTripScreen(model) { nav.navigate("dashboard") { popUpTo("home") } } } }
                composable("dashboard") { DashboardScreen(model, { nav.navigate("expense") }, { model.loadMembers(); nav.navigate("members") }) }
                composable("expense") { FormScaffold("Add expense", nav) { AddExpenseScreen(model) { nav.popBackStack() } } }
                composable("members") { MembersScreen(model, nav) }
            }
        }
    }
}

@Composable private fun SetupScreen(model: MainViewModel, done: () -> Unit) { var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; FormPage("Welcome to Trip Expense Manager", "Set up your local profile to start sharing expenses.") { Input("Your name", name) { name = it }; Input("Phone number", phone, KeyboardType.Phone) { phone = it }; PrimaryButton("Continue", name.isNotBlank() && phone.isNotBlank()) { model.register(name, phone, done) }; ErrorText(model.error) } }

@Composable private fun HomeScreen(model: MainViewModel, create: () -> Unit, join: () -> Unit) { FormPage("Ready for the trip?", "Create a shared budget or join one with a code.") { Text("Hi, ${model.user?.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(20.dp)); PrimaryButton("Create trip", true, create); OutlinedButton(onClick = join, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) { Text("Join with a trip ID") }; ErrorText(model.error) } }

@Composable private fun CreateTripScreen(model: MainViewModel, done: () -> Unit) { var name by remember { mutableStateOf("") }; var budget by remember { mutableStateOf("") }; Input("Trip name", name) { name = it }; Input("Budget per person (₹)", budget, KeyboardType.Number) { budget = it }; PrimaryButton("Create trip", name.isNotBlank() && budget.toDoubleOrNull() != null) { model.createTrip(name, budget, done) }; ErrorText(model.error) }
@Composable private fun JoinTripScreen(model: MainViewModel, done: () -> Unit) { var code by remember { mutableStateOf("") }; Input("Trip ID / join code", code) { code = it }; PrimaryButton("Join trip", code.isNotBlank()) { model.joinTrip(code, done) }; ErrorText(model.error) }
@Composable private fun AddExpenseScreen(model: MainViewModel, done: () -> Unit) { var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; Input("Expense name", name) { name = it }; Input("Amount (₹)", amount, KeyboardType.Decimal) { amount = it }; Input("Description (optional)", description) { description = it }; PrimaryButton("Add expense", name.isNotBlank() && amount.toDoubleOrNull() != null) { model.addExpense(name, amount, description, done) }; ErrorText(model.error) }

@Composable private fun DashboardScreen(model: MainViewModel, add: () -> Unit, members: () -> Unit) { val trip = model.trip; LaunchedEffect(Unit) { model.refreshData() }; Scaffold(floatingActionButton = { FloatingActionButton(onClick = add, containerColor = MaterialTheme.colorScheme.secondary) { Icon(Icons.Default.Add, "Add expense") } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text(trip?.tripName ?: "Trip", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(trip?.tripId ?: "", color = Color.Gray) }; Row { IconButton({ model.refreshData() }) { Icon(Icons.Default.Refresh, "Refresh") }; IconButton(members) { Icon(Icons.Default.People, "Members") } } }; Spacer(Modifier.height(8.dp)); Summary(trip) }; item { Text("Recent expenses", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; if (model.expenses.isEmpty()) item { Text("No expenses yet. Add the first one.", color = Color.Gray) } else items(model.expenses) { expense -> ExpenseRow(expense) }; item { Spacer(Modifier.height(80.dp)) } } } }

@Composable private fun Summary(trip: TripSummary?) { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F0E8)), shape = RoundedCornerShape(20.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Budget", trip?.totalBudget); Metric("Spent", trip?.totalSpent); Metric("Left", trip?.remaining) }; Text("${trip?.memberCount ?: 0} members", Modifier.padding(start = 18.dp, bottom = 16.dp), color = Color(0xFF176B5B), fontWeight = FontWeight.Bold) } }
@Composable private fun Metric(label: String, value: Double?) { Column { Text(label, color = Color.Gray, fontSize = 13.sp); Text("₹${String.format("%.0f", value ?: 0.0)}", fontWeight = FontWeight.Bold, fontSize = 18.sp) } }
@Composable private fun ExpenseRow(expense: Expense) { ListItem(headlineContent = { Text(expense.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${expense.paidByName}${if (expense.description.isNotBlank()) " · ${expense.description}" else ""}") }, trailingContent = { Text("₹${String.format("%.0f", expense.amount)}", fontWeight = FontWeight.Bold) }) }
@Composable private fun MembersScreen(model: MainViewModel, nav: NavHostController) { Scaffold(topBar = { TopAppBar(title = { Text("Trip members") }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding -> LazyColumn(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(model.members) { member -> ListItem(headlineContent = { Text(member.name) }, supportingContent = { Text(if (member.isAdmin) "Trip admin" else "Member") }) } } } }

@Composable private fun FormScaffold(title: String, nav: NavHostController, content: @Composable () -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding -> Column(Modifier.padding(padding).padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } } }
@Composable private fun FormPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Spacer(Modifier.height(44.dp)); Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.Gray); Spacer(Modifier.height(14.dp)); content() } }
@Composable private fun Input(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), shape = RoundedCornerShape(14.dp)) }
@Composable private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) { Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) { Text(label, fontSize = 16.sp) } }
@Composable private fun ErrorText(error: String?) { if (error != null) Text(error, color = MaterialTheme.colorScheme.error) }
