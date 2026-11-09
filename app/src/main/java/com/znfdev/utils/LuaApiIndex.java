package com.znfdev.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the dumped game API ({"type":"table","members":{...}} format)
 * from app assets (or a file) and provides lookups for code completion.
 */
public class LuaApiIndex {

    /** A single symbol from the dump. */
    public static class Symbol {
        public final String name;
        public final String type;                 // "function", "table", "string", ...
        public final String value;                // non-null if the dump stored a value
        public final Map<String, Symbol> members; // null when not a table

        Symbol(String name, String type, String value, Map<String, Symbol> members) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.members = members;
        }

        public boolean isTable()    { return members != null; }
        public boolean isFunction() { return "function".equals(type); }
        public int memberCount()    { return members == null ? 0 : members.size(); }
    }

    public interface LoadCallback {
        void onLoaded(LuaApiIndex index);
        void onError(Throwable error);
    }

    private final Map<String, Symbol> globals = new HashMap<String, Symbol>();
    private boolean hideInternalKeys = true; // hide __index, __gc, __parent0, ...

    /** Parses the asset on a background thread; callback on the main thread. */
    public static void loadAsync(Context context, final String assetName, final LoadCallback callback) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream in = app.getAssets().open(assetName);
                        String json = readAll(in);
                        final LuaApiIndex index = parse(json);
                        main.post(new Runnable() {
                                @Override
                                public void run() { callback.onLoaded(index); }
                            });
                    } catch (final Throwable t) {
                        main.post(new Runnable() {
                                @Override
                                public void run() { callback.onError(t); }
                            });
                    }
                }
            }, "LuaApiIndexLoader").start();
    }

    /** Synchronous load from a plain file, e.g. /sdcard/bgfx_api.json. */
    public static LuaApiIndex loadFromFile(String path) throws Exception {
        return parse(readAll(new FileInputStream(new File(path))));
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder(1 << 20);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
        } finally {
            try { in.close(); } catch (Exception ignored) { }
        }
        return sb.toString();
    }

    private static LuaApiIndex parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        LuaApiIndex index = new LuaApiIndex();
        JSONObject members = root.optJSONObject("members");
        if (members != null) {
            Iterator<String> keys = members.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject value = members.optJSONObject(key);
                if (value != null) index.globals.put(key, parseSymbol(key, value));
            }
        }
        return index;
    }

    private static Symbol parseSymbol(String name, JSONObject json) {
        String type = json.optString("type", "unknown");
        String value = json.has("value") ? json.optString("value") : null;

        Map<String, Symbol> members = null;
        JSONObject membersJson = json.optJSONObject("members");
        if (membersJson != null) {
            members = new HashMap<String, Symbol>();
            Iterator<String> keys = membersJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject child = membersJson.optJSONObject(key);
                if (child != null) members.put(key, parseSymbol(key, child));
            }
        }
        return new Symbol(name, type, value, members);
    }

    public void setHideInternalKeys(boolean hide) { hideInternalKeys = hide; }

    /** All top-level names (used to colorize known globals). */
    public Set<String> globalNames() {
        Set<String> out = new HashSet<String>();
        for (Symbol s : globals.values()) out.add(s.name);
        return out;
    }

    /** Resolves a dotted path like "io". Null when unknown. */
    public Symbol lookup(String path) {
        if (path == null || path.length() == 0) return null;
        String[] parts = path.split("\\.");
        Symbol current = null;
        Map<String, Symbol> level = globals;
        for (String part : parts) {
            if (level == null) return null;
            current = level.get(part);
            if (current == null) return null;
            level = current.members;
        }
        return current;
    }

    /**
     * Completion-friendly path resolver. Walks the dotted chain step by step,
     * applying the same fallbacks at EACH step that {@link #membersForCompletion}
     * used to apply only at the leaf. This is what makes paths like
     * `Blockman.Instance.getPlayer` resolvable: `Instance` is a function
     * (no members in the dump), so we fall back to its parent table `Blockman`
     * and continue the lookup from there.
     *
     * Fallbacks applied per step:
     *   - function with no members → guess return type (e.g. getPlayer → Player);
     *     if no guess, fall back to the most recent parent table.
     *   - userdata with no members → guess class from the field name
     *     (e.g. m_playerControl → PlayerController).
     *   - table → descend into its members normally.
     *
     * Returns the resolved symbol, or null if the path can't be resolved.
     */
    public Symbol resolveForCompletion(String path) {
        if (path == null || path.length() == 0) return null;
        String[] parts = path.split("\\.");
        Map<String, Symbol> level = globals;
        Symbol current = null;
        Symbol parentTable = null; // most recent table encountered in the chain

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (level == null) return null;
            current = level.get(part);
            if (current == null) return null;

            Map<String, Symbol> nextLevel = null;
            if (current.members != null && !current.members.isEmpty()) {
                // Real table (or a function/userdata that happens to have members)
                nextLevel = current.members;
                parentTable = current;
            } else if (current.isFunction()) {
                Symbol returned = guessReturnTypeByName(part);
                if (returned != null && returned.isTable()) {
                    nextLevel = returned.members;
                    current = returned;
                    parentTable = returned;
                } else {
                    // Fall back: function behaves as if it returns the parent
                    // table's type, so the next lookup happens in the parent.
                    nextLevel = (parentTable != null) ? parentTable.members : level;
                    current = parentTable;
                }
            } else if ("userdata".equals(current.type)) {
                Symbol guessed = guessClassFor(part);
                if (guessed != null && guessed.isTable()) {
                    nextLevel = guessed.members;
                    current = guessed;
                    parentTable = guessed;
                } else {
                    nextLevel = (parentTable != null) ? parentTable.members : level;
                    current = parentTable;
                }
            }
            level = nextLevel;
        }
        return current;
    }

    /**
     * Completion-friendly variant of {@link #membersOf(String)}.
     *
     * Uses {@link #resolveForCompletion} to walk the chain step by step,
     * applying fallbacks at each level (function → return type guess,
     * userdata → class guess, function → parent table). This makes paths like
     * `Blockman.Instance.getPlayer` resolvable even though `Instance` is a
     * function with no members in the dump.
     *
     * Special case: the literal token `"string"` (produced by the editor when
     * a local is assigned a string literal) returns the standard `string`
     * library's members.
     */
    public List<Symbol> membersForCompletion(String path) {
        if (path == null || path.length() == 0) return Collections.emptyList();

        // Special case: literal string → use the standard `string` library
        if ("string".equals(path)) {
            return membersOf("string");
        }

        Symbol symbol = resolveForCompletion(path);

        if (symbol != null && symbol.isTable()) {
            return membersOfSymbol(symbol);
        }

        // resolveForCompletion already applies the function→return-type and
        // userdata→class guesses per step, so by the time we get here the
        // symbol is unlikely to still be a function/userdata. But just in
        // case (e.g. single-segment global function), try the fallbacks.
        if (symbol != null && symbol.isFunction()) {
            Symbol returned = guessReturnTypeByName(leafOf(path));
            if (returned != null && returned.isTable()) {
                return membersOfSymbol(returned);
            }
            List<Symbol> parent = membersOfParent(path);
            if (!parent.isEmpty()) return parent;
        }

        if (symbol != null && "userdata".equals(symbol.type)) {
            if (symbol.members != null && !symbol.members.isEmpty()) {
                return membersOfSymbol(symbol);
            }
            Symbol guessed = guessClassFor(leafOf(path));
            if (guessed != null && guessed.isTable()) {
                return membersOfSymbol(guessed);
            }
            List<Symbol> parent = membersOfParent(path);
            if (!parent.isEmpty()) return parent;
        }

        // Last resort: parent table's members (e.g. user typed
        // `Blockman.SomeUnknownThing:` where SomeUnknownThing doesn't exist)
        return membersOfParent(path);
    }

    /** Like {@link #membersOf(String)} but takes a resolved Symbol directly. */
    private List<Symbol> membersOfSymbol(Symbol symbol) {
        if (symbol == null || symbol.members == null) return Collections.emptyList();
        List<Symbol> out = new ArrayList<Symbol>();
        for (Symbol member : symbol.members.values()) {
            if (hideInternalKeys && member.name.startsWith("__")) continue;
            out.add(member);
        }
        Collections.sort(out, SYMBOL_ORDER);
        return out;
    }

    /** Returns the leaf segment of a dotted path. */
    private static String leafOf(String path) {
        if (path == null) return null;
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    /** Members of the table at `path` minus its last segment. Empty if N/A. */
    private List<Symbol> membersOfParent(String path) {
        if (path == null) return Collections.emptyList();
        int dot = path.lastIndexOf('.');
        if (dot <= 0) return Collections.emptyList();
        String parent = path.substring(0, dot);
        Symbol p = resolveForCompletion(parent);
        if (p == null || !p.isTable()) return Collections.emptyList();
        return membersOfSymbol(p);
    }

    /**
     * Guesses the class table for a userdata field name.
     * "m_playerControl" → "PlayerControl" → prefix-matches "PlayerController".
     */
    private Symbol guessClassFor(String memberName) {
        if (memberName == null || memberName.length() == 0) return null;
        String base = memberName;
        if (base.startsWith("m_")) base = base.substring(2);
        if (base.length() == 0) return null;

        // PascalCase the first letter; leave the rest alone so
        // "playerControl" → "PlayerControl", "personView" → "PersonView".
        String pascal = Character.toUpperCase(base.charAt(0)) + base.substring(1);
        return findClassByName(pascal);
    }

    /**
     * Guesses the class of the value returned by a function, based on its name.
     * Delegates to {@link #guessReturnTypeByName(String)} with the leaf of the path.
     */
    private Symbol guessReturnType(String path) {
        return guessReturnTypeByName(leafOf(path));
    }

    /**
     * Same as {@link #guessReturnType} but takes a bare function name.
     * "getPlayer" → looks for class "Player" (exact match, then prefix match
     *               against globals, so it resolves to "PlayerController").
     * "getWorld"  → exact match "World".
     * "createMesh" → "Mesh".
     * Returns null for new()/Instance() (handled by parent-table fallback),
     * for isFoo()/hasFoo() (return booleans), and for names that don't match
     * any known pattern.
     */
    private Symbol guessReturnTypeByName(String leaf) {
        if (leaf == null || leaf.length() == 0) return null;

        String guess = null;
        // Only strip the prefix if the next char is uppercase, so we don't
        // trigger on "getfoo" (which probably returns a primitive, not a class).
        if (leaf.length() > 3 && leaf.startsWith("get") && Character.isUpperCase(leaf.charAt(3))) {
            guess = leaf.substring(3);
        } else if (leaf.length() > 3 && leaf.startsWith("Get") && Character.isUpperCase(leaf.charAt(3))) {
            guess = leaf.substring(3);
        } else if (leaf.length() > 6 && leaf.startsWith("create") && Character.isUpperCase(leaf.charAt(6))) {
            guess = leaf.substring(6);
        } else if (leaf.length() > 6 && leaf.startsWith("Create") && Character.isUpperCase(leaf.charAt(6))) {
            guess = leaf.substring(6);
        } else if (leaf.length() > 4 && leaf.startsWith("make") && Character.isUpperCase(leaf.charAt(4))) {
            guess = leaf.substring(4);
        } else if (leaf.length() > 4 && leaf.startsWith("Make") && Character.isUpperCase(leaf.charAt(4))) {
            guess = leaf.substring(4);
        }
        // new(), Instance(), isFoo(), hasFoo(), setXxx() → no guess
        // (let the parent-table fallback handle them, or return empty)

        return findClassByName(guess);
    }

    /**
     * Finds a class table by name. Tries exact match first, then prefix match
     * (shortest match wins, so "Player" prefers "Player" over "PlayerController"
     * when both exist — though in practice usually only one does).
     */
    private Symbol findClassByName(String guess) {
        if (guess == null || guess.length() == 0) return null;

        // Exact match first
        Symbol direct = globals.get(guess);
        if (direct != null && direct.isTable()) return direct;

        // Prefix match (shortest name wins)
        Symbol best = null;
        int bestLen = Integer.MAX_VALUE;
        for (Map.Entry<String, Symbol> e : globals.entrySet()) {
            String name = e.getKey();
            if (name.length() >= guess.length()
                && name.startsWith(guess)
                && e.getValue().isTable()) {
                if (name.length() < bestLen) {
                    best = e.getValue();
                    bestLen = name.length();
                }
            }
        }
        return best;
    }

    /** Members of a table at the given path (sorted). Empty when not a table. */
    public List<Symbol> membersOf(String path) {
        Symbol symbol = lookup(path);
        if (symbol == null || symbol.members == null) return Collections.emptyList();
        List<Symbol> out = new ArrayList<Symbol>();
        for (Symbol member : symbol.members.values()) {
            if (hideInternalKeys && member.name.startsWith("__")) continue;
            out.add(member);
        }
        Collections.sort(out, SYMBOL_ORDER);
        return out;
    }

    /** Global symbols whose name starts with `prefix` (sorted). */
    public List<Symbol> completeGlobals(String prefix) {
        List<Symbol> out = new ArrayList<Symbol>();
        for (Symbol symbol : globals.values()) {
            if (hideInternalKeys && symbol.name.startsWith("__")) continue;
            if (prefix == null || prefix.length() == 0 || symbol.name.startsWith(prefix)) {
                out.add(symbol);
            }
        }
        Collections.sort(out, SYMBOL_ORDER);
        return out;
    }

    private static final Comparator<Symbol> SYMBOL_ORDER = new Comparator<Symbol>() {
        @Override
        public int compare(Symbol a, Symbol b) { return a.name.compareTo(b.name); }
    };
}
