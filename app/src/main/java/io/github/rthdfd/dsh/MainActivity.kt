package io.github.rthdfd.dsh

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var root: FrameLayout
    private lateinit var setupView: View
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var primaryButton: Button
    private lateinit var secondaryButton: Button
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var commandRunning = false
    private var skipNextResumeCheck = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        root = FrameLayout(this)
        webView = createWebView()
        setupView = createSetupView()
        root.addView(webView, matchParentParams())
        root.addView(setupView, matchParentParams())
        setContentView(root)
        refreshState(autoStart = true)
    }

    override fun onResume() {
        super.onResume()
        if (skipNextResumeCheck) {
            skipNextResumeCheck = false
        } else if (!commandRunning) {
            refreshState(autoStart = false)
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            webView.visibility == View.VISIBLE && webView.canGoBack() -> webView.goBack()
            webView.visibility == View.VISIBLE -> showRunning()
            else -> super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_CHOOSER_REQUEST) return
        val result = if (resultCode == RESULT_OK) {
            data?.clipData?.let { clip ->
                Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
            } ?: data?.data?.let { arrayOf(it) }
        } else {
            null
        }
        fileChooserCallback?.onReceiveValue(result)
        fileChooserCallback = null
    }

    private fun configureSystemBars() {
        window.statusBarColor = color(R.color.dsh_surface)
        window.navigationBarColor = color(R.color.dsh_surface)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    private fun createSetupView(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.dsh_background))
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val mark = TextView(this).apply {
            text = "DSH"
            textSize = 34f
            setTextColor(color(R.color.dsh_blue))
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(mark, linearParams(match = true, height = dp(64)))

        statusText = TextView(this).apply {
            textSize = 20f
            setTextColor(color(R.color.dsh_text))
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(statusText, linearParams(match = true, top = dp(20)))

        detailText = TextView(this).apply {
            textSize = 15f
            setTextColor(color(R.color.dsh_muted))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }
        content.addView(detailText, linearParams(match = true, top = dp(10)))

        progress = ProgressBar(this).apply { visibility = View.GONE }
        content.addView(progress, linearParams(match = false, width = dp(36), height = dp(36), top = dp(22)))

        primaryButton = actionButton(primary = true)
        content.addView(primaryButton, linearParams(match = true, height = dp(52), top = dp(26)))

        secondaryButton = actionButton(primary = false)
        content.addView(secondaryButton, linearParams(match = true, height = dp(52), top = dp(10)))

        val divider = View(this).apply { setBackgroundColor(color(R.color.dsh_divider)) }
        content.addView(divider, linearParams(match = true, height = dp(1), top = dp(30)))

        val notice = TextView(this).apply {
            text = "非官方 Android 客户端 · DeepSeek Harness 0.1.0-rc.6 · MIT"
            textSize = 12f
            setTextColor(color(R.color.dsh_muted))
            gravity = Gravity.CENTER
        }
        content.addView(notice, linearParams(match = true, top = dp(18)))
        return scroll
    }

    private fun createWebView(): WebView = WebView(this).apply {
        setBackgroundColor(color(R.color.dsh_surface))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mediaPlaybackRequiresUserGesture = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (isLocalDshUri(uri)) return false
                openExternal(uri)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                setupView.visibility = View.GONE
                this@MainActivity.webView.visibility = View.VISIBLE
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = fileChooserParams.createIntent().apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                }
                return try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileChooserCallback = null
                    false
                }
            }
        }
        setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (!isLocalDshUri(Uri.parse(url))) {
                openExternal(Uri.parse(url))
                return@DownloadListener
            }
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        })
        visibility = View.GONE
    }

    private fun refreshState(autoStart: Boolean) {
        if (!TermuxBridge.isInstalled(this)) {
            showSetup("需要 Termux", "安装 Termux 后返回 DSH。请使用 GitHub 或 F-Droid 版本。")
            configureButtons(
                primary = "安装 Termux" to { openExternal(Uri.parse(TermuxBridge.TERMUX_RELEASES_URL)) },
                secondary = "重新检查" to { refreshState(autoStart = false) },
            )
            return
        }
        if (!TermuxBridge.hasRunCommandPermission(this)) {
            showSetup("需要运行权限", "在 DSH 的系统权限页允许“在 Termux 环境中运行命令”。")
            configureButtons(
                primary = "授予权限" to { requestRunPermission() },
                secondary = "打开应用设置" to { TermuxBridge.openAppSettings(this) },
            )
            return
        }

        runAsset("status.sh", "检查 DSH", "读取本地服务状态", background = true) { result ->
            if (!result.succeeded && looksLikeExternalAppsBlocked(result)) {
                showExternalAppsSetup()
                return@runAsset
            }
            when (result.stdout.trim()) {
                "READY" -> openDsh()
                "STARTING" -> {
                    showBusy("DSH 正在启动", "等待本地服务响应…")
                    root.postDelayed({ refreshState(autoStart = false) }, 1800)
                }
                else -> if (autoStart) startDsh() else showStopped()
            }
        }
    }

    private fun showExternalAppsSetup() {
        showSetup("允许外部命令", "在 Termux 中执行初始化命令，然后回到 DSH。")
        configureButtons(
            primary = "复制命令并打开 Termux" to {
                val command = "mkdir -p ~/.termux && printf 'allow-external-apps = true\\n' > ~/.termux/termux.properties"
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("DSH Termux setup", command))
                Toast.makeText(this, "命令已复制", Toast.LENGTH_SHORT).show()
                TermuxBridge.openTermux(this)
            },
            secondary = "重新检查" to { refreshState(autoStart = false) },
        )
    }

    private fun showRunning() {
        showSetup("DSH 正在运行", "本地服务地址：http://127.0.0.1:3080")
        configureButtons(
            primary = "重新打开 DSH" to { openDsh() },
            secondary = "停止 DSH" to { stopDsh() },
        )
    }

    private fun stopDsh() {
        showBusy("正在停止 DSH", "关闭本地 Web 服务…")
        runAsset("stop.sh", "停止 DSH", "停止 DeepSeek Harness Web 服务", background = true) { result ->
            if (result.succeeded) {
                showStopped()
            } else {
                showFailure("停止失败", result)
            }
        }
    }

    private fun showStopped() {
        showSetup("DSH 未运行", "首次安装会在 Termux 中编译依赖，耗时取决于网络和手机性能。")
        configureButtons(
            primary = "启动 DSH" to { startDsh() },
            secondary = "安装或修复" to { installDsh() },
        )
    }

    private fun installDsh() {
        showBusy("正在安装 DSH", "Termux 将显示完整安装日志。安装结束后会自动返回结果。")
        runAsset("setup.sh", "安装 DSH", "安装 DeepSeek Harness 及 Android 兼容依赖", background = false) { result ->
            if (result.succeeded) {
                Toast.makeText(this, "DSH 安装完成", Toast.LENGTH_SHORT).show()
                startDsh()
            } else {
                showFailure("安装失败", result)
            }
        }
    }

    private fun startDsh() {
        showBusy("正在启动 DSH", "启动本地服务…")
        runAsset("start.sh", "启动 DSH", "启动 DeepSeek Harness Web 服务", background = true) { result ->
            if (!result.succeeded) {
                if (result.exitCode == 3) {
                    installDsh()
                } else if (looksLikeExternalAppsBlocked(result)) {
                    showExternalAppsSetup()
                } else {
                    showFailure("启动失败", result)
                }
                return@runAsset
            }
            waitForServer(attempt = 0)
        }
    }

    private fun waitForServer(attempt: Int) {
        executor.execute {
            val ready = isServerReady()
            runOnUiThread {
                when {
                    ready -> openDsh()
                    attempt < 20 -> root.postDelayed({ waitForServer(attempt + 1) }, 800)
                    else -> showSetup("服务未响应", "DSH 已启动，但 127.0.0.1:3080 暂无响应。")
                        .also {
                            configureButtons(
                                primary = "重试" to { waitForServer(0) },
                                secondary = "打开 Termux" to { TermuxBridge.openTermux(this) },
                            )
                        }
                }
            }
        }
    }

    private fun openDsh() {
        commandRunning = false
        setupView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        if (webView.url == null) webView.loadUrl(DSH_URL) else webView.reload()
    }

    private fun runAsset(
        assetName: String,
        label: String,
        description: String,
        background: Boolean,
        onResult: (CommandResult) -> Unit,
    ) {
        commandRunning = true
        val script = assets.open(assetName).bufferedReader().use { it.readText() }
        TermuxBridge.sendScript(this, script, label, description, background) { result ->
            commandRunning = false
            onResult(result)
        }
    }

    private fun requestRunPermission() {
        // Termux exposes RUN_COMMAND as an additional app permission, not a normal runtime dialog.
        TermuxBridge.openAppSettings(this)
    }

    private fun isServerReady(): Boolean = try {
        (URL(DSH_URL).openConnection() as HttpURLConnection).run {
            connectTimeout = 1200
            readTimeout = 1200
            requestMethod = "GET"
            useCaches = false
            val ok = responseCode in 200..399
            disconnect()
            ok
        }
    } catch (_: Exception) {
        false
    }

    private fun showFailure(title: String, result: CommandResult) {
        val detail = result.displayText.takeLast(2200).ifBlank { "Termux 未返回错误详情。" }
        showSetup(title, detail)
        configureButtons(
            primary = "重试" to { refreshState(autoStart = false) },
            secondary = "打开 Termux" to { TermuxBridge.openTermux(this) },
        )
    }

    private fun showBusy(title: String, detail: String) {
        showSetup(title, detail)
        progress.visibility = View.VISIBLE
        primaryButton.visibility = View.GONE
        secondaryButton.visibility = View.GONE
    }

    private fun showSetup(title: String, detail: String): View {
        setupView.visibility = View.VISIBLE
        webView.visibility = View.GONE
        statusText.text = title
        detailText.text = detail
        progress.visibility = View.GONE
        primaryButton.visibility = View.VISIBLE
        secondaryButton.visibility = View.VISIBLE
        return setupView
    }

    private fun configureButtons(
        primary: Pair<String, () -> Unit>,
        secondary: Pair<String, () -> Unit>,
    ) {
        primaryButton.text = primary.first
        primaryButton.setOnClickListener { primary.second() }
        secondaryButton.text = secondary.first
        secondaryButton.setOnClickListener { secondary.second() }
    }

    private fun looksLikeExternalAppsBlocked(result: CommandResult): Boolean {
        val text = result.displayText.lowercase()
        return text.contains("allow-external-apps") || text.contains("permission denied") || text.contains("not allowed")
    }

    private fun isLocalDshUri(uri: Uri): Boolean =
        uri.scheme == "http" && (uri.host == "127.0.0.1" || uri.host == "localhost") && uri.port == 3080

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enqueueDownload(url: String, userAgent: String, disposition: String, mimeType: String) {
        val fileName = android.webkit.URLUtil.guessFileName(url, disposition, mimeType)
        val detectedMime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileName.substringAfterLast('.', ""))
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType.ifBlank { detectedMime ?: "application/octet-stream" })
            addRequestHeader("User-Agent", userAgent)
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(this, "已加入下载", Toast.LENGTH_SHORT).show()
    }

    private fun actionButton(primary: Boolean): Button = Button(this).apply {
        textSize = 15f
        isAllCaps = false
        setTextColor(if (primary) Color.WHITE else color(R.color.dsh_blue))
        setBackgroundColor(if (primary) color(R.color.dsh_blue) else Color.TRANSPARENT)
        minHeight = 0
        minimumHeight = 0
    }

    private fun color(id: Int): Int = getColor(id)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchParentParams() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun linearParams(
        match: Boolean,
        width: Int = if (match) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        top: Int = 0,
    ) = LinearLayout.LayoutParams(width, height).apply { topMargin = top }

    companion object {
        private const val DSH_URL = "http://127.0.0.1:3080/"
        private const val FILE_CHOOSER_REQUEST = 701
    }
}
