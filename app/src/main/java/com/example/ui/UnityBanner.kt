package com.example.ui

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

@Composable
fun UnityBannerAd(placementId: String = "Banner_Android") {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        factory = { ctx ->
            val bannerView = BannerView(activity, placementId, UnityBannerSize(320, 50))
            bannerView.load()
            bannerView
        }
    )
}
