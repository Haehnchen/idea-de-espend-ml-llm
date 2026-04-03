import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.attributes.Attribute

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "de.espend.ml.llm"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2026.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugins("com.intellij.mcpServer", "org.intellij.plugins.markdown")

        // AI Assistant plugin (ml.llm) from marketplace
        compatiblePlugins("com.intellij.ml.llm")
    }

    // Kotlinx Serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // SQLite JDBC for RTK stats panel
    implementation("org.xerial:sqlite-jdbc:3.51.3.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    // Disable buildSearchableOptions as it requires exclusive IDEA instance
    buildSearchableOptions {
        enabled = false
    }
    prepareJarSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version = properties("pluginVersion")
        changeNotes.set(file("src/main/resources/META-INF/change-notes.html").readText().replace("<html>", "").replace("</html>", ""))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Helper: parse plugin.xml InputStream → (pluginId, pluginName)
fun parsePluginXml(stream: java.io.InputStream): Pair<String?, String?> {
    return try {
        val factory = DocumentBuilderFactory.newInstance().also {
            it.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            it.isValidating = false
        }
        val doc = factory.newDocumentBuilder().parse(stream)
        val id = doc.getElementsByTagName("id").item(0)?.textContent?.trim()
        val name = doc.getElementsByTagName("name").item(0)?.textContent?.trim()
        id to name
    } catch (_: Exception) {
        null to null
    }
}

// Helper: find plugin.xml in JARs under plugin dir → (pluginId, pluginName)
fun readPluginXmlFromDir(pluginDir: File): Pair<String?, String?> {
    val libDir = pluginDir.resolve("lib").takeIf { it.exists() } ?: return null to null
    libDir.listFiles { f -> f.extension == "jar" }
        ?.sortedBy { it.name }
        ?.forEach { jar ->
            try {
                JarFile(jar).use { jf ->
                    val entry = jf.getJarEntry("META-INF/plugin.xml") ?: return@use
                    val (id, name) = parsePluginXml(jf.getInputStream(entry))
                    if (id != null) return id to name
                }
            } catch (_: Exception) {}
        }
    return null to null
}


// Print all plugin dependencies (bundled + compatible) with ID, name, and path
tasks.register("printPluginDependencies") {
    group = "intellij"
    description = "Print all bundled and compatible plugins with ID, name, and path"
    notCompatibleWithConfigurationCache("Reads plugin directories and resolves configurations at execution time")

    doLast {
        val extractedAttr = Attribute.of("intellijPlatformExtracted", Boolean::class.javaObjectType)

        // type\tid\tname\tpath  (tab-separated, pipe with -q flag)
        fun printPlugin(type: String, id: String?, name: String?, dir: File) =
            println("$type\t${id ?: ""}\t${name ?: ""}\t${dir.absolutePath}")

        intellijPlatform.platformPath.resolve("plugins").toFile()
            .listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> readPluginXmlFromDir(dir).let { (id, name) -> if (id != null) Triple(id, name, dir) else null } }
            ?.sortedBy { it.first }
            ?.forEach { (id, name, dir) -> printPlugin("bundled", id, name, dir) }

        configurations.getByName("intellijPlatformPluginDependency")
            .incoming
            .artifactView { attributes { attribute(extractedAttr, true) } }
            .files
            .sortedBy { it.name }
            .forEach { extractedDir ->
                val (id, name) = extractedDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.firstNotNullOfOrNull { subDir -> readPluginXmlFromDir(subDir).let { (i, n) -> if (i != null) i to n else null } }
                    ?: (null to null)
                printPlugin("compatible", id, name, extractedDir)
            }
    }
}

// CLI tool for dumping session HTML
tasks.register<JavaExec>("dumpSession") {
    group = "session"
    description = "Dump session HTML to file for debugging"
    mainClass.set("de.espend.ml.llm.session.cli.SessionHtmlDumper")
    classpath = sourceSets["main"].runtimeClasspath

    // Pass all args after -- to the application
    // Usage: ./gradlew dumpSession --args="--provider=claude --id=SESSION_ID"
}


