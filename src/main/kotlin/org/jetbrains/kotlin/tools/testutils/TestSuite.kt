package org.jetbrains.kotlin.tools.testutils

/**
 * All children should be located in subpackage in order to optimize search performance
 */
const val PACKAGE_PREFIX = "org.jetbrains.kotlin.tools"

abstract class TestSuite {
    private var memoryWatcher: MemoryWatcher? = null

    open fun acceptArguments(args: List<String>): Boolean = true

    open fun setUp() {
        memoryWatcher = MemoryWatcher(true)
    }

    open fun tearDown() {
        memoryWatcher?.let {
            reportStatistics("maximal.memory.used", "${it.maxMemory}")
            it.dispose()
        }
    }

    abstract fun run(args: List<String>, workingDir: String?)
}