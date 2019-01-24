package org.jetbrains.kotlin.tools.gradleimportcmd

import com.intellij.build.SyncViewManager
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.ide.actions.ImportModuleAction
import com.intellij.ide.impl.PatchProjectUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.newProjectWizard.AddModuleWizard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarterBase
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportBuilder
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportProvider
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File


class GradleImportCmdMain : ApplicationStarterBase(
        "importAndSave",
        1
) {
    private val LOG = Logger.getInstance("#org.jetbrains.kotlin.tools.gradleimportcmd.GradleImportCmdMain")

    override fun isHeadless(): Boolean = true

    override fun getUsageMessage(): String = "Usage: idea importAndSave <path-to-gradle-project>"

    override fun processCommand(args: Array<out String>?, currentDirectory: String?) {
        println("started!")

        val application = ApplicationManagerEx.getApplicationEx()

        try {
            application.runReadAction {
                try {
                    application.isSaveAllowed = true
                    run()
                } catch (e: Exception) {
                    LOG.error(e)
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            application.exit(true, true)
        }
    }

    val myVerboseLevel = 0
    lateinit var projectPath: String

    override fun premain(args: Array<out String>) {
        if (args.size != 2) {
            printHelp()
        }

        projectPath = args[1]
        if (!File(projectPath).isDirectory) {
            println("$projectPath is not directory")
            printHelp()
        }
    }

    var project: Project? = null

    private fun run() {
        projectPath = projectPath.replace(File.separatorChar, '/')
        val vfsProject = LocalFileSystem.getInstance().findFileByPath(projectPath)
        if (vfsProject == null) {
            logError(InspectionsBundle.message("inspection.application.file.cannot.be.found", projectPath))
            printHelp()
        }

        logMessage(1, InspectionsBundle.message("inspection.application.opening.project"))

        project = ProjectUtil.openProject(projectPath, null, false)
        importGradleProject()

        if (project == null) {
            logError("Unable to open project")
            gracefulExit()
            return
        }

        ApplicationManager.getApplication().runWriteAction { VirtualFileManager.getInstance().refreshWithoutFileWatcher(false) }

        PatchProjectUtil.patchProject(project)

        logMessageLn(1, InspectionsBundle.message("inspection.done"))
        logMessage(1, InspectionsBundle.message("inspection.application.initializing.project"))

        FileDocumentManager.getInstance().saveAllDocuments()
        ApplicationManager.getApplication().saveSettings()
    }

    private fun importGradleProject() {
        ProjectRootManager.getInstance(project!!).projectSdk = ProjectJdkTable.getInstance().findJdk("1.8")

        val kotlinDslGradleFile = projectPath + '/'.toString() + GradleConstants.KOTLIN_DSL_SCRIPT_NAME
        val projectDataManager = ServiceManager.getService(ProjectDataManager::class.java)
        val gradleProjectImportBuilder = GradleProjectImportBuilder(projectDataManager)
        val gradleProjectImportProvider = GradleProjectImportProvider(gradleProjectImportBuilder)
        val wizard = AddModuleWizard(project, File(kotlinDslGradleFile).path, gradleProjectImportProvider)
        ImportModuleAction.createFromWizard(project, wizard)
        ExternalProjectsManagerImpl.getInstance(project!!).setStoreExternally(false)

        val importSpec = ImportSpecBuilder(project!!, GradleConstants.SYSTEM_ID)
                .build()
        ExternalSystemUtil.refreshProject(projectPath, importSpec)

        project!!.save()
        ProjectManagerEx.getInstanceEx().openProject(project!!)

        FileDocumentManager.getInstance().saveAllDocuments()
        ApplicationManager.getApplication().saveSettings(true)
        ApplicationManager.getApplication().saveAll()
    }

    private fun closeProject() {
        if (project?.isDisposed == false) {
            ProjectUtil.closeAndDispose(project!!)
            project = null
        }
    }

    private fun gracefulExit() {
        if (false) {
            System.exit(1)
        } else {
            closeProject()
            throw RuntimeException("Failed to proceed")
        }
    }

    private fun logMessage(minVerboseLevel: Int, message: String) {
        if (myVerboseLevel >= minVerboseLevel) {
            println(message)
        }
    }

    private fun logError(message: String) {
        System.err.println(message)
    }

    private fun logMessageLn(minVerboseLevel: Int, message: String) {
        if (myVerboseLevel >= minVerboseLevel) {
            println(message)
        }
    }

    private fun printHelp() {
        println(usageMessage)
        System.exit(1)
    }
}