package org.jetbrains.kotlin.tools.cachesuploader

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.ZipUtil
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils
import org.jetbrains.kotlin.tools.testutils.MessageStatus
import org.jetbrains.kotlin.tools.testutils.printMessage
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipOutputStream

class CompilationOutputsUploader(private val remoteCacheUrl: String, project: Project?) {
    private val projectPath: String = project?.basePath!!
    private val projectFileStorage = ProjectFileStorage(project)
    private val kotlinRepoUrl = "git@github.com:JetBrains/kotlin.git"
    private val commitHistoryJsonFileName = "commit_history.json"

    fun upload() {
        val uploader = JpsCompilationPartsUploader(remoteCacheUrl)

        val executorThreadsCount = Runtime.getRuntime().availableProcessors()
        printMessage("$executorThreadsCount threads will be used for upload", MessageStatus.NORMAL)
        val executor = NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)
        executor.prestartAllCoreThreads()

        try {
            val commitHash = getCommitHash() ?: throw Exception("There is no file with git last commit hash")

            uploadCachesWithMetadata(uploader, executor)
            uploadCompilationOutputs(uploader, executor)
            uploadDist(uploader, executor)
            uploadBuildSrc(uploader, executor)

            executor.waitForAllComplete()

            updateCommitHistory(uploader, commitHash)
        } catch (e: Exception){
            println(e)
        }
        finally {
            executor.close()
            StreamUtil.closeStream(uploader)
        }
    }

    private fun uploadCachesWithMetadata(uploader: JpsCompilationPartsUploader,
                                         executor: NamedThreadPoolExecutor) {
        executor.submit {
            printMessage("Upload jps caches")
            // Upload jps caches
            var sourcePath = "caches/${getCommitHash()}"
            if (uploader.isExist(sourcePath)) return@submit
            val zipFile = File(projectPath, "cachesZipped")
            zipBinaryData(zipFile, projectFileStorage.getCompileServerFolder())
            uploader.upload(sourcePath, zipFile)
            FileUtil.delete(zipFile)
            printMessage("Caches uploaded")

            // Upload compilation metadata
            printMessage("Upload compilation metadata")
            sourcePath = "metadata/${getCommitHash()}"
            if (uploader.isExist(sourcePath)) return@submit
            uploader.upload(sourcePath, projectFileStorage.getTargetSourcesState())
            printMessage("Metadata uploaded")
            return@submit
        }
    }

    private fun uploadDist(uploader: JpsCompilationPartsUploader,
                           executor: NamedThreadPoolExecutor) {
        executor.submit {
            printMessage("Uploading Dist...")
            val sourcePath = "dist/${getCommitHash()}"
            if (uploader.isExist(sourcePath)) return@submit
            val outputFolder = File(projectPath, "dist")
            val zipFile = File(projectPath, "distZipped")
            zipBinaryData(zipFile, outputFolder)
            uploader.upload(sourcePath, zipFile)
            FileUtil.delete(zipFile)
            printMessage("Dist uploaded")
        }
    }

    private fun uploadBuildSrc(uploader: JpsCompilationPartsUploader,
                           executor: NamedThreadPoolExecutor) {
        executor.submit {
            printMessage("Uploading buildSrc...")
            val sourcePath = "buildSrc/${getCommitHash()}"
            if (uploader.isExist(sourcePath)) return@submit
            val outputFolder = File(projectPath, "buildSrc/build/classes/java")
            val zipFile = File(projectPath, "buildSrcZipped")
            zipBinaryData(zipFile, outputFolder)
            uploader.upload(sourcePath, zipFile)
            FileUtil.delete(zipFile)
            printMessage("Dist uploaded")
        }
    }

    private fun uploadCompilationOutputs(uploader: JpsCompilationPartsUploader,
                                         executor: NamedThreadPoolExecutor) {
        executor.submit {
            printMessage("Compilation outputs uploading")
            val sourcePath = "out/${getCommitHash()}"
            if (uploader.isExist(sourcePath)) return@submit
            val outputFolder = File(projectPath, "out")
            val zipFile = File(projectPath, "outZipped")
            zipBinaryData(zipFile, outputFolder)
            uploader.upload(sourcePath, zipFile)
            FileUtil.delete(zipFile)
            printMessage("Compilation outputs uploaded")
        }
    }

    private fun zipBinaryData(zipFile: File, dir: File) {
        try {
            val zos = ZipOutputStream(FileOutputStream(zipFile))
            ZipUtil.addDirToZipRecursively(zos, null, dir, "", null, null)
            zos.close()
        } catch (e:Exception){
            println(e.message)
        }
    }

    private fun updateCommitHistory(uploader: JpsCompilationPartsUploader, commitHash: String) {
        if (uploader.isExist(commitHistoryJsonFileName)) {
            val json = uploader.getAsString(commitHistoryJsonFileName)
            val objectMapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val commitHistory = objectMapper.readValue(json, object : TypeReference<MutableMap<String?, Set<String?>?>?>() {})
            commitHistory?.set(kotlinRepoUrl, commitHistory[kotlinRepoUrl]?.union(listOf(commitHash)))
            val commitHistoryLocalFile = File(projectPath, commitHistoryJsonFileName)
            commitHistoryLocalFile.writeText(Gson().toJson(commitHistory))
            uploader.upload(commitHistoryJsonFileName, commitHistoryLocalFile)
        } else {
            val commitHistory = mutableMapOf<String?, Set<String?>?>()
            commitHistory[kotlinRepoUrl] = setOf(commitHash)
            val commitHistoryLocalFile = File(projectPath, commitHistoryJsonFileName)
            commitHistoryLocalFile.writeText(Gson().toJson(commitHistory))
            uploader.upload(commitHistoryJsonFileName, commitHistoryLocalFile)
        }
    }

    private fun getCommitHash(): String? {
        File(projectPath, "git.branch").also {
            return if(it.exists()) {
                it.readText()
            } else {
                null
            }
        }
    }

    class JpsCompilationPartsUploader(serverUrl: String): CompilationPartsUploader(serverUrl) {
        fun isExist(path: String): Boolean {
            val code: Int = doHead(path)
            if (code == 200) {
                printMessage("File '$path' already exist on server, nothing to upload", MessageStatus.NORMAL)
                return true
            }
            if (code != 404) {
                error("HEAD $path responded with unexpected $code")
            }
            return false
        }

        fun getAsString(path: String): String {
            var response: CloseableHttpResponse? = null
            try {
                val url: String = myServerUrl + StringUtil.trimStart(path, "/")
                printMessage("GET $url", MessageStatus.NORMAL)

                val request = HttpGet(url)
                response = myHttpClient.execute(request)

                return EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_OCTET_STREAM.charset)
            }
            catch (e: Exception) {
                throw Exception("Failed to GET $path: ", e)
            }
            finally {
                StreamUtil.closeStream(response)
            }
        }

        fun upload(path: String, file: File): Boolean {
            return super.upload(path, file, false)
        }
    }
}