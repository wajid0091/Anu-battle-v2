package com.example.ui

import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.ContextWrapper
import android.app.Activity
import java.util.*


fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}

fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EsportsApp(viewModel: EsportsViewModel) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    var activeScreen by remember { mutableStateOf("home") }
    var previousScreen by remember { mutableStateOf("home") }
    var inAdminMode by remember { mutableStateOf(false) }
    var inNotificationsMode by remember { mutableStateOf(false) }
    var selectedTournamentId by remember { mutableStateOf<String?>(null) }

    val showMessage by viewModel.depositRequestSuccess.collectAsStateWithLifecycle()

    LaunchedEffect(showMessage) {
        showMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearDepositStatusText()
        }
    }

    if (userState == null) {
        // Authentication Gate
        LoginRegisterScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            loginError = loginError
        )
    } else {
        val user = userState!!
        
        Scaffold(
            bottomBar = {
                if (!inAdminMode && !inNotificationsMode && selectedTournamentId == null) {
                    Column {
                        HorizontalDivider(
                            color = DarkAccent.copy(alpha = 0.8f),
                            thickness = 1.dp
                        )
                        NavigationBar(
                            containerColor = ElegantNavBg,
                            tonalElevation = 8.dp,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                        val navItems = listOf(
                            NavigationItem("home", "Home", Icons.Default.Home, Icons.Outlined.Home),
                            NavigationItem("games", "Games", Icons.Default.SportsEsports, Icons.Outlined.SportsEsports),
                            NavigationItem("store", "Store", Icons.Default.ShoppingBag, Icons.Outlined.ShoppingBag),
                            NavigationItem("rewards", "Rewards", Icons.Default.Star, Icons.Outlined.Star),
                            NavigationItem("profile", "Profile", Icons.Default.Person, Icons.Outlined.Person)
                        )

                        navItems.forEach { item ->
                            val isSelected = activeScreen == item.id
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    previousScreen = activeScreen
                                    activeScreen = item.id
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                                        contentDescription = item.label,
                                        tint = if (isSelected) NeonGold else GrayText
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        color = if (isSelected) NeonGold else GrayText,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = CharcoalCard
                                )
                            )
                        }
                    }
                }
            }
        }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CharcoalBg)
            ) {
                if (inAdminMode && (user.isAdmin || user.isHostManager)) {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AdminDashboardScreen(
                            viewModel = viewModel,
                            onBack = { inAdminMode = false }
                        )
                    }
                } else if (inNotificationsMode) {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NotificationsScreen(
                            viewModel = viewModel,
                            onBack = { inNotificationsMode = false }
                        )
                    }
                } else if (selectedTournamentId != null) {
                    TournamentDetailScreen(
                        tournamentId = selectedTournamentId!!,
                        viewModel = viewModel,
                        onBack = { selectedTournamentId = null }
                    )
                } else {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header Box details
                            HeaderBox(
                                user = user,
                                viewModel = viewModel,
                                onAdminClick = { if (user.isAdmin || user.isHostManager) inAdminMode = true },
                                onNotificationClick = { inNotificationsMode = true }
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                when (activeScreen) {
                                    "home" -> HomeScreen(
                                        viewModel = viewModel,
                                        onNavigate = { 
                                            previousScreen = activeScreen
                                            activeScreen = it 
                                        },
                                        onSelectTournament = { selectedTournamentId = it }
                                    )
                                    "games" -> GamesScreen(
                                        viewModel = viewModel,
                                        onSelectTournament = { selectedTournamentId = it }
                                    )
                                    "store" -> StoreScreen(viewModel = viewModel)
                                    "rewards" -> RewardsScreen(viewModel = viewModel)
                                    "profile" -> ProfileScreen(viewModel = viewModel, onLogout = { viewModel.logout() }, onNavigateToAdmin = { inAdminMode = true }, onNavigateToReferrals = { 
                                        previousScreen = activeScreen
                                        activeScreen = "referrals" 
                                    })
                                    "referrals" -> ReferralsScreen(viewModel = viewModel, onBack = { activeScreen = previousScreen })
                                }
                            }
                            // Global Unity Banner Ad shown above Bottom Navigation
                            UnityBannerAd()
                        }
                    }
                }
            }
        }
    }
}

