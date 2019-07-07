package org.jetbrains.kotlin.tools.testutils

import com.intellij.openapi.util.LowMemoryWatcher
import java.lang.Long.max
import kotlin.system.exitProcess

class MemoryWatcher(val exitOnLowMemory: Boolean) {
    // add low memory notifications
    var afterGcLowMemoryNotifier: LowMemoryWatcher? = null
    var beforeGcLowMemoryNotifier: LowMemoryWatcher? = null
    var maxMemory: Long = 0

    init {
        var afterGcLowMemoryNotifier = LowMemoryWatcher.register({
            printMemory(true)
            if (exitOnLowMemory) {
                exitProcess(3)
            }
        }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)
        var beforeGcLowMemoryNotifier = LowMemoryWatcher.register({
            val runtime = Runtime.getRuntime()
            maxMemory = max(maxMemory, runtime.totalMemory() - runtime.freeMemory())
            printMemory(false)
        }, LowMemoryWatcher.LowMemoryWatcherType.ALWAYS)
    }

    fun dispose() {
        afterGcLowMemoryNotifier = null
        beforeGcLowMemoryNotifier = null
    }
}