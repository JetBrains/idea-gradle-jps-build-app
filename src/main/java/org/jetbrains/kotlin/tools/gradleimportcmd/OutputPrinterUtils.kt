package org.jetbrains.kotlin.tools.gradleimportcmd

import java.io.ByteArrayOutputStream
import java.io.PrintWriter

val runOnTeamcity = ! ((System.getProperty("disableTeamcityInteraction") ?: "false").toBoolean())

fun escapeTcCharacters(message: String) = message
        .replace("|", "||")
        .replace("\n", "|n")
        .replace("\r", "|r")
        .replace("'", "|'")
        .replace("[", "|[")
        .replace("]", "|]")

private fun printMessage(rawMessage: String, tcMessage: String) {
    if (runOnTeamcity) {
        println(tcMessage)
    } else {
        println(rawMessage)
    }
}

fun printProgress(message: String) {
    printMessage(message, "##teamcity[progressMessage '${escapeTcCharacters(message)}']")
}

enum class MessageStatus {
    NORMAL, WARNING, FAILURE, ERROR
}

fun printMessage(message: String, status: MessageStatus? = null, errorDetails: String? = null) {
    // Not sure that we should print control symbols if the message is simple
    if (status == null && errorDetails == null) {
        println(message)
        return
    }
    val statusStr = if (status == null) "" else "status='$status'"
    val errorDetailsStr = if (errorDetails == null) "" else "errorDetails='${escapeTcCharacters(errorDetails)}'"
    printMessage(message, "##teamcity[message text='${escapeTcCharacters(message)}' $errorDetailsStr $statusStr]")
}

fun printException(e: Throwable) {
    if (runOnTeamcity) {
        e.printStackTrace()
    } else {
        val stream = ByteArrayOutputStream()
        PrintWriter(stream).use {
            e.printStackTrace(it)
        }
        printMessage(e.message?:"", MessageStatus.ERROR, stream.toString())
    }
}

enum class OperationType(val startName: String, val finishName: String) {
    TEST("testStarted", "testFinished"), COMPILATION("compilationStarted", "compilationFinished")
}

fun startOperation(operation: OperationType, name: String) {
    printMessage("Start test $name", "##teamcity[${operation.startName} name='${escapeTcCharacters(name)}']")
}

fun finishOperation(operation: OperationType, name: String, failureMessage: String? = null, duration: Long? = null) {
    if (failureMessage != null) {
        reportTestError(name, failureMessage)
    }
    val durationMsg = if (duration == null) "" else "duration='$duration'"
    printMessage("Finish test $name", "##teamcity[${operation.startName} name='${escapeTcCharacters(name)}' $durationMsg]")
}

fun reportTestError(name: String, failureMessage: String) {
    printMessage("Test failed: $failureMessage", "##teamcity[testFailed name='${escapeTcCharacters(name)}' message='${escapeTcCharacters(failureMessage)}']")
}

fun reportStatistics(key: String, value: String) {
    printMessage("Reported statistics $key=$value", "##teamcity[buildStatisticValue key='${escapeTcCharacters(key)}' value='${escapeTcCharacters(value)}']")
}

//TODO maybe use ##teamcity[buildProblem description='<description>' identity='<identity>']
//TODO may be helpful for running in parallel  ##teamcity[<messageName> flowId='flowId' ...]
