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

enum class ProgressType(val message: String) {
    START("progressStart"), FINISH("progressFinish"), MESSAGE("progressMessage")
}

fun printProgress(message: String, type: ProgressType) {
    printMessage(message, "##teamcity[${type.message} '${escapeTcCharacters(message)}']")
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

fun startTest(name: String) {
    printMessage("Start test $name", "#teamcity[testStarted name='${escapeTcCharacters(name)}']")
}

fun finishTest(name: String, failureMessage: String? = null, duration: Long? = null) {
    if (failureMessage != null) {
        printMessage("Test failed: $failureMessage", "##teamcity[testFailed name='${escapeTcCharacters(name)}' message='${escapeTcCharacters(failureMessage)}']")
    }
    val durationMsg = if (duration == null) "" else "duration='$duration'"
    printMessage("Finish test $name", "#teamcity[testFinished name='${escapeTcCharacters(name)}' $durationMsg]")
}

//TODO maybe use ##teamcity[buildProblem description='<description>' identity='<identity>']
//TODO may be helpful for running in parallel  ##teamcity[<messageName> flowId='flowId' ...]
