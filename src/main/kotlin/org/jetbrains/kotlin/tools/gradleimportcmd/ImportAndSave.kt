package org.jetbrains.kotlin.tools.gradleimportcmd

import org.jetbrains.kotlin.tools.testutils.TestSuite
import org.jetbrains.kotlin.tools.testutils.importProject

open class ImportAndSave : TestSuite() {
    val jdkPath = System.getProperty("jdkPath")!!
    val projectPath = System.getProperty("projectPath")!!

    override fun run(args: List<String>, workingDir: String?) {
        importProject(projectPath, jdkPath, true)
    }
}