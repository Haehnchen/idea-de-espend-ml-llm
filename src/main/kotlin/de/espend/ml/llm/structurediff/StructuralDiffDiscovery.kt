package de.espend.ml.llm.structurediff

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.io.File

object StructuralDiffDiscovery {
    private val log = Logger.getInstance(StructuralDiffDiscovery::class.java)

    private val additionalDirectories: List<File>
        get() = listOfNotNull(
            File("/opt/homebrew/bin"),
            File("/usr/local/bin"),
            File("/usr/bin"),
            System.getProperty("user.home")?.let { File(it, "bin") },
            System.getProperty("user.home")?.let { File(it, ".local/bin") },
            System.getProperty("user.home")?.let { File(it, ".cargo/bin") },
            System.getProperty("user.home")?.let { File(it, "scoop/shims") },
            System.getenv("ProgramData")?.let { File(it, "chocolatey/bin") },
        )

    @Synchronized
    fun discoverOnce() {
        val settings = StructuralDiffSettings.instance
        if (settings.discoveryCompleted) return
        settings.discoveryCompleted = true
        addNewTools(settings, discoverInstalled())
    }

    fun discoverInstalled(): List<StructuralDiffToolConfig> = StructuralDiffEngineKind.entries.mapNotNull { kind ->
        val executable = kind.executableNames.firstNotNullOfOrNull(::locate) ?: return@mapNotNull null
        val version = StructuralDiffEngine.probe(kind, executable.absolutePath) ?: return@mapNotNull null
        StructuralDiffToolConfig().apply {
            name = kind.displayName
            engineId = kind.id
            this.executable = executable.absolutePath
            arguments = kind.defaultArguments
            environment = kind.defaultEnvironment.toMutableMap()
        }.also { log.info("Detected $version at ${executable.absolutePath}") }
    }

    fun addNewTools(settings: StructuralDiffSettings, detected: List<StructuralDiffToolConfig>): Int {
        val paths = settings.tools.mapNotNull { it.executable }.map(::normalizedPath).toMutableSet()
        val names = settings.tools.mapNotNull { it.name }.toMutableSet()
        var count = 0
        detected.forEach { candidate ->
            if (!paths.add(normalizedPath(candidate.executable.orEmpty()))) return@forEach
            val base = candidate.name.orEmpty().ifBlank { "Diff Tool" }
            var name = base
            var index = 2
            while (name in names) name = "$base ${index++}"
            candidate.name = name
            names += name
            settings.tools.add(candidate)
            if (settings.defaultToolName.isNullOrBlank()) settings.defaultToolName = name
            count++
        }
        return count
    }

    private fun locate(name: String): File? {
        PathEnvironmentVariableUtil.findInPath(name)?.let { return it }
        val names = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            listOf(name, "$name.exe", "$name.cmd", "$name.bat")
        } else {
            listOf(name)
        }
        return additionalDirectories.asSequence()
            .flatMap { directory -> names.asSequence().map { File(directory, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private fun normalizedPath(path: String): String = File(path).absoluteFile.normalize().path
}

class StructuralDiffStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        StructuralDiffDiscovery.discoverOnce()
    }
}
