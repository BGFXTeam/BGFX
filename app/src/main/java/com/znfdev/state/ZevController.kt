package com.znfdev.state

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.znfdev.bgfx.UpdateChecker
import com.znfdev.core.lua.SymbolSource
import com.znfdev.models.PluginItem
import com.znfdev.net.LspClient
import com.znfdev.net.LspStatus
import com.znfdev.utils.FileManager
import com.znfdev.utils.LatestVersion
import com.znfdev.utils.LuaApiIndex
import com.znfdev.utils.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * All state behind the panel. One instance backs one GuiOverlay; the screens only read
 * these values and call the functions, so they stay easy to preview and test.
 * Disk work runs on Dispatchers.IO, state is only touched from the main thread.
 */
class ZevController(context: Context) {

    private val app: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---- host callbacks (what BGFX does with the things you pick)
    var onRun: ((String) -> Unit)? = null
    var onLoadPlugin: ((PluginItem) -> Unit)? = null
    var onScriptLoaded: ((File, String) -> Unit)? = null
    var onFileSelected: ((File) -> Unit)? = null
    var requestStorage: (() -> Unit)? = null

    // ---- shell
    var tab by mutableIntStateOf(0)
    var editor by mutableStateOf<EditorRequest?>(null)

    // ---- execute
    var code by mutableStateOf("-- tap to edit\n")

    // ---- API index (completion + highlighting)
    var apiIndex by mutableStateOf<LuaApiIndex?>(null)
        private set
    var symbols by mutableStateOf<SymbolSource?>(null)
        private set
    var globals by mutableStateOf<Set<String>?>(null)
        private set

    // ---- LSP socket (see net/LspClient.kt and README's architecture section)
    val lsp = LspClient()
    val lspStatus: LspStatus get() = lsp.status

    // ---- storage + settings
    var hasStorage by mutableStateOf(StorageAccess.hasAccess(app))
        private set
    var defaultScript by mutableStateOf("")
        private set
    var autoExec by mutableStateOf(false)
        private set

    // ---- license key (key.json)
    var license by mutableStateOf<LicenseKey?>(null)
        private set
    private var licenseBucket = LicenseBucket.NORMAL
    private var licenseJob: Job? = null

    // ---- scripts / plugins / files
    var scripts by mutableStateOf<List<File>>(emptyList())
        private set
    var scriptsRevision by mutableIntStateOf(0)
        private set
    var plugins by mutableStateOf<List<PluginItem>>(emptyList())
        private set
    var pluginsRevision by mutableIntStateOf(0)
        private set
    var filesPage by mutableStateOf(FilesPage(StorageAccess.ROOT, emptyList()))
        private set
    var filesRevision by mutableIntStateOf(0)
        private set
    var filesDirection by mutableIntStateOf(1)
        private set

    // ---- team
    var members by mutableStateOf(
        listOf(
            TeamMember(
                "TheKing", "Founder", "the_king.78.mp4",
                "https://raw.githubusercontent.com/BGFXTeam/hub/refs/heads/main/the_king.78.png",
                "Native & Core Development, Reverse Engineer", 0xFFFDAB67
            ),
            TeamMember(
                "ZNFDev", "Founder", "znfdev.mp4",
                "https://raw.githubusercontent.com/BGFXTeam/hub/refs/heads/main/znfdev.png",
                "Native, UX/UI & API Development, Reverse Engineer", 0xFFE0E0E0
            ),
            TeamMember(
                "VortexHacker", "Contributor", "vortex.mp4",
                "https://raw.githubusercontent.com/BGFXTeam/hub/refs/heads/main/vortex.png",
                "Native & Bypass Development, Reverse Engineer", 0xFF175B8C
            )
        )
    )

    val canGoUp: Boolean get() = filesPage.path != StorageAccess.ROOT

    val versionText: String
        get() {
            val v = LatestVersion()
            return v.versionName + " " + v.versionLabel
        }

    init {
        loadSettings()
        startLicenseWatch()
        lsp.onRunResult = { result ->
            if (!result.ok) toast(result.output.ifBlank { "Run failed" })
        }
        lsp.start()
    }

    // ================================================================== API

    fun updateApiIndex(index: LuaApiIndex?) {
        apiIndex = index
        symbols = if (index != null) RemoteSymbolSource(ApiSource(index), lsp, uri = "execute://current") else null
        globals = index?.globalNames()
    }

    // ================================================================== access + settings

    /** Call when the host resumes: the user may have just granted (or revoked) file access. */
    fun refreshAccess() {
        val now = StorageAccess.hasAccess(app)
        if (now != hasStorage) {
            hasStorage = now
            if (now) {
                loadSettings()
                refreshScripts(); refreshPlugins(); refreshFiles()
                startLicenseWatch()
            } else {
                licenseJob?.cancel()
            }
        }
    }

    fun grantStorage() {
        requestStorage?.invoke()
    }

