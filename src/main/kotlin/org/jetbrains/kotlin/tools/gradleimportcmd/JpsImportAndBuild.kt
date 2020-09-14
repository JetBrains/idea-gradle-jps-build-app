package org.jetbrains.kotlin.tools.gradleimportcmd

import org.jetbrains.kotlin.tools.testutils.*

@Suppress("unused")
class JpsImportAndBuild : ImportAndSave() {
    override fun run(args: List<String>, workingDir: String?) {
        printMessage("Build for branch ${System.getenv("build_vcs_branch_kotlin")}, " +
                "commit: ${System.getenv("build_vcs_number_kotlin")}")

        importProject(projectPath, jdkPath, false)?.let {
            if(buildProject(it) && System.getenv("build_vcs_branch_kotlin") == "refs/heads/master") {
                uploadCaches(it)
            } else {
                revertIdeaVersionBuildChanges()
            }
        }
    }
}