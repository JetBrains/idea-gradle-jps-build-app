package org.jetbrains.kotlin.tools.cachesuploader

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project
import java.io.File

class ProjectFileStorage(private val project: Project?) {
    fun getCompileServerFolder(): File =  BuildManager.getInstance().getProjectSystemDirectory(project)!!

    fun getTargetSourcesState(): File {
        val fileOrFolder = File(BuildManager.getInstance().getProjectSystemDirectory(project)!!, "target_sources_state.json")

        if(fileOrFolder.exists()) {
            return fileOrFolder
        } else {
            throw Exception("target_sources_state.json does not exist")
        }
    }
}