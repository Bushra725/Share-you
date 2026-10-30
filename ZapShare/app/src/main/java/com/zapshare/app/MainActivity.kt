package com.zapshare.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.zapshare.app.data.ShareItem
import com.zapshare.app.downloader.VideoDownloader
import com.zapshare.app.toolbar.ShareToolbar
import com.zapshare.app.ui.navigation.ZapShareApp
import com.zapshare.app.ui.theme.ZapTheme
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow

/** Things other apps hand us via the share sheet. */
sealed class IncomingShare {
    data class Files(val items: List<ShareItem>) : IncomingShare()
    data class Link(val url: String) : IncomingShare()
}

object IntentBus {
    val incoming = MutableStateFlow<IncomingShare?>(null)
    val route = MutableStateFlow<String?>(null)
}

// AppCompatActivity: needed for in-app language switching; it is also a FragmentActivity (BiometricPrompt).
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { ZapTheme { ZapShareApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(ShareToolbar.EXTRA_ROUTE)?.let { IntentBus.route.value = it }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = streamExtra(intent)
                if (stream != null) {
                    IntentBus.incoming.value = IncomingShare.Files(listOf(FileUtils.queryMeta(this, stream)))
                } else {
                    VideoDownloader.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let {
                        IntentBus.incoming.value = IncomingShare.Link(it)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = streamListExtra(intent)
                if (list.isNotEmpty()) IntentBus.incoming.value = IncomingShare.Files(list.map { FileUtils.queryMeta(this, it) })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamListExtra(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) ?: emptyList()
}
