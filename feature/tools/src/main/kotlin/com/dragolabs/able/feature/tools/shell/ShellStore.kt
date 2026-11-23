package com.dragolabs.able.feature.tools.shell

import android.content.Context
import com.dragolabs.able.feature.tools.R
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Feature-local persistence for the command shell: history (last 100 commands), saved snippets
 * and the one-time warning acknowledgement.
 *
 * Stored as JSON under [Context.getNoBackupFilesDir], which Android never includes in cloud or
 * device-to-device backups, so commands (which may contain package names or values) stay on
 * this device.
 */
class ShellStore(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.noBackupFilesDir, "shell")
    private val historyFile = File(dir, "history.json")
    private val snippetsFile = File(dir, "snippets.json")
    private val warningFile = File(dir, "warning_acknowledged")
    private val mutex = Mutex()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _snippets = MutableStateFlow<List<ShellSnippet>>(emptyList())
    val snippets: StateFlow<List<ShellSnippet>> = _snippets.asStateFlow()

    /** Null while loading. */
    private val _warningAcknowledged = MutableStateFlow<Boolean?>(null)
    val warningAcknowledged: StateFlow<Boolean?> = _warningAcknowledged.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                dir.mkdirs()
                _history.value = readHistory()
                _snippets.value = readSnippets() ?: starterSnippets().also { writeSnippets(it) }
                _warningAcknowledged.value = warningFile.exists()
            }
        }
    }

    fun record(command: String) = mutate {
        _history.update { ShellHistory.record(it, command) }
        writeHistory(_history.value)
    }

    fun clearHistory() = mutate {
        _history.value = emptyList()
        writeHistory(emptyList())
    }

    fun saveSnippet(id: String?, label: String, command: String) = mutate {
        val snippet = ShellSnippet(id ?: UUID.randomUUID().toString(), label.trim(), command.trim())
        _snippets.update { ShellSnippets.upsert(it, snippet) }
        writeSnippets(_snippets.value)
    }

    fun deleteSnippet(id: String) = mutate {
        _snippets.update { ShellSnippets.remove(it, id) }
        writeSnippets(_snippets.value)
    }

    fun acknowledgeWarning() = mutate {
        _warningAcknowledged.value = true
        warningFile.writeText("1")
    }

    private fun mutate(block: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                dir.mkdirs()
                runCatching(block)
            }
        }
    }

    private fun readHistory(): List<String> = runCatching {
        if (!historyFile.exists()) return emptyList()
        val array = JSONArray(historyFile.readText())
        List(array.length()) { array.getString(it) }.takeLast(ShellHistory.MAX_ENTRIES)
    }.getOrDefault(emptyList())

    private fun writeHistory(list: List<String>) {
        writeAtomically(historyFile, JSONArray(list).toString())
    }

    /** Null when no snippet file exists yet (first launch: seed the starter set). */
    private fun readSnippets(): List<ShellSnippet>? {
        if (!snippetsFile.exists()) return null
        return runCatching {
            val array = JSONArray(snippetsFile.readText())
            List(array.length()) {
                val o = array.getJSONObject(it)
                ShellSnippet(o.getString("id"), o.getString("label"), o.getString("command"))
            }
        }.getOrDefault(emptyList())
    }

    private fun writeSnippets(list: List<ShellSnippet>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("label", it.label).put("command", it.command)) }
        writeAtomically(snippetsFile, array.toString())
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    private fun starterSnippets(): List<ShellSnippet> {
        val labels = listOf(
            R.string.shell_snippet_android_version,
            R.string.shell_snippet_user_apps,
            R.string.shell_snippet_battery,
            R.string.shell_snippet_adb_enabled,
            R.string.shell_snippet_disabled_apps,
            R.string.shell_snippet_data_space,
        )
        return ShellSnippets.STARTER_COMMANDS.mapIndexed { index, command ->
            ShellSnippet("starter-$index", appContext.getString(labels[index]), command)
        }
    }
}
