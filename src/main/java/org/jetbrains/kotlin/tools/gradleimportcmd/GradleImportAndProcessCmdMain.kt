package org.jetbrains.kotlin.tools.gradleimportcmd

import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.InternalCompileDriver
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarterBase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler.CompileStatusNotification
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ThreeState
import org.jetbrains.plugins.gradle.settings.DefaultGradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

const val cmd = "importAndProcess"

class GradleImportAndProcessCmdMain : ApplicationStarterBase(cmd, 3) {
    override fun isHeadless(): Boolean = true
    private val IMPORT_AND_BUILD = "importAndBuild"
    override fun getUsageMessage(): String = "Usage: idea $cmd [importAndSave|$IMPORT_AND_BUILD] <path-to-gradle-project> <path-to-jdk>"


    private lateinit var projectPath: String
    private lateinit var jdkPath: String
    private var isBuildEnabled: Boolean = false
    private lateinit var mySdk: Sdk

    private fun printHelp() {
        println(usageMessage)
        exitProcess(1)
    }

    override fun premain(args: Array<out String>) {
        if (args.size != 4) {
            printHelp()
        }

        isBuildEnabled = args[1] == IMPORT_AND_BUILD
        projectPath = args[2]
        jdkPath = args[3]
        if (!File(projectPath).isDirectory) {
            printMessage("$projectPath is not directory", MessageStatus.ERROR)
            printHelp()
        }
    }

    private inline fun printMemory(afterGc: Boolean) {
        val runtime = Runtime.getRuntime()
        printMessage("Low memory ${if (afterGc) "after GC" else ", invoking GC"}. Total memory=${runtime.totalMemory()}, free=${runtime.freeMemory()}", if (afterGc) MessageStatus.ERROR else MessageStatus.WARNING)
    }

    override fun processCommand(args: Array<String>, currentDirectory: String?) {
        printProgress("Processing command $args in working directory $currentDirectory", ProgressType.START)
        System.setProperty("idea.skip.indices.initialization", "true")

        // add low memory notifications
        var afterGcLowMemoryNotifier = LowMemoryWatcher.register({
            printMemory(true)
            exitProcess(2)

        }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)
        var beforeGcLowMemoryNotifier = LowMemoryWatcher.register({ printMemory(false) },
                LowMemoryWatcher.LowMemoryWatcherType.ALWAYS)


        val application = ApplicationManagerEx.getApplicationEx()

        try {
            val project = application.runReadAction(Computable<Project?> {
                return@Computable try {
                    application.isSaveAllowed = true
                    importProject()
                } catch (e: Exception) {
                    printException(e)
                    null
                }
            })
            if (isBuildEnabled) {
                buildProject(project)
            }
        } catch (t: Throwable) {
            printException(t)
        } finally {
            afterGcLowMemoryNotifier = null // low memory notifications are not required any more
            beforeGcLowMemoryNotifier = null
            printProgress("Exit application", ProgressType.FINISH)
            application.exit(true, true)
        }
    }

    private fun buildProject(project: Project?) {
        val finishedLautch = CountDownLatch(1)
        if (project == null) {
            printMessage("Project is null", MessageStatus.ERROR)
            exitProcess(1)
        } else {
            startTest("Compile project with JPS")
            var errorsCount = 0
            var abortedStatus = false
            val callback = CompileStatusNotification { aborted, errors, warnings, compileContext ->
                run {
                    try {
                        errorsCount = errors
                        abortedStatus = aborted
                        printMessage("Compilation done. Aborted=$aborted, Errors=$errors, Warnings=$warnings", MessageStatus.WARNING)
                        CompilerMessageCategory.values().forEach { category ->
                            compileContext.getMessages(category).forEach {
                                val message = "$category - ${it.virtualFile?.canonicalPath ?: "-"}: ${it.message}"
                                when (category) {
                                    CompilerMessageCategory.ERROR -> printMessage(message, MessageStatus.ERROR)
                                    CompilerMessageCategory.WARNING -> printMessage(message, MessageStatus.WARNING)
                                    else -> printMessage(message)
                                }
                            }
                        }
                    } finally {
                        finishedLautch.countDown()
                    }
                }
            }

            CompilerConfigurationImpl.getInstance(project).setBuildProcessHeapSize(3500)

            val compileContext = InternalCompileDriver(project).rebuild(callback)
            while (!finishedLautch.await(1, TimeUnit.MINUTES)) {
                if (!compileContext.progressIndicator.isRunning) {
                    printMessage("Progress indicator says that compilation is not running.", MessageStatus.ERROR)
                    break
                }
                printProgress("Compilation status: Errors: ${compileContext.getMessages(CompilerMessageCategory.ERROR).size}. Warnings: ${compileContext.getMessages(CompilerMessageCategory.WARNING).size}.", ProgressType.MESSAGE)
            }

            if (errorsCount > 0 || abortedStatus) {
                finishTest("Compile project with JPS", "Compilation failed with $errorsCount errors")
                exitProcess(4)
            } else {
                finishTest("Compile project with JPS")
            }
        }
    }


