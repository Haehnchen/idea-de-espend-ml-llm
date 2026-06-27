package de.espend.ml.llm.difftastic

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class DifftasticStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        DifftasticDiffToolOrder.ensureDifftasticLastForKnownPlaces()
    }
}
