package org.jetbrains.kotlin.tools.cachesuploader

import com.google.common.hash.Hashing
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import java.io.File
import java.nio.charset.StandardCharsets

class SourcesStateProcessor(val sourceState: Map<String, Map<String, Map<String, String>>>,  private val projectPath: String) {
    //val SOURCES_STATE_TYPE = TypeToken<Map<String, Map<String, BuildTargetState>>>().getType()

    val SOURCES_STATE_FILE_NAME = "target_sources_state.json"
    val IDENTIFIER = "\$BUILD_DIR\$"

    val PRODUCTION_TYPES = listOf("java-production", "resources-production", "gradle-resources-production")
    val TEST_TYPES = listOf("java-test", "resources-test", "gradle-resources-test")
    val ARTIFACT_TYPE = listOf("artifact")

    val PRODUCTION = "production"
    val TEST = "test"

    val gson = Gson()

    fun getAllCompilationOutputs(): List<CompilationOutput> {
        return getProductionCompilationOutputs(sourceState) + getTestsCompilationOutputs(sourceState)
    }

//    fun parseSourcesStateFile(json: String = FileUtil.loadFile(sourceStateFile, CharsetToolkit.UTF8)): Map<String, Map<String, BuildTargetState>> {
//        return gson.fromJson(json, SOURCES_STATE_TYPE)
//    }
//
//    fun getSourceStateFile(): File {
//        return File(context.compilationData.dataStorageRoot, SOURCES_STATE_FILE_NAME)
//    }

    fun getProductionCompilationOutputs(currentSourcesState: Map<String, Map<String, Map<String, String>>>): List<CompilationOutput> {
        return getCompilationOutputs(PRODUCTION, PRODUCTION_TYPES, currentSourcesState)
    }

    private fun getTestsCompilationOutputs(currentSourcesState: Map<String, Map<String, Map<String, String>>>): List<CompilationOutput> {
        return getCompilationOutputs(TEST, TEST_TYPES, currentSourcesState)
    }

    private fun getCompilationOutputs(
            prefix: String,
            uploadParams: List<String>,
            currentSourcesState: Map<String, Map<String, Map<String, String>>> ): List<CompilationOutput> {
        val root = File(projectPath, "out")

        val firstParamMap = currentSourcesState[uploadParams[0]]
        val secondParamMap = currentSourcesState[uploadParams[1]]
        val thirdParamMap = currentSourcesState[uploadParams[2]]

        val firstParamKeys = HashSet(firstParamMap!!.keys)
        val secondParamKeys = HashSet(secondParamMap!!.keys)
        val thirdParamKeys = HashSet(thirdParamMap!!.keys)

        val intersection = firstParamKeys.intersect(secondParamKeys).intersect(thirdParamKeys)

        val compilationOutputs = ArrayList<CompilationOutput>()

        intersection.forEach { buildTargetName ->
            val firstParamState = firstParamMap[buildTargetName]
            val secondParamState = secondParamMap[buildTargetName]
            val thirdParamState = thirdParamMap[buildTargetName]

            val outputPath = (firstParamState!!["relativePath"] ?: error("Can't get relative path")).replace(IDENTIFIER, root.absolutePath)

            val hash = calculateStringHash(firstParamState["hash"] + secondParamState!!["hash"] + thirdParamState!!["hash"])
            compilationOutputs.add(CompilationOutput(buildTargetName, prefix, hash, outputPath))
        }

        firstParamKeys.removeAll(intersection)
        firstParamKeys.forEach { buildTargetName ->
            val firstParamState = firstParamMap.get(buildTargetName)
            val outputPath = firstParamState!!["relativePath"]!!.replace(IDENTIFIER, root.getAbsolutePath())

            compilationOutputs.add(CompilationOutput(buildTargetName, uploadParams[0], firstParamState["hash"] ?: error("Can't get hash"), outputPath))
        }

        secondParamKeys.removeAll(intersection)
        secondParamKeys.forEach { buildTargetName ->
            val secondParamState = secondParamMap.get(buildTargetName)
            val outputPath = secondParamState!!["relativePath"]!!.replace(IDENTIFIER, root.getAbsolutePath())

            compilationOutputs.add(CompilationOutput(buildTargetName, uploadParams[1], secondParamState["hash"] ?: error("Can't get hash"), outputPath))
        }

        thirdParamKeys.removeAll(intersection)
        thirdParamKeys.forEach { buildTargetName ->
            val thirdParamState = thirdParamMap.get(buildTargetName)
            val outputPath = thirdParamState!!["relativePath"]!!.replace(IDENTIFIER, root.getAbsolutePath())

            compilationOutputs.add(CompilationOutput(buildTargetName, uploadParams[2], thirdParamState["hash"] ?: error("Can't get hash"), outputPath))
        }

        return compilationOutputs
    }

    fun calculateStringHash(content: String): String {
        val hasher = Hashing.murmur3_128().newHasher()
        return hasher.putString(content, StandardCharsets.UTF_8).hash().toString()
    }
}