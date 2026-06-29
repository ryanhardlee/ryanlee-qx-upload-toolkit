package com.example.screens

import android.util.Log
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainViewModel
import com.example.ui.theme.*
import com.example.utils.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTokStudioWebScreen(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsStateWithLifecycle()
    fun getString(key: String): String = LanguageManager.getString(key, language)

    val initialUrl = "https://www.tiktok.com/tiktokstudio/upload?from=webapp"
    val fallbackUrl1 = "https://www.tiktok.com/upload"
    val fallbackUrl2 = "https://www.tiktok.com/"

    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var isDesktopMode by remember { mutableStateOf(true) }
    var uploadFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Upgraded Browser Debug states (All 12 items required by spec)
    var lastNavigationEvent by remember { mutableStateOf("Browser initialized") }
    var lastWebError by remember { mutableStateOf<String?>(null) }
    var isDebugConsoleExpanded by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("No page loaded") }
    var userAgentStr by remember { mutableStateOf("Loading user-agent...") }
    var cookiesEnabled by remember { mutableStateOf(false) }
    var thirdPartyCookiesEnabled by remember { mutableStateOf(false) }
    var lastRedirectUrl by remember { mutableStateOf<String?>(null) }
    var popupEventCount by remember { mutableStateOf(0) }
    var fileChooserEventCount by remember { mutableStateOf(0) }
    var cookieFlushResult by remember { mutableStateOf("NOT_FLUSHED") }
    var webViewPackageVersion by remember { mutableStateOf("Unknown") }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val info = WebView.getCurrentWebViewPackage()
                webViewPackageVersion = "${info?.packageName ?: "Unknown"} (${info?.versionName ?: "Unknown"})"
            } catch (e: Exception) {
                webViewPackageVersion = "Error: ${e.message}"
            }
        } else {
            webViewPackageVersion = "API < 26"
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = uploadFileCallback
        if (callback != null) {
            val data = result.data
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            callback.onReceiveValue(results)
            uploadFileCallback = null
            lastNavigationEvent = "File chooser closed. Selected: ${results?.size ?: 0} files"
        }
    }

    // Enable hardware back press to go back in WebView history
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    // Apply or Toggle Desktop Mode helper
    fun applySettings(wv: WebView, desktop: Boolean, triggerReload: Boolean = false) {
        val settings = wv.settings
        if (desktop) {
            val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            settings.userAgentString = desktopUserAgent
            userAgentStr = desktopUserAgent
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = null // default
            userAgentStr = "Default"
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
        }
        if (triggerReload) {
            wv.reload()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberNavy)
                    .border(width = 1.dp, color = CyberLine)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(CyberGlowPurple.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "QX",
                                color = CyberGlowPurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Experimental WebView — QX extension/runtime is not active.",
                                color = CyberGlowRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "RyanLee QX Experimental Sandbox (Testing Only)",
                                color = CyberMuted,
                                fontSize = 8.5.sp
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .background(CyberDark, RoundedCornerShape(8.dp))
                            .border(1.dp, CyberLine, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = CyberGlowRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Control Toolbar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Back
                    IconButton(
                        onClick = { webView?.goBack() },
                        enabled = webView?.canGoBack() == true,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = CyberGlowCyan),
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (webView?.canGoBack() == true) CyberDark else CyberDark.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .border(1.dp, CyberLine, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Forward
                    IconButton(
                        onClick = { webView?.goForward() },
                        enabled = webView?.canGoForward() == true,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = CyberGlowCyan),
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (webView?.canGoForward() == true) CyberDark else CyberDark.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .border(1.dp, CyberLine, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Refresh
                    IconButton(
                        onClick = { webView?.reload() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = CyberGlowCyan),
                        modifier = Modifier
                            .size(32.dp)
                            .background(CyberDark, RoundedCornerShape(6.dp))
                            .border(1.dp, CyberLine, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Home/Upload initial link
                    IconButton(
                        onClick = { webView?.loadUrl(initialUrl) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = CyberGlowCyan),
                        modifier = Modifier
                            .size(32.dp)
                            .background(CyberDark, RoundedCornerShape(6.dp))
                            .border(1.dp, CyberLine, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Desktop mode toggle
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(CyberDark, RoundedCornerShape(6.dp))
                            .border(1.dp, if (isDesktopMode) CyberGlowCyan else CyberLine, RoundedCornerShape(6.dp))
                            .clickable {
                                isDesktopMode = !isDesktopMode
                                lastNavigationEvent = "Desktop mode toggled: $isDesktopMode"
                                webView?.let { applySettings(it, isDesktopMode, triggerReload = true) }
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isDesktopMode) "DESKTOP MODE: ON" else "DESKTOP MODE: OFF",
                            color = if (isDesktopMode) CyberGlowCyan else CyberMuted,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Open externally fallback
                    IconButton(
                        onClick = {
                            try {
                                viewModel.setTikTokOpened(true)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open external browser", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = CyberGlowCyan),
                        modifier = Modifier
                            .size(32.dp)
                            .background(CyberDark, RoundedCornerShape(6.dp))
                            .border(1.dp, CyberLine, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Open Externally",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        containerColor = CyberDark
    ) { innerPadding ->
        val honestStatus = remember(currentUrl, pageTitle, fileChooserEventCount) {
            val urlLower = currentUrl.lowercase()
            val titleLower = pageTitle.lowercase()
            val isAuthUpload = (urlLower.contains("tiktok.com/tiktokstudio/upload") || 
                                urlLower.contains("tiktok.com/upload") || 
                                urlLower.contains("creator-center/content/upload")) && 
                                !urlLower.contains("login") && !urlLower.contains("auth")
            when {
                isAuthUpload -> "Upload page detected."
                fileChooserEventCount > 0 -> "Upload page detected (File chooser active)."
                else -> "WebView login not completed."
            }
        }

        val statusColor = when (honestStatus) {
            "Upload page detected." -> CyberGlowGreen
            "Upload page detected (File chooser active)." -> CyberGlowCyan
            else -> CyberGlowRed
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = CyberGlowPurple,
                    trackColor = CyberNavy
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CyberNavy,
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TIKTOK BROWSER STATUS: $honestStatus",
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Honest warning box for experimental WebView
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberGlowRed.copy(alpha = 0.12f))
                    .border(width = 1.dp, color = CyberGlowRed.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Warning",
                        tint = CyberGlowRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Android WebView cannot load the RyanLee QX browser extension. Use QX Extension Browser / future QX Browser for the full method.",
                        color = CyberText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    url?.let {
                                        lastRedirectUrl = it
                                        currentUrl = it
                                        lastNavigationEvent = "shouldOverrideUrlLoading (Redirect): $it"
                                        if (it.startsWith("http://") || it.startsWith("https://")) {
                                            return false
                                        }
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                            ctx.startActivity(intent)
                                            return true
                                        } catch (e: Exception) {
                                            return true
                                        }
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    isLoading = true
                                    url?.let {
                                        currentUrl = it
                                        lastNavigationEvent = "onPageStarted: $it"
                                    }
                                    // Refresh cookies state
                                    try {
                                        val cookieManager = CookieManager.getInstance()
                                        cookiesEnabled = cookieManager.acceptCookie()
                                    } catch (e: Exception) {}
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    url?.let {
                                        currentUrl = it
                                        lastNavigationEvent = "onPageFinished: $it"
                                    }
                                    // Flush cookies
                                    try {
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.flush()
                                        cookieFlushResult = "SUCCESS (Flushed onPageFinished)"
                                        cookiesEnabled = cookieManager.acceptCookie()
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP && view != null) {
                                            thirdPartyCookiesEnabled = cookieManager.acceptThirdPartyCookies(view)
                                        }
                                    } catch (e: Exception) {
                                        cookieFlushResult = "FAIL: ${e.message}"
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    lastWebError = "Code $errorCode: $description (Failing URL: $failingUrl)"
                                    lastNavigationEvent = "onReceivedError trigger"
                                    Log.e("TikTokBrowser", "Web error: $lastWebError")
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    title?.let { pageTitle = it }
                                }

                                override fun onShowFileChooser(
                                    webView: WebView,
                                    filePathCallback: ValueCallback<Array<Uri>>,
                                    fileChooserParams: FileChooserParams
                                ): Boolean {
                                    uploadFileCallback = filePathCallback
                                    fileChooserEventCount++
                                    lastNavigationEvent = "onShowFileChooser (Video upload triggered)"
                                    try {
                                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "video/*"
                                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/mp4"))
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                        }
                                        fileChooserLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error selecting files", Toast.LENGTH_SHORT).show()
                                        filePathCallback.onReceiveValue(null)
                                        uploadFileCallback = null
                                        lastWebError = "File chooser intent error: ${e.message}"
                                    }
                                    return true
                                }

                                // Handle popups and redirect windows (such as OAuth login screens) in the same WebView
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    popupEventCount++
                                    lastNavigationEvent = "onCreateWindow (Popup requested)"
                                    
                                    val tempWebView = WebView(ctx).apply {
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                                url?.let {
                                                    currentUrl = it
                                                    webView?.loadUrl(it)
                                                }
                                                return true
                                            }
                                        }
                                    }
                                    
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    if (transport != null) {
                                        transport.webView = tempWebView
                                        resultMsg.sendToTarget()
                                        return true
                                    }
                                    return false
                                }
                            }

                            val wv = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mediaPlaybackRequiresUserGesture = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                setSupportMultipleWindows(true)
                                setJavaScriptCanOpenWindowsAutomatically(true)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }
                            }

                            // Robust Session Cookie persistence
                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    setAcceptThirdPartyCookies(wv, true)
                                    thirdPartyCookiesEnabled = true
                                }
                                cookiesEnabled = acceptCookie()
                            }

                            applySettings(wv, isDesktopMode, triggerReload = false)
                            webView = wv
                            loadUrl(initialUrl)
                        }
                    },
                    update = {
                        webView = it
                    }
                )
            }

            // High-Tech Cyber Console panel at the bottom of the screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberNavy)
                    .border(width = 1.dp, color = CyberLine)
            ) {
                // Console toggle header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDebugConsoleExpanded = !isDebugConsoleExpanded }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (lastWebError == null) CyberGlowGreen else CyberGlowRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QX BROWSER CONSOLE",
                            color = CyberText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Icon(
                        imageVector = if (isDebugConsoleExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Console",
                        tint = CyberGlowCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isDebugConsoleExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .verticalScroll(rememberScrollState())
                            .background(CyberDark)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 0. Honest Status
                        ConsoleRow(label = "HONEST TIKTOK STATUS", value = honestStatus, valueColor = statusColor)
                        // 1. Current URL
                        ConsoleRow(label = "CURRENT URL", value = currentUrl)
                        // 2. Page Title
                        ConsoleRow(label = "PAGE TITLE", value = pageTitle)
                        // 3. User Agent
                        ConsoleRow(label = "USER AGENT", value = userAgentStr)
                        // 4. Desktop Mode ON/OFF
                        ConsoleRow(label = "DESKTOP MODE ACTIVE", value = if (isDesktopMode) "ON" else "OFF", valueColor = if (isDesktopMode) CyberGlowCyan else CyberMuted)
                        // 5. Cookies Enabled
                        ConsoleRow(label = "COOKIES ENABLED", value = if (cookiesEnabled) "YES" else "NO", valueColor = if (cookiesEnabled) CyberGlowGreen else CyberGlowRed)
                        // 6. Third-Party Cookies Enabled
                        ConsoleRow(label = "THIRD-PARTY COOKIES ENABLED", value = if (thirdPartyCookiesEnabled) "YES" else "NO", valueColor = if (thirdPartyCookiesEnabled) CyberGlowGreen else CyberGlowRed)
                        // 7. Last Redirect URL
                        ConsoleRow(label = "LAST REDIRECT URL", value = lastRedirectUrl ?: "None")
                        // 8. Last WebView Error
                        ConsoleRow(
                            label = "LAST WEBVIEW ERROR",
                            value = lastWebError ?: "None (All functions optimal)",
                            valueColor = if (lastWebError == null) CyberGlowGreen else CyberGlowRed
                        )
                        // 9. Popup Event Count
                        ConsoleRow(label = "POPUP WINDOWS COUNT", value = "$popupEventCount events", valueColor = if (popupEventCount > 0) CyberGlowCyan else CyberText)
                        // 10. File Chooser Event Count
                        ConsoleRow(label = "FILE CHOOSER TRIGGERS", value = "$fileChooserEventCount events", valueColor = if (fileChooserEventCount > 0) CyberGlowCyan else CyberText)
                        // 11. Cookie Flush Result
                        ConsoleRow(label = "COOKIE FLUSH RESULT", value = cookieFlushResult, valueColor = if (cookieFlushResult.startsWith("SUCCESS")) CyberGlowGreen else CyberGlowCyan)
                        // 12. WebView Package & Version
                        ConsoleRow(label = "WEBVIEW PACKAGE/VERSION", value = webViewPackageVersion)
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = CyberText
) {
    Column {
        Text(
            text = label,
            color = CyberGlowCyan,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
}
