fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
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
        intellijIdeaUltimate("2025.3.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugins("com.intellij.mcpServer", "org.intellij.plugins.markdown")

        // AI Assistant plugin (ml.llm) from marketplace
        compatiblePlugins("com.intellij.ml.llm")
    }

    // Kotlinx Serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // SQLite JDBC for RTK stats panel
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
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

    // HOT-RELOAD FIX:
    // PrepareSandboxTask extends Sync and includes ALL plugin dependencies (including compatiblePlugins)
    // in pluginsClasspath. When the user's plugin changes, Sync re-executes and re-syncs EVERYTHING
    // to the sandbox — including the compatible plugin com.intellij.ml.llm — updating JAR timestamps.
    // The running IDE's VFS listener detects these timestamp changes and tries to hot-reload
    // com.intellij.ml.llm, which fails because it's not unload-safe.
    //
    // Fix: exclude compatible plugins from the sandbox sync. They're already bundled in the IDE
    // installation — the sandbox doesn't need its own copy. This prevents Sync from touching their
    // JARs when only the user's plugin has changed.
    named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareSandbox") {
        pluginsClasspath.setFrom(
            pluginsClasspath.filter { file ->
                // Exclude compatible plugins (they're bundled in the IDE, no need to sync to sandbox)
                !file.path.contains("com.intellij.ml.llm") && !file.path.contains("org.intellij.plugins.markdown")
            }
        )
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

// CLI tool for dumping session HTML
tasks.register<JavaExec>("dumpSession") {
    group = "session"
    description = "Dump session HTML to file for debugging"
    mainClass.set("de.espend.ml.llm.session.cli.SessionHtmlDumper")
    classpath = sourceSets["main"].runtimeClasspath

    // Pass all args after -- to the application
    // Usage: ./gradlew dumpSession --args="--provider=claude --id=SESSION_ID"
}


