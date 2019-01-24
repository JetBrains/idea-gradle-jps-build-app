package org.jetbrains.kotlin.tools.gradleimportcmd

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.ide.impl.PatchProjectUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarterBase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File


class GradleImportCmdMain : ApplicationStarterBase(
        "importAndSave",
        2
) {
    private val LOG = Logger.getInstance("#org.jetbrains.kotlin.tools.gradleimportcmd.GradleImportCmdMain")

    override fun isHeadless(): Boolean = true

    override fun getUsageMessage(): String = "Usage: idea importAndSave <path-to-gradle-project>"

    override fun processCommand(args: Array<out String>?, currentDirectory: String?) {
        println("Initializing")

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
    lateinit var jdkPath: String

    override fun premain(args: Array<out String>) {
        if (args.size != 3) {
            printHelp()
        }

        projectPath = args[1]
        jdkPath = args[2]
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

    lateinit var mySdk: Sdk

    private fun importGradleProject() {
        val table = JavaAwareProjectJdkTableImpl.getInstanceEx()

        WriteAction.runAndWait<RuntimeException> {
            mySdk = (table.defaultSdkType as JavaSdk).createJdk("1.8", jdkPath)
            ProjectJdkTable.getInstance().addJdk(mySdk)
            ProjectRootManager.getInstance(project!!).projectSdk = mySdk
        }

        ExternalSystemUtil.refreshProject(
                project!!,
                GradleConstants.SYSTEM_ID,
                projectPath,
                object : ExternalProjectRefreshCallback {
                    override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                        if (externalProject != null) {
                            ServiceManager.getService(ProjectDataManager::class.java)
                                    .importData(externalProject, project!!, true)
                        }
                    }
                },
                false,
                ProgressExecutionMode.IN_BACKGROUND_ASYNC,
                true
        )

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