    private fun importProject(): Project? {
        printProgress("Opening project", ProgressType.MESSAGE)
        projectPath = projectPath.replace(File.separatorChar, '/')
        val vfsProject = LocalFileSystem.getInstance().findFileByPath(projectPath)
        if (vfsProject == null) {
            printMessage("Cannot find directory $projectPath", MessageStatus.ERROR)
            printHelp()
        }

        val project = ProjectUtil.openProject(projectPath, null, false)

        if (project == null) {
            printMessage("Unable to open project", MessageStatus.ERROR)
            gracefulExit(project)
            return null
        }
        DefaultGradleProjectSettings.getInstance(project).isDelegatedBuild = false
        printProgress("Project loaded, refreshing from Gradle", ProgressType.MESSAGE)
        WriteAction.runAndWait<RuntimeException> {
            val sdkType = JavaSdk.getInstance()
            mySdk = sdkType.createJdk("JDK_1.8", jdkPath, false)

            ProjectJdkTable.getInstance().addJdk(mySdk)
            ProjectRootManager.getInstance(project).projectSdk = mySdk
        }

        val projectSettings = GradleProjectSettings()
        projectSettings.externalProjectPath = projectPath
        projectSettings.delegatedBuild = ThreeState.NO
        projectSettings.storeProjectFilesExternally = ThreeState.NO
        projectSettings.withQualifiedModuleNames()

        val systemSettings = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID)
        val linkedSettings: Collection<ExternalProjectSettings> = systemSettings.getLinkedProjectsSettings() as Collection<ExternalProjectSettings>
        linkedSettings.filter { it is GradleProjectSettings }.forEach { systemSettings.unlinkExternalProject(it.externalProjectPath) }

        systemSettings.linkProject(projectSettings)

        val startTime = System.nanoTime() //NB do not use currentTimeMillis() as it is sensitive to time adjustment
        startTest("Import project")
        refreshProject(
                project,
                GradleConstants.SYSTEM_ID,
                projectPath,
                object : ExternalProjectRefreshCallback {
                    override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                        finishTest("Import project", duration = (System.nanoTime() - startTime) / 1000_000)
                        if (externalProject != null) {
                            ServiceManager.getService(ProjectDataManager::class.java)
                                    .importData(externalProject, project, true)
                        } else {
                            finishTest("Import project", "Filed to import project. See IDEA logs for details")
                            gracefulExit(project)
                        }
                    }
                },
                false,
                ProgressExecutionMode.MODAL_SYNC,
                true
        )

        printProgress("Unloading buildSrc modules", ProgressType.MESSAGE)

        val moduleManager = ModuleManager.getInstance(project)
        val buildSrcModuleNames = moduleManager.sortedModules
                .filter { it.name.contains("buildSrc") }
                .map { it.name }
        moduleManager.setUnloadedModules(buildSrcModuleNames)

        printProgress("Save IDEA projects", ProgressType.MESSAGE)

        project.save()
        ProjectManagerEx.getInstanceEx().openProject(project)
        FileDocumentManager.getInstance().saveAllDocuments()
        ApplicationManager.getApplication().saveSettings()
        ApplicationManager.getApplication().saveAll()

        printProgress("Import done", ProgressType.MESSAGE)
        return project
    }


    private fun gracefulExit(project: Project?) {
        if (project?.isDisposed == false) {
            ProjectUtil.closeAndDispose(project)
        }
        throw RuntimeException("Failed to proceed")
    }
}