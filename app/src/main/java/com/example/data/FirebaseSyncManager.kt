package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirebaseSyncManager(
    private val context: Context,
    private val dao: EsportsDao,
    private val scope: CoroutineScope
) {
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")
    private val tournamentsRef = database.getReference("tournaments")
    private val tasksRef = database.getReference("daily_task_templates")
    private val transactionsRef = database.getReference("transactions")
    private val promosRef = database.getReference("promo_sliders")
    val settingsRef = database.getReference("settings")
    private val diamondRef = database.getReference("diamond_packages")
    private val notificationsRef = database.getReference("notifications")
    
    private var activeUsersListener: ValueEventListener? = null
    private var tournamentsListener: ValueEventListener? = null
    private var tasksListener: ValueEventListener? = null
    private var transactionsListener: ValueEventListener? = null
    private var promosListener: ValueEventListener? = null
    private var progressListener: ValueEventListener? = null
    private var diamondListener: ValueEventListener? = null
    private var notificationsListener: ValueEventListener? = null
    private var globalNotificationsListener: ValueEventListener? = null

    init {
        startMetadataSync()
        seedDefaultsIfEmpty()
    }

    fun startUserAndProgressSync(emailKey: String) {
        stopUserAndProgressSync()

        Log.d("FirebaseSync", "Starting real-time sync for $emailKey")
        
        val userRef = usersRef.child(emailKey)
        activeUsersListener = userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val user = snapshot.toUserEntity()
                    user?.let {
                        scope.launch(Dispatchers.IO) {
                            dao.insertUser(it)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseSync", "User sync cancelled: ${error.message}")
            }
        })

        // Listen to task progress for this user
        val progressRef = database.getReference("task_progress").child(emailKey)
        progressListener = progressRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val progressList = mutableListOf<TaskProgressEntity>()
                for (child in snapshot.children) {
                    val taskId = child.key ?: continue
                    val currentValue = child.child("currentValue").getValue(Int::class.java) ?: 0
                    val claimed = child.child("claimed").getValue(Boolean::class.java) ?: false
                    val lastUpdated = child.child("lastUpdated").getValue(Long::class.java) ?: 0L
                    progressList.add(
                        TaskProgressEntity(
                            compositeKey = "${emailKey}_$taskId",
                            emailKey = emailKey,
                            taskId = taskId,
                            currentValue = currentValue,
                            claimed = claimed,
                            lastUpdated = lastUpdated
                        )
                    )
                }
                scope.launch(Dispatchers.IO) {
                    dao.insertTaskProgresses(progressList)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Listen to tournament ad progress
        val adProgressRef = database.getReference("tournament_ad_progress").child(emailKey)
        adProgressRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TournamentAdProgressEntity>()
                for (child in snapshot.children) {
                    val tournamentId = child.key ?: continue
                    val watchedCount = child.child("watchedCount").getValue(Int::class.java) ?: 0
                    list.add(
                        TournamentAdProgressEntity(
                            compositeKey = "${emailKey}_$tournamentId",
                            emailKey = emailKey,
                            tournamentId = tournamentId,
                            watchedCount = watchedCount
                        )
                    )
                }
                scope.launch(Dispatchers.IO) {
                    dao.insertTournamentAdProgresses(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Listen to specific user notifications
        val userNotifRef = notificationsRef.child(emailKey)
        notificationsListener = userNotifRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifList = mutableListOf<NotificationEntity>()
                for (child in snapshot.children) {
                    child.toNotificationEntity()?.let { notifList.add(it.copy(emailKey = emailKey)) }
                }
                scope.launch(Dispatchers.IO) {
                    dao.insertNotifications(notifList)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Listen to global announcements
        val globalNotifRef = notificationsRef.child("GLOBAL")
        globalNotificationsListener = globalNotifRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifList = mutableListOf<NotificationEntity>()
                for (child in snapshot.children) {
                    child.toNotificationEntity()?.let { notifList.add(it.copy(emailKey = "GLOBAL")) }
                }
                scope.launch(Dispatchers.IO) {
                    dao.insertNotifications(notifList)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private var allUsersListener: ValueEventListener? = null
    fun startAdminAllUsersSync() {
        if (allUsersListener == null) {
            allUsersListener = usersRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<UserEntity>()
                    for (child in snapshot.children) {
                        child.toUserEntity()?.let { list.add(it) }
                    }
                    scope.launch(Dispatchers.IO) {
                        list.forEach { dao.insertUser(it) }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    fun stopUserAndProgressSync() {
        activeUsersListener?.let { usersRef.removeEventListener(it) }
        progressListener?.let { database.getReference("task_progress").removeEventListener(it) }
    }

    private fun startMetadataSync() {
        tournamentsListener = tournamentsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TournamentEntity>()
                for (child in snapshot.children) {
                    val t = child.toTournamentEntity()
                    if (t != null) list.add(t)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearTournaments()
                    dao.insertTournaments(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        tasksListener = tasksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<DailyTaskEntity>()
                for (child in snapshot.children) {
                    val task = child.toDailyTaskEntity()
                    if (task != null) list.add(task)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearDailyTasks()
                    dao.insertDailyTasks(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        transactionsListener = transactionsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TransactionRecordEntity>()
                for (child in snapshot.children) {
                    val tx = child.toTransactionRecordEntity()
                    if (tx != null) list.add(tx)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearTransactions()
                    dao.insertTransactions(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        promosListener = promosRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<PromoSliderEntity>()
                for (child in snapshot.children) {
                    val p = child.toPromoSliderEntity()
                    if (p != null) list.add(p)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearPromoSliders()
                    dao.insertPromoSliders(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        diamondListener = diamondRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<DiamondPackEntity>()
                for (child in snapshot.children) {
                    val p = child.toDiamondPackEntity()
                    if (p != null) list.add(p)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearDiamondPacks()
                    dao.insertDiamondPacks(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun seedDefaultsIfEmpty() {
        settingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val defaults = mapOf(
                        "unity_game_id" to "5763487",
                        "unity_rewarded_id" to "Rewarded_Android",
                        "unity_interstitial_id" to "Interstitial_Android"
                    )
                    settingsRef.setValue(defaults)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        tournamentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val now = System.currentTimeMillis()
                    val initialTournaments = listOf(
                        TournamentEntity(id = "match_01", title = "BGMI Squad Master Series S1", gameType = "BGMI", mapType = "Sanhok", entryFee = 30.0, prizePool = 5000.0, slotsFilled = 25, totalSlots = 25, adsRequired = 3, scheduleTimeMillis = now - 3600000, status = "COMPLETED", roomId = "987213", roomPassword = "pass88"),
                        TournamentEntity(id = "match_02", title = "Apex Custom Legends Cup", gameType = "Apex Legends", mapType = "World's Edge", entryFee = 25.0, prizePool = 2500.0, slotsFilled = 14, totalSlots = 20, adsRequired = 2, scheduleTimeMillis = now + 1200000, status = "UPCOMING", roomId = "554611", roomPassword = "apex99"),
                        TournamentEntity(id = "match_03", title = "CODM Lone Wolf Duel League", gameType = "Call of Duty", mapType = "Nuketown", entryFee = 20.0, prizePool = 1500.0, slotsFilled = 8, totalSlots = 8, adsRequired = 0, scheduleTimeMillis = now + 3000000, status = "OPEN", roomId = "771212", roomPassword = "codm33"),
                        TournamentEntity(id = "match_04", title = "Free Fire Solo Survival Arena", gameType = "Free Fire", mapType = "Bermuda", entryFee = 0.0, prizePool = 1000.0, slotsFilled = 12, totalSlots = 100, adsRequired = 4, scheduleTimeMillis = now + 14400000, status = "OPEN", roomId = "112233", roomPassword = "ff99")
                    )
                    for (t in initialTournaments) {
                        tournamentsRef.child(t.id).setValue(t)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        tasksRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val initialTasks = listOf(
                        DailyTaskEntity("task_01", "Play Game 2 Minutes", "PLAY_MINS", 2, 5.0),
                        DailyTaskEntity("task_02", "Play Game 15 Minutes", "PLAY_MINS", 15, 10.0),
                        DailyTaskEntity("task_03", "Play Game 20 Minutes", "PLAY_MINS", 20, 15.0),
                        DailyTaskEntity("task_04", "Join 1 Tournament", "JOIN_TOURNEY", 1, 8.0),
                        DailyTaskEntity("task_05", "Win 1 Tournament Match", "WIN_MATCH", 1, 25.0),
                        DailyTaskEntity("task_06", "Refer 1 Friend", "REFER", 1, 15.0)
                    )
                    for (task in initialTasks) {
                        tasksRef.child(task.id).setValue(task)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        diamondRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val initialPacks = listOf(
                        DiamondPackEntity("dim_1", "110 Diamonds", 100),
                        DiamondPackEntity("dim_2", "231 Diamonds", 200),
                        DiamondPackEntity("dim_3", "583 Diamonds", 500),
                        DiamondPackEntity("dim_4", "1188 Diamonds", 1000)
                    )
                    for (p in initialPacks) {
                        diamondRef.child(p.id).setValue(p)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun saveUserDirectly(user: UserEntity) {
        usersRef.child(user.emailKey).setValue(user)
    }

    fun saveTournamentDirectly(tournament: TournamentEntity) {
        tournamentsRef.child(tournament.id).setValue(tournament)
    }

    fun deleteTournamentDirectly(id: String) {
        tournamentsRef.child(id).removeValue()
    }

    fun saveTaskTemplateDirectly(task: DailyTaskEntity) {
        tasksRef.child(task.id).setValue(task)
    }

    fun deleteTaskTemplateDirectly(id: String) {
        tasksRef.child(id).removeValue()
    }

    fun saveTaskProgressDirectly(progress: TaskProgressEntity) {
        val ref = database.getReference("task_progress")
            .child(progress.emailKey)
            .child(progress.taskId)
        
        val values = mapOf(
            "currentValue" to progress.currentValue,
            "claimed" to progress.claimed
        )
        ref.setValue(values)
    }

    fun saveTransactionDirectly(tx: TransactionRecordEntity) {
        transactionsRef.child(tx.id).setValue(tx)
    }

    fun savePromoSliderDirectly(promo: PromoSliderEntity) {
        promosRef.child(promo.id).setValue(promo)
    }

    fun deletePromoSliderDirectly(id: String) {
        promosRef.child(id).removeValue()
    }

    fun saveDiamondPackDirectly(pack: DiamondPackEntity) {
        diamondRef.child(pack.id).setValue(pack)
    }

    fun deleteDiamondPackDirectly(id: String) {
        diamondRef.child(id).removeValue()
    }
}

private fun DataSnapshot.toUserEntity(): UserEntity? {
    return try {
        val emailKey = key ?: ""
        val name = child("name").getValue(String::class.java) ?: ""
        val email = child("email").getValue(String::class.java) ?: ""
        val mainWallet = child("mainWallet").getValue(Double::class.java) ?: 0.0
        val bonusWallet = child("bonusWallet").getValue(Double::class.java) ?: 0.0
        val winningWallet = child("winningWallet").getValue(Double::class.java) ?: 0.0
        val coins = child("coins").getValue(Double::class.java) ?: 0.0
        val matchesPlayed = child("matchesPlayed").getValue(Int::class.java) ?: 0
        val matchesWon = child("matchesWon").getValue(Int::class.java) ?: 0
        val banned = child("banned").getValue(Boolean::class.java) ?: false
        val isAdmin = child("isAdmin").getValue(Boolean::class.java) ?: false
        val password = child("password").getValue(String::class.java) ?: ""
        val gameUid = child("gameUid").getValue(String::class.java) ?: ""
        val referCode = child("referCode").getValue(String::class.java) ?: ""
        val referredBy = child("referredBy").getValue(String::class.java) ?: ""
        val deviceId = child("deviceId").getValue(String::class.java) ?: ""
        val totalReferrals = child("totalReferrals").getValue(Int::class.java) ?: 0
        val inboxMessage = child("inboxMessage").getValue(String::class.java) ?: ""
        val lastGameUidChangeTime = child("lastGameUidChangeTime").getValue(Long::class.java) ?: 0L
        val joinedTournaments = child("joinedTournaments").getValue(String::class.java) ?: ""
        val dailyRewardDay = child("dailyRewardDay").getValue(Int::class.java) ?: 1
        val lastDailyRewardTime = child("lastDailyRewardTime").getValue(Long::class.java) ?: 0L
        val isHostManager = child("isHostManager").getValue(Boolean::class.java) ?: false
        val hostTournaments = child("hostTournaments").getValue(Boolean::class.java) ?: false
        val hostUsers = child("hostUsers").getValue(Boolean::class.java) ?: false
        val hostWithdrawals = child("hostWithdrawals").getValue(Boolean::class.java) ?: false
        val hostAnnouncements = child("hostAnnouncements").getValue(Boolean::class.java) ?: false
        val managedTournamentIds = child("managedTournamentIds").getValue(String::class.java) ?: ""
        val ipAddress = child("ipAddress").getValue(String::class.java) ?: ""
        UserEntity(
            emailKey = emailKey, name = name, email = email, mainWallet = mainWallet, bonusWallet = bonusWallet,
            winningWallet = winningWallet, coins = coins, matchesPlayed = matchesPlayed, matchesWon = matchesWon,
            banned = banned, isAdmin = isAdmin, password = password, gameUid = gameUid, referCode = referCode,
            referredBy = referredBy, deviceId = deviceId, totalReferrals = totalReferrals,
            inboxMessage = inboxMessage, lastGameUidChangeTime = lastGameUidChangeTime,
            joinedTournaments = joinedTournaments, dailyRewardDay = dailyRewardDay, lastDailyRewardTime = lastDailyRewardTime,
            isHostManager = isHostManager, hostTournaments = hostTournaments, hostUsers = hostUsers,
            hostWithdrawals = hostWithdrawals, hostAnnouncements = hostAnnouncements, managedTournamentIds = managedTournamentIds,
            ipAddress = ipAddress
        )
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toTournamentEntity(): TournamentEntity? {
    return try {
        val id = key ?: ""
        val title = child("title").getValue(String::class.java) ?: ""
        val gameType = child("gameType").getValue(String::class.java) ?: ""
        val mapType = child("mapType").getValue(String::class.java) ?: ""
        val entryFee = child("entryFee").getValue(Double::class.java) ?: 0.0
        val prizePool = child("prizePool").getValue(Double::class.java) ?: 0.0
        val perKillPrize = child("perKillPrize").getValue(Double::class.java) ?: 0.0
        val rankPrizes = child("rankPrizes").getValue(String::class.java) ?: ""
        val slotsFilled = child("slotsFilled").getValue(Int::class.java) ?: 0
        val totalSlots = child("totalSlots").getValue(Int::class.java) ?: 0
        val adsRequired = child("adsRequired").getValue(Int::class.java) ?: 0
        val scheduleTimeMillis = child("scheduleTimeMillis").getValue(Long::class.java) ?: 0L
        val status = child("status").getValue(String::class.java) ?: "OPEN"
        val roomId = child("roomId").getValue(String::class.java) ?: ""
        val roomPassword = child("roomPassword").getValue(String::class.java) ?: ""
        val bannerUrl = child("bannerUrl").getValue(String::class.java) ?: ""
        val description = child("description").getValue(String::class.java) ?: ""
        val showRewardIndex = child("showRewardIndex").getValue(Boolean::class.java) ?: true
        val entryCurrency = child("entryCurrency").getValue(String::class.java) ?: "CASH"
        val prizeCurrency = child("prizeCurrency").getValue(String::class.java) ?: "CASH"
        TournamentEntity(
            id = id, title = title, gameType = gameType, mapType = mapType, entryFee = entryFee, prizePool = prizePool,
            perKillPrize = perKillPrize, rankPrizes = rankPrizes,
            slotsFilled = slotsFilled, totalSlots = totalSlots, adsRequired = adsRequired,
            scheduleTimeMillis = scheduleTimeMillis, status = status, roomId = roomId, roomPassword = roomPassword,
            bannerUrl = bannerUrl, description = description, showRewardIndex = showRewardIndex,
            entryCurrency = entryCurrency, prizeCurrency = prizeCurrency
        )
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toDailyTaskEntity(): DailyTaskEntity? {
    return try {
        val id = key ?: ""
        val title = child("title").getValue(String::class.java) ?: ""
        val taskType = child("taskType").getValue(String::class.java) ?: ""
        val targetValue = child("targetValue").getValue(Int::class.java) ?: 1
        val coinReward = child("coinReward").getValue(Double::class.java) ?: 0.0
        val isDaily = child("isDaily").getValue(Boolean::class.java) ?: false
        DailyTaskEntity(id, title, taskType, targetValue, coinReward, isDaily)
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toTransactionRecordEntity(): TransactionRecordEntity? {
    return try {
        val id = key ?: ""
        val emailKey = child("emailKey").getValue(String::class.java) ?: ""
        val type = child("type").getValue(String::class.java) ?: ""
        val amount = child("amount").getValue(Double::class.java) ?: 0.0
        val coins = child("coins").getValue(Double::class.java) ?: 0.0
        val status = child("status").getValue(String::class.java) ?: ""
        val timestamp = child("timestamp").getValue(Long::class.java) ?: 0L
        val details = child("details").getValue(String::class.java) ?: ""
        val screenshotUrl = child("screenshotUrl").getValue(String::class.java) ?: ""
        TransactionRecordEntity(id, emailKey, type, amount, coins, status, timestamp, details, screenshotUrl)
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toPromoSliderEntity(): PromoSliderEntity? {
    return try {
        val id = key ?: ""
        val imageUrl = child("imageUrl").getValue(String::class.java) ?: ""
        val title = child("title").getValue(String::class.java) ?: ""
        val actionUrl = child("actionUrl").getValue(String::class.java) ?: ""
        val active = child("active").getValue(Boolean::class.java) ?: true
        PromoSliderEntity(id, imageUrl, title, actionUrl, active)
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toDiamondPackEntity(): DiamondPackEntity? {
    return try {
        val id = key ?: ""
        val title = child("title").getValue(String::class.java) ?: ""
        val coinCost = child("coinCost").getValue(Int::class.java) ?: 0
        val imageUrl = child("imageUrl").getValue(String::class.java) ?: ""
        DiamondPackEntity(id, title, coinCost, imageUrl)
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toNotificationEntity(): NotificationEntity? {
    return try {
        val id = key ?: ""
        val emailKey = child("emailKey").getValue(String::class.java) ?: ""
        val title = child("title").getValue(String::class.java) ?: ""
        val message = child("message").getValue(String::class.java) ?: ""
        val timestamp = child("timestamp").getValue(Long::class.java) ?: 0L
        val isRead = child("isRead").getValue(Boolean::class.java) ?: false
        val type = child("type").getValue(String::class.java) ?: ""
        NotificationEntity(id, emailKey, title, message, timestamp, isRead, type)
    } catch (e: Exception) {
        null
    }
}
