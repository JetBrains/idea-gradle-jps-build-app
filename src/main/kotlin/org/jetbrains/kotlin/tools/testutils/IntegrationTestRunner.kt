package org.jetbrains.kotlin.tools.testutils

import com.intellij.openapi.application.ApplicationStarterBase
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import org.reflections.Reflections
import kotlin.system.exitProcess

class IntegrationTestRunner : ApplicationStarterBase("runIntegrationTest", 0) {
    private val testSuits: List<TestSuite>

    init {
        testSuits = Reflections(PACKAGE_PREFIX).getSubTypesOf(TestSuite::class.java).map { it -> it.newInstance() }
    }

    override fun isHeadless(): Boolean = true

    override fun getUsageMessage(): String =
            "Usage: runIntegrationTest <testName> <arguments> where testNAme one of:${System.lineSeparator()}" +
                    testSuits.joinToString(separator = ",", postfix = ".") { it.javaClass.simpleName }

    private fun findSuite(args: Array<out String>): List<TestSuite> {
        val subArgs = subArgs(args)
        return testSuits.filter { it.javaClass.simpleName == args[1] && it.acceptArguments(subArgs) }
    }

    private fun checkArguments(args: Array<String>): Boolean {
        if (args.size < 2) {
            return false
        }
        if (args[0] != "runIntegrationTest") return false
        val foundSuits = findSuite(args)
        if (foundSuits.size > 1) {
            System.err.println("More than one suite fits the passed arguments.")
        }
        return foundSuits.size == 1
    }

    override fun premain(args: Array<String>) {
        if (!checkArguments(args)) {
            System.err.println(usageMessage)
            exitProcess(1)
        }
    }

    override fun processExternalCommandLine(args: Array<String>, currentDirectory: String?) {
        if (!checkArguments(args)) {
            Messages.showMessageDialog(usageMessage, StringUtil.toTitleCase(commandName), Messages.getInformationIcon())
            return
        }
        try {
            processCommand(args, currentDirectory)
        } catch (e: Exception) {
            Messages.showMessageDialog(String.format("Error showing %s: %s", commandName, e.message),
                    StringUtil.toTitleCase(commandName),
                    Messages.getErrorIcon())
        } finally {
            saveAll()
        }
    }

    override fun processCommand(args: Array<out String>, currentDirectory: String?) {
        // TODO support comma-separated list of arguments?
        try {
            val suite = findSuite(args).first()
            suite.setUp()
            try {
                suite.run(subArgs(args), currentDirectory)
            } finally {
                suite.tearDown()
            }
        } catch (_: IllegalUserArgumentException) {
            System.err.println("Invalid command line arguments")
            System.err.println(usageMessage)
            exitProcess(2)
        } catch (e: Exception) {
            System.err.println("Failed to run ${args[1]}")
            e.printStackTrace()
            exitProcess(1)
        }
    }

    private fun subArgs(args: Array<out String>): List<String> = if (args.size == 2) emptyList<String>() else args.toList().subList(2, args.size - 1)

}

class IllegalUserArgumentException : RuntimeException()