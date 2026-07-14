package com.example

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.OkHttpClient
import okhttp3.Cache
import okhttp3.Request
import okhttp3.Headers
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // Register file chooser launcher for upload inputs in WebView
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val dataString = data.dataString
                val clipData = data.clipData
                if (clipData != null) {
                    val uris = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    uris
                } else if (dataString != null) {
                    arrayOf(Uri.parse(dataString))
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            null
        }
        fileChooserCallback?.onReceiveValue(results)
        fileChooserCallback = null
    }

    // Broadcast receiver to intercept incoming SMS for OTP autofill
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION") {
                val bundle = intent.extras ?: return
                try {
                    val pdus = bundle.get("pdus") as Array<*>? ?: return
                    for (pdu in pdus) {
                        val format = bundle.getString("format")
                        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            SmsMessage.createFromPdu(pdu as ByteArray, format)
                        } else {
                            @Suppress("DEPRECATION")
                            SmsMessage.createFromPdu(pdu as ByteArray)
                        }
                        val body = message.messageBody ?: ""
                        
                        // Detect a sequence of 4 to 6 numbers which is standard for Iranian OTP codes
                        val otpRegex = Regex("""\b\d{4,6}\b""")
                        val match = otpRegex.find(body)
                        if (match != null) {
                            val code = match.value
                            runOnUiThread {
                                webView?.evaluateJavascript(
                                    """
                                    (function() {
                                        var inputs = document.querySelectorAll('input[type="tel"], input[autocomplete="one-time-code"], input[name*="code"], input[id*="otp"], input[class*="otp"], input[placeholder*="کد"]');
                                        for (var i = 0; i < inputs.length; i++) {
                                            inputs[i].value = '$code';
                                            inputs[i].dispatchEvent(new Event('input', { bubbles: true }));
                                            inputs[i].dispatchEvent(new Event('change', { bubbles: true }));
                                            break;
                                        }
                                    })();
                                    """.trimIndent(), null
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-create WebView cache directories to prevent Chromium opendir errors/warnings
        try {
            val codeCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            if (!codeCacheDir.exists()) {
                codeCacheDir.mkdirs()
            }
            java.io.File(codeCacheDir, "js").mkdirs()
            java.io.File(codeCacheDir, "wasm").mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Safely register SMS Receiver for OTP capture with RECEIVER_EXPORTED on API 34+
        val filter = IntentFilter("android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(smsReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(smsReceiver, filter)
        }

        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainScreen(
                        onWebViewCreated = { webViewInstance ->
                            webView = webViewInstance
                        },
                        fileChooserLauncher = fileChooserLauncher,
                        onFileChooserRequest = { callback ->
                            fileChooserCallback = callback
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

@Composable
fun MainScreen(
    onWebViewCreated: (WebView) -> Unit,
    fileChooserLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onFileChooserRequest: (ValueCallback<Array<Uri>>) -> Unit
) {
    val context = LocalContext.current
    
    // UI State
    var isOnline by remember { mutableStateOf(checkInitialNetwork(context)) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Startup Permissions Request State
    var showPermissionRationale by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.CAMERA,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Permissions handled, proceed silently to load app
    }

    // Check permissions at startup
    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            showPermissionRationale = true
        }
    }

    // Network connectivity monitoring
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }
            override fun onLost(network: Network) {
                isOnline = checkInitialNetwork(context)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isOnline) {
                // Optimized SwipeRefreshLayout wrapping native WebView
                AndroidView(
                    factory = { ctx ->
                        var webViewRef: WebView? = null

                        val swipeRefreshLayout = object : androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx) {
                            override fun canChildScrollUp(): Boolean {
                                return webViewRef?.canScrollVertically(-1) == true
                            }
                        }.apply {
                            // Match the app's premium deep-blue design palette
                            setColorSchemeColors(
                                android.graphics.Color.parseColor("#001C55"),
                                android.graphics.Color.parseColor("#0050FF")
                            )
                        }

                        val webView = WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = false
                                displayZoomControls = false
                                setSupportZoom(true)
                            }
                            
                            // Hide scrollbars as requested
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            isNestedScrollingEnabled = true
                                                        webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    isLoading = true
                                    loadProgress = 0f
                                    swipeRefreshLayout.isRefreshing = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    loadProgress = 1f
                                    swipeRefreshLayout.isRefreshing = false
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    return handleUrlRedirects(ctx, request.url.toString())
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                    return handleUrlRedirects(ctx, url)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    if (request == null) return null
                                    val url = request.url.toString()
                                    
                                    // Only intercept GET requests for static assets on HTTP/HTTPS
                                    if (request.method.equals("GET", ignoreCase = true) &&
                                        (url.startsWith("http://") || url.startsWith("https://")) &&
                                        WebViewCacheManager.isStaticAsset(url)
                                    ) {
                                        try {
                                            val client = WebViewCacheManager.getOkHttpClient(ctx)
                                            val headersBuilder = okhttp3.Headers.Builder()
                                            for ((key, value) in request.requestHeaders) {
                                                try {
                                                    headersBuilder.add(key, value)
                                                } catch (e: Exception) {
                                                    // Ignore invalid headers
                                                }
                                            }
                                            
                                            val okhttpRequest = okhttp3.Request.Builder()
                                                .url(url)
                                                .headers(headersBuilder.build())
                                                .build()
                                                
                                            val response = client.newCall(okhttpRequest).execute()
                                            val responseBody = response.body
                                            if (responseBody != null) {
                                                val contentTypeHeader = response.header("Content-Type", "application/octet-stream")
                                                val mimeType = contentTypeHeader?.split(";")?.get(0)?.trim() ?: "application/octet-stream"
                                                val encoding = if (contentTypeHeader?.contains("charset=") == true) {
                                                    contentTypeHeader.split("charset=").getOrNull(1)?.split(";")?.getOrNull(0)?.trim() ?: "utf-8"
                                                } else {
                                                    "utf-8"
                                                }
                                                
                                                val responseHeaders = mutableMapOf<String, String>()
                                                for (name in response.headers.names()) {
                                                    responseHeaders[name] = response.header(name) ?: ""
                                                }
                                                
                                                return WebResourceResponse(
                                                    mimeType,
                                                    encoding,
                                                    response.code,
                                                    response.message.ifEmpty { "OK" },
                                                    responseHeaders,
                                                    responseBody.byteStream()
                                                )
                                            }
                                        } catch (e: Exception) {
                                            // Fallback silently if any exception occurs, letting the WebView fetch normally
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress.toFloat() / 100f
                                }

                                override fun onShowFileChooser(
                                    webView: WebView,
                                    filePathCallback: ValueCallback<Array<Uri>>,
                                    fileChooserParams: FileChooserParams
                                ): Boolean {
                                    onFileChooserRequest(filePathCallback)
                                    val intent = fileChooserParams.createIntent()
                                    try {
                                        fileChooserLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        return false
                                    }
                                    return true
                                }
                            }

                            loadUrl("https://hydraulic-hamzeh.ir")
                        }

                        webViewRef = webView
                        swipeRefreshLayout.addView(webView)

                        swipeRefreshLayout.setOnRefreshListener {
                            webView.reload()
                        }

                        webViewInstance = webView
                        onWebViewCreated(webView)

                        swipeRefreshLayout
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Minimalist loading bar at the top of the screen (Chrome style)
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF0050FF),
                        trackColor = Color.Transparent
                    )
                }
            } else {
                    // Fully localized beautiful Persian offline screen
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SignalWifiOff,
                                contentDescription = "آفلاین",
                                tint = Color(0xFF001C55),
                                modifier = Modifier.size(100.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "شما آفلاین هستید",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF001C55)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لطفاً اتصال اینترنت خود را بررسی کنید و دوباره تلاش کنید.",
                                fontSize = 15.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    isOnline = checkInitialNetwork(context)
                                    if (isOnline) {
                                        webViewInstance?.reload()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001C55)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "تلاش مجدد")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تلاش مجدد",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    // Interactive custom popup dialog explaining permission requests at startup
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "دسترسی", tint = Color(0xFF001C55))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "درخواست مجوزهای دسترسی",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF001C55)
                    )
                }
            },
            text = {
                Text(
                    text = "برای کارایی کامل، اپلیکیشن به دسترسی گالری و دوربین (جهت آپلود عکس و فایل در سایت) و همچنین دسترسی پیامک (جهت دریافت خودکار کد تایید OTP) نیاز دارد.",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(requiredPermissions)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001C55)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("موافقم و ادامه", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showPermissionRationale = false },
                    colors = ButtonDefaults.textButtonColors()
                ) {
                    Text("بعداً", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

// Logic to handle payment, social media, maps, and external redirects natively
private fun handleUrlRedirects(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    val host = uri.host ?: ""

    // 1. Same host (hydraulic-hamzeh.ir) -> load directly in the WebView
    if (host.contains("hydraulic-hamzeh.ir")) {
        return false
    }

    // 2. Payment Gateways (Zarinpal, Shaparak, etc.) -> open immediately in external browser
    val isPaymentGateway = url.contains("zarinpal", ignoreCase = true) ||
                           url.contains("shaparak", ignoreCase = true) ||
                           url.contains("pep.ir", ignoreCase = true) ||
                           url.contains("sadad", ignoreCase = true) ||
                           url.contains("samanpay", ignoreCase = true) ||
                           url.contains("asanpardakht", ignoreCase = true) ||
                           url.contains("pay.ir", ignoreCase = true) ||
                           url.contains("bank", ignoreCase = true) ||
                           url.contains("/pay", ignoreCase = true) ||
                           url.contains("checkout", ignoreCase = true)

    if (isPaymentGateway) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    // 3. Social Media Apps (Telegram, Instagram, WhatsApp) -> try launch native app first
    val isSocialApp = host.contains("t.me") || url.startsWith("tg:") ||
                      host.contains("instagram.com") || url.startsWith("instagram:") ||
                      host.contains("wa.me") || url.startsWith("whatsapp:")

    if (isSocialApp) {
        try {
            val nativeUri = when {
                url.startsWith("tg:") || url.startsWith("instagram:") || url.startsWith("whatsapp:") -> uri
                host.contains("t.me") -> Uri.parse(url.replace("https://t.me/", "tg://resolve?domain="))
                host.contains("instagram.com") -> {
                    val username = uri.lastPathSegment ?: ""
                    Uri.parse("instagram://user?username=$username")
                }
                host.contains("wa.me") -> Uri.parse("whatsapp://send?phone=${uri.lastPathSegment}")
                else -> uri
            }
            val intent = Intent(Intent.ACTION_VIEW, nativeUri)
            
            // Try to open native app
            val pm = context.packageManager
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to external web browser
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            return true
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            } catch (ex: Exception) {
                return false
            }
        }
    }

    // 4. Map Applications (Google Maps, Neshan, Balad) -> open natively
    val isMapApp = url.startsWith("geo:") ||
                   host.contains("maps.google") ||
                   host.contains("neshan.org") ||
                   host.contains("balad.ir")

    if (isMapApp) {
        try {
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(mapIntent)
            return true
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            } catch (ex: Exception) {
                return false
            }
        }
    }

    // 5. Calls, SMS, Mails -> open native handlers
    if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:")) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    // 6. General external links -> Open in default browser
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(browserIntent)
        return true
    } catch (e: Exception) {
        return false // Fallback to loading inside WebView if no external app found
    }
}

