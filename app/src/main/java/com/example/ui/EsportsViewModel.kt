package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.database.*
import com.unity3d.ads.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File

class EsportsViewModel(
    private val context: Context,
    private val dao: EsportsDao,
    private val syncManager: FirebaseSyncManager
) : ViewModel() {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("esports_prefs", Context.MODE_PRIVATE)

    // Logged in User Sessions
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setLoginError(error: String?) {
        _loginError.value = error
    }

    // Active Tournaments, Tasks, Transactions
    val tournaments: StateFlow<List<TournamentEntity>> = dao.getAllTournamentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyTasks: StateFlow<List<DailyTaskEntity>> = dao.getAllDailyTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _taskProgress = MutableStateFlow<List<TaskProgressEntity>>(emptyList())
    val taskProgress: StateFlow<List<TaskProgressEntity>> = _taskProgress.asStateFlow()

    private val _tournamentAdProgress = MutableStateFlow<List<TournamentAdProgressEntity>>(emptyList())
    val tournamentAdProgress: StateFlow<List<TournamentAdProgressEntity>> = _tournamentAdProgress.asStateFlow()

    val transactions: StateFlow<List<TransactionRecordEntity>> = dao.getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val promoSliders: StateFlow<List<PromoSliderEntity>> = dao.getActivePromoSlidersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = dao.getAllUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unity Ads Settings state
    private val _unityGameId = MutableStateFlow("5763487")
    val unityGameId: StateFlow<String> = _unityGameId.asStateFlow()

    private val _unityRewardedId = MutableStateFlow("Rewarded_Android")
    val unityRewardedId: StateFlow<String> = _unityRewardedId.asStateFlow()

    private val _unityInterstitialId = MutableStateFlow("Interstitial_Android")
    val unityInterstitialId: StateFlow<String> = _unityInterstitialId.asStateFlow()
    
    private var isUnityRewardedLoaded = false
    private var isUnityInterstitialLoaded = false
    private var pendingAdTournamentId: String? = null
    private var pendingInterstitialRequested = false
    private var currentAdActivity: android.app.Activity? = null
    private var currentAdResultCallback: ((Boolean) -> Unit)? = null
    private var currentInterstitialResultCallback: ((Boolean) -> Unit)? = null
    
    fun preloadUnityAds() {
        val rewardedId = _unityRewardedId.value.trim()
        val interstitialId = _unityInterstitialId.value.trim()
        
        if (UnityAds.isInitialized) {
            if (rewardedId.isNotBlank()) {
                UnityAds.load(rewardedId, object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placement: String?) {
                        isUnityRewardedLoaded = true
                        Log.d("UnityAds", "Rewarded Ad Loaded in Background")
                        val pendingTourney = pendingAdTournamentId
                        val activity = currentAdActivity
                        val callback = currentAdResultCallback
                        if (pendingTourney != null && activity != null && callback != null) {
                            pendingAdTournamentId = null
                            currentAdActivity = null
                            currentAdResultCallback = null
                            executeShowUnityAd(activity, rewardedId, pendingTourney, callback)
                        }
                    }
                    override fun onUnityAdsFailedToLoad(placement: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                        isUnityRewardedLoaded = false
                        Log.e("UnityAds", "Rewarded Ad Failed to Load: $message")
                    }
                })
            }
            if (interstitialId.isNotBlank()) {
                UnityAds.load(interstitialId, object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placement: String?) {
                        isUnityInterstitialLoaded = true
                        Log.d("UnityAds", "Interstitial Ad Loaded in Background")
                        val pendingReq = pendingInterstitialRequested
                        val activity = currentAdActivity
                        val callback = currentInterstitialResultCallback
                        if (pendingReq && activity != null && callback != null) {
                            pendingInterstitialRequested = false
                            currentAdActivity = null
                            currentInterstitialResultCallback = null
                            executeShowUnityInterstitialAd(activity, interstitialId, callback)
                        }
                    }
                    override fun onUnityAdsFailedToLoad(placement: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                        isUnityInterstitialLoaded = false
                        Log.e("UnityAds", "Interstitial Ad Failed to Load: $message")
                    }
                })
            }
        }
    }

    private val _epNumber = MutableStateFlow("")
    val epNumber: StateFlow<String> = _epNumber.asStateFlow()
    private val _epTitle = MutableStateFlow("")
    val epTitle: StateFlow<String> = _epTitle.asStateFlow()

    private val _jcNumber = MutableStateFlow("")
    val jcNumber: StateFlow<String> = _jcNumber.asStateFlow()
    private val _jcTitle = MutableStateFlow("")
    val jcTitle: StateFlow<String> = _jcTitle.asStateFlow()
    
    private val _minWithdraw = MutableStateFlow("50")
    val minWithdraw: StateFlow<String> = _minWithdraw.asStateFlow()

    private val _referCoinReward = MutableStateFlow(0.0)
    val referCoinReward: StateFlow<Double> = _referCoinReward.asStateFlow()

    private val _referCashReward = MutableStateFlow(0.0)
    val referCashReward: StateFlow<Double> = _referCashReward.asStateFlow()

    private val _dbDailyRewards = MutableStateFlow(listOf(5.0, 5.0, 5.0, 10.0, 5.0, 5.0, 15.0))
    val dbDailyRewards: StateFlow<List<Double>> = _dbDailyRewards.asStateFlow()

    private val _supportEmail = MutableStateFlow("")
    val supportEmail: StateFlow<String> = _supportEmail.asStateFlow()

    private val _termsText = MutableStateFlow("")
    val termsText: StateFlow<String> = _termsText.asStateFlow()

    private val _privacyText = MutableStateFlow("")
    val privacyText: StateFlow<String> = _privacyText.asStateFlow()

    val diamondPacks: StateFlow<List<DiamondPackEntity>> = dao.getAllDiamondPacksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<NotificationEntity>> = _currentUser.flatMapLatest { user ->
        val email = user?.emailKey ?: ""
        if (email.isNotBlank()) dao.getNotificationsFlow(email) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Watch cooldown timer state (seconds active count from preferences)
    private val _cooldowns = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cooldowns: StateFlow<Map<String, Int>> = _cooldowns.asStateFlow()

    // 7-day Login Claim trackers (persistent via Firebase task_progress/emailKey/day_X)
    private val _claimedDays = MutableStateFlow<Set<Int>>(emptySet())
    val claimedDays: StateFlow<Set<Int>> = _claimedDays.asStateFlow()

    // Deposit/Withdraw stats
    private val _depositRequestSuccess = MutableStateFlow<String?>(null)
    val depositRequestSuccess: StateFlow<String?> = _depositRequestSuccess.asStateFlow()

    private fun showOsNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel("anu_battle_channel", "Tournament Alerts", android.app.NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val builder = androidx.core.app.NotificationCompat.Builder(context, "anu_battle_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            notificationManager.notify((System.currentTimeMillis() / 1000).toInt(), builder.build())
        } catch (e: Exception) {
            // Permission missing
        }
    }

    init {
        // Listen to remote global announcements for instant push
        val globalPushRef = FirebaseDatabase.getInstance().getReference("notifications").child("GLOBAL")
        globalPushRef.addChildEventListener(object: com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                // Only show if it was created in the last 1 minute (prevents spam on fresh install)
                if (System.currentTimeMillis() - timestamp < 60000) {
                    val title = snapshot.child("title").getValue(String::class.java) ?: "New Announcement"
                    val message = snapshot.child("message").getValue(String::class.java) ?: ""
                    showOsNotification(title, message)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })

        // Restore existing user session from sharedPrefs
        val savedEmailKey = sharedPrefs.getString("logged_in_email_key", null)
        if (savedEmailKey != null) {
            loadUserSession(savedEmailKey)
        }

        // Regularly tick down cooldown timers once a second
        viewModelScope.launch {
            while (true) {
                delay(1000)
                updateCooldownTicks()
            }
        }

        // Regularly check for tournament credentials unlocking
        viewModelScope.launch {
            val notifiedMatches = mutableSetOf<String>()
            while (true) {
                delay(30000) // check every 30 seconds
                val now = System.currentTimeMillis()
                val user = _currentUser.value
                tournaments.value.forEach { match ->
                    val timeToMatch = match.scheduleTimeMillis - now
                    if (timeToMatch in 0..600000 && !notifiedMatches.contains(match.id) && user != null) {
                        notifiedMatches.add(match.id)
                        
                        // Push an inbox notification to the current user
                        val msg = "Room Info Ready! Lobby credentials for ${match.title} are now unlocked! Check the matches page."
                        FirebaseDatabase.getInstance().getReference("users").child(user.emailKey).child("inboxMessage").setValue(msg)
                        
                        showOsNotification("Tournament Ready", msg)
                    }
                }
            }
        }

        // Sync Unity settings values from Firebase Realtime Database
        syncManager.settingsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    _unityGameId.value = snapshot.child("unity_game_id").getValue(String::class.java) ?: "5763487"
                    _unityRewardedId.value = snapshot.child("unity_rewarded_id").getValue(String::class.java) ?: "Rewarded_Android"
                    _unityInterstitialId.value = snapshot.child("unity_interstitial_id").getValue(String::class.java) ?: "Interstitial_Android"
                    _epNumber.value = snapshot.child("ep_number").getValue(String::class.java) ?: ""
                    _epTitle.value = snapshot.child("ep_title").getValue(String::class.java) ?: ""
                    _jcNumber.value = snapshot.child("jc_number").getValue(String::class.java) ?: ""
                    _jcTitle.value = snapshot.child("jc_title").getValue(String::class.java) ?: ""
                    _minWithdraw.value = snapshot.child("min_withdraw").getValue(String::class.java) ?: "50"
                    
                    val r1 = snapshot.child("daily_reward_1").getValue(Double::class.java) ?: snapshot.child("daily_reward_1").getValue(Long::class.java)?.toDouble() ?: 5.0
                    val r2 = snapshot.child("daily_reward_2").getValue(Double::class.java) ?: snapshot.child("daily_reward_2").getValue(Long::class.java)?.toDouble() ?: 5.0
                    val r3 = snapshot.child("daily_reward_3").getValue(Double::class.java) ?: snapshot.child("daily_reward_3").getValue(Long::class.java)?.toDouble() ?: 5.0
                    val r4 = snapshot.child("daily_reward_4").getValue(Double::class.java) ?: snapshot.child("daily_reward_4").getValue(Long::class.java)?.toDouble() ?: 10.0
                    val r5 = snapshot.child("daily_reward_5").getValue(Double::class.java) ?: snapshot.child("daily_reward_5").getValue(Long::class.java)?.toDouble() ?: 5.0
                    val r6 = snapshot.child("daily_reward_6").getValue(Double::class.java) ?: snapshot.child("daily_reward_6").getValue(Long::class.java)?.toDouble() ?: 5.0
                    val r7 = snapshot.child("daily_reward_7").getValue(Double::class.java) ?: snapshot.child("daily_reward_7").getValue(Long::class.java)?.toDouble() ?: 15.0
                    
                    _referCoinReward.value = snapshot.child("refer_coin_reward").getValue(Double::class.java) ?: snapshot.child("refer_coin_reward").getValue(Long::class.java)?.toDouble() ?: 0.0
                    _referCashReward.value = snapshot.child("refer_cash_reward").getValue(Double::class.java) ?: snapshot.child("refer_cash_reward").getValue(Long::class.java)?.toDouble() ?: 0.0

                    _supportEmail.value = snapshot.child("support_email").getValue(String::class.java) ?: "support@example.com"
                    _termsText.value = snapshot.child("terms_text").getValue(String::class.java) ?: "Terms and conditions..."
                    _privacyText.value = snapshot.child("privacy_text").getValue(String::class.java) ?: "Privacy policy..."

                    _dbDailyRewards.value = listOf(r1, r2, r3, r4, r5, r6, r7)

                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val cfg = dao.getAppConfigSync() ?: AppConfigEntity()
                            val updatedParam = cfg.copy(referCoinReward = _referCoinReward.value, referCashReward = _referCashReward.value)
                            dao.saveAppConfig(updatedParam)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Dynamic automatic pre-initialization of Unity Ads with admin configurations
                    preInitializeUnityAds(_unityGameId.value)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun preInitializeUnityAds(gameId: String) {
        val trimmedGameId = gameId.trim()
        if (trimmedGameId.isBlank()) return
        viewModelScope.launch(Dispatchers.Main) {
            if (!UnityAds.isInitialized) {
                try {
                    UnityAds.initialize(context, trimmedGameId, false, object : IUnityAdsInitializationListener {
                        override fun onInitializationComplete() {
                            Log.d("UnityAds", "Unity Ads pre-initialized successfully.")
                            preloadUnityAds()
                        }
                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, msg: String?) {
                            Log.e("UnityAds", "Unity Ads pre-initialization failed: $msg")
                        }
                    })
                } catch (e: Exception) {
                    Log.e("UnityAds", "Unity Ads pre-initialization crashed silently: ${e.message}", e)
                }
            }
        }
    }

    private fun updateIpAddress(emailKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = getPublicIpAddress()
            if (ip != "Unknown") {
                FirebaseDatabase.getInstance().getReference("users").child(emailKey).child("ipAddress").setValue(ip)
            }
        }
    }

    private fun loadUserSession(emailKey: String) {
        updateIpAddress(emailKey)
        viewModelScope.launch {
            _isLoading.value = true
            syncManager.startUserAndProgressSync(emailKey)
            
            // Launch collection in parallel coroutines so that infinite flows do not block
            launch {
                dao.getUserFlow(emailKey).collect { user ->
                    if (user != null) {
                        val isForcedAdmin = emailKey == "modspak4_gmail_com" || user.email.trim().lowercase().contains("admin") || user.email.trim().lowercase() == "modspak4@gmail.com"
                        if (isForcedAdmin && !user.isAdmin) {
                            viewModelScope.launch(Dispatchers.IO) {
                                dao.insertUser(user.copy(isAdmin = true))
                                FirebaseDatabase.getInstance().getReference("users").child(emailKey).child("isAdmin").setValue(true)
                            }
                        }
                        _currentUser.value = if (isForcedAdmin) user.copy(isAdmin = true) else user
                    }
                }
            }

            launch {
                dao.getTaskProgressFlow(emailKey).collect { progressList ->
                    _taskProgress.value = progressList
                    val claims = progressList
                        .filter { it.taskId.startsWith("day_") && it.claimed }
                        .map { it.taskId.substringAfter("day_").toIntOrNull() ?: 0 }
                        .filter { it > 0 }
                        .toSet()
                    _claimedDays.value = claims
                }
            }

            launch {
                dao.getAllTournamentAdProgressFlow(emailKey).collect { progressList ->
                    _tournamentAdProgress.value = progressList
                }
            }

            _isLoading.value = false
        }
    }

    private fun load7DayClaims(emailKey: String) {
        // No-op or keep as empty shell since we load in parallel in loadUserSession
    }

    // Dynamic Normalization login & register
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginError.value = "Email and Password cannot be blank!"
            return
        }
        val emailKey = normalizeEmail(email)
        _isLoading.value = true
        _loginError.value = null

        // Safe Firebase RTDB single event check to pull profiles
        FirebaseDatabase.getInstance().getReference("users").child(emailKey)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _isLoading.value = false
                    if (snapshot.exists()) {
                        val savedPassword = snapshot.child("password").getValue(String::class.java) ?: ""
                        if (savedPassword.isNotEmpty() && savedPassword != password) {
                            _loginError.value = "Incorrect password!"
                            return
                        }
                        
                        // User exists! Pull profile, save dynamically and initialize session
                        val name = snapshot.child("name").getValue(String::class.java) ?: "Competitor"
                        val isForcedAdmin = email.trim().lowercase().contains("admin") || email.trim().lowercase() == "modspak4@gmail.com"
                        val model = UserEntity(
                            emailKey = emailKey,
                            name = name,
                            email = email.trim(),
                            mainWallet = snapshot.child("mainWallet").getValue(Double::class.java) ?: 0.0,
                            bonusWallet = snapshot.child("bonusWallet").getValue(Double::class.java) ?: 0.0,
                            winningWallet = snapshot.child("winningWallet").getValue(Double::class.java) ?: 0.0,
                            coins = snapshot.child("coins").getValue(Double::class.java) ?: 0.0,
                            matchesPlayed = snapshot.child("matchesPlayed").getValue(Int::class.java) ?: 0,
                            matchesWon = snapshot.child("matchesWon").getValue(Int::class.java) ?: 0,
                            banned = snapshot.child("banned").getValue(Boolean::class.java) ?: false,
                            isAdmin = snapshot.child("isAdmin").getValue(Boolean::class.java) ?: false || isForcedAdmin,
                            password = savedPassword,
                            gameUid = snapshot.child("gameUid").getValue(String::class.java) ?: "",
                            referCode = snapshot.child("referCode").getValue(String::class.java) ?: "",
                            isHostManager = snapshot.child("isHostManager").getValue(Boolean::class.java) ?: false,
                            hostTournaments = snapshot.child("hostTournaments").getValue(Boolean::class.java) ?: false,
                            hostUsers = snapshot.child("hostUsers").getValue(Boolean::class.java) ?: false,
                            hostWithdrawals = snapshot.child("hostWithdrawals").getValue(Boolean::class.java) ?: false,
                            hostAnnouncements = snapshot.child("hostAnnouncements").getValue(Boolean::class.java) ?: false,
                            managedTournamentIds = snapshot.child("managedTournamentIds").getValue(String::class.java) ?: "",
                            ipAddress = snapshot.child("ipAddress").getValue(String::class.java) ?: ""
                        )
                        
                        if (isForcedAdmin && !(snapshot.child("isAdmin").getValue(Boolean::class.java) ?: false)) {
                            FirebaseDatabase.getInstance().getReference("users").child(emailKey).child("isAdmin").setValue(true)
                        }
                        
                        if (model.banned) {
                            _loginError.value = "This account has been banned due to violation of policies."
                            return
                        }

                        viewModelScope.launch(Dispatchers.IO) {
                            dao.insertUser(model)
                            sharedPrefs.edit().putString("logged_in_email_key", emailKey).apply()
                            loadUserSession(emailKey)
                        }
                    } else {
                        _loginError.value = "No account found under this email. Please register first!"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _isLoading.value = false
                    _loginError.value = "Sync Error: ${error.message}"
                }
            })
    }

    private suspend fun getPublicIpAddress(): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.ipify.org")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                val ip = reader.readLine()
                reader.close()
                ip ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
        }
    }

    fun register(email: String, name: String, password: String, gameUid: String, referCode: String) {
        if (email.isBlank() || name.isBlank() || password.isBlank() || gameUid.isBlank()) {
            _loginError.value = "Required fields cannot be blank!"
            return
        }
        val emailKey = normalizeEmail(email)
        _isLoading.value = true
        _loginError.value = null

        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()

        // Check if user already exists
        FirebaseDatabase.getInstance().getReference("users").child(emailKey)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        _isLoading.value = false
                        _loginError.value = "Email is already registered. Please login!"
                    } else {
                        // Process the referral code if valid, check device limit
                        viewModelScope.launch(Dispatchers.IO) {
                            var validReferralUserKey = ""
                            var grantReferBonus = false
                            
                            if (referCode.isNotBlank()) {
                                try {
                                    val spamRef = FirebaseDatabase.getInstance().getReference("spam_devices").child(deviceId).get().await()
                                    val isCurrentDeviceSpam = spamRef.exists() && spamRef.getValue(Boolean::class.java) == true

                                    if (!isCurrentDeviceSpam) {
                                        val dbRef = FirebaseDatabase.getInstance().getReference("users")
                                        val snap = dbRef.get().await()
                                        var referee: UserEntity? = null
                                        
                                        for (child in snap.children) {
                                            val u = child.getValue(UserEntity::class.java)
                                            if (u != null && u.referCode == referCode.trim()) {
                                                referee = u
                                                break
                                            }
                                        }
                                        
                                        if (referee != null && referee.deviceId != deviceId) {
                                            val refereeSpamRef = FirebaseDatabase.getInstance().getReference("spam_devices").child(referee.deviceId).get().await()
                                            val isRefereeDeviceSpam = refereeSpamRef.exists() && refereeSpamRef.getValue(Boolean::class.java) == true

                                            if (!isRefereeDeviceSpam) {
                                                // Count accounts registered on this device ID so far
                                                var deviceAccountsCount = 0
                                                for (child in snap.children) {
                                                    val uD = child.child("deviceId").getValue(String::class.java)
                                                    if (uD == deviceId) deviceAccountsCount++
                                                }
                                                
                                                // If more than 0 existing accounts, limit reached (allowed: only 1 per device)
                                                if (deviceAccountsCount == 0) {
                                                    validReferralUserKey = referee.emailKey
                                                    grantReferBonus = true
                                                } else {
                                                    // Automatically flag device as spam if someone makes multiple accounts
                                                    FirebaseDatabase.getInstance().getReference("spam_devices").child(deviceId).setValue(true)
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            // Spawn absolutely pristine, zero-balance account
                            val isUserAdmin = email.trim().lowercase().contains("admin") || email.trim().lowercase() == "modspak4@gmail.com"
                            val newUser = UserEntity(
                                emailKey = emailKey,
                                name = name.trim(),
                                email = email.trim(),
                                mainWallet = 0.0,
                                bonusWallet = 0.0,
                                winningWallet = 0.0,
                                coins = 0.0,
                                matchesPlayed = 0,
                                matchesWon = 0,
                                banned = false,
                                isAdmin = isUserAdmin,
                                password = password,
                                gameUid = gameUid.trim(),
                                referCode = "${name.trim()}_${(10..99).random()}", // generated self refer code
                                referredBy = validReferralUserKey,
                                deviceId = deviceId,
                                totalReferrals = 0
                            )

                            syncManager.saveUserDirectly(newUser)

                            // Save transaction history log of sign-up
                            val welcomeTx = TransactionRecordEntity(
                                id = "signup_${UUID.randomUUID()}",
                                emailKey = emailKey,
                                type = "SIGNUP",
                                amount = 0.0,
                                coins = 0.0,
                                status = "SUCCESS",
                                timestamp = System.currentTimeMillis(),
                                details = "Pristine onboarding register complete"
                            )
                            syncManager.saveTransactionDirectly(welcomeTx)
                            
                            if (grantReferBonus && validReferralUserKey.isNotBlank()) {
                                // Add refer coins/bonus from config
                                val config = dao.getAppConfigSync() ?: AppConfigEntity()
                                // Credit referee
                                FirebaseDatabase.getInstance().getReference("users").child(validReferralUserKey).get().addOnSuccessListener { rSnap ->
                                    val rUser = rSnap.getValue(UserEntity::class.java)
                                    if (rUser != null) {
                                        val updatedRef = rUser.copy(
                                            totalReferrals = rUser.totalReferrals + 1,
                                            coins = rUser.coins + config.referCoinReward,
                                            bonusWallet = rUser.bonusWallet + config.referCashReward
                                        )
                                        syncManager.saveUserDirectly(updatedRef)
                                        
                                        if (config.referCoinReward > 0 || config.referCashReward > 0) {
                                            val refTx = TransactionRecordEntity(
                                                id = "refer_${UUID.randomUUID()}",
                                                emailKey = rUser.emailKey,
                                                type = "REFERRAL",
                                                amount = config.referCashReward,
                                                coins = config.referCoinReward,
                                                status = "SUCCESS",
                                                timestamp = System.currentTimeMillis(),
                                                details = "Referral bonus for signing up ${newUser.name}"
                                            )
                                            syncManager.saveTransactionDirectly(refTx)
                                        }
                                    }
                                }
                            }

                            dao.insertUser(newUser)
                            sharedPrefs.edit().putString("logged_in_email_key", emailKey).apply()
                            
                            withContext(Dispatchers.Main) {
                                loadUserSession(emailKey)
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _isLoading.value = false
                    _loginError.value = "Connection lost: ${error.message}"
                }
            })
    }

    fun logout() {
        _isLoading.value = false
        _loginError.value = null
        syncManager.stopUserAndProgressSync()
        sharedPrefs.edit().remove("logged_in_email_key").apply()
        _currentUser.value = null
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearUsers()
            dao.clearTaskProgress()
        }
    }

    private fun normalizeEmail(email: String): String {
        return email.trim().lowercase().replace(".", "_")
    }

    // 7-Day Claims Log Flow
    fun claimDailyReward(onError: (String) -> Unit) {
        val user = _currentUser.value ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val spamRef = FirebaseDatabase.getInstance().getReference("spam_devices").child(user.deviceId).get().await()
                if (spamRef.exists() && spamRef.getValue(Boolean::class.java) == true) {
                    withContext(Dispatchers.Main) {
                        onError("Device is blocked from rewards (Duplicate Accounts Detected).")
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    val oneDayMillis = 24 * 60 * 60 * 1000L
                    
                    var currentDay = if (now - user.lastDailyRewardTime > 2 * oneDayMillis && user.lastDailyRewardTime > 0) 1 else user.dailyRewardDay
                    
                    // Prevent claiming early
                    if (now - user.lastDailyRewardTime < oneDayMillis && user.lastDailyRewardTime > 0) {
                        val diff = oneDayMillis - (now - user.lastDailyRewardTime)
                        val hours = diff / (60 * 60 * 1000L)
                        val minutes = (diff % (60 * 60 * 1000L)) / (60 * 1000L)
                        onError("Next reward available in ${hours}h ${minutes}m.")
                        return@withContext
                    }
                    
                    val rewardsList = _dbDailyRewards.value
                    val coinsReward = rewardsList.getOrElse(currentDay - 1) { 5.0 }
                    
                    val nextDay = if (currentDay >= 7) 1 else currentDay + 1
                    
                    val updatedUser = user.copy(
                        coins = user.coins + coinsReward,
                        dailyRewardDay = nextDay,
                        lastDailyRewardTime = now
                    )
                    syncManager.saveUserDirectly(updatedUser)

                    val tx = TransactionRecordEntity(
                        id = "claim_${user.emailKey}_day${currentDay}_${UUID.randomUUID()}",
                        emailKey = user.emailKey,
                        type = "REWARD_CLAIM",
                        amount = 0.0,
                        coins = coinsReward,
                        status = "SUCCESS",
                        timestamp = now,
                        details = "Day $currentDay reward claimed",
                        screenshotUrl = ""
                    )
                    syncManager.saveTransactionDirectly(tx)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun incrementTournamentAdWatched(tournamentId: String) {
        val user = _currentUser.value ?: return
        val currentProgress = _tournamentAdProgress.value.find { it.tournamentId == tournamentId }?.watchedCount ?: 0
        val newProgress = currentProgress + 1
        FirebaseDatabase.getInstance().getReference("tournament_ad_progress").child(user.emailKey).child(tournamentId).child("watchedCount").setValue(newProgress)
    }

    // Real Unity Ads integration with fallback simulation to handle live ads without blockage
    fun showUnityRewardedAd(activity: android.app.Activity, tournamentId: String, onAdShowResult: (Boolean) -> Unit = {}) {
        val user = _currentUser.value ?: return
        val placementId = _unityRewardedId.value.trim()
        val gameId = _unityGameId.value.trim()

        if (gameId.isBlank()) {
            activity.runOnUiThread {
                android.widget.Toast.makeText(activity, "Unity Ads config missing.", android.widget.Toast.LENGTH_SHORT).show()
                onAdShowResult(false)
            }
            return
        }

        activity.runOnUiThread {
            try {
                // Ensure Unity Ads is initialized
                if (!UnityAds.isInitialized) {
                    UnityAds.initialize(activity, gameId, false, object : IUnityAdsInitializationListener {
                        override fun onInitializationComplete() {
                            loadAndShowUnityAdPlayback(activity, placementId, tournamentId, onAdShowResult)
                        }
                        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, msg: String?) {
                            activity.runOnUiThread {
                                android.widget.Toast.makeText(activity, "Unity Ads Failed: $msg.", android.widget.Toast.LENGTH_SHORT).show()
                                onAdShowResult(false)
                            }
                        }
                    })
                } else {
                    loadAndShowUnityAdPlayback(activity, placementId, tournamentId, onAdShowResult)
                }
            } catch (e: Exception) {
                Log.e("UnityAds", "Error launching Unity Ads: ${e.message}", e)
                android.widget.Toast.makeText(activity, "Ads error.", android.widget.Toast.LENGTH_SHORT).show()
                onAdShowResult(false)
            }
        }
    }

    private fun executeShowUnityAd(
        activity: android.app.Activity,
        placementId: String,
        tournamentId: String,
        onAdShowResult: (Boolean) -> Unit
    ) {
        try {
            UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(placement: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "Failed to present ad: $message.", android.widget.Toast.LENGTH_SHORT).show()
                        onAdShowResult(false)
                        preloadUnityAds() // Preload next ad
                    }
                }

                override fun onUnityAdsShowStart(placement: String?) {}
                override fun onUnityAdsShowClick(placement: String?) {}

                override fun onUnityAdsShowComplete(placement: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                    activity.runOnUiThread {
                        if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            android.widget.Toast.makeText(activity, "Rewarded Video Complete! Coins credited successfully.", android.widget.Toast.LENGTH_SHORT).show()
                            processAdCompletionReward(tournamentId)
                            onAdShowResult(true)
                        } else {
                            android.widget.Toast.makeText(activity, "Ad video skipped before completion.", android.widget.Toast.LENGTH_SHORT).show()
                            onAdShowResult(false)
                        }
                        preloadUnityAds() // Preload next ad
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("UnityAds", "Error showing Unity Ad: ${e.message}", e)
            activity.runOnUiThread {
                android.widget.Toast.makeText(activity, "Ads presenter issue.", android.widget.Toast.LENGTH_SHORT).show()
                onAdShowResult(false)
                preloadUnityAds() // Preload next ad
            }
        }
    }

    private fun loadAndShowUnityAdPlayback(
        activity: android.app.Activity,
        placementId: String,
        tournamentId: String,
        onAdShowResult: (Boolean) -> Unit
    ) {
        if (isUnityRewardedLoaded) {
            isUnityRewardedLoaded = false
            executeShowUnityAd(activity, placementId, tournamentId, onAdShowResult)
        } else {
            activity.runOnUiThread {
                android.widget.Toast.makeText(activity, "Loading Unity video ad, please wait...", android.widget.Toast.LENGTH_SHORT).show()
            }
            pendingAdTournamentId = tournamentId
            currentAdActivity = activity
            currentAdResultCallback = onAdShowResult
            preloadUnityAds()
        }
    }

    // Unity Ads playback simulation with 120 secs local anti-ban cooldown counter
    fun processAdCompletionReward(tournamentId: String) {
        val user = _currentUser.value ?: return
        
        // Save timestamp in SharedPreferences for this tournament ID
        sharedPrefs.edit().putLong("last_watched_$tournamentId", System.currentTimeMillis()).apply()
        updateCooldownTicks()

        // Update task progress if user has task "WATCH_AD"
        saveTaskProgressByAction("PLAY_MINS", 1) // Play 2 minutes roughly
        saveTaskProgressByAction("WATCH_AD", 1) // Also track ad watch tasks
        
        viewModelScope.launch(Dispatchers.IO) {
            val spamRef = FirebaseDatabase.getInstance().getReference("spam_devices").child(user.deviceId).get().await()
            if (spamRef.exists() && spamRef.getValue(Boolean::class.java) == true) {
                withContext(Dispatchers.Main) { 
                    android.widget.Toast.makeText(context, "Device is blocked from rewards (Duplicate Accounts).", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Grant dynamic reward for watching ad
            val rewardCoins = if (tournamentId == "rewards_wallet_watch") {
                (10..25).random().toDouble()
            } else {
                5.0
            }
            val updatedUser = user.copy(coins = user.coins + rewardCoins)
            syncManager.saveUserDirectly(updatedUser)

            val tx = TransactionRecordEntity(
                id = "ad_reward_${user.emailKey}_${UUID.randomUUID()}",
                emailKey = user.emailKey,
                type = "AD_REWARD",
                amount = 0.0,
                coins = rewardCoins,
                status = "SUCCESS",
                timestamp = System.currentTimeMillis(),
                details = "Unity Rewarded video reward ($rewardCoins Coins)"
            )
            syncManager.saveTransactionDirectly(tx)
        }
    }

    private fun updateCooldownTicks() {
        val now = System.currentTimeMillis()
        val currentCooldowns = mutableMapOf<String, Int>()
        val allMatches = tournaments.value

        for (match in allMatches) {
            val lastWatched = sharedPrefs.getLong("last_watched_${match.id}", 0L)
            val elapsed = (now - lastWatched) / 1000
            val remaining = (60 - elapsed).toInt()
            if (lastWatched > 0 && remaining > 0) {
                currentCooldowns[match.id] = remaining
            }
        }
        
        // Cooldown for rewards_wallet_watch (3 minutes = 180 seconds)
        val lastWatchedReward = sharedPrefs.getLong("last_watched_rewards_wallet_watch", 0L)
        val elapsedReward = (now - lastWatchedReward) / 1000
        val remainingReward = (180 - elapsedReward).toInt()
        if (lastWatchedReward > 0 && remainingReward > 0) {
            currentCooldowns["rewards_wallet_watch"] = remainingReward
        }

        _cooldowns.value = currentCooldowns
    }

    // Join Tournament logic using wallets and dynamic watch limits check
    fun joinTournament(tournament: TournamentEntity, adCountWatched: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = _currentUser.value
        if (user == null) {
            onError("Login required!")
            return
        }
        
        // Check if user is already joined
        if (user.joinedTournaments.contains(tournament.id)) {
            onError("You have already joined this tournament!")
            return
        }

        if (tournament.slotsFilled >= tournament.totalSlots) {
            onError("Lobby is fully completed & full!")
            return
        }

        // Check if user has watched required ads (Only if FREE)
        if (tournament.entryCurrency == "FREE" && tournament.adsRequired > 0 && adCountWatched < tournament.adsRequired) {
            onError("You must complete all ${tournament.adsRequired} required video views to bypass registration. ($adCountWatched/$tournament.adsRequired completed)")
            return
        }

        // Deduct fees
        val fee = tournament.entryFee
        var updatedUser = user
        if (tournament.entryCurrency == "COINS" && fee > 0.0) {
            if (user.coins < fee) {
                onError("Insufficient Coins! Total: ${user.coins.toInt()} Coins. Fee: ${fee.toInt()} Coins.")
                return
            }
            updatedUser = updatedUser.copy(
                coins = user.coins - fee,
                matchesPlayed = user.matchesPlayed + 1,
                joinedTournaments = if (user.joinedTournaments.isBlank()) tournament.id else "${user.joinedTournaments},${tournament.id}"
            )
            syncManager.saveUserDirectly(updatedUser)
        } else if (tournament.entryCurrency == "CASH" && fee > 0.0) {
            val totalBalance = user.mainWallet + user.bonusWallet + user.winningWallet
            if (totalBalance < fee) {
                onError("Insufficient wallet credits! Total: Rs.${totalBalance}. Fee: Rs.${fee}. Please deposit cash.")
                return
            }

            // Deduct from wallets (order: bonusWallet -> winningWallet -> mainWallet)
            var remains = fee
            var updatedBonus = user.bonusWallet
            var updatedWinning = user.winningWallet
            var updatedMain = user.mainWallet

            if (updatedBonus >= remains) {
                updatedBonus -= remains
                remains = 0.0
            } else {
                remains -= updatedBonus
                updatedBonus = 0.0
            }

            if (remains > 0.0) {
                if (updatedWinning >= remains) {
                    updatedWinning -= remains
                    remains = 0.0
                } else {
                    remains -= updatedWinning
                    updatedWinning = 0.0
                }
            }

            if (remains > 0.0) {
                if (updatedMain >= remains) {
                    updatedMain -= remains
                    remains = 0.0
                } else {
                    onError("Wallet calculation error.")
                    return
                }
            }

            // Update user balance to Firebase & Room
            updatedUser = updatedUser.copy(
                mainWallet = updatedMain,
                bonusWallet = updatedBonus,
                winningWallet = updatedWinning,
                matchesPlayed = user.matchesPlayed + 1,
                joinedTournaments = if (user.joinedTournaments.isBlank()) tournament.id else "${user.joinedTournaments},${tournament.id}"
            )
            syncManager.saveUserDirectly(updatedUser)
        } else {
            // Free tournament or fee = 0
            updatedUser = updatedUser.copy(
                matchesPlayed = user.matchesPlayed + 1,
                joinedTournaments = if (user.joinedTournaments.isBlank()) tournament.id else "${user.joinedTournaments},${tournament.id}"
            )
            syncManager.saveUserDirectly(updatedUser)
        }

        // Increment slots filled in Firebase
        val updatedTournament = tournament.copy(slotsFilled = tournament.slotsFilled + 1)
        syncManager.saveTournamentDirectly(updatedTournament)

        // Save transactional log
        val txId = "join_${tournament.id}_${user.emailKey}_${UUID.randomUUID()}"
        val tx = TransactionRecordEntity(
            id = txId,
            emailKey = user.emailKey,
            type = "JOIN",
            amount = -tournament.entryFee,
            coins = 0.0,
            status = "SUCCESS",
            timestamp = System.currentTimeMillis(),
            details = "Successfully joined lobby: ${tournament.title}"
        )
        syncManager.saveTransactionDirectly(tx)

        // Increment dynamic task Join Tourney progress
        saveTaskProgressByAction("JOIN_TOURNEY", 1)

        onSuccess()
    }

    fun saveTaskProgressByAction(actionType: String, increment: Int) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val matchingTasks = dailyTasks.value.filter { it.taskType == actionType }
            val now = System.currentTimeMillis()
            val calNow = java.util.Calendar.getInstance()
            calNow.timeInMillis = now
            val strNow = "${calNow.get(java.util.Calendar.YEAR)}-${calNow.get(java.util.Calendar.DAY_OF_YEAR)}"

            for (taskTemplate in matchingTasks) {
                val taskId = taskTemplate.id
                val progressKey = "${user.emailKey}_$taskId"
                val activeProgress = _taskProgress.value.find { it.taskId == taskId }
                
                var currentVal = activeProgress?.currentValue ?: 0
                var isClaimed = activeProgress?.claimed ?: false

                if (taskTemplate.isDaily && activeProgress != null) {
                    val calLast = java.util.Calendar.getInstance()
                    calLast.timeInMillis = activeProgress.lastUpdated
                    val strLast = "${calLast.get(java.util.Calendar.YEAR)}-${calLast.get(java.util.Calendar.DAY_OF_YEAR)}"
                    if (strNow != strLast) {
                        currentVal = 0 // Reset daily task
                        isClaimed = false
                    }
                }

                if (isClaimed) continue // do not perform increments if already claimed. Wait, if claimed but daily? It would have been reset by above check if next day.

                val target = taskTemplate.targetValue
                val updatedProgress = TaskProgressEntity(
                    compositeKey = progressKey,
                    emailKey = user.emailKey,
                    taskId = taskId,
                    currentValue = currentVal + increment,
                    claimed = currentVal + increment >= target,
                    lastUpdated = now
                )
                syncManager.saveTaskProgressDirectly(updatedProgress)

                // If freshly completed claim, trigger award coins automatically
                if (currentVal < target && (currentVal + increment) >= target) {
                    val updatedUser = user.copy(coins = user.coins + taskTemplate.coinReward)
                    syncManager.saveUserDirectly(updatedUser)

                    val tx = TransactionRecordEntity(
                        id = "task_complete_${taskId}_${user.emailKey}_${UUID.randomUUID()}",
                        emailKey = user.emailKey,
                        type = "REWARD_CLAIM",
                        amount = 0.0,
                        coins = taskTemplate.coinReward,
                        status = "SUCCESS",
                        timestamp = System.currentTimeMillis(),
                        details = "Challenge '${taskTemplate.title}' reward auto-claimed"
                    )
                    syncManager.saveTransactionDirectly(tx)
                }
            }
        }
    }

    // Save customized user task progress completion values
    fun saveTaskProgress(taskId: String, increment: Int) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val progressKey = "${user.emailKey}_$taskId"
            val activeProgress = _taskProgress.value.find { it.taskId == taskId }
            val taskTemplate = dailyTasks.value.find { it.id == taskId } ?: return@launch
            
            var currentVal = activeProgress?.currentValue ?: 0
            var isClaimed = activeProgress?.claimed ?: false

            val now = System.currentTimeMillis()
            val calNow = java.util.Calendar.getInstance()
            calNow.timeInMillis = now
            val strNow = "${calNow.get(java.util.Calendar.YEAR)}-${calNow.get(java.util.Calendar.DAY_OF_YEAR)}"

            if (taskTemplate.isDaily && activeProgress != null) {
                val calLast = java.util.Calendar.getInstance()
                calLast.timeInMillis = activeProgress.lastUpdated
                val strLast = "${calLast.get(java.util.Calendar.YEAR)}-${calLast.get(java.util.Calendar.DAY_OF_YEAR)}"
                if (strNow != strLast) {
                    currentVal = 0
                    isClaimed = false
                }
            }

            if (isClaimed) return@launch

            val target = taskTemplate.targetValue

            val updatedProgress = TaskProgressEntity(
                compositeKey = progressKey,
                emailKey = user.emailKey,
                taskId = taskId,
                currentValue = currentVal + increment,
                claimed = currentVal + increment >= target,
                lastUpdated = now
            )
            syncManager.saveTaskProgressDirectly(updatedProgress)

            // If freshly completed claim, trigger award coins automatically
            if (currentVal < target && (currentVal + increment) >= target) {
                // Award coins
                val updatedUser = user.copy(coins = user.coins + taskTemplate.coinReward)
                syncManager.saveUserDirectly(updatedUser)

                val tx = TransactionRecordEntity(
                    id = "task_complete_${taskId}_${user.emailKey}_${UUID.randomUUID()}",
                    emailKey = user.emailKey,
                    type = "REWARD_CLAIM",
                    amount = 0.0,
                    coins = taskTemplate.coinReward,
                    status = "SUCCESS",
                    timestamp = System.currentTimeMillis(),
                    details = "Challenge '${taskTemplate.title}' reward auto-claimed"
                )
                syncManager.saveTransactionDirectly(tx)
            }
        }
    }

    fun manuallyClaimTaskReward(taskId: String, onComplete: (Boolean, String) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val spamRef = FirebaseDatabase.getInstance().getReference("spam_devices").child(user.deviceId).get().await()
            if (spamRef.exists() && spamRef.getValue(Boolean::class.java) == true) {
                withContext(Dispatchers.Main) { onComplete(false, "Device is blocked from rewards (Duplicate Accounts Detected).") }
                return@launch
            }

            val taskTemplate = dailyTasks.value.find { it.id == taskId }
            if (taskTemplate == null) {
                withContext(Dispatchers.Main) { onComplete(false, "Task not found") }
                return@launch
            }

            var currentVal = 0
            if (taskTemplate.taskType == "REFER") {
                currentVal = user.totalReferrals
            } else {
                val p = _taskProgress.value.find { it.taskId == taskId }
                currentVal = p?.currentValue ?: 0
            }

            if (currentVal < taskTemplate.targetValue) {
                withContext(Dispatchers.Main) { onComplete(false, "Task not yet completed") }
                return@launch
            }

            val activeProgress = _taskProgress.value.find { it.taskId == taskId }
            if (activeProgress?.claimed == true) {
                withContext(Dispatchers.Main) { onComplete(false, "Already claimed") }
                return@launch
            }

            val updatedProgress = com.example.data.TaskProgressEntity(
                compositeKey = "${user.emailKey}_$taskId",
                emailKey = user.emailKey,
                taskId = taskId,
                currentValue = currentVal,
                claimed = true,
                lastUpdated = System.currentTimeMillis()
            )
            syncManager.saveTaskProgressDirectly(updatedProgress)

            val updatedUser = user.copy(coins = user.coins + taskTemplate.coinReward)
            syncManager.saveUserDirectly(updatedUser)

            val tx = com.example.data.TransactionRecordEntity(
                id = "task_manual_claim_${taskId}_${user.emailKey}_${java.util.UUID.randomUUID()}",
                emailKey = user.emailKey,
                type = "REWARD_CLAIM",
                amount = 0.0,
                coins = taskTemplate.coinReward,
                status = "SUCCESS",
                timestamp = System.currentTimeMillis(),
                details = "Challenge '${taskTemplate.title}' reward claimed manually"
            )
            syncManager.saveTransactionDirectly(tx)

            withContext(Dispatchers.Main) { onComplete(true, "Reward claimed successfully!") }
        }
    }

    // Deposit flow: credits user wallet directly in demo structure or registers a PENDING transaction
    fun requestDeposit(amount: Double, screenshotUrl: String, method: String, acntNumber: String, acntName: String) {
        val user = _currentUser.value ?: return
        val id = "dep_${UUID.randomUUID().toString().substring(0, 6)}"
        val newTx = TransactionRecordEntity(
            id = id,
            emailKey = user.emailKey,
            type = "DEPOSIT",
            amount = amount,
            coins = 0.0,
            status = "PENDING",
            timestamp = System.currentTimeMillis(),
            details = "Method: $method\nSender Name: $acntName\nSender Number: $acntNumber",
            screenshotUrl = screenshotUrl
        )
        syncManager.saveTransactionDirectly(newTx)
        saveTaskProgressByAction("DEPOSIT", 1)
        _depositRequestSuccess.value = "Deposit of Rs.${amount} is in queue! Admin will approve shortly."
    }

    // Buy Coins package: uses cash wallets to purchase coin balance vouchers
    fun buyCoins(packageTitle: String, price: Double, coinsYield: Double, bonusYield: Double) {
        val user = _currentUser.value ?: return
        val totalBalance = user.mainWallet + user.winningWallet

        if (totalBalance < price) {
            _depositRequestSuccess.value = "Error: Insufficient balance to purchase voucher! Please deposit cash."
            return
        }

        // Deduct from main/winning wallet
        var remains = price
        var updatedMain = user.mainWallet
        var updatedWinning = user.winningWallet

        if (updatedWinning >= remains) {
            updatedWinning -= remains
            remains = 0.0
        } else {
            remains -= updatedWinning
            updatedWinning = 0.0
        }

        if (remains > 0.0 && updatedMain >= remains) {
            updatedMain -= remains
            remains = 0.0
        }

        val updatedUser = user.copy(
            mainWallet = updatedMain,
            winningWallet = updatedWinning,
            coins = user.coins + coinsYield + bonusYield
        )
        syncManager.saveUserDirectly(updatedUser)

        val txId = "buy_coins_${UUID.randomUUID().toString().substring(0, 8)}"
        val tx = TransactionRecordEntity(
            id = txId,
            emailKey = user.emailKey,
            type = "COIN_PURCHASE",
            amount = -price,
            coins = coinsYield + bonusYield,
            status = "SUCCESS",
            timestamp = System.currentTimeMillis(),
            details = "Purchased $packageTitle (+${bonusYield} Bonus)"
        )
        syncManager.saveTransactionDirectly(tx)
        _depositRequestSuccess.value = "Successfully purchased $packageTitle!"
    }

    fun requestWithdraw(amount: Double, details: String, onError: (String) -> Unit) {
        val user = _currentUser.value ?: return
        val minimum = _minWithdraw.value.toDoubleOrNull() ?: 50.0
        if (amount < minimum) {
            onError("Minimum withdrawal amount is Rs.$minimum")
            return
        }
        if (user.winningWallet < amount) {
            onError("Insufficient withdrawable winnings balance! (Winning: Rs.${user.winningWallet})")
            return
        }

        // Deduct immediately as a temporary lock
        val updatedUser = user.copy(winningWallet = user.winningWallet - amount)
        syncManager.saveUserDirectly(updatedUser)

        val txId = "with_${UUID.randomUUID().toString().substring(0, 6)}"
        val tx = TransactionRecordEntity(
            id = txId,
            emailKey = user.emailKey,
            type = "WITHDRAW",
            amount = -amount,
            coins = 0.0,
            status = "PENDING",
            timestamp = System.currentTimeMillis(),
            details = details
        )
        syncManager.saveTransactionDirectly(tx)
        _depositRequestSuccess.value = "Withdraw request for Rs.${amount} placed successfully!"
    }

    fun submitDiamondRedemption(packTitle: String, coinCost: Int) {
        val user = _currentUser.value ?: return
        if (user.coins >= coinCost) {
            val updatedUser = user.copy(coins = user.coins - coinCost)
            syncManager.saveUserDirectly(updatedUser)

            val tx = TransactionRecordEntity(
                id = "diamond_${user.emailKey}_${UUID.randomUUID()}",
                emailKey = user.emailKey,
                type = "WITHDRAW",
                amount = 0.0,
                coins = -coinCost.toDouble(),
                status = "PENDING",
                timestamp = System.currentTimeMillis(),
                details = "Free Fire Diamond Top-Up: $packTitle (Game UID: ${user.gameUid})",
                screenshotUrl = ""
            )
            syncManager.saveTransactionDirectly(tx)
        }
    }

    fun clearDepositStatusText() {
        _depositRequestSuccess.value = null
    }

    // ============================================
    // SECURE ADMIN CONTROL OPERATIONS
    // ============================================

    fun adminCreateTournament(t: TournamentEntity) {
        val user = _currentUser.value
        val isManaged = user?.managedTournamentIds?.split(",")?.map { it.trim() }?.contains(t.id) == true
        if (user?.isAdmin == true || (user?.isHostManager == true && user.hostTournaments == true && (user.managedTournamentIds.isBlank() || isManaged))) {
            syncManager.saveTournamentDirectly(t)
            if (user?.isAdmin == true) {
                adminSendAnnouncement("New Tournament!", "A new tournament '${t.title}' has been scheduled. Join now!")
            }
        }
    }

    fun adminDeleteTournament(id: String) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.deleteTournamentDirectly(id)
        }
    }

    fun adminDistributeTournamentReward(tournamentId: String, distributions: Map<String, Double>, onComplete: (String) -> Unit) {
        val admin = _currentUser.value ?: return
        val isManaged = admin.managedTournamentIds.split(",").map { it.trim() }.contains(tournamentId)
        val canManage = admin.isAdmin || (admin.isHostManager && admin.hostTournaments && (admin.managedTournamentIds.isBlank() || isManaged))
        if (!canManage) return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val t = tournaments.value.find { it.id == tournamentId }
            if (t == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete("Tournament not found") }
                return@launch
            }
            
            // Distribute
            distributions.forEach { (emailKey, amount) ->
                val u = allUsers.value.find { it.emailKey == emailKey }
                if (u != null) {
                    val isCoin = t.prizeCurrency == "COINS"
                    val updatedUser = if (isCoin) {
                        u.copy(coins = u.coins + amount)
                    } else {
                        u.copy(winningWallet = u.winningWallet + amount)
                    }
                    syncManager.saveUserDirectly(updatedUser)
                    
                    // Transaction history
                    val tx = com.example.data.TransactionRecordEntity(
                        id = "tx_tourney_win_${t.id}_${u.emailKey}_${java.util.UUID.randomUUID()}",
                        emailKey = u.emailKey,
                        type = if(isCoin) "TOURNAMENT_WIN_COINS" else "TOURNAMENT_WIN_CASH",
                        amount = if(isCoin) 0.0 else amount,
                        coins = if(isCoin) amount else 0.0,
                        status = "SUCCESS",
                        timestamp = System.currentTimeMillis(),
                        details = "Won ${if(isCoin) "${amount.toInt()} coins" else "Rs.${amount.toInt()}"} from tournament ${t.title}"
                    )
                    syncManager.saveTransactionDirectly(tx)
                }
            }
            
            val updatedTournament = t.copy(status = "COMPLETED")
            syncManager.saveTournamentDirectly(updatedTournament)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete("Rewards distributed successfully!") }
        }
    }

    fun adminCreateTaskTemplate(task: DailyTaskEntity) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.saveTaskTemplateDirectly(task)
        }
    }

    fun adminKickPlayerFromTournament(tournamentId: String, emailKey: String, onComplete: (String) -> Unit) {
        val admin = _currentUser.value ?: return
        val isManaged = admin.managedTournamentIds.split(",").map { it.trim() }.contains(tournamentId)
        val canManage = admin.isAdmin || (admin.isHostManager && admin.hostTournaments && (admin.managedTournamentIds.isBlank() || isManaged))
        if (!canManage) return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val t = tournaments.value.find { it.id == tournamentId }
            val u = allUsers.value.find { it.emailKey == emailKey }
            if (t == null || u == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete("Error: not found") }
                return@launch
            }
            
            val newJoined = u.joinedTournaments.split(",").filter { it.isNotBlank() && it != tournamentId }.joinToString(",")
            val updatedUser = u.copy(joinedTournaments = newJoined)
            syncManager.saveUserDirectly(updatedUser)
            
            val updatedTournament = t.copy(slotsFilled = if (t.slotsFilled > 0) t.slotsFilled - 1 else 0)
            syncManager.saveTournamentDirectly(updatedTournament)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete("Player kicked") }
        }
    }

    fun adminAddBotsToTournament(tournamentId: String, botCount: Int, onComplete: (String) -> Unit = {}) {
        val admin = _currentUser.value ?: return
        val isManaged = admin.managedTournamentIds.split(",").map { it.trim() }.contains(tournamentId)
        val canManage = admin.isAdmin || (admin.isHostManager && admin.hostTournaments && (admin.managedTournamentIds.isBlank() || isManaged))
        if (!canManage) return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val t = tournaments.value.find { it.id == tournamentId } ?: return@launch
            val updatedTournament = t.copy(slotsFilled = t.slotsFilled + botCount)
            syncManager.saveTournamentDirectly(updatedTournament)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete("Added $botCount bots successfully!") }
        }
    }

    fun adminDeleteTaskTemplate(id: String) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.deleteTaskTemplateDirectly(id)
        }
    }

    fun redeemReferralCode(code: String, onComplete: (Boolean, String) -> Unit) {
        val user = _currentUser.value ?: return
        if (user.referredBy.isNotBlank()) {
            onComplete(false, "You have already redeemed a referral code!")
            return
        }
        if (code.trim() == user.referCode) {
            onComplete(false, "You cannot redeem your own code!")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if current user device is banned from referral rewards
                val spamRef = FirebaseDatabase.getInstance().getReference("spam_devices").child(user.deviceId).get().await()
                if (spamRef.exists() && spamRef.getValue(Boolean::class.java) == true) {
                    withContext(Dispatchers.Main) { onComplete(false, "Your device is blocked from referral rewards due to previous spam.") }
                    return@launch
                }

                val dbRef = FirebaseDatabase.getInstance().getReference("users")
                val snap = dbRef.get().await()
                var referee: UserEntity? = null
                
                for (child in snap.children) {
                    val u = child.getValue(UserEntity::class.java)
                    if (u != null && u.referCode == code.trim()) {
                        referee = u
                        break
                    }
                }
                
                if (referee == null) {
                    withContext(Dispatchers.Main) { onComplete(false, "Invalid referral code!") }
                    return@launch
                }
                
                if (referee.deviceId == user.deviceId) {
                    withContext(Dispatchers.Main) { onComplete(false, "Cannot redeem from the same device!") }
                    return@launch
                }

                val refereeSpamRef = FirebaseDatabase.getInstance().getReference("spam_devices").child(referee.deviceId).get().await()
                if (refereeSpamRef.exists() && refereeSpamRef.getValue(Boolean::class.java) == true) {
                    withContext(Dispatchers.Main) { onComplete(false, "The referral code owner is blocked due to spam.") }
                    return@launch
                }

                // Credit the referee
                val config = dao.getAppConfigSync() ?: AppConfigEntity()
                
                val updatedReferee = referee.copy(
                    totalReferrals = referee.totalReferrals + 1,
                    coins = referee.coins + config.referCoinReward,
                    bonusWallet = referee.bonusWallet + config.referCashReward
                )
                syncManager.saveUserDirectly(updatedReferee)
                
                if (config.referCoinReward > 0 || config.referCashReward > 0) {
                    val refTx = TransactionRecordEntity(
                        id = "refer_${UUID.randomUUID()}",
                        emailKey = referee.emailKey,
                        type = "REFERRAL",
                        amount = config.referCashReward,
                        coins = config.referCoinReward,
                        status = "SUCCESS",
                        timestamp = System.currentTimeMillis(),
                        details = "Referral bonus from matching code ${user.name}"
                    )
                    syncManager.saveTransactionDirectly(refTx)
                }

                // Update current user
                val updatedUser = user.copy(referredBy = referee.emailKey)
                syncManager.saveUserDirectly(updatedUser)
                
                withContext(Dispatchers.Main) { onComplete(true, "Referral code redeemed successfully!") }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete(false, "Network error. Please try again.") }
            }
        }
    }

    fun adminSetPlatformSettings(
        gameId: String, rewardedId: String, interstitialId: String,
        epNum: String, epName: String, jcNum: String, jcName: String, minW: String,
        r1: Double, r2: Double, r3: Double, r4: Double, r5: Double, r6: Double, r7: Double,
        referCoin: Double, referCash: Double
    ) {
        if (_currentUser.value?.isAdmin == true) {
            val settings = mapOf(
                "unity_game_id" to gameId,
                "unity_rewarded_id" to rewardedId,
                "unity_interstitial_id" to interstitialId,
                "ep_number" to epNum,
                "ep_title" to epName,
                "jc_number" to jcNum,
                "jc_title" to jcName,
                "min_withdraw" to minW,
                "daily_reward_1" to r1,
                "daily_reward_2" to r2,
                "daily_reward_3" to r3,
                "daily_reward_4" to r4,
                "daily_reward_5" to r5,
                "daily_reward_6" to r6,
                "daily_reward_7" to r7,
                "refer_coin_reward" to referCoin,
                "refer_cash_reward" to referCash
            )
            syncManager.settingsRef.updateChildren(settings)
        }
    }

    fun adminSetLegalSettings(supportEmail: String, terms: String, privacy: String) {
        if (_currentUser.value?.isAdmin == true) {
            val sMap = mapOf(
                "support_email" to supportEmail,
                "terms_text" to terms,
                "privacy_text" to privacy
            )
            syncManager.settingsRef.updateChildren(sMap)
        }
    }

    fun adminCreateDiamondPack(pack: DiamondPackEntity) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.saveDiamondPackDirectly(pack)
        }
    }

    fun adminDeleteDiamondPack(id: String) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.deleteDiamondPackDirectly(id)
        }
    }

    fun adminSendInboxMessage(userId: String, message: String) {
        if (_currentUser.value?.isAdmin == true) {
            FirebaseDatabase.getInstance().getReference("users").child(userId)
                .child("inboxMessage").setValue(message)
        }
    }

    fun clearInboxMessage() {
        val user = _currentUser.value ?: return
        FirebaseDatabase.getInstance().getReference("users").child(user.emailKey)
            .child("inboxMessage").setValue("")
    }

    fun uploadImageToImgBB(file: File, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val url = ImgBBRepository.uploadImage(file)
            withContext(Dispatchers.Main) {
                if (url != null) {
                    onSuccess(url)
                } else {
                    onError("Failed to upload image")
                }
            }
        }
    }

    fun savePromoSlider(promo: PromoSliderEntity) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.savePromoSliderDirectly(promo)
        }
    }

    fun deletePromoSlider(id: String) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.deletePromoSliderDirectly(id)
        }
    }

    fun initiateAdminSync() {
        val user = _currentUser.value
        if (user?.isAdmin == true || user?.isHostManager == true) {
            syncManager.startAdminAllUsersSync()
        }
    }

    fun adminBanControlUser(emailKey: String, bannedState: Boolean) {
        val user = _currentUser.value
        if (user?.isAdmin == true || (user?.isHostManager == true && user.hostUsers == true)) {
            FirebaseDatabase.getInstance().getReference("users").child(emailKey)
                .child("banned").setValue(bannedState)
        }
    }

    fun adminRevokeReferralRewards(emailKey: String) {
        if (_currentUser.value?.isAdmin == true) {
            val u = allUsers.value.find { it.emailKey == emailKey }
            if (u != null) {
                val updatedUser = u.copy(
                    totalReferrals = 0,
                    bonusWallet = 0.0,
                    coins = if (u.coins > 100) u.coins - 100 else 0.0
                )
                syncManager.saveUserDirectly(updatedUser)
                // Blacklist this deviceID globally
                FirebaseDatabase.getInstance().getReference("spam_devices").child(u.deviceId).setValue(true)
            }
        }
    }

    fun adminApproveTransaction(tx: TransactionRecordEntity) {
        if (_currentUser.value?.isAdmin == true) {
            // Update tx state to APPROVED
            FirebaseDatabase.getInstance().getReference("transactions").child(tx.id)
                .child("status").setValue("APPROVED")

            // Send notification
            sendUserNotification(
                emailKey = tx.emailKey,
                title = "Transaction Approved",
                message = "Your ${tx.type.lowercase()} transaction has been approved.",
                type = tx.type
            )

            // If deposit, credit user wallet root
            if (tx.type == "DEPOSIT") {
                FirebaseDatabase.getInstance().getReference("users").child(tx.emailKey)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val currentWallet = snapshot.child("mainWallet").getValue(Double::class.java) ?: 0.0
                                FirebaseDatabase.getInstance().getReference("users").child(tx.emailKey)
                                    .child("mainWallet").setValue(currentWallet + tx.amount)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            } else if (tx.type == "WITHDRAW") {
                // Already deducted. Simply update tx details
                FirebaseDatabase.getInstance().getReference("transactions").child(tx.id)
                    .child("details").setValue("Withdrawal request APPROVED. Transferred successfully.")
            }
        }
    }

    fun adminRejectTransaction(tx: TransactionRecordEntity) {
        if (_currentUser.value?.isAdmin == true) {
            FirebaseDatabase.getInstance().getReference("transactions").child(tx.id)
                .child("status").setValue("REJECTED")

            sendUserNotification(
                emailKey = tx.emailKey,
                title = "Transaction Rejected",
                message = "Your ${tx.type.lowercase()} request was rejected. Please contact support.",
                type = tx.type
            )

            // If withdraw, refund the deducted amount
            if (tx.type == "WITHDRAW") {
                FirebaseDatabase.getInstance().getReference("users").child(tx.emailKey)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val currentWinnings = snapshot.child("winningWallet").getValue(Double::class.java) ?: 0.0
                                FirebaseDatabase.getInstance().getReference("users").child(tx.emailKey)
                                    .child("winningWallet").setValue(currentWinnings + Math.abs(tx.amount))
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
        }
    }

    fun adminAdjustUserWallets(emailKey: String, main: Double, bonus: Double, winning: Double, coins: Double) {
        val user = _currentUser.value
        if (user?.isAdmin == true || (user?.isHostManager == true && user.hostUsers == true)) {
            val ref = FirebaseDatabase.getInstance().getReference("users").child(emailKey)
            ref.child("mainWallet").setValue(main)
            ref.child("bonusWallet").setValue(bonus)
            ref.child("winningWallet").setValue(winning)
            ref.child("coins").setValue(coins)
        }
    }

    fun adminSetHostPermissions(
        emailKey: String, 
        isHostManager: Boolean, 
        hostTournaments: Boolean, 
        hostUsers: Boolean, 
        hostWithdrawals: Boolean, 
        hostAnnouncements: Boolean, 
        managedTournamentIds: String
    ) {
        if (_currentUser.value?.isAdmin == true) {
            val updates = mapOf(
                "isHostManager" to isHostManager,
                "hostTournaments" to hostTournaments,
                "hostUsers" to hostUsers,
                "hostWithdrawals" to hostWithdrawals,
                "hostAnnouncements" to hostAnnouncements,
                "managedTournamentIds" to managedTournamentIds
            )
            FirebaseDatabase.getInstance().getReference("users").child(emailKey).updateChildren(updates)
        }
    }

    fun markNotificationsAsRead() {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.markAllNotificationsAsRead(user.emailKey)
            // also update in firebase to respect read status across devices
            val notifs = notifications.value.filter { !it.isRead }
            for (n in notifs) {
                val path = if(n.emailKey == "GLOBAL") "notifications/GLOBAL/${n.id}/isRead" else "notifications/${user.emailKey}/${n.id}/isRead"
                FirebaseDatabase.getInstance().getReference(path).setValue(true)
            }
        }
    }

    fun adminSendAnnouncement(title: String, message: String) {
        if (_currentUser.value?.isAdmin == true) {
            val id = UUID.randomUUID().toString()
            val notif = mapOf(
                "emailKey" to "GLOBAL",
                "title" to title,
                "message" to message,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false,
                "type" to "ANNOUNCEMENT"
            )
            FirebaseDatabase.getInstance().getReference("notifications/GLOBAL/$id").setValue(notif)
        }
    }

    fun sendUserNotification(emailKey: String, title: String, message: String, type: String) {
        val id = UUID.randomUUID().toString()
        val notif = mapOf(
            "emailKey" to emailKey,
            "title" to title,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false,
            "type" to type
        )
        FirebaseDatabase.getInstance().getReference("notifications/$emailKey/$id").setValue(notif)
    }

    fun showUnityInterstitialAd(activity: android.app.Activity, onAdClosed: (Boolean) -> Unit = {}) {
        val gameId = _unityGameId.value.trim()
        val interstitialId = _unityInterstitialId.value.trim()
        
        if (gameId.isBlank() || interstitialId.isBlank()) {
            onAdClosed(true)
            return
        }
        
        activity.runOnUiThread {
            if (!UnityAds.isInitialized) {
                UnityAds.initialize(activity, gameId, false, object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        loadAndShowInterstitialPlayback(activity, interstitialId, onAdClosed)
                    }
                    override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, msg: String?) {
                        onAdClosed(true)
                    }
                })
            } else {
                loadAndShowInterstitialPlayback(activity, interstitialId, onAdClosed)
            }
        }
    }
    
    private fun loadAndShowInterstitialPlayback(activity: android.app.Activity, placementId: String, onAdClosed: (Boolean) -> Unit) {
        if (isUnityInterstitialLoaded) {
            isUnityInterstitialLoaded = false
            executeShowUnityInterstitialAd(activity, placementId, onAdClosed)
        } else {
            pendingInterstitialRequested = true
            currentAdActivity = activity
            currentInterstitialResultCallback = onAdClosed
            preloadUnityAds()
        }
    }
    
    private fun executeShowUnityInterstitialAd(activity: android.app.Activity, placementId: String, onAdClosed: (Boolean) -> Unit) {
        try {
            UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(placement: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                    activity.runOnUiThread {
                        onAdClosed(true)
                        preloadUnityAds()
                    }
                }
                override fun onUnityAdsShowStart(placement: String?) {}
                override fun onUnityAdsShowClick(placement: String?) {}
                override fun onUnityAdsShowComplete(placement: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                    activity.runOnUiThread {
                        onAdClosed(true)
                        preloadUnityAds()
                    }
                }
            })
        } catch (e: Exception) {
            activity.runOnUiThread {
                onAdClosed(true)
                preloadUnityAds()
            }
        }
    }
}

