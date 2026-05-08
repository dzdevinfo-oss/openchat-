package com.openchat.app.util

import javax.inject.Inject
import javax.inject.Singleton

data class Artifact(val type: String, val content: String, val language: String, val title: String = "")

@Singleton
class ArtifactDetector @Inject constructor() {
    fun detect(messageContent: String): List<Artifact> {
        val artifacts = mutableListOf<Artifact>()
        val regex = Regex("```(html|xml|react|svg|artifact)\\s*\\n(.*?)\\s*```", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(messageContent)
        
        for (match in matches) {
            val lang = match.groupValues[1].lowercase()
            val content = match.groupValues[2]
            
            if (lang == "html" || lang == "react" || lang == "svg" || lang == "artifact") {
                artifacts.add(Artifact(type = lang, content = content, language = lang))
            }
        }
        return artifacts
    }
}
