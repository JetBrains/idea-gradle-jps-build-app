package org.jetbrains.kotlin.tools.cachesuploader

import java.io.File

class ProjectFileStorage(private val projectPath: String) {

    fun getCompileServerFolder(): File =  getFileOrFolder("compile-server")

    fun getTargetSourcesState(): File = getFileOrFolder("compile-server/target_sources_state.json")

    private fun getFileOrFolder(name: String): File {
        val fileOrFolder = File(projectPath, name)

        if(fileOrFolder.exists()) {
            return fileOrFolder
        } else {
            throw Exception("$name folder does not exist")
        }
    }
}