    private fun loadSettings() {
        if (!hasStorage) return
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    Settings.reload()
                    val s = Settings.getSettings()
                    s.getDefaultScript() to s.isAutoExec()
                }.getOrNull()
            }
            if (loaded != null) {
                defaultScript = loaded.first ?: ""
                autoExec = loaded.second
            }
        }
    }

    /**
     * Polls key.json every 20s. Only toasts a reminder when crossing *into* a more urgent
     * bucket (last hour, then last 5 minutes, then expired) — never repeatedly while
     * sitting in one, and never on the way back to NORMAL after a renewed key.
     */
    private fun startLicenseWatch() {
        if (!hasStorage) return
        licenseJob?.cancel()
        licenseJob = scope.launch {
            while (true) {
                val loaded = withContext(Dispatchers.IO) { LicenseKeyStore.read() }
                license = loaded
                val bucket = if (loaded == null) LicenseBucket.NORMAL else bucketFor(loaded.remainingMs())
                if (bucket != licenseBucket) {
                    if (loaded != null) {
                        when (bucket) {
                            LicenseBucket.HOUR -> toast("Key expires in under an hour")
                            LicenseBucket.MINUTES -> toast("Key expires in under 5 minutes")
                            LicenseBucket.EXPIRED -> toast("Key has expired")
                            LicenseBucket.NORMAL -> Unit
                        }
                    }
                    licenseBucket = bucket
                }
                delay(20_000)
            }
        }
    }

    fun updateDefaultScript(name: String) {
        if (!hasStorage || name == defaultScript) return
        defaultScript = name
        scope.launch(Dispatchers.IO) { runCatching { Settings.getSettings().setDefaultScript(name) } }
    }

    fun updateAutoExec(value: Boolean) {
        if (!hasStorage) return
        autoExec = value
        scope.launch(Dispatchers.IO) { runCatching { Settings.getSettings().setAutoExec(value) } }
    }

    fun checkForUpdates() {
        toast("Checking for updates\u2026")
        UpdateChecker.fetch { data, error ->
            when {
                error != null || data == null -> toast("Couldn't reach the server")
                UpdateChecker.isUpdateAvailable(data) -> toast("An update is available")
                else -> toast("You're up to date")
            }
        }
    }

    // ================================================================== execute + editor

    /** Sends over the LSP socket when a host is connected, otherwise the local callback. */
    fun run() {
        if (lsp.isConnected) {
            lsp.didChange("execute://current", code)
            lsp.run(code)
        } else {
            onRun?.invoke(code)
        }
    }

    fun openExecuteEditor() {
        editor = EditorRequest("Execute", null, code) { code = it }
    }

    fun closeEditor() {
        editor = null
    }

    // ================================================================== scripts

    fun refreshScripts() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    ensureDirs()
                    FileManager.INSTANCE.getScripts().sortedBy { it.name.lowercase() }
                }.getOrDefault(emptyList())
            }
            scripts = list
            scriptsRevision++
        }
    }

    fun openScript(file: File) {
        scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }
            if (text == null) {
                toast("Couldn't read ${file.name}")
                return@launch
            }
            editor = EditorRequest(file.name, file.parent?.removePrefix("/storage/emulated/0/"), text) {
                saveScript(file, it)
            }
        }
    }

    private fun saveScript(file: File, text: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { file.writeText(text) }.isSuccess }
            if (!ok) {
                toast("Couldn't save ${file.name}")
                return@launch
            }
            refreshScripts()
            if (lsp.isConnected) {
                lsp.loadScript(file.name, text) { result -> if (!result.ok) toast(result.output.ifBlank { "Couldn't load ${file.name}" }) }
            } else {
                onScriptLoaded?.invoke(file, text)
            }
        }
    }

    // ================================================================== plugins

    fun refreshPlugins() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    ensureDirs()
                    FileManager.INSTANCE.getPlugins().sortedBy { it.name.lowercase() }
                }.getOrDefault(emptyList())
            }
            plugins = list
            pluginsRevision++
        }
    }

    fun loadPlugin(plugin: PluginItem) {
        if (lsp.isConnected) {
            lsp.loadPlugin(plugin.name ?: "", plugin.main ?: "") { result ->
                if (!result.ok) toast(result.output.ifBlank { "Couldn't load ${plugin.name}" })
            }
        } else {
            onLoadPlugin?.invoke(plugin)
        }
    }

    // ================================================================== files

    fun refreshFiles() = showDirectory(filesPage.path, filesDirection)

    fun openDirectory(dir: File) = showDirectory(dir.absolutePath.trimEnd('/') + "/", 1)

    fun goUp() {
        if (!canGoUp) return
        val parent = File(filesPage.path).parentFile?.absolutePath?.trimEnd('/')?.plus("/")
        val target = if (parent == null || parent.length < StorageAccess.ROOT.length) StorageAccess.ROOT else parent
        showDirectory(target, -1)
    }

    fun selectFile(file: File) {
        if (!lsp.isConnected) {
            onFileSelected?.invoke(file)
            return
        }
        scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { file.readText() }.getOrNull() }
            if (text == null) {
                toast("Couldn't read ${file.name}")
                return@launch
            }
            lsp.loadScript(file.name, text) { result -> if (!result.ok) toast(result.output.ifBlank { "Couldn't load ${file.name}" }) }
        }
    }

    private fun showDirectory(path: String, direction: Int) {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    FileManager.INSTANCE.getFiles(path)
                        .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                }.getOrDefault(emptyList())
            }
            filesDirection = direction
            filesPage = FilesPage(path, list)
            filesRevision++
        }
    }

    // ================================================================== misc

    private fun ensureDirs() {
        if (!hasStorage) return
        File(StorageAccess.SCRIPTS).mkdirs()
        File(StorageAccess.PLUGINS).mkdirs()
    }

    private fun toast(message: String) {
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    }

    fun dispose() {
        lsp.stop()
        scope.cancel()
    }
}
