package org.jetbrains.kotlin.tools.cachesuploader

import groovy.lang.Closure
import org.jetbrains.kotlin.tools.testutils.MessageStatus
import org.jetbrains.kotlin.tools.testutils.printMessage
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class NamedThreadPoolExecutor(threadNamePrefix: String, maximumPoolSize: Int) : ThreadPoolExecutor(maximumPoolSize, maximumPoolSize, 1, TimeUnit.MINUTES, LinkedBlockingDeque<Runnable>(2048)) {
    val counter = AtomicInteger()
    private val futures = LinkedList<Future<*>>()
    private val errors = ConcurrentLinkedDeque<Throwable>()

    init {
        threadFactory = ThreadFactory { runnable ->
            val thread = Thread(runnable, threadNamePrefix + ' ' + counter.incrementAndGet())
            thread.priority = Thread.NORM_PRIORITY - 1
            thread
        }
    }

    fun close() {
        shutdown()
        awaitTermination(10, TimeUnit.SECONDS)
        shutdownNow()
    }

    fun submit(block: () -> Unit) {
        futures.add(this.submit(Runnable {
            try {
                block()
            } catch (e: Throwable) {
                errors.add(e)
            }
        }))
    }

    fun waitForAllComplete() {
        while (!futures.isEmpty()) {
            val iterator = futures.listIterator()
            while (iterator.hasNext()) {
                val f: Future<*> = iterator.next()
                if (f.isDone) {
                    iterator.remove()
                }
            }

            val size = futures.size
            if (size == 0) break
            printMessage("$size task${if (size != 1) "s" else ""} left...", MessageStatus.NORMAL)
            if (size < 100) {
                futures.last().get()
            }
            else {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1))
            }
        }
    }
}