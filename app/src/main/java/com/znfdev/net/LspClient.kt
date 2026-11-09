package com.znfdev.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.znfdev.core.lua.Sym
import org.json.JSONArray
import org.json.JSONObject

enum class LspStatus { DISCONNECTED, CONNECTING, RETRYING, CONNECTED }

class Diagnostic(val line: Int, val startCol: Int, val endCol: Int, val severity: Int, val message: String)

class RunResult(val ok: Boolean, val output: String)

/**
 * Talks to a companion process on the same device (the BGFX host) over a single
 * WebSocket, using JSON-RPC 2.0 messages shaped like the Language Server Protocol —
 * `textDocument/completion`, `textDocument/publishDiagnostics` — plus a small `bgfx/ *`
 * extension for things LSP doesn't cover (running code, plugin state). Using LSP's own
 * method names means a real Lua language server can sit behind the same socket later
 * without changing this client. See the architecture notes in README.md.
 *
 * Every call degrades gracefully: while disconnected, completion methods just return
 * null/empty so callers fall back to the local `LuaApiIndex`.
 */
class LspClient(host: String = "127.0.0.1", port: Int = 8085) {

    var status by mutableStateOf(LspStatus.DISCONNECTED)
        private set

    var onDiagnostics: ((uri: String, List<Diagnostic>) -> Unit)? = null
    var onRunResult: ((RunResult) -> Unit)? = null

    private val socket = Zocket(host, port, path = "/lsp")
    private var nextId by mutableIntStateOf(1)
    private val pending = HashMap<Int, (JSONObject?) -> Unit>()

    init {
        socket.onState = { s ->
            status = when (s) {
                ZocketState.CONNECTING -> LspStatus.CONNECTING
                ZocketState.OPEN -> LspStatus.CONNECTED
                ZocketState.RETRYING -> LspStatus.RETRYING
                ZocketState.IDLE, ZocketState.CLOSED -> LspStatus.DISCONNECTED
            }
        }
        socket.onOpen = {
            notify("initialize", JSONObject().put("client", "ZevWidgets").put("protocolVersion", 1))
        }
        socket.onMessage = ::handleMessage
    }

    fun start() = socket.start()
    fun stop() = socket.stop()

    val isConnected: Boolean get() = status == LspStatus.CONNECTED

    // ------------------------------------------------------------------ document sync

    fun didOpen(uri: String, text: String) = notify(
        "textDocument/didOpen",
        JSONObject().put("textDocument", JSONObject().put("uri", uri).put("text", text))
    )

    fun didChange(uri: String, text: String) = notify(
        "textDocument/didChange",
        JSONObject().put("textDocument", JSONObject().put("uri", uri)).put("text", text)
    )

    // ------------------------------------------------------------------ completion

    /**
     * [path] is the dotted qualifier our completion engine already resolved (`""` for a
     * bare global prefix, `"Blockman.Instance"` for a member lookup) — see
     * `LuaCompletion.parseQualifier`. Real LSP servers key completion off a cursor
     * position instead; swapping that in later only touches this one call site.
     * Fire-and-forget: [onResult] runs on the main thread, or never while offline.
     */
    fun completion(uri: String, path: String, prefix: String, onResult: (List<Sym>) -> Unit) {
        if (!isConnected) return
        val params = JSONObject()
            .put("textDocument", JSONObject().put("uri", uri))
            .put("path", path)
            .put("prefix", prefix)
        request("textDocument/completion", params) { result ->
            if (result == null) return@request
            val items = result.optJSONArray("items") ?: JSONArray()
            val out = ArrayList<Sym>(items.length())
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                out.add(Sym(it.getString("name"), it.optString("kind", "unknown"), it.optInt("memberCount", 0)))
            }
            onResult(out)
        }
    }

    // ------------------------------------------------------------------ bgfx extension

    fun run(code: String) {
        if (!isConnected) return
        request("bgfx/run", JSONObject().put("code", code)) { result ->
            val ok = result?.optBoolean("ok", true) ?: false
            val output = result?.optString("output", "") ?: ""
            onRunResult?.invoke(RunResult(ok, output))
        }
    }

    /** Loads a named script file onto the host (Scripts / Files tabs). */
    fun loadScript(name: String, code: String, onResult: (RunResult) -> Unit) {
        if (!isConnected) return
        val params = JSONObject().put("name", name).put("code", code)
        request("bgfx/loadScript", params) { result ->
            onResult(RunResult(result?.optBoolean("ok", true) ?: false, result?.optString("output", "") ?: ""))
        }
    }

    /** Loads a plugin by its plugin.json entry (Plugins tab). */
    fun loadPlugin(name: String, main: String, onResult: (RunResult) -> Unit) {
        if (!isConnected) return
        val params = JSONObject().put("name", name).put("main", main)
        request("bgfx/loadPlugin", params) { result ->
            onResult(RunResult(result?.optBoolean("ok", true) ?: false, result?.optString("output", "") ?: ""))
        }
    }

    // ------------------------------------------------------------------ wire format

    private fun notify(method: String, params: JSONObject) {
        send(JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params))
    }

    private fun request(method: String, params: JSONObject, onResult: (JSONObject?) -> Unit) {
        val id = nextId++
        pending[id] = onResult
        send(JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params))
    }

    private fun send(json: JSONObject) {
        socket.send(json.toString())
    }

    private fun handleMessage(text: String) {
        val json = try { JSONObject(text) } catch (t: Throwable) { return }

        val id = if (json.has("id")) json.optInt("id", -1) else -1
        if (id >= 0 && (json.has("result") || json.has("error"))) {
            val callback = pending.remove(id) ?: return
            callback(json.optJSONObject("result"))
            return
        }

        when (json.optString("method")) {
            "textDocument/publishDiagnostics" -> {
                val params = json.optJSONObject("params") ?: return
                val uri = params.optString("uri")
                val arr = params.optJSONArray("diagnostics") ?: JSONArray()
                val diags = ArrayList<Diagnostic>(arr.length())
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    val range = d.optJSONObject("range")
                    diags.add(
                        Diagnostic(
                            line = range?.optInt("line", 0) ?: 0,
                            startCol = range?.optInt("startCol", 0) ?: 0,
                            endCol = range?.optInt("endCol", 0) ?: 0,
                            severity = d.optInt("severity", 1),
                            message = d.optString("message", "")
                        )
                    )
                }
                onDiagnostics?.invoke(uri, diags)
            }
        }
    }
}
