package com.antigravity.studio.settings

import android.content.Context
import android.content.SharedPreferences
import com.antigravity.studio.auth.GoogleOAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de persistencia de políticas de gobernanza y auto-aprobación agéntica.
 * Utiliza SharedPreferences para almacenar las preferencias de forma persistente
 * y expone un StateFlow reactivo para la interfaz de usuario y el motor RealAgentEngine.
 * Conforme a SPEC-003.
 */
class AgentSettingsManager(
    private val prefs: SharedPreferences? = null
) {

    private val inMemoryFallback = ConcurrentHashMap<String, Any>()

    private val _policyFlow: MutableStateFlow<AgentPermissionPolicy>
    val policyFlow: StateFlow<AgentPermissionPolicy>

    init {
        val initialPolicy = loadPolicyFromStorage()
        _policyFlow = MutableStateFlow(initialPolicy)
        policyFlow = _policyFlow.asStateFlow()
    }

    /**
     * Retorna la política de permisos vigente.
     */
    fun getPolicy(): AgentPermissionPolicy = _policyFlow.value

    /**
     * Guarda y difunde una nueva política de permisos.
     */
    @Synchronized
    fun savePolicy(policy: AgentPermissionPolicy) {
        if (prefs != null) {
            prefs.edit()
                .putBoolean(KEY_AUTO_APPROVE_READ, policy.autoApproveRead)
                .putBoolean(KEY_AUTO_APPROVE_WRITE, policy.autoApproveWrite)
                .putBoolean(KEY_AUTO_APPROVE_SAFE_BASH, policy.autoApproveSafeBash)
                .putBoolean(KEY_REQUIRE_APPROVAL_DESTRUCTIVE, policy.requireApprovalForDestructive)
                .apply()
        } else {
            inMemoryFallback[KEY_AUTO_APPROVE_READ] = policy.autoApproveRead
            inMemoryFallback[KEY_AUTO_APPROVE_WRITE] = policy.autoApproveWrite
            inMemoryFallback[KEY_AUTO_APPROVE_SAFE_BASH] = policy.autoApproveSafeBash
            inMemoryFallback[KEY_REQUIRE_APPROVAL_DESTRUCTIVE] = policy.requireApprovalForDestructive
        }
        _policyFlow.value = policy
    }

    /**
     * Actualiza la política atómicamente mediante un lambda transformador.
     */
    fun updatePolicy(transform: (AgentPermissionPolicy) -> AgentPermissionPolicy) {
        val current = getPolicy()
        val updated = transform(current)
        savePolicy(updated)
    }

    /**
     * Retorna la clave API de Gemini configurada, o intenta leerla del archivo local .gemini/api_key.
     */
    fun getGeminiApiKey(): String? = getApiKey()

    /**
     * Guarda o elimina la clave API de Gemini en SharedPreferences y archivo local.
     */
    fun setGeminiApiKey(key: String?) {
        if (key.isNullOrBlank()) {
            if (prefs != null) {
                prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
            } else {
                inMemoryFallback.remove(KEY_GEMINI_API_KEY)
            }
            try {
                val ctx = GoogleOAuthManager.getAppContext()
                val candidateDirs = listOfNotNull(
                    ctx?.filesDir,
                    File("/data/data/com.antigravity.studio/files").takeIf { it.exists() }
                )
                for (dir in candidateDirs) {
                    try {
                        val keyFile = File(dir, ".gemini/api_key")
                        if (keyFile.exists()) keyFile.delete()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        } else {
            saveApiKey(key)
        }
    }

    /**
     * Retorna la clave API de Gemini configurada, o intenta leerla del archivo local .gemini/api_key.
     */
    fun getApiKey(): String? {
        val key = if (prefs != null) {
            prefs.getString(KEY_GEMINI_API_KEY, null)
        } else {
            inMemoryFallback[KEY_GEMINI_API_KEY] as? String
        }
        if (!key.isNullOrBlank()) return key.trim()

        try {
            val ctx = GoogleOAuthManager.getAppContext()
            val candidateDirs = listOfNotNull(
                ctx?.filesDir,
                File("/data/data/com.antigravity.studio/files").takeIf { it.exists() }
            )
            for (dir in candidateDirs) {
                val keyFile = File(dir, ".gemini/api_key")
                if (keyFile.exists() && keyFile.isFile) {
                    val content = keyFile.readText(Charsets.UTF_8).trim()
                    if (content.isNotEmpty()) return content
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Guarda la clave API de Gemini en SharedPreferences y en el archivo local .gemini/api_key.
     */
    @Synchronized
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (prefs != null) {
            prefs.edit().putString(KEY_GEMINI_API_KEY, trimmed).apply()
        } else {
            inMemoryFallback[KEY_GEMINI_API_KEY] = trimmed
        }

        try {
            val ctx = GoogleOAuthManager.getAppContext()
            val candidateDirs = listOfNotNull(
                ctx?.filesDir,
                File("/data/data/com.antigravity.studio/files").takeIf { it.exists() }
            )
            for (dir in candidateDirs) {
                try {
                    val geminiDir = File(dir, ".gemini")
                    if (!geminiDir.exists()) {
                        geminiDir.mkdirs()
                    }
                    val keyFile = File(geminiDir, "api_key")
                    keyFile.writeText(trimmed, Charsets.UTF_8)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * Evalúa si una acción de una categoría dada debe auto-aprobarse según la política actual.
     */
    fun shouldAutoApprove(category: ActionCategory, commandOrPath: String? = null): Boolean {
        val policy = getPolicy()
        return when (category) {
            ActionCategory.FILE_READ -> policy.autoApproveRead
            ActionCategory.FILE_WRITE -> policy.autoApproveWrite
            ActionCategory.BASH_SAFE -> {
                if (commandOrPath != null && isDestructiveBashCommand(commandOrPath)) {
                    false
                } else {
                    policy.autoApproveSafeBash
                }
            }
            ActionCategory.BASH_DESTRUCTIVE -> {
                if (policy.requireApprovalForDestructive) {
                    false
                } else {
                    policy.autoApproveSafeBash
                }
            }
        }
    }

    /**
     * Detecta si un comando Bash es potencialmente destructivo o de alto riesgo.
     * Lista negra no eludible según SPEC-003 §5.1.
     */
    fun isDestructiveBashCommand(command: String): Boolean {
        val trimmed = command.trim()
        val lower = trimmed.lowercase()

        // 1. rm con flags recursivas o forzadas (-r, -rf, -fr, etc.)
        if (DESTRUCTIVE_RM_REGEX.containsMatchIn(lower)) return true

        // 2. git push con fuerza (-f, --force) o git reset --hard
        if (lower.contains("git push") && (lower.contains("--force") || lower.contains("-f"))) return true
        if (lower.contains("git reset") && lower.contains("--hard")) return true
        if (lower.contains("git clean") && (lower.contains("-f") || lower.contains("-df"))) return true

        // 3. Comandos de bajo nivel de disco y particionado (dd, mkfs, fdisk)
        if (DESTRUCTIVE_DISK_REGEX.containsMatchIn(lower)) return true

        // 4. Inyección por tubería directa de red a shell (curl | bash, wget | sh)
        if (PIPE_TO_SHELL_REGEX.containsMatchIn(lower)) return true

        // 5. Permisos totales recursivos (chmod -R 777)
        if (CHMOD_777_REGEX.containsMatchIn(lower)) return true

        // 6. Fork bombs y comandos de apagado / reinicio
        if (lower.contains(":(){ :|:& };:") || lower.contains("reboot") || lower.contains("shutdown") || lower.contains("poweroff")) {
            return true
        }

        return false
    }

    /**
     * Comprueba si un comando Bash se considera estrictamente de sólo lectura / verificación segura.
     */
    fun isSafeBashCommand(command: String): Boolean {
        if (isDestructiveBashCommand(command)) return false

        val trimmed = command.trim()
        val firstToken = trimmed.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return false

        // Lista blanca de comandos de sólo lectura y verificación
        val safeCommands = setOf(
            "ls", "pwd", "dir", "echo", "cat", "head", "tail", "grep",
            "which", "where", "find", "stat", "file", "wc", "uname",
            "date", "whoami", "env", "printenv", "tree"
        )

        if (safeCommands.contains(firstToken)) return true

        // Comandos de desarrollo en modo chequeo seguro
        if (firstToken == "git") {
            val safeGitSubcommands = setOf("status", "diff", "log", "branch", "show", "tag", "remote")
            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size >= 2 && safeGitSubcommands.contains(tokens[1].lowercase())) {
                return true
            }
        }

        if (firstToken == "cargo" && trimmed.contains("check")) return true
        if (firstToken == "python" || firstToken == "python3" || firstToken == "py") {
            if (trimmed.endsWith("--version") || trimmed.endsWith("-v")) return true
        }
        if (firstToken == "node" || firstToken == "npm" || firstToken == "npx") {
            if (trimmed.endsWith("--version") || trimmed.endsWith("-v")) return true
        }

        return false
    }

    /**
     * Clasifica un comando Bash en categoría segura o destructiva.
     */
    fun categorizeBashCommand(command: String): ActionCategory {
        return if (isDestructiveBashCommand(command)) {
            ActionCategory.BASH_DESTRUCTIVE
        } else {
            ActionCategory.BASH_SAFE
        }
    }

    private fun loadPolicyFromStorage(): AgentPermissionPolicy {
        return if (prefs != null) {
            AgentPermissionPolicy(
                autoApproveRead = prefs.getBoolean(KEY_AUTO_APPROVE_READ, true),
                autoApproveWrite = prefs.getBoolean(KEY_AUTO_APPROVE_WRITE, false),
                autoApproveSafeBash = prefs.getBoolean(KEY_AUTO_APPROVE_SAFE_BASH, true),
                requireApprovalForDestructive = prefs.getBoolean(KEY_REQUIRE_APPROVAL_DESTRUCTIVE, true)
            )
        } else {
            AgentPermissionPolicy(
                autoApproveRead = inMemoryFallback[KEY_AUTO_APPROVE_READ] as? Boolean ?: true,
                autoApproveWrite = inMemoryFallback[KEY_AUTO_APPROVE_WRITE] as? Boolean ?: false,
                autoApproveSafeBash = inMemoryFallback[KEY_AUTO_APPROVE_SAFE_BASH] as? Boolean ?: true,
                requireApprovalForDestructive = inMemoryFallback[KEY_REQUIRE_APPROVAL_DESTRUCTIVE] as? Boolean ?: true
            )
        }
    }

    companion object {
        const val PREFS_NAME = "antigravity_agent_settings"

        const val KEY_AUTO_APPROVE_READ = "pref_auto_approve_read"
        const val KEY_AUTO_APPROVE_WRITE = "pref_auto_approve_write"
        const val KEY_AUTO_APPROVE_SAFE_BASH = "pref_auto_approve_safe_bash"
        const val KEY_REQUIRE_APPROVAL_DESTRUCTIVE = "pref_require_approval_destructive"
        const val KEY_GEMINI_API_KEY = "pref_gemini_api_key"

        private val DESTRUCTIVE_RM_REGEX = Regex("""\brm\s+.*(-[a-zA-Z]*[rf][a-zA-Z]*|--recursive)""", RegexOption.IGNORE_CASE)
        private val DESTRUCTIVE_DISK_REGEX = Regex("""\b(dd\s+if=|mkfs(\.[a-zA-Z0-9]+)?\b|fdisk\b)""", RegexOption.IGNORE_CASE)
        private val PIPE_TO_SHELL_REGEX = Regex("""\b(curl|wget)\s+.*\|\s*(ba)?sh""", RegexOption.IGNORE_CASE)
        private val CHMOD_777_REGEX = Regex("""\bchmod\s+(-[a-zA-Z]*[rR][a-zA-Z]*\s+)?(777|a\+rwx)""", RegexOption.IGNORE_CASE)

        @Volatile
        private var defaultInstance: AgentSettingsManager? = null

        fun getInstance(context: Context? = null): AgentSettingsManager {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: run {
                    val appContext = context?.applicationContext ?: GoogleOAuthManager.getAppContext()
                    val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val mgr = AgentSettingsManager(prefs)
                    defaultInstance = mgr
                    mgr
                }
            }
        }

        fun init(prefs: SharedPreferences): AgentSettingsManager {
            return synchronized(this) {
                val mgr = AgentSettingsManager(prefs)
                defaultInstance = mgr
                mgr
            }
        }

        fun init(context: Context): AgentSettingsManager {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return init(prefs)
        }

        fun getGeminiApiKey(): String? = getInstance().getGeminiApiKey()
        fun setGeminiApiKey(key: String?) = getInstance().setGeminiApiKey(key)
        fun getApiKey(): String? = getInstance().getApiKey()
        fun saveApiKey(apiKey: String) = getInstance().saveApiKey(apiKey)

        fun resetInstanceForTest() {
            synchronized(this) {
                defaultInstance = null
            }
        }
    }
}