// Utility function to check initial network connectivity
private fun checkInitialNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// Custom Cache Manager for WebView's static resources using OkHttp with aggressive Cache-Control overriding
object WebViewCacheManager {
    private var okHttpClientInstance: okhttp3.OkHttpClient? = null

    fun getOkHttpClient(context: Context): okhttp3.OkHttpClient {
        return okHttpClientInstance ?: synchronized(this) {
            okHttpClientInstance ?: run {
                val cacheDir = java.io.File(context.cacheDir, "okhttp-static-cache")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                val cacheSize = 100 * 1024 * 1024L // 100 MiB cache
                val cache = okhttp3.Cache(cacheDir, cacheSize)
                
                okhttp3.OkHttpClient.Builder()
                    .cache(cache)
                    .addNetworkInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        val url = chain.request().url.toString()
                        if (isStaticAsset(url)) {
                            // Intercept network response and rewrite response headers to force-cache static files for 30 days
                            response.newBuilder()
                                .header("Cache-Control", "public, max-age=2592000") // 30 days
                                .header("Pragma", "public")
                                .removeHeader("Expires")
                                .build()
                        } else {
                            response
                        }
                    }
                    .build().also { okHttpClientInstance = it }
            }
        }
    }

    fun isStaticAsset(url: String): Boolean {
        val cleanUrl = url.lowercase().split("?")[0].split("#")[0]
        return cleanUrl.endsWith(".css") ||
               cleanUrl.endsWith(".js") ||
               cleanUrl.endsWith(".png") ||
               cleanUrl.endsWith(".jpg") ||
               cleanUrl.endsWith(".jpeg") ||
               cleanUrl.endsWith(".gif") ||
               cleanUrl.endsWith(".webp") ||
               cleanUrl.endsWith(".svg") ||
               cleanUrl.endsWith(".ico") ||
               cleanUrl.endsWith(".woff") ||
               cleanUrl.endsWith(".woff2") ||
               cleanUrl.endsWith(".ttf") ||
               cleanUrl.endsWith(".otf")
    }
}
