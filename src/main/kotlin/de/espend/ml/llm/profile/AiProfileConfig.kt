package de.espend.ml.llm.profile

data class AiProfileConfig(
    var id: String = "",
    var name: String = "",
    var platform: String = AiProfilePlatformRegistry.PLATFORM_CLAUDE_CODE,
    var apiType: String = "",
    var transport: String = "",
    var claudeCodeExecutable: String = "",
    var apiKey: String = "",
    var baseUrl: String = "",
    var model: String = "",
    var isEnabled: Boolean = true
) {
    fun effectiveTransport(): String {
        return transport.ifBlank { AiProfilePlatformRegistry.defaultTransport(platform) }
    }

    fun effectiveApiType(): String {
        return apiType.ifBlank { AiProfilePlatformRegistry.defaultApiType(platform) }
    }
}
