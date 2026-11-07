package ro.hack4arad.topul.ui.place

import ro.hack4arad.topul.ui.theme.Scrim
import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.ui.theme.Night

/** „Turul 3D”: scena Gaussian splat a locului, afișată de vizualizatorul web de pe server. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Tour3DScreen(placeId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0F1226"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl(Network.baseUrl + "tur3d/$placeId")
        }
    }
    DisposableEffect(Unit) { onDispose { webView.destroy() } }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Scrim.copy(alpha = 0.7f), contentColor = Color.White),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clip(CircleShape),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi") }
    }
}
