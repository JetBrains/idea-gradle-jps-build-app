package org.jetbrains.kotlin.tools.gradleimportcmd

import org.gradle.internal.impldep.org.apache.ivy.util.MemoryUtil
import org.jetbrains.kotlin.tools.testutils.*

@Suppress("unused")
open class TestSequentialImports : ImportAndSave() {

    override fun run(args: List<String>, workingDir: String?) {
        importProject(projectPath, jdkPath, false, "_initial_import")?.let {
            // set delegation mode and save projects internally
            setDelegationMode(projectPath, it, true)
            System.gc()
            val usedMemoryFirstImport = MemoryUtil.getUsedMemory()
            importProject(projectPath, jdkPath, false, "_first_import")
            val imlFilesBefore = readStoredConfigFiles(projectPath)

            importProject(projectPath, jdkPath, false, "_second_import")
            val imlFilesAfter = readStoredConfigFiles(projectPath)
            val usedMemoryAfterAllImports = MemoryUtil.getUsedMemory()

            System.gc()
            reportStatistics("memory_increase_after_sequential_imports", ""+(usedMemoryAfterAllImports - usedMemoryFirstImport))
            if (usedMemoryAfterAllImports - usedMemoryFirstImport > 10 * 1024 * 1024) {
                printMessage("Amount of used memory increased ${usedMemoryAfterAllImports - usedMemoryFirstImport} bytes after sequential imports.", MessageStatus.ERROR)
            }

            if (imlFilesAfter != imlFilesBefore) {
                printMessage("Content of stored iml files changed after sequential reimports.")
                //TODO implement smarter comparison
                TODO("NOT IMPLEMENTED")
            }
        }
    }
}