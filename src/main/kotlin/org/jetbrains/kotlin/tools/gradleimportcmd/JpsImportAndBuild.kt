package org.jetbrains.kotlin.tools.gradleimportcmd

import org.jetbrains.kotlin.tools.testutils.TestSuite
import org.jetbrains.kotlin.tools.testutils.buildProject
import org.jetbrains.kotlin.tools.testutils.importProject
import org.jetbrains.kotlin.tools.testutils.setDelegationMode

class JpsImportAndBuild : ImportAndSave() {
    override fun run(args: List<String>, workingDir: String?) {
        importProject(projectPath, jdkPath)?.let {
            setDelegationMode(projectPath, it, true)
            buildProject(it)
        }
    }

}