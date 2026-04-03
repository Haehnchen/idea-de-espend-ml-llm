package de.espend.ml.llm

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFrame
import java.awt.Component
import javax.swing.SwingUtilities

object ProjectResolutionUtils {

    fun resolveProject(event: AnActionEvent): Project? {
        return resolveProject(
            preferredProject = event.project ?: CommonDataKeys.PROJECT.getData(event.dataContext),
            component = event.inputEvent?.component
        )
    }

    fun resolveProject(preferredProject: Project? = null, component: Component? = null): Project? {
        preferredProject
            ?.takeUnless { it.isDisposed }
            ?.let { return it }

        component?.let {
            CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(it))
                ?.takeUnless(Project::isDisposed)
                ?.let { project -> return project }

            (SwingUtilities.getWindowAncestor(it) as? IdeFrame)
                ?.project
                ?.takeUnless(Project::isDisposed)
                ?.let { project -> return project }
        }

        return ProjectManager.getInstance().openProjects.singleOrNull { !it.isDisposed }
    }
}
