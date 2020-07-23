package org.jetbrains.kotlin.tools.cachesuploader

import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.util.EntityUtils
import org.jetbrains.kotlin.tools.testutils.MessageStatus
import org.jetbrains.kotlin.tools.testutils.printMessage
import java.io.Closeable
import java.io.File

open class CompilationPartsUploader(val myServerUrl: String): Closeable {
    val myHttpClient: CloseableHttpClient = HttpClientBuilder.create()
            .setUserAgent("Parts Uploader")
            .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
            .setMaxConnTotal(20)
            .setMaxConnPerRoute(10)
            .build()

    override fun close() {
        StreamUtil.closeStream(myHttpClient)
    }

    fun upload (path: String, file: File, sendHead: Boolean): Boolean {
        printMessage("Preparing to upload $file to $myServerUrl", MessageStatus.NORMAL)

        if (!file.exists()) throw Exception("The file " + file.path + " does not exist")

        if (sendHead) {
            val code = doHead(path)
            if (code == 200) {
                printMessage("File '$path' already exist on server, nothing to upload", MessageStatus.NORMAL)
                return false
            }
            if (code != 404) {
                error("HEAD $path responded with unexpected $code")
            }
        }
        val response = doPut(path, file).toString()
        if (StringUtil.isEmptyOrSpaces(response)) {
            printMessage("Performed '$path' upload.", MessageStatus.NORMAL)
        } else {
            printMessage("Performed '$path' upload. Server answered: $response", MessageStatus.NORMAL)
        }
        return true
    }

    fun doHead(path: String): Int {
        var response: CloseableHttpResponse? = null
        try {
            val url: String = myServerUrl + StringUtil.trimStart(path, "/")
            printMessage("HEAD $url", MessageStatus.NORMAL)

            val request = HttpGet(url)
            response = myHttpClient.execute(request)
            return response.statusLine.statusCode
        }
        catch (e: Exception) {
            throw Exception("Failed to HEAD $path: ", e)
        }
        finally {
            StreamUtil.closeStream(response)
        }
    }

    private fun doPut(path: String, file: File): Int {
        var response: CloseableHttpResponse? = null
        try {
            val url: String = myServerUrl + StringUtil.trimStart(path, "/")
            printMessage("PUT $url", MessageStatus.NORMAL)

            val request = HttpPut(url)
            request.entity = FileEntity(file, ContentType.APPLICATION_OCTET_STREAM)

            response = myHttpClient.execute(request)

            EntityUtils.consume(response.getEntity())
            return response.statusLine.statusCode
        }
        catch (e: Exception) {
            throw Exception("Failed to PUT file to $path: ", e)
        }
        finally {
            StreamUtil.closeStream(response)
        }
    }
}
