package org.jetbrains.kotlin.tools.gradleimportcmd

import org.jetbrains.kotlin.tools.testutils.*

class JpsImportAndBuild : ImportAndSave() {
    override fun run(args: List<String>, workingDir: String?) {
        setIndexInitialization(false)
        importProject(projectPath, jdkPath)?.let {
            setDelegationMode(projectPath, it, true)
            buildProject(it)
        }
    }

}