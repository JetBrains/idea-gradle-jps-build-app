package org.jetbrains.kotlin.tools.testutils

import com.intellij.ide.CliResult
import com.intellij.openapi.application.ApplicationStarterBase
import org.reflections.Reflections
import java.util.concurrent.Future
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

    override fun processExternalCommandLineAsync(args: Array<String>, currentDirectory: String?): Future<out CliResult> {
        if (!checkArguments(args)) {
            return CliResult.error(1, usageMessage)
        }
        return try {
            processCommand(args, currentDirectory)
        } catch (e: Exception) {
            CliResult.error(2, String.format("Error showing %s: %s", commandName, e.message))
        } finally {
            saveAll()
        }
    }

    override fun processCommand(args: Array<out String>, currentDirectory: String?): Future<out CliResult> {
        // TODO support comma-separated list of arguments?
        return try {
            val suite = findSuite(args).first()
            suite.setUp()
            try {
                suite.run(subArgs(args), currentDirectory)
            } finally {
                suite.tearDown()
            }
            CliResult.ok()
        } catch (_: IllegalUserArgumentException) {
            CliResult.error(2, "Invalid command line arguments")
        } catch (e: Exception) {
            e.printStackTrace()
            CliResult.error(3,"Failed to run ${args[1]}")
        }
    }

    private fun subArgs(args: Array<out String>): List<String> = if (args.size == 2) emptyList<String>() else args.toList().subList(2, args.size - 1)

}

class IllegalUserArgumentException : RuntimeException()