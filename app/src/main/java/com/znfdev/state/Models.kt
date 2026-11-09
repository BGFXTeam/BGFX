package com.znfdev.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import com.znfdev.core.lua.SymbolSource
import com.znfdev.core.lua.Sym
import com.znfdev.net.LspClient
import com.znfdev.utils.LuaApiIndex

/** A request to open the full-screen code editor. */
class EditorRequest(
    val title: String,
    val subtitle: String?,
    val initialCode: String,
    val onSave: (String) -> Unit
)

/** A card on the credits screen. [color] is 0xAARRGGBB, or null for an automatic tint. */
class TeamMember(
    val name: String,
    val role: String,
    val videoAsset: String? = null,
    val picUrl: String? = null,
    val note: String? = null,
    val color: Long? = null
)

/** One directory listing, kept together so the Files screen can animate between pages. */
class FilesPage(val path: String, val entries: List<java.io.File>)

/** Feeds the completion engine from the parsed API dump. */
class ApiSource(private val index: LuaApiIndex) : SymbolSource {
    override fun completeGlobals(prefix: String): List<Sym> = index.completeGlobals(prefix).map { it.toSym() }
    override fun membersForCompletion(path: String): List<Sym> = index.membersForCompletion(path).map { it.toSym() }

    private fun LuaApiIndex.Symbol.toSym() = Sym(name, type ?: "unknown", memberCount(), value)
}

/**
 * Prefers live results from the LSP socket and falls back to the local API dump when
 * offline or before the first reply for a given path has arrived. `SymbolSource`'s
 * methods are synchronous, so a request that's still in flight returns whatever the
 * local index has *and* kicks off a background refresh; [version] bumps once that
 * refresh lands so `CodeEditor` knows to recompute the popup.
 */
class RemoteSymbolSource(private val local: SymbolSource, private val lsp: LspClient, private val uri: String) : SymbolSource {

    private val versionState = mutableIntStateOf(0)
    val version: State<Int> get() = versionState

    private val globalsCache = HashMap<String, List<Sym>>()
    private val membersCache = HashMap<String, List<Sym>>()
    private val inFlight = HashSet<String>()

    override fun completeGlobals(prefix: String): List<Sym> {
        refreshIfNeeded("g:$prefix") { onResult ->
            lsp.completion(uri, path = "", prefix = prefix) { onResult(it) }
        }
        return globalsCache[prefix] ?: local.completeGlobals(prefix)
    }

    override fun membersForCompletion(path: String): List<Sym> {
        refreshIfNeeded("m:$path") { onResult ->
            lsp.completion(uri, path = path, prefix = "") { onResult(it) }
        }
        return membersCache[path] ?: local.membersForCompletion(path)
    }

    private fun refreshIfNeeded(key: String, request: ((List<Sym>) -> Unit) -> Unit) {
        if (!lsp.isConnected || key in inFlight) return
        inFlight.add(key)
        request { results ->
            inFlight.remove(key)
            if (key.startsWith("g:")) globalsCache[key.substring(2)] = results
            else membersCache[key.substring(2)] = results
            versionState.value++
        }
    }
}
