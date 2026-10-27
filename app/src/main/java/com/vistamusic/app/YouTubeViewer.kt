package com.vistamusic.app

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Official, visibly displayed YouTube player. We do not remove ads,
 * extract audio, obscure branding, or attempt background YouTube playback.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeViewer(videoId: String?, searchQuery: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val target = if (!videoId.isNullOrBlank())
        "https://www.youtube.com/embed/$videoId?playsinline=1&autoplay=1&controls=1"
    else YouTubeCatalog.youtubeSearchUrl(searchQuery.orEmpty())
    val browser = remember(target) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            setBackgroundColor(android.graphics.Color.BLACK)
            loadUrl(target, mapOf("Referer" to "https://github.com/yuregabriell/VistaMusic"))
        }
    }
    DisposableEffect(browser) {
        onDispose {
            browser.stopLoading()
            browser.loadUrl("about:blank")
            browser.destroy()
        }
    }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF090B0D))) {
            Row(Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("YouTube · player oficial",
                    color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Fechar", tint = Color.White)
                }
            }
            AndroidView(factory = { browser }, modifier = Modifier
                .fillMaxWidth().weight(1f))
            Text("Vídeos podem conter anúncios. A reprodução é feita no YouTube.",
                color = Color(0xFF929B9D), fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
        }
    }
}
