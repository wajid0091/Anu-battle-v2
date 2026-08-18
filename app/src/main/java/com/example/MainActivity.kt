package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.FirebaseSyncManager
import com.example.ui.EsportsApp
import com.example.ui.EsportsViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.workers.PromoNotificationWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize Firebase SDK
        FirebaseApp.initializeApp(this)

        // Setup periodic worker to fetch new tournaments from DB and push local notifications if app is killed
        val workRequest = PeriodicWorkRequestBuilder<PromoNotificationWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PromoNotificationWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // Initialize local Room Database caching replica
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "esports_database"
        )
        .fallbackToDestructiveMigration()
        .build()

        // Create the real-time continuous sync manager
        val syncManager = FirebaseSyncManager(
            context = applicationContext,
            dao = database.dao,
            scope = lifecycleScope
        )

        // Create ViewModel
        val viewModel = EsportsViewModel(
            context = applicationContext,
            dao = database.dao,
            syncManager = syncManager
        )

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                EsportsApp(viewModel = viewModel)
            }
        }
    }
}