data class NavigationItem(
    val id: String,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

// ============================================
// COMPONENT 1: HEADER BOX
// ============================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderBox(
    user: UserEntity,
    viewModel: EsportsViewModel,
    onAdminClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount = notifications.count { !it.isRead }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            brush = Brush.radialGradient(listOf(NeonOrange, NeonGold)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.name.take(1).uppercase(),
                        color = CharcoalBg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = user.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "UID: ${if (user.gameUid.isNotBlank()) user.gameUid else user.emailKey.take(8)}",
                        color = Color.Gray,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Wallet Chips
                Card(
                    onClick = { /* Wallet view action */ },
                    colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Wallet",
                            tint = NeonGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Rs.${String.format("%.1f", user.mainWallet + user.bonusWallet + user.winningWallet)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "Coins",
                            tint = NeonOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${user.coins.toInt()}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (user.isAdmin) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onAdminClick,
                        modifier = Modifier
                            .background(NeonGold.copy(alpha = 0.2f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Console",
                            tint = NeonGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onNotificationClick) {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge(containerColor = NeonOrange) {
                                    Text(unreadCount.toString(), color = Color.White)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Alerts",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// SCREEN 1: LOGIN & REGISTER GATE
// ============================================
@Composable
fun LoginRegisterScreen(
    viewModel: EsportsViewModel,
    isLoading: Boolean,
    loginError: String?
) {
    val context = LocalContext.current
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var gameUid by remember { mutableStateOf("") }
    var referCode by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    val termsTxt by viewModel.termsText.collectAsStateWithLifecycle()
    val privacyTxt by viewModel.privacyText.collectAsStateWithLifecycle()

    if (showPrivacyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", color = NeonGold) },
            text = { 
                Text(
                    text = if (privacyTxt.isNotBlank()) privacyTxt else "No privacy policy set yet.", 
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) 
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showPrivacyDialog = false }) { Text("Close", color = NeonGold) }
            },
            containerColor = CharcoalBg
        )
    }

    if (showTermsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms & Conditions", color = NeonGold) },
            text = { 
                Text(
                    text = if (termsTxt.isNotBlank()) termsTxt else "No terms set yet.", 
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) 
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showTermsDialog = false }) { Text("Close", color = NeonGold) }
            },
            containerColor = CharcoalBg
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo Icon Container
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(
                        brush = Brush.verticalGradient(listOf(NeonOrange, NeonGold)),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "Anu battle",
                    tint = CharcoalBg,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ANU BATTLE",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Dynamic Live Esports Arena",
                color = NeonGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRegisterMode) "Create New Account" else "Sign In to Lobbies",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    if (isRegisterMode) {
                        // 1. Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("In-Game Handle / Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Email
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Password
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                val description = if (passwordVisible) "Hide password" else "Show password"
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, description, tint = NeonGold)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 4. Confirm Password
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(imageVector = image, "Toggle confirmation visibility", tint = NeonGold)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 5. Game UID
                        OutlinedTextField(
                            value = gameUid,
                            onValueChange = { gameUid = it },
                            label = { Text("Game UID") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 6. Refer Code (Optional)
                        OutlinedTextField(
                            value = referCode,
                            onValueChange = { referCode = it },
                            label = { Text("Refer Code (Optional)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                        )

                    } else {
                        // Login Mode
                        // Email Field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGold,
                                unfocusedBorderColor = GrayText,
                                focusedLabelColor = NeonGold,
                                unfocusedLabelColor = GrayText,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, "Toggle visibility", tint = NeonGold)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }

                    if (loginError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = loginError,
                            color = Color.Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("By continuing, you agree to our ", color = GrayText, fontSize = 11.sp)
                        Text(
                            "Terms",
                            color = NeonGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showTermsDialog = true }.padding(horizontal = 2.dp)
                        )
                        Text("& ", color = GrayText, fontSize = 11.sp)
                        Text(
                            "Privacy Policy",
                            color = NeonGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showPrivacyDialog = true }.padding(horizontal = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator(color = NeonGold)
                    } else {
                        Button(
                            onClick = {
                                if (isRegisterMode) {
                                    if (name.isBlank() || email.isBlank() || password.isBlank() || gameUid.isBlank()) {
                                        viewModel.setLoginError("Please fill out all required fields!")
                                        return@Button
                                    }
                                    if (password != confirmPassword) {
                                        viewModel.setLoginError("Passwords do not match!")
                                        return@Button
                                    }
                                    viewModel.register(email, name, password, gameUid, referCode)
                                } else {
                                    if (email.isBlank() || password.isBlank()) {
                                        viewModel.setLoginError("Please enter your email and password!")
                                        return@Button
                                    }
                                    viewModel.login(email, password)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_action")
                        ) {
                            Text(
                                text = if (isRegisterMode) "REGISTER" else "LOGIN",
                                color = CharcoalBg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = {
                isRegisterMode = !isRegisterMode
                viewModel.setLoginError(null)
            }) {
                Text(
                    text = if (isRegisterMode) "Already registered? Login Here" else "Join the arena! Register Here",
                    color = NeonGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ============================================
// TAB SCREEN: HOME TABS / BANNER ACTIONS
// ============================================
@Composable
fun HomeScreen(viewModel: EsportsViewModel, onNavigate: (String) -> Unit, onSelectTournament: (String) -> Unit) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    
    val openMatches = tournaments.filter { it.status == "OPEN" }
    
    val promos by viewModel.promoSliders.collectAsStateWithLifecycle(initialValue = emptyList())
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Broadcast Banners Layout
        item {
            if (promos.isNotEmpty()) {
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { promos.size })
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(140.dp).padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) { page ->
                    val promo = promos[page]
                    Card(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp).clickable {
                            if (promo.actionUrl.isNotBlank()) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(promo.actionUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Invalid link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                    ) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(promo.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = promo.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
            } else {
                // Fallback Banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(CharcoalCard, NeonOrange.copy(alpha = 0.15f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "OFFICIAL BANNER",
                                color = NeonOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Anu Battle Tournament Series Launch!",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Watch daily rewarded videos, gather coin multipliers, and claim free entries into Premium Battle Royales.",
                                color = GrayText,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Fast Action Buttons grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FastGridButton(
                    title = "Invite Friend",
                    icon = Icons.Default.PersonAdd,
                    color = NeonGold,
                    onClick = { onNavigate("referrals") }
                )
                FastGridButton(
                    title = "Gold Store",
                    icon = Icons.Default.ShoppingBag,
                    color = NeonOrange,
                    onClick = { onNavigate("store") }
                )
                FastGridButton(
                    title = "Challenge Tasks",
                    icon = Icons.Default.Task,
                    color = MintGreen,
                    onClick = { onNavigate("rewards") }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Active Lobby stats heading
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Battle Arenas",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onNavigate("games") }) {
                    Text(text = "See All", color = NeonGold, fontSize = 13.sp)
                }
            }
        }

        if (openMatches.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No open lobbies available at the moment.",
                            color = GrayText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(openMatches.take(3)) { match ->
                TournamentCard(
                    tournament = match,
                    user = user,
                    onClick = { onSelectTournament(match.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}

@Composable
fun RowScope.FastGridButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .padding(4.dp)
            .height(84.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================
// TAB SCREEN: TOURNEY GAMES LOBBY (COMPOSABLE FILTERED GRID)
// ============================================
@Composable
fun GamesScreen(viewModel: EsportsViewModel, onSelectTournament: (String) -> Unit) {
    val context = LocalContext.current
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    val cooldownedInfo by viewModel.cooldowns.collectAsStateWithLifecycle()
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    
    var gameFilter by remember { mutableStateOf("All") }
    var statusFilter by remember { mutableStateOf("All") }

    val games = listOf("All", "Free Fire", "Clash Squad", "Lone Wolf")
    val statuses = listOf("All", "OPEN", "UPCOMING", "LIVE", "COMPLETED")

    val filteredList = tournaments.filter { match ->
        val gMatch = gameFilter == "All" || match.gameType.lowercase().contains(gameFilter.lowercase())
        val sMatch = statusFilter == "All" || match.status == statusFilter
        gMatch && sMatch
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Categories horizontal scrolls
        item {
            Text(
                text = "Select Tournament Game",
                color = GrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                games.forEach { game ->
                    val selected = gameFilter == game
                    Card(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable { gameFilter = game },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) NeonGold else CharcoalCard
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = game,
                            color = if (selected) CharcoalBg else Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                statuses.forEach { status ->
                    val selected = statusFilter == status
                    Card(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable { statusFilter = status },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) NeonOrange else CharcoalCard
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = status,
                            color = if (selected) CharcoalBg else Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No games matched current filter parameters.",
                            color = GrayText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredList) { match ->
                TournamentCard(
                    tournament = match,
                    user = user,
                    onClick = { onSelectTournament(match.id) }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

// ============================================
// WIDGET: INDIVIDUAL TOURNAMENT CARD
// ============================================
@Composable
fun TournamentCard(
    tournament: TournamentEntity,
    user: UserEntity?,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, NeonGold.copy(alpha = 0.2f))
    ) {
        Column {
            // Header Image/Gradient Block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                if (tournament.bannerUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(tournament.bannerUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Tournament Banner",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient dark overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, CharcoalCard.copy(alpha = 0.95f)),
                                    startY = 60f
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(NeonOrange.copy(alpha = 0.45f), CharcoalCard)
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Status Badge (Upper Left)
                    val isRegUpcoming = System.currentTimeMillis() < tournament.registrationOpenTimeMillis
                    val displayStatus = if (isRegUpcoming) "UPCOMING" else tournament.status
                    val badgeColor = when (displayStatus) {
                        "COMPLETED" -> Color.Gray
                        "UPCOMING" -> NeonOrange
                        "LIVE" -> Color.Red
                        else -> MintGreen
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = displayStatus,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Title & Description (Overlay on Image Bottom)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tournament.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${tournament.gameType} Tournament • Map: ${tournament.mapType}",
                                color = Color.LightGray.copy(alpha = 0.9f),
                                fontSize = 11.sp
                            )
                        }
                        
                        if (displayStatus == "OPEN") {
                            Button(
                                onClick = onClick,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("JOIN", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Stats footer (Bottom Black/Charcoal area matching second picture perfectly!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C1322)) // Blackish color
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "PRIZE POOL", color = GrayText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (tournament.prizeCurrency == "COINS") "${tournament.prizePool.toInt()} Coins" else "Rs.${tournament.prizePool.toInt()}",

                            color = NeonGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "ENTRY FEE", color = GrayText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        val entryText = when (tournament.entryCurrency) {
                            "COINS" -> "${tournament.entryFee.toInt()} Coins"
                            "FREE" -> "FREE"
                            else -> if (tournament.entryFee == 0.0) "FREE" else "Rs.${tournament.entryFee.toInt()}"
                        }
                            Text(
                                text = entryText,
                                color = Color.White,
                                fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "SLOTS FILLED", color = GrayText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${tournament.slotsFilled}/${tournament.totalSlots}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// WIDGET: FULL SCREEN TOURNAMENT DETAILS SCREEN
// ============================================
@Composable
fun TournamentDetailScreen(
    tournamentId: String,
    viewModel: EsportsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    val tournament = tournaments.find { it.id == tournamentId } ?: return
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val user = userState ?: return
    val cooldowns by viewModel.cooldowns.collectAsStateWithLifecycle()
    val remainingCd = cooldowns[tournament.id] ?: 0

    val adProgressList by viewModel.tournamentAdProgress.collectAsStateWithLifecycle()
    val adsWatchedForRegister = adProgressList.find { it.tournamentId == tournamentId }?.watchedCount ?: 0

    val isUserJoined = user.joinedTournaments.contains(tournament.id)
    val clipboardManager = LocalClipboardManager.current
    
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val winnersTransactions = transactions.filter { it.type.startsWith("TOURNAMENT_WIN") && it.id.contains("_${tournament.id}_") }.sortedByDescending { if (it.type.contains("COIN")) it.coins else it.amount }

    val now = System.currentTimeMillis()
    val timeToMatch = tournament.scheduleTimeMillis - now
    val isScheduleUnlockReady = timeToMatch <= 600000 // 10 minutes prior

    Scaffold(
        bottomBar = {
            Surface(
                color = CharcoalCard,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tournament.status == "COMPLETED") {
                        Text(
                            text = "TOURNAMENT COMPLETED",
                            color = NeonGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MintGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (System.currentTimeMillis() < tournament.registrationOpenTimeMillis) {
                        Text(
                            text = "UPCOMING REGISTRATION",
                            color = NeonOrange,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Upcoming",
                            tint = NeonOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Column {
                            Text(
                                text = "Entry Ticket cost:",
                                color = GrayText,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val entryText = when (tournament.entryCurrency) {
                                "COINS" -> "${tournament.entryFee.toInt()} Coins"
                                "FREE" -> "FREE"
                                else -> if (tournament.entryFee == 0.0) "FREE" else "Rs.${tournament.entryFee.toInt()}"
                            }
                            Text(
                                text = entryText,
                                color = NeonGold,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (isUserJoined) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MintGreen.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "REGISTERED",
                                    color = MintGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        } else {
                            val adRequiredButNotDone = tournament.entryCurrency == "FREE" && tournament.adsRequired > 0 && adsWatchedForRegister < tournament.adsRequired
                            if (adRequiredButNotDone) {
                                Button(
                                    onClick = {
                                        if (!isNetworkAvailable(context)) {
                                            Toast.makeText(context, "No Internet Connection!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (remainingCd > 0) {
                                            Toast.makeText(context, "Please wait ${remainingCd} seconds before watching the next ad.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val activity = context.findActivity()
                                            if (activity != null) {
                                                viewModel.showUnityRewardedAd(activity, tournament.id) { success ->
                                                    if (success) {
                                                        viewModel.incrementTournamentAdWatched(tournament.id)
                                                        Toast.makeText(context, "Ad completed! (${adsWatchedForRegister + 1}/${tournament.adsRequired})", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "Ad failed to load. Please try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(start = 16.dp).height(44.dp)
                                ) {
                                    Text("WATCH AD (${adsWatchedForRegister}/${tournament.adsRequired})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (!isNetworkAvailable(context)) {
                                            Toast.makeText(context, "No Internet Connection!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        viewModel.joinTournament(
                                            tournament = tournament,
                                            adCountWatched = adsWatchedForRegister,
                                            onSuccess = {
                                                Toast.makeText(context, "Successfully joined tournament lobby!", Toast.LENGTH_LONG).show()
                                                val activity = context.findActivity()
                                                if (activity != null) {
                                                    viewModel.showUnityInterstitialAd(activity)
                                                }
                                            },
                                            onError = { error ->
                                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(start = 16.dp).height(44.dp)
                                ) {
                                    Text(
                                        text = "SECURE SLOT",
                                        color = CharcoalBg,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(CharcoalBg)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Back navigation & Header Row
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(CharcoalCard, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = tournament.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Cover Banner Image
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, NeonGold.copy(alpha = 0.2f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (tournament.bannerUrl.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(tournament.bannerUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Tournament Banner",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(NeonOrange.copy(alpha = 0.45f), CharcoalBg)
                                        )
                                    )
                            )
                        }

                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                    )
                                )
                        )

                        // Badges + Text overlaid inside the banner
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = NeonOrange.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, NeonOrange),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = tournament.gameType.uppercase(),
                                    color = NeonOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = tournament.title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Mode: Solo • Map: ${tournament.mapType} • Format: Classic",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Horizontal Status Cards (Row of 3 cards)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Prize Pool
                    Card(
                        modifier = Modifier.weight(1.1f),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.DarkGray)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = NeonGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "PRIZE POOL",
                                color = GrayText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (tournament.prizeCurrency == "COINS") "${tournament.prizePool.toInt()} Coins" else "Rs.${tournament.prizePool.toInt()}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Card 2: Date & Time
                    val sdf = SimpleDateFormat("EE dd MMM", Locale.getDefault())
                    val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    val dateStr = sdf.format(Date(tournament.scheduleTimeMillis))
                    val timeStr = timeSdf.format(Date(tournament.scheduleTimeMillis))
                    Card(
                        modifier = Modifier.weight(1.2f),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.DarkGray)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = NeonOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "DATE & TIME",
                                color = GrayText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dateStr,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = timeStr,
                                color = Color.LightGray,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Card 3: Slots Remaining
                    val remainingSlots = (tournament.totalSlots - tournament.slotsFilled).coerceAtLeast(0)
                    Card(
                        modifier = Modifier.weight(1.1f),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.DarkGray)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MintGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "SLOTS REMAIN.",
                                color = GrayText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$remainingSlots left",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "(${tournament.slotsFilled} filled)",
                                color = Color.LightGray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Per Kill & Rank Allocation Info
            val hasPerKill = tournament.isPerKillEnabled && tournament.perKillPrize > 0.0
            if (tournament.showRewardIndex && (hasPerKill || tournament.rankPrizes.isNotBlank())) {
                item {
                    Text(
                        text = "Prize Description",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.DarkGray),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (hasPerKill) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Reward Per Kill", color = GrayText, fontSize = 13.sp)
                                    Text(if (tournament.currencyType == "COINS") "${tournament.perKillPrize.toInt()} Coins" else "Rs.${tournament.perKillPrize}", color = NeonGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (hasPerKill && tournament.rankPrizes.isNotBlank()) {
                                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                            }
                            if (tournament.rankPrizes.isNotBlank()) {
                                Text("Rank Allocation", color = GrayText, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                                val ranks = tournament.rankPrizes.split(",")
                                ranks.forEachIndexed { index, prizeStr ->
                                    val safePrize = prizeStr.trim()
                                    if (safePrize.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("#${index + 1} Rank Prizes", color = Color.LightGray, fontSize = 12.sp)
                                            val displayText = if (safePrize.contains("CASH", ignoreCase = true) || safePrize.contains("COIN", ignoreCase = true)) safePrize.uppercase() else if (tournament.prizeCurrency == "COINS") "$safePrize Coins" else "Rs.$safePrize"
                                            Text(displayText, color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Completed Winners section
            if (tournament.status == "COMPLETED" && winnersTransactions.isNotEmpty()) {
                item {
                    Text(
                        text = "Tournament Winners & Payouts",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            winnersTransactions.forEachIndexed { idx, tx ->
                                val winnerUser = allUsers.find { it.emailKey == tx.emailKey }
                                val iconTint = when (idx) {
                                    0 -> NeonGold
                                    1 -> Color(0xFFC0C0C0) // Silver
                                    2 -> Color(0xFFCD7F32) // Bronze
                                    else -> MintGreen
                                }
                                val rewardText = if (tx.type.contains("COIN")) "${tx.coins.toInt()} Coins" else "Rs.${tx.amount.toInt()}"
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = "Rank",
                                            tint = iconTint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = winnerUser?.name ?: "Unknown Player",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = rewardText,
                                        color = MintGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Credentials copy box if user joined
            if (isUserJoined) {
                item {
                    Text(
                        text = "Lobby Credentials",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (isScheduleUnlockReady) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Room ID: ${tournament.roomId.ifBlank { "Unassigned" }}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (tournament.roomId.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(tournament.roomId))
                                                Toast.makeText(context, "Room ID Copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = NeonGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Room Pass: ${tournament.roomPassword.ifBlank { "Unassigned" }}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (tournament.roomPassword.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(tournament.roomPassword))
                                                Toast.makeText(context, "Password Copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = NeonGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                val remainingMins = (timeToMatch / 60000).coerceAtLeast(0)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = GrayText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Lobby Credentials lock lifts in $remainingMins minutes. (10 mins prior to match)",
                                        color = GrayText,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Ads watch verification if required and not joined yet
            if (!isUserJoined && tournament.adsRequired > 0) {
                item {
                    Text(
                        text = "Registration Task Required",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeonGold.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "This lobby requires viewing sponsored videos to unlock the registration ticket.",
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Video Views: $adsWatchedForRegister / ${tournament.adsRequired}",
                                    color = NeonGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = {
                                        if (!isNetworkAvailable(context)) {
                                            Toast.makeText(context, "No Internet Connection!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (remainingCd > 0) {
                                            Toast.makeText(context, "Please wait ${remainingCd} seconds before watching the next ad.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val activity = context.findActivity()
                                            if (activity != null) {
                                                viewModel.showUnityRewardedAd(activity, tournament.id) { success ->
                                                    if (success) {
                                                        viewModel.incrementTournamentAdWatched(tournament.id)
                                                        Toast.makeText(context, "Ad completed! (${adsWatchedForRegister + 1}/${tournament.adsRequired})", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "Ad failed to load. Please try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = remainingCd == 0 && adsWatchedForRegister < tournament.adsRequired
                                ) {
                                    val adLabel = if (remainingCd > 0) "WAIT ${remainingCd}s" else "PLAY REWARDED VIDEO"
                                    Text(text = adLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Lobby Description Box
            item {
                Text(
                    text = "Lobby Description",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.DarkGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = tournament.description.ifBlank { "Admin assembled tournament for Anu Battle league." },
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Rewards Index Box
            item {
                Text(
                    text = "Rewards Index",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.DarkGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Kill Premium:", color = GrayText, fontSize = 12.sp)
                            Text(text = "Rs.10.0 per single kill", color = NeonGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Placement Allocations:", color = GrayText, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Rank 1: 50% of Pool (Rs.${tournament.prizePool * 0.5})\n" +
                                   "• Rank 2: 30% of Pool (Rs.${tournament.prizePool * 0.3})\n" +
                                   "• Rank 3: 20% of Pool (Rs.${tournament.prizePool * 0.2})",
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Terms & Standing Rules Box
            item {
                Text(
                    text = "Terms & Arena Standing Rules",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.DarkGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "1. No hacks or mods are allowed. Doing so results in a permanent ban.\n" +
                                   "2. Teaming is strictly banned. Players caught will be disqualified immediately.\n" +
                                   "3. Join custom lobby 10 minutes prior to match schedule to secure placement.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// TAB SCREEN: STORE / BALANCES OPERATIONS / VOUCHERS
// ============================================
@Composable
fun StoreScreen(viewModel: EsportsViewModel) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val user = userState ?: return

    val epTitle by viewModel.epTitle.collectAsStateWithLifecycle()
    val epNum by viewModel.epNumber.collectAsStateWithLifecycle()
    val jcTitle by viewModel.jcTitle.collectAsStateWithLifecycle()
    val jcNum by viewModel.jcNumber.collectAsStateWithLifecycle()
    val minWithdraw by viewModel.minWithdraw.collectAsStateWithLifecycle()
    val diamondPacks by viewModel.diamondPacks.collectAsStateWithLifecycle()

    var selectedDiamondPack by remember { mutableStateOf<com.example.data.DiamondPackEntity?>(null) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var depositAmountText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("Easypaisa") }
    var senderName by remember { mutableStateOf("") }
    var senderPhone by remember { mutableStateOf("") }
    var screenshotUrl by remember { mutableStateOf("") }
    var uploadingScreenshot by remember { mutableStateOf(false) }
    var showAllItems by remember { mutableStateOf(false) }

    if (showAllItems) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showAllItems = false }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(text = "All Redeem Items", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
            }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(diamondPacks) { pack ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            if (pack.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = pack.imageUrl,
                                    contentDescription = pack.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(60.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Diamond,
                                    contentDescription = "Diamonds",
                                    tint = NeonGold,
                                    modifier = Modifier.size(40.dp).padding(bottom = 8.dp)
                                )
                            }
                            Text(text = pack.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "${pack.coinCost} Coins", color = NeonOrange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { selectedDiamondPack = pack },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Redeem", color = CharcoalBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    } else {
    val screenshotPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingScreenshot = true
            val tempFile = java.io.File(context.cacheDir, "deposit_proof.jpg")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { out ->
                    inputStream.copyTo(out)
                }
            }
            viewModel.uploadImageToImgBB(tempFile, onSuccess = { url ->
                screenshotUrl = url
                uploadingScreenshot = false
                Toast.makeText(context, "Screenshot uploaded!", Toast.LENGTH_SHORT).show()
            }, onError = {
                uploadingScreenshot = false
                Toast.makeText(context, "Failed to upload screenshot.", Toast.LENGTH_SHORT).show()
            })
        }
    }

    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmountText by remember { mutableStateOf("") }
    
    var withdrawAccName by remember { mutableStateOf("") }
    var withdrawAccNum by remember { mutableStateOf("") }
    var withdrawMethod by remember { mutableStateOf("EasyPaisa") }
    var methodExpanded by remember { mutableStateOf(false) }
    
    // Diamond Redemption Dialog
    if (selectedDiamondPack != null) {
        val pack = selectedDiamondPack!!
        Dialog(onDismissRequest = { selectedDiamondPack = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Confirm Redemption", color = NeonGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("You are about to redeem ${pack.title}.", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Cost: ${pack.coinCost.toInt()} Coins", color = NeonOrange, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Target UID: ${user.gameUid}", color = MintGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { selectedDiamondPack = null }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                if (user.coins >= pack.coinCost) {
                                    Toast.makeText(context, "Redemption Request Submitted!", Toast.LENGTH_SHORT).show()
                                    viewModel.submitDiamondRedemption(pack.title, pack.coinCost)
                                    selectedDiamondPack = null
                                } else {
                                    Toast.makeText(context, "Not enough coins for this bundle!", Toast.LENGTH_SHORT).show()
                                    selectedDiamondPack = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold)
                        ) {
                            Text("SUBMIT", color = CharcoalBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        item {
            Text(
                text = "Wallet Operations",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Balances summary card
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Pocket Money Balance", color = GrayText, fontSize = 11.sp)
                    Text(
                        text = "Rs.${user.mainWallet}",
                        color = MintGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Bonus Wallet", color = GrayText, fontSize = 11.sp)
                            Text(
                                text = "Rs.${user.bonusWallet}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Winnings (Withdrawable)", color = GrayText, fontSize = 11.sp)
                            Text(
                                text = "Rs.${user.winningWallet}",
                                color = NeonGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = { showDepositDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Deposit")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Deposit Cash")
                        }

                        Button(
                            onClick = { showWithdrawDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Withdraw", tint = CharcoalBg)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Withdraw", color = CharcoalBg)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Redeem Diamonds (Free Fire)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (diamondPacks.size > 4) {
                    Text(
                        text = "View All",
                        color = NeonGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showAllItems = true }.padding(4.dp)
                    )
                }
            }
            Text(
                text = "Directly redeem your collected gameplay coins into Free Fire Diamond items.",
                color = GrayText,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        val displayPacks = if (diamondPacks.size > 4) diamondPacks.take(4) else diamondPacks
        val chunkedPacks = displayPacks.chunked(2)
        for (rowPacks in chunkedPacks) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (pack in rowPacks) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                if (pack.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = pack.imageUrl,
                                        contentDescription = pack.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(60.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Diamond,
                                        contentDescription = "Diamonds",
                                        tint = NeonGold,
                                        modifier = Modifier.size(40.dp).padding(bottom = 8.dp)
                                    )
                                }
                                Text(text = pack.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "${pack.coinCost} Coins", color = NeonOrange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { selectedDiamondPack = pack },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Redeem", color = CharcoalBg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (rowPacks.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Deposit dialogue modal
    if (showDepositDialog) {
        Dialog(onDismissRequest = { showDepositDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Deposit Cash Credits",
                        color = NeonGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = { selectedMethod = "Easypaisa" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedMethod == "Easypaisa") MintGreen else Color.DarkGray)
                        ) { Text("Easypaisa") }
                        Button(
                            onClick = { selectedMethod = "JazzCash" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedMethod == "JazzCash") MintGreen else Color.DarkGray)
                        ) { Text("JazzCash") }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Card(colors = CardDefaults.cardColors(containerColor = CharcoalBg), modifier = Modifier.fillMaxWidth().padding(bottom=10.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Send payment to:", color = GrayText, fontSize = 12.sp)
                            Text(if (selectedMethod == "Easypaisa") epTitle else jcTitle, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(if (selectedMethod == "Easypaisa") epNum else jcNum, color = NeonGold, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(value = depositAmountText, onValueChange = { depositAmountText = it }, label = { Text("Amount (Rs.)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeonGold), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom=6.dp))
                    OutlinedTextField(value = senderName, onValueChange = { senderName = it }, label = { Text("Sender Name") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeonGold), singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom=6.dp))
                    OutlinedTextField(value = senderPhone, onValueChange = { senderPhone = it }, label = { Text("Sender Phone/Account") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeonGold), singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom=12.dp))

                    Button(
                        onClick = { screenshotPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uploadingScreenshot) "UPLOADING..." else if (screenshotUrl.isNotEmpty()) "SCREENSHOT UPLOADED" else "UPLOAD SCREENSHOT", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { showDepositDialog = false }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                val amt = depositAmountText.toDoubleOrNull() ?: 0.0
                                if (amt > 0.0 && senderName.isNotBlank() && senderPhone.isNotBlank() && screenshotUrl.isNotBlank()) {
                                    viewModel.requestDeposit(amt, screenshotUrl, selectedMethod, senderPhone, senderName)
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:modspak4@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "New Deposit Request from ${user.name}")
                                        putExtra(Intent.EXTRA_TEXT, "User: ${user.email}\nName: $senderName\nPhone: $senderPhone\nMethod: $selectedMethod\nAmount: Rs.$amt\nScreenshot URL: $screenshotUrl\n\nPlease approve this from the Admin Panel.")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Send email to Admin"))
                                    showDepositDialog = false
                                    depositAmountText = ""
                                    senderName = ""
                                    senderPhone = ""
                                    screenshotUrl = ""
                                } else {
                                    Toast.makeText(context, "Please fill all fields and upload screenshot", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !uploadingScreenshot && screenshotUrl.isNotBlank() && depositAmountText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("SUBMIT REQUEST")
                        }
                    }
                }
            }
        }
    }

    // Withdraw dialogue modal
    if (showWithdrawDialog) {
        Dialog(onDismissRequest = { showWithdrawDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Withdraw Winnings",
                        color = NeonGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = withdrawAmountText,
                        onValueChange = { withdrawAmountText = it },
                        label = { Text("Amount (Rs.)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = withdrawAccName,
                        onValueChange = { withdrawAccName = it },
                        label = { Text("Account Holder Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = withdrawAccNum,
                        onValueChange = { withdrawAccNum = it },
                        label = { Text("Account Number") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Payment Method", color = GrayText, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("EasyPaisa", "JazzCash", "NayaPay", "SadaPay").forEach { method ->
                            androidx.compose.material3.FilterChip(
                                selected = withdrawMethod == method,
                                onClick = { withdrawMethod = method },
                                label = { Text(method) },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGold,
                                    selectedLabelColor = CharcoalBg,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { showWithdrawDialog = false }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                val amt = withdrawAmountText.toDoubleOrNull() ?: 0.0
                                if (withdrawAccName.isBlank() || withdrawAccNum.isBlank()) {
                                    Toast.makeText(context, "Please fill all details", Toast.LENGTH_SHORT).show()
                                } else if (amt >= minWithdraw.toDoubleOrNull() ?: 0.0) {
                                    val details = "Method: $withdrawMethod | Name: $withdrawAccName | Acc: $withdrawAccNum"
                                    viewModel.requestWithdraw(amt, details) { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                    if (user.winningWallet >= amt) { // Optional check before opening email
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:modspak4@gmail.com")
                                            putExtra(Intent.EXTRA_SUBJECT, "New Withdrawal Request from ${user.name}")
                                            putExtra(Intent.EXTRA_TEXT, "User: ${user.email}\n$details\nAmount: Rs.$amt\n\nPlease approve this from the Admin Panel.")
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Send email to Admin"))
                                    }
                                    showWithdrawDialog = false
                                    withdrawAmountText = ""
                                    withdrawAccName = ""
                                    withdrawAccNum = ""
                                } else {
                                    Toast.makeText(context, "Minimum withdrawal is $minWithdraw Rs", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("SUBMIT WITHDRAW")
                        }
                    }
                }
            }
        }
    }
}
}

data class VoucherPackage(
    val title: String,
    val description: String,
    val price: Double,
    val coinsYield: Double,
    val bonusYield: Double
)

// ============================================
// TAB SCREEN: DYNAMIC TASKS REWARDS / 7-DAY LOGIN CLAIMS
// ============================================
@Composable
fun RewardsScreen(viewModel: EsportsViewModel) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val user = userState ?: return
    
    val dailyTasks by viewModel.dailyTasks.collectAsStateWithLifecycle()
    val taskProgress by viewModel.taskProgress.collectAsStateWithLifecycle()
    val claimedDays by viewModel.claimedDays.collectAsStateWithLifecycle()
    val dbDailyRewards by viewModel.dbDailyRewards.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
        item {
            Text(
                text = "Daily Rewards Arena",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Text(
                text = "Claim consecutive daily bonuses. Resets if streak lapses.",
                color = GrayText,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Beautiful 7-Day rewards horizontal list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                val rewardsList = dbDailyRewards
                val now = System.currentTimeMillis()
                var currentDay = user?.dailyRewardDay ?: 1
                if (now - (user?.lastDailyRewardTime ?: 0L) > (2 * 24 * 60 * 60 * 1000L) && (user?.lastDailyRewardTime ?: 0L) > 0) {
                    currentDay = 1
                }
                
                for (day in 1..7) {
                    val claimed = day < currentDay
                    val isToday = day == currentDay
                    // The user can claim if it's 'today' and 24 hours have passed since last claim
                    val canClaimToday = isToday && (now - (user?.lastDailyRewardTime ?: 0L) >= (24 * 60 * 60 * 1000L) || (user?.lastDailyRewardTime ?: 0L) == 0L)
                    
                    Card(
                        modifier = Modifier
                            .width(84.dp)
                            .padding(end = 8.dp)
                            .clickable(enabled = canClaimToday) {
                                viewModel.claimDailyReward { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (claimed) MintGreen.copy(alpha = 0.2f) else if (canClaimToday) NeonGold.copy(alpha=0.1f) else CharcoalCard
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (claimed) MintGreenBorder else if(canClaimToday) NeonGold else Color.DarkGray
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Day $day",
                                color = if (claimed) MintGreen else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            if (claimed) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Claimed",
                                    tint = MintGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "CLAIMED",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            } else {
                                Icon(
                                    imageVector = if (canClaimToday) Icons.Default.Star else Icons.Default.Lock,
                                    contentDescription = "Coins Reward",
                                    tint = if (canClaimToday) NeonGold else GrayText,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "${rewardsList.getOrElse(day - 1) { 5.0 }.toInt()} Coins",
                                    color = if (canClaimToday) NeonGold else GrayText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            val cooldowns by viewModel.cooldowns.collectAsStateWithLifecycle()
            val remainingCd = cooldowns["rewards_wallet_watch"] ?: 0

            Card(
                colors = CardDefaults.cardColors(containerColor = if (remainingCd == 0) NeonGold.copy(alpha=0.15f) else Color.Gray.copy(alpha=0.15f)),
                border = BorderStroke(1.dp, if (remainingCd == 0) NeonGold else Color.Gray),
                modifier = Modifier.fillMaxWidth().clickable(enabled = remainingCd == 0) {
                    if (!isNetworkAvailable(context)) {
                        Toast.makeText(context, "No Internet Connection!", Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    val activity = context.findActivity()
                    if (activity != null) {
                        viewModel.showUnityRewardedAd(activity, "rewards_wallet_watch") { success ->
                            if (success) {
                                Toast.makeText(context, "You earned coins from watching an ad!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Failed to locate active rendering window context.", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Watch Ads to Earn 10,000+ Coins", color = if (remainingCd == 0) NeonGold else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (remainingCd > 0) "Cooldown active! Please wait ${remainingCd} seconds to watch again." else "Watch short video clips to gather coins for Diamond redemption.",
                            color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top=4.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = "Watch AD",
                        tint = NeonGold,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        item {
            Text(
                text = "Daily Tasks & Challenges",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
            )
            Text(
                text = "Complete challenges to earn reward coins directly into your wallet.",
                color = GrayText,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        val rewardTasks = dailyTasks.filter { it.taskType != "REFER" }

        if (rewardTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No active tasks available. Check back soon!", color = GrayText, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(rewardTasks) { task ->
                val progress = taskProgress.find { it.taskId == task.id }
                var currentVal = progress?.currentValue ?: 0
                var isClaimed = progress?.claimed == true || currentVal >= task.targetValue

                if (task.isDaily && progress != null) {
                    val now = System.currentTimeMillis()
                    val calNow = java.util.Calendar.getInstance()
                    calNow.timeInMillis = now
                    val strNow = "${calNow.get(java.util.Calendar.YEAR)}-${calNow.get(java.util.Calendar.DAY_OF_YEAR)}"

                    val calLast = java.util.Calendar.getInstance()
                    calLast.timeInMillis = progress.lastUpdated
                    val strLast = "${calLast.get(java.util.Calendar.YEAR)}-${calLast.get(java.util.Calendar.DAY_OF_YEAR)}"
                    
                    if (strNow != strLast) {
                        currentVal = 0
                        isClaimed = false
                    }
                }

                val percentProgress = if (task.targetValue > 0) currentVal.toFloat() / task.targetValue.toFloat() else 0f
                val clampedPercent = percentProgress.coerceIn(0f, 1f)

                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    border = BorderStroke(1.dp, if (isClaimed) MintGreen.copy(alpha=0.3f) else Color.DarkGray)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isClaimed) MintGreen.copy(alpha = 0.15f) else Color.DarkGray,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isClaimed) Icons.Default.CheckCircle else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isClaimed) MintGreen else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "+${task.coinReward.toInt()} Coins",
                                    color = NeonGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { clampedPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (isClaimed) MintGreen else NeonOrange,
                                trackColor = Color.DarkGray,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Progress: $currentVal / ${task.targetValue}",
                                    color = GrayText,
                                    fontSize = 11.sp
                                )
                                if (isClaimed) {
                                    Text(
                                        text = "CLAIMED",
                                        color = MintGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "In Progress",
                                        color = NeonOrange,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    } // end LazyColumn
    } // end Column wrapper
}

@Composable
fun ReferralsScreen(viewModel: EsportsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val user = userState ?: return

    var redeemCode by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Referral Center",
                color = NeonGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val dailyTasks by viewModel.dailyTasks.collectAsStateWithLifecycle()
            val taskProgress by viewModel.taskProgress.collectAsStateWithLifecycle()
            val referTasks = dailyTasks.filter { it.taskType == "REFER" }.sortedBy { it.targetValue }

            // Stats Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your Referral Stats", color = NeonGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Referrals", color = GrayText, fontSize = 12.sp)
                            Text("${user.totalReferrals}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Earnings (Coins)", color = GrayText, fontSize = 12.sp)
                            Text("${(viewModel.referCoinReward.value * user.totalReferrals).toInt()}", color = NeonGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Bonus Earned", color = GrayText, fontSize = 12.sp)
                            Text("Rs.${String.format("%.1f", viewModel.referCashReward.value * user.totalReferrals)}", color = MintGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // My Code Card
            Text("Your Referral Code", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = user.referCode,
                    onValueChange = {},
                    readOnly = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintGreen,
                        focusedTextColor = MintGreen,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                val inviteMessage = "Join the ultimate Esports tournaments on Anu Battle! \uD83C\uDFAE\uD83C\uDFC6\nUse my invite code: ${user.referCode}\nDownload the app here: https://anu-battle-tournament.freedev.app/"
                
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Invite Link", inviteMessage)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Invite link & code copied!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(48.dp).height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = CharcoalBg)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, inviteMessage)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share via"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(48.dp).height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
            }
            
            Text(
                "Share your code with friends. Once they sign up, you both get rewards!",
                color = GrayText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Invited Friends
            Text("Invited Friends", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
            val myInvitedUsers = allUsers.filter { it.referredBy == user.emailKey }
            
            if (myInvitedUsers.isEmpty()) {
                Text("You haven't invited anyone yet.", color = GrayText, fontSize = 14.sp)
            } else {
                myInvitedUsers.forEach { invited ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, NeonGold.copy(alpha=0.2f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(invited.name, color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Joined ✅", color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Redeem Code Card
            Text("Redeem a Code", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (user.referredBy.isNotBlank()) {
                Text("You have already redeemed a code (${user.referredBy}).", color = GrayText, fontSize = 14.sp)
            } else {
                OutlinedTextField(
                    value = redeemCode,
                    onValueChange = { redeemCode = it },
                    label = { Text("Friend's Referral Code") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (redeemCode.isNotBlank()) {
                            redeeming = true
                            viewModel.redeemReferralCode(redeemCode) { success, msg ->
                                redeeming = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (success) redeemCode = ""
                            }
                        }
                    },
                    enabled = !redeeming && redeemCode.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (redeeming) "REDEEMING..." else "REDEEM", color = CharcoalBg, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            if (referTasks.isNotEmpty()) {
                Text("Referral Milestones", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                referTasks.forEach { task ->
                    val progress = taskProgress.find { it.taskId == task.id }
                    val currentVal = user.totalReferrals
                    val isClaimed = progress?.claimed == true
                    val canClaim = currentVal >= task.targetValue && !isClaimed
                    
                    val percentProgress = if (task.targetValue > 0) currentVal.toFloat() / task.targetValue.toFloat() else 0f
                    val clampedPercent = percentProgress.coerceIn(0f, 1f)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        border = BorderStroke(1.dp, if (isClaimed) MintGreen.copy(alpha=0.3f) else Color.DarkGray)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isClaimed) MintGreen.copy(alpha = 0.15f) else Color.DarkGray,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isClaimed) Icons.Default.CheckCircle else Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (isClaimed) MintGreen else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = task.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "+${task.coinReward.toInt()} Coins",
                                        color = NeonGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { clampedPercent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (isClaimed) MintGreen else NeonOrange,
                                    trackColor = Color.DarkGray,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Progress: $currentVal / ${task.targetValue}",
                                        color = GrayText,
                                        fontSize = 11.sp
                                    )
                                    if (isClaimed) {
                                        Text("CLAIMED", color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    } else if (canClaim) {
                                        var claiming by remember { mutableStateOf(false) }
                                        Text(
                                            text = if (claiming) "CLAIMING..." else "CLAIM REWARD",
                                            color = NeonGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                if (!isNetworkAvailable(context)) {
                                                    Toast.makeText(context, "No Internet Connection!", Toast.LENGTH_SHORT).show()
                                                    return@clickable
                                                }
                                                if (!claiming) {
                                                    claiming = true
                                                    viewModel.manuallyClaimTaskReward(task.id) { _, msg ->
                                                        claiming = false
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        Text("In Progress", color = NeonOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
@Composable
fun ProfileScreen(viewModel: EsportsViewModel, onLogout: () -> Unit, onNavigateToAdmin: () -> Unit, onNavigateToReferrals: () -> Unit) {
    val context = LocalContext.current
    val userState by viewModel.currentUser.collectAsStateWithLifecycle()
    val user = userState ?: return

    var editUsername by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(user.name) }
    var gameUidInput by remember { mutableStateOf(user.gameUid) }

    var redeemCode by remember { mutableStateOf("") }
    var showTxHistory by remember { mutableStateOf(false) }

    var showPrivacyDialogProfile by remember { mutableStateOf(false) }
    val termsTxt by viewModel.termsText.collectAsStateWithLifecycle()
    val privacyTxt by viewModel.privacyText.collectAsStateWithLifecycle()
    val sptEmail by viewModel.supportEmail.collectAsStateWithLifecycle()

    var showTermsDialogProfile by remember { mutableStateOf(false) }

    if (showPrivacyDialogProfile) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPrivacyDialogProfile = false },
            title = { Text("Privacy Policy", color = NeonGold) },
            text = { 
                Text(
                    text = if (privacyTxt.isNotBlank()) privacyTxt else "No privacy policy set yet.", 
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) 
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showPrivacyDialogProfile = false }) { Text("Close", color = NeonGold) }
            },
            containerColor = CharcoalBg
        )
    }

    if (showTermsDialogProfile) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTermsDialogProfile = false },
            title = { Text("Terms & Conditions", color = NeonGold) },
            text = { 
                Text(
                    text = if (termsTxt.isNotBlank()) termsTxt else "No terms set yet.", 
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) 
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showTermsDialogProfile = false }) { Text("Close", color = NeonGold) }
            },
            containerColor = CharcoalBg
        )
    }

    if (showTxHistory) {
        val txs by viewModel.transactions.collectAsStateWithLifecycle()
        val userTxs = txs.filter { it.emailKey == user.emailKey }
        Dialog(onDismissRequest = { showTxHistory = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).padding(vertical = 24.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Transaction History", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (userTxs.isEmpty()) {
                        Text("No transactions found.", color = GrayText, fontSize = 14.sp)
                    } else {
                        LazyColumn {
                            items(userTxs) { tx ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                        Text(text = tx.type, color = NeonGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = tx.details, color = Color.White, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "Amount: Rs.${tx.amount} | Coins: ${tx.coins.toInt()}", color = MintGreen, fontSize = 12.sp)
                                            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                            Text(text = sdf.format(Date(tx.timestamp)), color = GrayText, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        brush = Brush.radialGradient(listOf(NeonOrange, NeonGold)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(1).uppercase(),
                    color = CharcoalBg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (user.isHostManager) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF9C27B0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("HOST MANAGER", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                text = user.email,
                color = GrayText,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Player statistics row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalCard, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatColumn(label = "Matches", value = "${user.matchesPlayed}")
                StatColumn(label = "Wins", value = "${user.matchesWon}")
                StatColumn(
                    label = "Earnings",
                    value = "Rs.${String.format("%.1f", user.mainWallet + user.winningWallet)}"
                )
                StatColumn(label = "Referrals", value = "${user.totalReferrals}")
            }
        }

        item {
            Text(
                text = "Personal Customizations",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp)
            )
        }

        // Custom action tiles
        item {
            ProfileTile(
                title = "Edit Profile Details",
                subtitle = "Change account display handles with custom avatars",
                icon = Icons.Default.Edit,
                onClick = { editUsername = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "Transaction History",
                subtitle = "View deposits, withdrawals, and tournament logs",
                icon = Icons.Default.ListAlt,
                onClick = { showTxHistory = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "My Match History",
                subtitle = "View details about your finished lobby scores",
                icon = Icons.Default.History,
                onClick = {
                    Toast.makeText(context, "No match history logs available.", Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "Refer & Claim Rewards",
                subtitle = "Share your code, redeem others, and earn",
                icon = Icons.Default.Share,
                onClick = {
                    onNavigateToReferrals()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "Contact Anu Battle Support",
                subtitle = "Lobby disputes and instant deposits helper details",
                icon = Icons.Default.Phone,
                onClick = {
                    Toast.makeText(context, "Support Email: ${if (sptEmail.isNotEmpty()) sptEmail else "assistance@anubattle.com"}", Toast.LENGTH_LONG).show()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "Privacy Policy",
                subtitle = "View how we manage and protect your data",
                icon = Icons.Default.Security,
                onClick = { showPrivacyDialogProfile = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileTile(
                title = "Terms & Conditions",
                subtitle = "Review our platform rules and policies",
                icon = Icons.Default.Description,
                onClick = { showTermsDialogProfile = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (user.isHostManager) {
                ProfileTile(
                    title = "Host Manager Panel",
                    subtitle = "Access tournament and user management",
                    icon = Icons.Default.AdminPanelSettings,
                    onClick = onNavigateToAdmin,
                    iconColor = Color(0xFF9C27B0)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            ProfileTile(
                title = "Logout from Arena",
                subtitle = "Clear auto registration states",
                icon = Icons.Default.Logout,
                onClick = onLogout,
                iconColor = Color.Red
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Edit username dialog modal
    if (editUsername) {
        Dialog(onDismissRequest = { editUsername = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalCard), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Edit Profile Details", color = NeonGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Display Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val now = System.currentTimeMillis()
                    val cooldownMillis = 30L * 24L * 60L * 60L * 1000L // 30 days
                    val canChangeUid = (now - user.lastGameUidChangeTime) > cooldownMillis
                    OutlinedTextField(
                        value = gameUidInput,
                        onValueChange = { gameUidInput = it },
                        label = { Text("Game UID") },
                        enabled = canChangeUid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, focusedBorderColor = NeonGold
                        ),
                        placeholder = { Text("Enter Free Fire UID") }
                    )
                    if (!canChangeUid) {
                        Text(
                            text = "Game UID can only be changed once every 30 days.",
                            color = Color.Red,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editUsername = false }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                if (renameInput.isNotBlank()) {
                                    val dbRef = FirebaseDatabase.getInstance().getReference("users").child(user.emailKey)
                                    dbRef.child("name").setValue(renameInput.trim())
                                    
                                    if (canChangeUid && gameUidInput.trim() != user.gameUid) {
                                        dbRef.child("gameUid").setValue(gameUidInput.trim())
                                        dbRef.child("lastGameUidChangeTime").setValue(System.currentTimeMillis())
                                    }
                                    
                                    editUsername = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("SAVE CHANGES")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = label, color = GrayText, fontSize = 11.sp)
    }
}

@Composable
fun ProfileTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    iconColor: Color = NeonGold
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = GrayText, fontSize = 10.sp)
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Forward",
                tint = GrayText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ============================================
// PANEL SCREEN: HIGH-FIDELITY RESPONSIVE BACKOFFICE ADMIN DASHBOARD
// ============================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: EsportsViewModel, onBack: () -> Unit) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val fullTabs = listOf("Users", "Add Tourney", "Task CRUD", "Diamond CRUD", "Settings", "Deposit/Withdraw Queue", "Promos")
    
    val adminTabs = remember(user?.isAdmin, user?.isHostManager, user?.hostTournaments, user?.hostWithdrawals) {
        if (user?.isAdmin == true) {
            fullTabs
        } else if (user?.isHostManager == true) {
            val allowed = mutableListOf<String>()
            // Host managers can no longer manage users or settings/announcements
            if (user?.hostTournaments == true) allowed.add("Add Tourney")
            if (user?.hostWithdrawals == true) allowed.add("Deposit/Withdraw Queue")
            allowed
        } else {
            emptyList()
        }
    }
    
    var adminTab by remember(adminTabs) { mutableStateOf(adminTabs.firstOrNull() ?: "Users") }

    LaunchedEffect(Unit) {
        viewModel.initiateAdminSync()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(CharcoalCard, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Backoffice Support Panel",
                color = NeonGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Tabs Header Selection Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            adminTabs.forEach { tab ->
                val active = adminTab == tab
                Card(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clickable { adminTab = tab },
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) NeonGold else CharcoalCard
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (active) CharcoalBg else Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Divider(color = NeonGold.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

        // Dynamic Subscreen Selector Panels
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            if (adminTabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("You do not have permission to view any pages.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                when (adminTab) {
                    "Users" -> AdminUsersTab(viewModel)
                    "Add Tourney" -> AdminTournamentsCreatorTab(viewModel)
                    "Task CRUD" -> AdminTaskCRUDTab(viewModel)
                    "Diamond CRUD" -> AdminDiamondCRUDTab(viewModel)
                    "Settings" -> AdminSettingsTab(viewModel)
                    "Deposit/Withdraw Queue" -> AdminTransactionsQueueTab(viewModel)
                    "Promos" -> AdminPromosTab(viewModel)
                }
            }
        }
    }
}

@Composable
fun AdminUsersTab(viewModel: EsportsViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val allTournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    var searchToken by remember { mutableStateOf("") }
    
    var editingUserWallets by remember { mutableStateOf<UserEntity?>(null) }
    var adjustMainWallet by remember { mutableStateOf("") }
    var adjustBonusWallet by remember { mutableStateOf("") }
    var adjustWinningsWallet by remember { mutableStateOf("") }
    var adjustCoinsWallet by remember { mutableStateOf("") }

    var viewFullDetailsUser by remember { mutableStateOf<UserEntity?>(null) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var broadcastTitle by remember { mutableStateOf("") }
    var broadcastMessage by remember { mutableStateOf("") }
    var showSpamAlertsDialog by remember { mutableStateOf(false) }
    
    var editingHostManager by remember { mutableStateOf<UserEntity?>(null) }
    var isHostManagerEnabled by remember { mutableStateOf(false) }
    var hostTournaments by remember { mutableStateOf(false) }
    var hostWithdrawals by remember { mutableStateOf(false) }
    var hostManagedTournaments by remember { mutableStateOf("") }
    var expandTournaments by remember { mutableStateOf(false) }

    val spamDeviceIds = users.groupBy { it.deviceId }.filter { it.value.size > 1 }.map { it.key }
    val filteredUsers = users.filter { u ->
        searchToken.isEmpty() || u.name.lowercase().contains(searchToken.lowercase()) || u.email.lowercase().contains(searchToken.lowercase())
    }

    if (showSpamAlertsDialog) {
        AlertDialog(
            onDismissRequest = { showSpamAlertsDialog = false },
            title = { Text("Spam Alerts (Duplicate Accounts)", color = Color.White) },
            text = {
                LazyColumn {
                    val groupedSpammers = users.filter { spamDeviceIds.contains(it.deviceId) }.groupBy { it.deviceId }
                    items(groupedSpammers.entries.toList()) { entry ->
                        val device = entry.key
                        val duplicateUsers = entry.value
                        Column(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()) {
                            Text(text = "Device ID: $device", color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "Accounts Created: ${duplicateUsers.size}", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            duplicateUsers.forEach { spammer ->
                                Text(text = "• ${spammer.name} (${spammer.email})", color = Color.LightGray, fontSize = 12.sp)
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = Color.DarkGray)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSpamAlertsDialog = false }) { Text("Close") }
            },
            containerColor = CharcoalCard,
            titleContentColor = Color.White
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchToken,
                onValueChange = { searchToken = it },
                label = { Text("Search users") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, focusedBorderColor = NeonGold
                ),
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Button(
                onClick = { showSpamAlertsDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.height(56.dp).padding(end = 4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Spam Alerts", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { showBroadcastDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = DefaultBlue),
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Broadcast", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredUsers, key = { it.emailKey }) { u ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = u.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (u.isHostManager) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF9C27B0), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("HOST", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(text = u.email, color = GrayText, fontSize = 11.sp)
                                Text(text = "Device: ${if (u.deviceId.length > 15) u.deviceId.take(15) + "..." else u.deviceId}", color = NeonOrange, fontSize = 9.sp)
                                Text(text = "IP: ${u.ipAddress.ifEmpty { "Unknown" }}", color = MintGreen, fontSize = 9.sp)
                            }
                            // Ban / Unban Toggle Button
                            Card(
                                onClick = { viewModel.adminBanControlUser(u.emailKey, !u.banned) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (u.banned) Color.Red else MintGreen
                                )
                            ) {
                                Text(
                                    text = if (u.banned) "BANNED" else "ACTIVE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Pocket: Rs.${u.mainWallet}", color = Color.White, fontSize = 11.sp)
                            Text(text = "Bonus: Rs.${u.bonusWallet}", color = Color.White, fontSize = 11.sp)
                            Text(text = "Winnings: Rs.${u.winningWallet}", color = Color.White, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Coins: ${u.coins.toInt()}", color = NeonGold, fontSize = 11.sp)
                            Text(text = "Referrals: ${u.totalReferrals}", color = MintGreen, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                onClick = { viewFullDetailsUser = u },
                                colors = ButtonDefaults.buttonColors(containerColor = DefaultBlue),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).padding(end = 2.dp)
                            ) {
                                Text("Details", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                onClick = {
                                    adjustMainWallet = u.mainWallet.toString()
                                    adjustBonusWallet = u.bonusWallet.toString()
                                    adjustWinningsWallet = u.winningWallet.toString()
                                    adjustCoinsWallet = u.coins.toString()
                                    editingUserWallets = u
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                            ) {
                                Text("Balances", color = CharcoalBg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
                            if (currentUser?.isAdmin == true) {
                                Button(
                                    onClick = {
                                        editingHostManager = u
                                        isHostManagerEnabled = u.isHostManager
                                        hostTournaments = u.hostTournaments
                                        hostWithdrawals = u.hostWithdrawals
                                        hostManagedTournaments = u.managedTournamentIds
                                        expandTournaments = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).padding(start = 2.dp)
                                ) {
                                    Text("Host Mngr", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingHostManager != null) {
        val user = editingHostManager!!
        Dialog(onDismissRequest = { editingHostManager = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalCard), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text(text = "Host Manager Settings: ${user.name}", color = Color(0xFF9C27B0), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Enable Host Manager Role", color = Color.White, fontWeight = FontWeight.Bold)
                        Switch(checked = isHostManagerEnabled, onCheckedChange = { isHostManagerEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF9C27B0), checkedTrackColor = Color(0xFF9C27B0).copy(alpha = 0.5f)))
                    }
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                    
                    if (isHostManagerEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hostTournaments, onCheckedChange = { hostTournaments = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF9C27B0)))
                            Text("Manage Tournaments", color = Color.White)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = hostWithdrawals, onCheckedChange = { hostWithdrawals = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF9C27B0)))
                            Text("Manage Withdrawals", color = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expandTournaments = !expandTournaments }.padding(vertical = 4.dp)) {
                            Text("Specific Tournaments Edit Access", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(imageVector = if (expandTournaments) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand", tint = Color.White)
                        }
                        Text("If none selected, they can add/edit ALL tournaments.", color = GrayText, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (expandTournaments) {
                            val currentManagedIds = hostManagedTournaments.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                            
                            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                                allTournaments.forEach { t ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = currentManagedIds.contains(t.id),
                                            onCheckedChange = { isChecked ->
                                                if (isChecked) currentManagedIds.add(t.id) else currentManagedIds.remove(t.id)
                                                hostManagedTournaments = currentManagedIds.joinToString(",")
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF9C27B0))
                                        )
                                        Text("${t.title} (${t.status})", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingHostManager = null }) { Text("Cancel", color = Color.Gray) }
                        Button(
                            onClick = {
                                viewModel.adminSetHostPermissions(
                                    emailKey = user.emailKey,
                                    isHostManager = isHostManagerEnabled,
                                    hostTournaments = hostTournaments,
                                    hostUsers = false,
                                    hostWithdrawals = hostWithdrawals,
                                    hostAnnouncements = false,
                                    managedTournamentIds = hostManagedTournaments.trim()
                                )
                                editingHostManager = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                        ) { Text("Save Permissions", color = Color.White) }
                    }
                }
            }
        }
    }

    if (showBroadcastDialog) {
        Dialog(onDismissRequest = { showBroadcastDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalCard), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Broadcast Announcement", color = NeonGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(value = broadcastTitle, onValueChange = { broadcastTitle = it }, label = { Text("Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = broadcastMessage, onValueChange = { broadcastMessage = it }, label = { Text("Message") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth(), minLines = 3)
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { showBroadcastDialog = false }) { Text("CANCEL", color = GrayText) }
                        Button(
                            onClick = {
                                viewModel.adminSendAnnouncement(broadcastTitle, broadcastMessage)
                                showBroadcastDialog = false
                                broadcastTitle = ""
                                broadcastMessage = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold)
                        ) { Text("SEND TO ALL", color = CharcoalBg) }
                    }
                }
            }
        }
    }

    viewFullDetailsUser?.let { user ->
        Dialog(onDismissRequest = { viewFullDetailsUser = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("User Full Details", color = NeonGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Divider(color = NeonGold.copy(0.3f), modifier = Modifier.padding(vertical = 8.dp))
                    
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text("Name: ${user.name}", color = Color.White, fontSize = 14.sp)
                            Text("Email: ${user.email}", color = GrayText, fontSize = 12.sp)
                            Text("Password (Plain): ${user.password}", color = Color.Red, fontSize = 12.sp) // Admin visibility
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text("Game UID: ${user.gameUid}", color = Color.White, fontSize = 14.sp)
                            Text("Device ID: ${user.deviceId}", color = NeonOrange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("IP Address: ${user.ipAddress.ifEmpty { "Unknown" }}", color = MintGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (users.count { it.deviceId == user.deviceId } > 1) {
                                Text("⚠️ Warning: Multiple accounts on this device!", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Refer Code: ${user.referCode}", color = MintGreen, fontSize = 14.sp)
                            Text("Referred By: ${if (user.referredBy.isEmpty()) "None" else user.referredBy}", color = Color.White, fontSize = 12.sp)
                            Text("Total Referrals: ${user.totalReferrals}", color = Color.White, fontSize = 12.sp)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Matches Played: ${user.matchesPlayed}", color = Color.White, fontSize = 12.sp)
                            Text("Matches Won: ${user.matchesWon}", color = Color.White, fontSize = 12.sp)
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.adminRevokeReferralRewards(user.emailKey)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("REVOKE SPAM REFERRAL REWARDS", color = Color.White, fontWeight = FontWeight.Bold) }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Send Direct Message to User", color = NeonGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = broadcastMessage,
                                onValueChange = { broadcastMessage = it },
                                label = { Text("Message specific user") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    viewModel.sendUserNotification(user.emailKey, "Message from Admin", broadcastMessage, "ANNOUNCEMENT")
                                    broadcastMessage = ""
                                },
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                            ) { Text("Send Notification") }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewFullDetailsUser = null }) { Text("CLOSE", color = Color.White) }
                    }
                }
            }
        }
    }

    editingUserWallets?.let { user ->
        Dialog(onDismissRequest = { editingUserWallets = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalCard), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Adjust Balances", color = NeonGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = user.email, color = GrayText, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(value = adjustMainWallet, onValueChange = { adjustMainWallet = it }, label = { Text("Main Wallet (Rs.)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
                    OutlinedTextField(value = adjustBonusWallet, onValueChange = { adjustBonusWallet = it }, label = { Text("Bonus Wallet (Rs.)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
                    OutlinedTextField(value = adjustWinningsWallet, onValueChange = { adjustWinningsWallet = it }, label = { Text("Winnings Wallet (Rs.)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))
                    OutlinedTextField(value = adjustCoinsWallet, onValueChange = { adjustCoinsWallet = it }, label = { Text("Coins Balance") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White))

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editingUserWallets = null }) {
                            Text("CANCEL", color = GrayText)
                        }
                        Button(
                            onClick = {
                                val m = adjustMainWallet.toDoubleOrNull() ?: 0.0
                                val b = adjustBonusWallet.toDoubleOrNull() ?: 0.0
                                val w = adjustWinningsWallet.toDoubleOrNull() ?: 0.0
                                val c = adjustCoinsWallet.toDoubleOrNull() ?: 0.0
                                viewModel.adminAdjustUserWallets(user.emailKey, m, b, w, c)
                                editingUserWallets = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
                        ) {
                            Text("APPLY CREDITS")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTournamentsCreatorTab(viewModel: EsportsViewModel) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val allTournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    
    val tournaments = remember(allTournaments, user) {
        if (user?.isAdmin == true || user?.managedTournamentIds.isNullOrBlank()) {
            allTournaments
        } else {
            val managedIds = user?.managedTournamentIds?.split(",")?.map { it.trim() } ?: emptyList()
            allTournaments.filter { managedIds.contains(it.id) }
        }
    }
    
    var showDialog by remember { mutableStateOf(false) }
    var editingTournamentId by remember { mutableStateOf<String?>(null) }
    var showPlayersDialogId by remember { mutableStateOf<String?>(null) }
    
    var title by remember { mutableStateOf("") }
    var entryCurrency by remember { mutableStateOf("CASH") } // "CASH", "COINS", "FREE"
    var prizeCurrency by remember { mutableStateOf("CASH") } // "CASH", "COINS"
    var mapType by remember { mutableStateOf("Bermuda") }
    var entryFee by remember { mutableStateOf("") }
    var prizePool by remember { mutableStateOf("") }
    var perKillPrize by remember { mutableStateOf("") }
    var rankPrizes by remember { mutableStateOf("") }
    var totalSlots by remember { mutableStateOf("100") }
    var adsRequired by remember { mutableStateOf("3") }
    var roomId by remember { mutableStateOf("") }
    var roomPassword by remember { mutableStateOf("") }
    var bannerUrl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var showRewardIndex by remember { mutableStateOf(true) }
    var scheduleDate by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("Classic") }
    var currencyType by remember { mutableStateOf("CASH") }
    var isPerKillEnabled by remember { mutableStateOf(true) }
    var regDate by remember { mutableStateOf("") }
    var regTime by remember { mutableStateOf("") }
    var scheduleTime by remember { mutableStateOf("") }
    var uploadingImage by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingImage = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = java.io.File.createTempFile("promo_", ".jpg", context.cacheDir)
                tempFile.outputStream().use { out ->
                    inputStream?.copyTo(out)
                }
                viewModel.uploadImageToImgBB(tempFile, onSuccess = { url ->
                    bannerUrl = url
                    uploadingImage = false
                    Toast.makeText(context, "Image uploaded!", Toast.LENGTH_SHORT).show()
                }, onError = {
                    uploadingImage = false
                    Toast.makeText(context, "Failed to upload image.", Toast.LENGTH_SHORT).show()
                })
            } catch (e: Exception) {
                uploadingImage = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val canCreate = user?.isAdmin == true

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (canCreate) {
            Button(
                onClick = {
                    editingTournamentId = null
                    title = ""
                    entryCurrency = "CASH"
                    prizeCurrency = "CASH"
                    mapType = "Bermuda"
                    entryFee = ""
                    prizePool = ""
                    perKillPrize = ""
                    rankPrizes = ""
                    totalSlots = "100"
                    adsRequired = "3"
                    roomId = ""
                    roomPassword = ""
                    bannerUrl = ""
                    description = ""
                    showRewardIndex = true
                    format = "Classic"
                    currencyType = "CASH"
                    isPerKillEnabled = true
                    val nowCal = java.util.Calendar.getInstance()
                    regDate = "${nowCal.get(java.util.Calendar.YEAR)}-${String.format("%02d", nowCal.get(java.util.Calendar.MONTH) + 1)}-${String.format("%02d", nowCal.get(java.util.Calendar.DAY_OF_MONTH))}"
                    regTime = "${String.format("%02d", nowCal.get(java.util.Calendar.HOUR_OF_DAY))}:${String.format("%02d", nowCal.get(java.util.Calendar.MINUTE))}"
                    
                    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.HOUR_OF_DAY, 2) }
                    val yr = cal.get(java.util.Calendar.YEAR)
                    val mo = String.format("%02d", cal.get(java.util.Calendar.MONTH) + 1)
                    val dy = String.format("%02d", cal.get(java.util.Calendar.DAY_OF_MONTH))
                    val hr = String.format("%02d", cal.get(java.util.Calendar.HOUR_OF_DAY))
                    val mn = String.format("%02d", cal.get(java.util.Calendar.MINUTE))
                    scheduleDate = "$yr-$mo-$dy"
                    scheduleTime = "$hr:$mn"
                    
                    showDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Text("ADD NEW TOURNAMENT", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Text(text = "Existing Playrooms List", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tournaments) { t ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(text = t.title, color = NeonGold, fontWeight = FontWeight.Bold)
                            Text(text = if(t.entryFee > 0) "Fee: Rs.${t.entryFee}" else "FREE", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Text(text = "Type: Free Fire | Map: ${t.mapType}", color = Color.Gray, fontSize = 12.sp)
                        Text(text = "Status: ${t.status} | Joined: ${t.slotsFilled}/${t.totalSlots}", color = GrayText, fontSize = 12.sp)

                        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    editingTournamentId = t.id
                                    title = t.title
                                    entryCurrency = t.entryCurrency
                                    prizeCurrency = t.prizeCurrency
                                    mapType = t.mapType
                                    entryFee = t.entryFee.toString()
                                    prizePool = t.prizePool.toString()
                                    perKillPrize = t.perKillPrize.toString()
                                    rankPrizes = t.rankPrizes
                                    totalSlots = t.totalSlots.toString()
                                    adsRequired = t.adsRequired.toString()
                                    roomId = t.roomId
                                    roomPassword = t.roomPassword
                                    bannerUrl = t.bannerUrl
                                    description = t.description
                                    showRewardIndex = t.showRewardIndex
                                    format = t.format
                                    currencyType = t.currencyType
                                    isPerKillEnabled = t.isPerKillEnabled
                                    val rCal = java.util.Calendar.getInstance().apply { timeInMillis = t.registrationOpenTimeMillis }
                                    regDate = "${rCal.get(java.util.Calendar.YEAR)}-${String.format("%02d", rCal.get(java.util.Calendar.MONTH) + 1)}-${String.format("%02d", rCal.get(java.util.Calendar.DAY_OF_MONTH))}"
                                    regTime = "${String.format("%02d", rCal.get(java.util.Calendar.HOUR_OF_DAY))}:${String.format("%02d", rCal.get(java.util.Calendar.MINUTE))}"
                                    
                                    val cal = java.util.Calendar.getInstance()
                                    cal.timeInMillis = t.scheduleTimeMillis
                                    val yr = cal.get(java.util.Calendar.YEAR)
                                    val mo = String.format("%02d", cal.get(java.util.Calendar.MONTH) + 1)
                                    val dy = String.format("%02d", cal.get(java.util.Calendar.DAY_OF_MONTH))
                                    val hr = String.format("%02d", cal.get(java.util.Calendar.HOUR_OF_DAY))
                                    val mn = String.format("%02d", cal.get(java.util.Calendar.MINUTE))
                                    scheduleDate = "$yr-$mo-$dy"
                                    scheduleTime = "$hr:$mn"
                                    
                                    showDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.weight(1f).height(35.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("EDIT", color = Color.White, fontSize = 11.sp)
                            }
                            Button(
                                onClick = { showPlayersDialogId = t.id },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                                modifier = Modifier.weight(1f).height(35.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("PLAYERS", color = CharcoalBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (canCreate) {
                                Button(
                                    onClick = { viewModel.adminDeleteTournament(t.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                                    modifier = Modifier.weight(1f).height(35.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("DELETE", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDialog = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Card(
                shape = androidx.compose.ui.graphics.RectangleShape,
                colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.padding(16.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (editingTournamentId == null) "Create Playroom Lobby" else "Edit Tournament Match",
                        color = NeonGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Note: Banner image should be 1200x500 for best display.", color = Color.Gray, fontSize = 10.sp)
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uploadingImage) "Uploading..." else if (bannerUrl.isNotEmpty()) "Change Banner Image" else "Upload Banner Image", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Match Title") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Rules & Description") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tournament Type", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("PAID", "FREE").forEach { type ->
                            val isSelected = if (type == "FREE") entryCurrency == "FREE" else entryCurrency != "FREE"
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = { 
                                    if (type == "FREE") entryCurrency = "FREE"
                                    else if (entryCurrency == "FREE") entryCurrency = "CASH"
                                },
                                label = { Text(type) },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Match Format", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("Classic", "Clash Squad", "1v1", "2v2", "4v4").forEach { fmt ->
                            androidx.compose.material3.FilterChip(
                                selected = format == fmt,
                                onClick = { format = fmt },
                                label = { Text(fmt) },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    if (entryCurrency != "FREE") {
                        Text("Entry Fee Currency", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("CASH", "COINS").forEach { type ->
                                androidx.compose.material3.FilterChip(
                                    selected = entryCurrency == type,
                                    onClick = { entryCurrency = type },
                                    label = { Text(type) },
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = entryFee,
                            onValueChange = { entryFee = it },
                            label = { Text("Entry Fee Amount") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        OutlinedTextField(
                            value = adsRequired,
                            onValueChange = { adsRequired = it },
                            label = { Text("Ads Required Watch Target") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }


                    Text("Total Prize Pool Currency", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CASH", "COINS").forEach { type ->
                            androidx.compose.material3.FilterChip(
                                selected = prizeCurrency == type,
                                onClick = { prizeCurrency = type },
                                label = { Text(type) },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = prizePool,
                        onValueChange = { prizePool = it },
                        label = { Text("Total Prize Pool Amount") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Switch(
                            checked = isPerKillEnabled,
                            onCheckedChange = { isPerKillEnabled = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = NeonGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Per Kill Reward", color = Color.White)
                    }
                    if (isPerKillEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Per Kill Currency", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("CASH", "COINS").forEach { type ->
                                androidx.compose.material3.FilterChip(
                                    selected = currencyType == type,
                                    onClick = { currencyType = type },
                                    label = { Text(type) },
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = perKillPrize,
                            onValueChange = { perKillPrize = it },
                            label = { Text("Per Kill Reward Amount") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = rankPrizes,
                        onValueChange = { rankPrizes = it },
                        label = { Text("Rank Prizes (e.g. 100 CASH, 50 COINS)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Switch(
                            checked = showRewardIndex,
                            onCheckedChange = { showRewardIndex = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = NeonGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show Reward Index (Rank Prizes)", color = Color.White)
                    }


                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = totalSlots,
                        onValueChange = { totalSlots = it },
                        label = { Text("Total Slots") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = roomId,
                        onValueChange = { roomId = it },
                        label = { Text("Lobby Pass ID (Will unlock 10 mins before)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = roomPassword,
                        onValueChange = { roomPassword = it },
                        label = { Text("Lobby Pass Secret") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scheduleDate,
                        onValueChange = { scheduleDate = it },
                        label = { Text("Match Date (YYYY-MM-DD)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scheduleTime,
                        onValueChange = { scheduleTime = it },
                        label = { Text("Match Time (HH:MM 24-Hour)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = regDate,
                        onValueChange = { regDate = it },
                        label = { Text("Registration Open Date (YYYY-MM-DD)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = regTime,
                        onValueChange = { regTime = it },
                        label = { Text("Registration Open Time (HH:MM 24-Hour)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                    )


                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = Color.White)
                        }
                        Button(
                            onClick = {
                                if (title.isBlank()) {
                                    Toast.makeText(context, "Title is required!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val finalEntryFee = if (entryCurrency == "FREE") 0.0 else (entryFee.toDoubleOrNull() ?: 0.0)
                                    val finalPrizePool = (prizePool.toDoubleOrNull() ?: 0.0)
                                    val finalAdsRequired = if (entryCurrency == "FREE") (adsRequired.toIntOrNull() ?: 3) else 0

                                    val finalScheduleMillis = try {
                                        val partsDate = scheduleDate.trim().split("-")
                                        val partsTime = scheduleTime.trim().split(":")
                                        if (partsDate.size == 3 && partsTime.size == 2) {
                                            val calendar = java.util.Calendar.getInstance()
                                            calendar.set(
                                                partsDate[0].toInt(),
                                                partsDate[1].toInt() - 1,
                                                partsDate[2].toInt(),
                                                partsTime[0].toInt(),
                                                partsTime[1].toInt(),
                                                0
                                            )
                                            calendar.timeInMillis
                                        } else {
                                            System.currentTimeMillis() + 1800000L
                                        }
                                    } catch (e: Exception) {
                                        System.currentTimeMillis() + 1800000L
                                    }
                                    val finalRegMillis = try {
                                        val partsDate = regDate.trim().split("-")
                                        val partsTime = regTime.trim().split(":")
                                        if (partsDate.size == 3 && partsTime.size == 2) {
                                            val calendar = java.util.Calendar.getInstance()
                                            calendar.set(
                                                partsDate[0].toInt(),
                                                partsDate[1].toInt() - 1,
                                                partsDate[2].toInt(),
                                                partsTime[0].toInt(),
                                                partsTime[1].toInt(),
                                                0
                                            )
                                            calendar.timeInMillis
                                        } else {
                                            System.currentTimeMillis()
                                        }
                                    } catch (e: Exception) {
                                        System.currentTimeMillis()
                                    }

                                    val original = tournaments.find { it.id == editingTournamentId }
                                    val entity = TournamentEntity(
                                        id = editingTournamentId ?: "match_${System.currentTimeMillis().toString().takeLast(6)}",
                                        title = title,
                                        gameType = "Free Fire",
                                        mapType = mapType,
                                        entryFee = finalEntryFee,
                                        prizePool = finalPrizePool,
                                        perKillPrize = perKillPrize.toDoubleOrNull() ?: 0.0,
                                        rankPrizes = rankPrizes,
                                        slotsFilled = original?.slotsFilled ?: 0,
                                        totalSlots = totalSlots.toIntOrNull() ?: 100,
                                        adsRequired = finalAdsRequired,
                                        scheduleTimeMillis = finalScheduleMillis,
                                        status = original?.status ?: "OPEN",
                                        roomId = roomId,
                                        roomPassword = roomPassword,
                                        bannerUrl = bannerUrl,
                                        description = description,
                                        showRewardIndex = showRewardIndex,
                                        entryCurrency = entryCurrency,
                                        prizeCurrency = prizeCurrency,
                                        format = format,
                                        currencyType = currencyType,
                                        isPerKillEnabled = isPerKillEnabled,
                                        registrationOpenTimeMillis = finalRegMillis
                                    )
                                    viewModel.adminCreateTournament(entity)
                                    Toast.makeText(context, "Tournament saved!", Toast.LENGTH_SHORT).show()
                                    showDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SAVE", color = CharcoalBg, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }

    if (showPlayersDialogId != null) {
        val tId = showPlayersDialogId!!
        val t = tournaments.find { it.id == tId }
        val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
        val joinedUsers = allUsers.filter { it.joinedTournaments.contains(tId) }
        
        var selectedDistributions by remember { mutableStateOf(mapOf<String, Double>()) }
        var inputAmounts by remember { mutableStateOf(mapOf<String, String>()) }
        val context = LocalContext.current

        androidx.compose.ui.window.Dialog(onDismissRequest = { showPlayersDialogId = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalBg),
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Tournament Players",
                            color = NeonGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        var botsToAdd by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedTextField(
                                value = botsToAdd,
                                onValueChange = { botsToAdd = it },
                                modifier = Modifier.width(65.dp).height(45.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { 
                                    val count = botsToAdd.toIntOrNull() ?: 0
                                    if(count > 0 && t != null) {
                                        viewModel.adminAddBotsToTournament(t.id, count) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                        botsToAdd = ""
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(35.dp).width(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                            ) {
                                Text("+BOTS", fontSize=10.sp)
                            }
                        }
                    }
                    Text("Distribute Prize Pool for ${t?.title ?: ""}", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(joinedUsers) { u ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(text = "Name: ${u.name}", color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(text = "UID: ${u.gameUid}", color = Color.LightGray, fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.adminKickPlayerFromTournament(tId, u.emailKey) { msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha=0.7f)),
                                            modifier = Modifier.height(30.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("KICK", color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    val amountStr = inputAmounts[u.emailKey] ?: ""
                                    OutlinedTextField(
                                        value = amountStr,
                                        onValueChange = { 
                                            inputAmounts = inputAmounts.toMutableMap().apply { put(u.emailKey, it) }
                                            val d = it.toDoubleOrNull()
                                            if (d != null && d > 0) {
                                                selectedDistributions = selectedDistributions.toMutableMap().apply { put(u.emailKey, d) }
                                            } else {
                                                selectedDistributions = selectedDistributions.toMutableMap().apply { remove(u.emailKey) }
                                            }
                                        },
                                        placeholder = { Text("Reward amount", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray),
                                        modifier = Modifier.fillMaxWidth().height(50.dp), singleLine = true
                                    )
                                }
                            }
                        }
                        if (joinedUsers.isEmpty()) {
                            item { Text("No players joined yet.", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (t != null) {
                                if (selectedDistributions.isEmpty()) {
                                    Toast.makeText(context, "No rewards assigned!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val summary = selectedDistributions.entries.joinToString("\\n") {
                                    "${allUsers.find { u -> u.emailKey == it.key }?.name}: ${it.value}"
                                }
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Confirm Distribution")
                                    .setMessage("You are about to distribute rewards:\\n$summary\\n\\nTournament status will be set to COMPLETED. Proceed?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        viewModel.adminDistributeTournamentReward(tId, selectedDistributions) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            showPlayersDialogId = null
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DISTRIBUTE REWARDS & FINISH", color = CharcoalBg, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showPlayersDialogId = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLOSE", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
fun AdminTaskCRUDTab(viewModel: EsportsViewModel) {
    val tasks by viewModel.dailyTasks.collectAsStateWithLifecycle()
    
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    
    var title by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf("WATCH_AD") }
    var targetValue by remember { mutableStateOf("2") }
    var coinReward by remember { mutableStateOf("10") }
    var isDaily by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (editingTaskId == null) "Add Daily Challenge Template" else "Edit Daily Challenge Template",
                    color = NeonGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Challenge Title text") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                    value = taskType,
                    onValueChange = { taskType = it },
                    label = { Text("Task Type (e.g. WATCH_AD, PLAY_MINS, REFER)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it },
                    label = { Text("Target progress cap") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                    value = coinReward,
                    onValueChange = { coinReward = it },
                    label = { Text("Coins reward yield") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = isDaily,
                        onCheckedChange = { isDaily = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = NeonGold)
                    )
                    Text("Is this a Daily Task? (Resets everyday)", color = Color.White)
                }
                
                Spacer(modifier = Modifier.height(14.dp))

                if (editingTaskId == null) {
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Title was left empty!", Toast.LENGTH_SHORT).show()
                            } else {
                                val entity = DailyTaskEntity(
                                    id = "task_${System.currentTimeMillis().toString().takeLast(4)}",
                                    title = title,
                                    taskType = taskType,
                                    targetValue = targetValue.toIntOrNull() ?: 1,
                                    coinReward = coinReward.toDoubleOrNull() ?: 5.0,
                                    isDaily = isDaily
                                )
                                viewModel.adminCreateTaskTemplate(entity)
                                Toast.makeText(context, "Challenge added!", Toast.LENGTH_SHORT).show()
                                title = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "ADD CHALLENGE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (title.isBlank()) {
                                    Toast.makeText(context, "Title is empty!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val entity = DailyTaskEntity(
                                        id = editingTaskId!!,
                                        title = title,
                                        taskType = taskType,
                                        targetValue = targetValue.toIntOrNull() ?: 1,
                                        coinReward = coinReward.toDoubleOrNull() ?: 5.0,
                                        isDaily = isDaily
                                    )
                                    viewModel.adminCreateTaskTemplate(entity)
                                    Toast.makeText(context, "Challenge modified successfully!", Toast.LENGTH_SHORT).show()
                                    
                                    editingTaskId = null
                                    title = ""
                                    taskType = "WATCH_AD"
                                    targetValue = "2"
                                    coinReward = "10"
                                    isDaily = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "SAVE CHANGES", color = CharcoalBg, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                editingTaskId = null
                                title = ""
                                taskType = "WATCH_AD"
                                targetValue = "2"
                                coinReward = "10"
                                isDaily = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "CANCEL", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tasks) { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = task.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "Type: ${task.taskType} | Target: ${task.targetValue} | Reward: ${task.coinReward.toInt()} Coins | Daily: ${if(task.isDaily) "Yes" else "No"}", color = GrayText, fontSize = 11.sp)
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    editingTaskId = task.id
                                    title = task.title
                                    taskType = task.taskType
                                    targetValue = task.targetValue.toString()
                                    coinReward = task.coinReward.toInt().toString()
                                    isDaily = task.isDaily
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = NeonGold)
                            }
                            IconButton(
                                onClick = { viewModel.adminDeleteTaskTemplate(task.id) }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsTab(viewModel: EsportsViewModel) {
    val gameId by viewModel.unityGameId.collectAsStateWithLifecycle()
    val rewardedId by viewModel.unityRewardedId.collectAsStateWithLifecycle()
    val interstitialId by viewModel.unityInterstitialId.collectAsStateWithLifecycle()
    val epTitle by viewModel.epTitle.collectAsStateWithLifecycle()
    val epNum by viewModel.epNumber.collectAsStateWithLifecycle()
    val jcTitle by viewModel.jcTitle.collectAsStateWithLifecycle()
    val jcNum by viewModel.jcNumber.collectAsStateWithLifecycle()
    val minWithdraw by viewModel.minWithdraw.collectAsStateWithLifecycle()
    val dbDailyRewards by viewModel.dbDailyRewards.collectAsStateWithLifecycle()

    val referCoinReward by viewModel.referCoinReward.collectAsStateWithLifecycle()
    val referCashReward by viewModel.referCashReward.collectAsStateWithLifecycle()

    val supportE by viewModel.supportEmail.collectAsStateWithLifecycle()
    val termsT by viewModel.termsText.collectAsStateWithLifecycle()
    val privacyT by viewModel.privacyText.collectAsStateWithLifecycle()

    var inputGameId by remember { mutableStateOf(gameId) }
    var inputRewardedId by remember { mutableStateOf(rewardedId) }
    var inputInterstitialId by remember { mutableStateOf(interstitialId) }
    var inputEpTitle by remember { mutableStateOf(epTitle) }
    var inputEpNum by remember { mutableStateOf(epNum) }
    var inputJcTitle by remember { mutableStateOf(jcTitle) }
    var inputJcNum by remember { mutableStateOf(jcNum) }
    var inputMinWithdraw by remember { mutableStateOf(minWithdraw) }

    var inputSupportEmail by remember { mutableStateOf(supportE) }
    var inputTermsText by remember { mutableStateOf(termsT) }
    var inputPrivacyText by remember { mutableStateOf(privacyT) }

    var inputReferCoin by remember { mutableStateOf(referCoinReward.toString()) }
    var inputReferCash by remember { mutableStateOf(referCashReward.toString()) }

    var inputR1 by remember { mutableStateOf("5.0") }
    var inputR2 by remember { mutableStateOf("5.0") }
    var inputR3 by remember { mutableStateOf("5.0") }
    var inputR4 by remember { mutableStateOf("10.0") }
    var inputR5 by remember { mutableStateOf("5.0") }
    var inputR6 by remember { mutableStateOf("5.0") }
    var inputR7 by remember { mutableStateOf("15.0") }

    LaunchedEffect(gameId, rewardedId, interstitialId, epTitle, epNum, jcTitle, jcNum, minWithdraw, dbDailyRewards, referCoinReward, referCashReward, supportE, termsT, privacyT) {
        inputGameId = gameId
        inputRewardedId = rewardedId
        inputInterstitialId = interstitialId
        inputEpTitle = epTitle
        inputEpNum = epNum
        inputJcTitle = jcTitle
        inputJcNum = jcNum
        inputMinWithdraw = minWithdraw
        inputReferCoin = referCoinReward.toString()
        inputReferCash = referCashReward.toString()
        
        inputSupportEmail = supportE
        inputTermsText = termsT
        inputPrivacyText = privacyT
        
        inputR1 = dbDailyRewards.getOrNull(0)?.toString() ?: "5.0"
        inputR2 = dbDailyRewards.getOrNull(1)?.toString() ?: "5.0"
        inputR3 = dbDailyRewards.getOrNull(2)?.toString() ?: "5.0"
        inputR4 = dbDailyRewards.getOrNull(3)?.toString() ?: "10.0"
        inputR5 = dbDailyRewards.getOrNull(4)?.toString() ?: "5.0"
        inputR6 = dbDailyRewards.getOrNull(5)?.toString() ?: "5.0"
        inputR7 = dbDailyRewards.getOrNull(6)?.toString() ?: "15.0"
    }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(text = "Manage Unity Ads Placer Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(value = inputGameId, onValueChange = { inputGameId = it }, label = { Text("Unity Game ID") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputRewardedId, onValueChange = { inputRewardedId = it }, label = { Text("Rewarded Placement ID") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputInterstitialId, onValueChange = { inputInterstitialId = it }, label = { Text("Interstitial Placement ID") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Payment Administration", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(value = inputEpTitle, onValueChange = { inputEpTitle = it }, label = { Text("Easypaisa Account Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputEpNum, onValueChange = { inputEpNum = it }, label = { Text("Easypaisa Account Number") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputJcTitle, onValueChange = { inputJcTitle = it }, label = { Text("JazzCash Account Title") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputJcNum, onValueChange = { inputJcNum = it }, label = { Text("JazzCash Account Number") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputMinWithdraw, onValueChange = { inputMinWithdraw = it }, label = { Text("Minimum Withdrawal Amount") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Daily Streak Rewards Setup (Coins)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = inputR1, onValueChange = { inputR1 = it }, label = { Text("Day 1") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.weight(1f))
            OutlinedTextField(value = inputR2, onValueChange = { inputR2 = it }, label = { Text("Day 2") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.weight(1f))
            OutlinedTextField(value = inputR3, onValueChange = { inputR3 = it }, label = { Text("Day 3") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = inputR4, onValueChange = { inputR4 = it }, label = { Text("Day 4") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.weight(1f))
            OutlinedTextField(value = inputR5, onValueChange = { inputR5 = it }, label = { Text("Day 5") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.weight(1f))
            OutlinedTextField(value = inputR6, onValueChange = { inputR6 = it }, label = { Text("Day 6") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputR7, onValueChange = { inputR7 = it }, label = { Text("Day 7 (Grand Bonus)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Referral Bonus Allocation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = inputReferCoin, onValueChange = { inputReferCoin = it }, label = { Text("Per Refer Coins") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.weight(1f))
            OutlinedTextField(value = inputReferCash, onValueChange = { inputReferCash = it }, label = { Text("Per Refer Cash (Bonus Wallet)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val r1 = inputR1.toDoubleOrNull() ?: 5.0
                val r2 = inputR2.toDoubleOrNull() ?: 5.0
                val r3 = inputR3.toDoubleOrNull() ?: 5.0
                val r4 = inputR4.toDoubleOrNull() ?: 10.0
                val r5 = inputR5.toDoubleOrNull() ?: 5.0
                val r6 = inputR6.toDoubleOrNull() ?: 5.0
                val r7 = inputR7.toDoubleOrNull() ?: 15.0
                
                val referCoin = inputReferCoin.toDoubleOrNull() ?: 0.0
                val referCash = inputReferCash.toDoubleOrNull() ?: 0.0

                viewModel.adminSetPlatformSettings(
                    inputGameId, inputRewardedId, inputInterstitialId,
                    inputEpNum, inputEpTitle, inputJcNum, inputJcTitle, inputMinWithdraw,
                    r1, r2, r3, r4, r5, r6, r7, referCoin, referCash
                )
                Toast.makeText(context, "Settings Updated successfully!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "UPDATE PLACEMENTS", color = CharcoalBg, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Support & Legal Information", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(value = inputSupportEmail, onValueChange = { inputSupportEmail = it }, label = { Text("Support Email/Contact") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputPrivacyText, onValueChange = { inputPrivacyText = it }, label = { Text("Privacy Policy Text") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 10)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = inputTermsText, onValueChange = { inputTermsText = it }, label = { Text("Terms & Conditions Text") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White), modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 10)

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = {
                viewModel.adminSetLegalSettings(inputSupportEmail, inputTermsText, inputPrivacyText)
                Toast.makeText(context, "Legal Settings Updated!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "UPDATE LEGAL & SUPPORT", color = CharcoalBg, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun AdminTransactionsQueueTab(viewModel: EsportsViewModel) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val pendingList = transactions.filter { it.status == "PENDING" }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Pending Cash Approvals Queue",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (pendingList.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Approval queue is currently empty.", color = GrayText, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn {
                items(pendingList) { tx ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "User emailKey: ${tx.emailKey}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(text = "Type: ${tx.type} | Amount: Rs.${String.format("%.1f", Math.abs(tx.amount))}", color = NeonGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                val sdf = SimpleDateFormat("HH:mm a", Locale.getDefault())
                                Text(text = sdf.format(Date(tx.timestamp)), color = GrayText, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Details: ${tx.details}", color = GrayText, fontSize = 11.sp)
                            
                            if (tx.screenshotUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Payment Proof Screenshot (Deposit):", color = NeonGold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                coil.compose.AsyncImage(
                                    model = tx.screenshotUrl,
                                    contentDescription = "Deposit Screenshot",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))

                            val context = LocalContext.current
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { 
                                        viewModel.adminRejectTransaction(tx) 
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:${tx.emailKey.replace(",", ".")}")
                                            putExtra(Intent.EXTRA_SUBJECT, "Update on your AnuBattle Request")
                                            putExtra(Intent.EXTRA_TEXT, "Hello, your request for ${tx.type} has been REJECTED. Please contact support for more details.")
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(text = "REJECT & EMAIL", color = Color.White)
                                }

                                Button(
                                    onClick = { 
                                        viewModel.adminApproveTransaction(tx)
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:${tx.emailKey.replace(",", ".")}")
                                            putExtra(Intent.EXTRA_SUBJECT, "Update on your AnuBattle Request")
                                            putExtra(Intent.EXTRA_TEXT, "Hello, your request for ${tx.type} has been APPROVED successfully.")
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(text = "APPROVE & EMAIL", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPromosTab(viewModel: EsportsViewModel) {
    val promos by viewModel.promoSliders.collectAsStateWithLifecycle(initialValue = emptyList())
    var promoTitle by remember { mutableStateOf("") }
    var actionUrl by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var uploadingImage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingImage = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = java.io.File.createTempFile("promo_", ".jpg", context.cacheDir)
                tempFile.outputStream().use { out ->
                    inputStream?.copyTo(out)
                }
                viewModel.uploadImageToImgBB(tempFile, onSuccess = { url ->
                    imageUrl = url
                    uploadingImage = false
                    Toast.makeText(context, "Image uploaded!", Toast.LENGTH_SHORT).show()
                }, onError = {
                    uploadingImage = false
                    Toast.makeText(context, "Failed to upload image.", Toast.LENGTH_SHORT).show()
                })
            } catch (e: Exception) {
                uploadingImage = false
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Add Promo Image", color = NeonGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = promoTitle,
            onValueChange = { promoTitle = it },
            label = { Text("Promo Title") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = actionUrl,
            onValueChange = { actionUrl = it },
            label = { Text("Action URL (Optional link)") },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { imagePickerLauncher.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uploadingImage) "Uploading..." else if (imageUrl.isNotEmpty()) "Change Image" else "Upload Promo Image", color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Selected image URL: $imageUrl", color = Color.Gray, fontSize = 10.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (imageUrl.isBlank()) {
                    Toast.makeText(context, "Please upload an image first!", Toast.LENGTH_SHORT).show()
                } else {
                    val id = "promo_${System.currentTimeMillis()}"
                    val entity = com.example.data.PromoSliderEntity(
                        id = id,
                        imageUrl = imageUrl,
                        title = promoTitle.ifBlank { "Promo" },
                        actionUrl = actionUrl
                    )
                    viewModel.savePromoSlider(entity)
                    Toast.makeText(context, "Promo added successfully!", Toast.LENGTH_SHORT).show()
                    promoTitle = ""
                    actionUrl = ""
                    imageUrl = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("SAVE PROMO", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Active Promos", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(promos) { p ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(p.title, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { viewModel.deletePromoSlider(p.id) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Promo", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDiamondCRUDTab(viewModel: EsportsViewModel) {
    val diamondPacks by viewModel.diamondPacks.collectAsStateWithLifecycle()

    var editingPackId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var coinCost by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (editingPackId == null) "Add Redeem Item" else "Edit Redeem Item",
                    color = NeonGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Item Title (e.g. Netflix 1 Month)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = coinCost,
                    onValueChange = { coinCost = it },
                    label = { Text("Coin Cost (e.g. 100)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Image URL (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val cost = coinCost.toIntOrNull() ?: -1
                        if (title.isBlank() || cost < 0) {
                            Toast.makeText(context, "Please enter valid Title and Coin Cost!", Toast.LENGTH_SHORT).show()
                        } else {
                            val id = editingPackId ?: "dim_${System.currentTimeMillis()}"
                            val pack = DiamondPackEntity(
                                id = id,
                                title = title,
                                coinCost = cost,
                                imageUrl = imageUrl
                            )
                            viewModel.adminCreateDiamondPack(pack)
                            Toast.makeText(context, if (editingPackId == null) "Redeem Item added!" else "Redeem Item modified!", Toast.LENGTH_SHORT).show()
                            editingPackId = null
                            title = ""
                            coinCost = ""
                            imageUrl = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (editingPackId == null) "SAVE ITEM" else "MODIFY ITEM", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                if (editingPackId != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            editingPackId = null
                            title = ""
                            coinCost = ""
                            imageUrl = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "CANCEL EDIT", color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Existing Redeem Items", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(diamondPacks) { pack ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = pack.title, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(text = "Cost: ${pack.coinCost} Coins", color = NeonGold, fontSize = 12.sp)
                        }
                        Row {
                            IconButton(onClick = {
                                editingPackId = pack.id
                                title = pack.title
                                coinCost = pack.coinCost.toString()
                                imageUrl = pack.imageUrl
                            }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Pack", tint = NeonGold)
                            }
                            IconButton(onClick = {
                                viewModel.adminDeleteDiamondPack(pack.id)
                                Toast.makeText(context, "Item Deleted!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Pack", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(viewModel: EsportsViewModel, onBack: () -> Unit) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.markNotificationsAsRead()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(text = "Announcements & Alerts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
        }

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "You have no new notifications.", color = GrayText, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(notifications) { notif ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (notif.isRead) CharcoalCard else DefaultBlue.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (notif.type == "ANNOUNCEMENT") Icons.Default.Campaign else Icons.Default.EmojiEvents,
                                    contentDescription = "Notification Icon",
                                    tint = if (notif.type == "ANNOUNCEMENT") NeonOrange else NeonGold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = notif.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = notif.message, color = Color.White.copy(0.8f), fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(notif.timestamp),
                                color = GrayText,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}