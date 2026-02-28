fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
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
        intellijIdeaUltimate("2025.3.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugins("com.intellij.mcpServer")

        // AI Assistant plugin (ml.llm) from marketplace
        compatiblePlugins("com.intellij.ml.llm")
    }

    // Kotlinx Serialization for JSON parsing
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Markdown to HTML conversion
    implementation("org.jetbrains:markdown:0.7.3")

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


