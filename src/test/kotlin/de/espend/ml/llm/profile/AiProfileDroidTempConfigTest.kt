package de.espend.ml.llm.profile

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText

class AiProfileDroidTempConfigTest {

    @Test
    fun `createTempHome should disable droid sound notifications`() {
        val tempHome = AiProfileDroidTempConfig.createTempHome(
            profileId = "profile-1",
            providerType = "anthropic",
            baseUrl = "https://example.invalid",
            modelIds = listOf("test-model")
        )

        val settingsPath = tempHome.resolve(".factory/settings.json")
        val json = JsonParser.parseString(settingsPath.readText()).asJsonObject

        assertEquals("off", json.get("completionSound").asString)
        assertEquals("off", json.get("awaitingInputSound").asString)
        assertNotNull(json.getAsJsonArray("customModels"))

        Files.deleteIfExists(settingsPath)
        Files.deleteIfExists(settingsPath.parent)
        Files.deleteIfExists(tempHome)
    }
}
