package org.jetbrains.kotlin.tools.gradleimportcmd

import org.jetbrains.kotlin.tools.testutils.*

@Suppress("unused")
class JpsImportAndBuild : ImportAndSave() {
    override fun run(args: List<String>, workingDir: String?) {
        importProject(projectPath, jdkPath, false)?.let {
            setDelegationMode(projectPath, it, true)
            buildProject(it)
        }
    }

}