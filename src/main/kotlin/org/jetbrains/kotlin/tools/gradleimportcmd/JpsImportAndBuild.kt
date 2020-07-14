package org.jetbrains.kotlin.tools.gradleimportcmd

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import org.jetbrains.jps.cmdline.LogSetup
import org.jetbrains.kotlin.tools.projectWizard.core.parseAs
import org.jetbrains.kotlin.tools.testutils.buildProject
import org.jetbrains.kotlin.tools.testutils.importProject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

@Suppress("unused")
class JpsImportAndBuild : ImportAndSave() {
    override fun run(args: List<String>, workingDir: String?) {
        print("============================================")
        print(SystemProperties.getBooleanProperty("jps.backward.ref.index.qwe", false))
        print(System.getProperty("jps.backward.ref.index.qwe", "---"))
        print(">>>>>")
        //SystemProperties.getBooleanProperty("intellij.build.incremental.compilation", false)
        //enableDebugLogging()
        val logDirectory = BuildManager.getBuildLogDirectory()
        FileUtil.delete(logDirectory)
        FileUtil.createDirectory(logDirectory)
        val properties = Properties()
        LogSetup.readDefaultLogConfig().use { config -> properties.load(config) }
        properties.setProperty("log4j.rootLogger", "debug, file")
        val logFile = File(logDirectory, LogSetup.LOG_CONFIG_FILE_NAME)
        BufferedOutputStream(FileOutputStream(logFile)).use { output -> properties.store(output, null) }

        importProject(projectPath, jdkPath, false)?.let {
            //setDelegationMode(projectPath, it, true)
            buildProject(it)
        }
    }

}