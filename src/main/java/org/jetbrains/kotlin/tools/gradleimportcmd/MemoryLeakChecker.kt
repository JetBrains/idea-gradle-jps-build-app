package org.jetbrains.kotlin.tools.gradleimportcmd

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.jetbrains.kotlin.utils.doNothing
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet


class MemoryLeakChecker(externalProject: DataNode<ProjectData>, val errorConsumer : (String) -> Unit) {
    val referencingObjects = HashMap<Any, Any>()
    val leakedObjects = ArrayList<Any>()
    var errorsCount = 0
        private set

    private val typesToSkip = setOf<String>("java.lang.String", "java.lang.Integer", "java.lang.Character", "java.lang.Byte", "java.lang.Long")

    private fun shouldBeProcessed(toProcess: Any?, processed: Set<Any>): Boolean {
        return toProcess != null && !typesToSkip.contains(toProcess.javaClass.name) && !processed.contains(toProcess)
    }

    private fun isLeakedObject(o: Any) = o is Proxy

    private fun reportLeakedObject(o: Any) {
        leakedObjects.add(o)
        var o : Any? = o
        val errMessage = StringBuilder()
        errMessage.append(String.format("Object [%s] seems to be a referenced gradle tooling api object. (it may lead to memory leaks during import) Referencing path: ", o))
        while (o != null) {
            errMessage.append(String.format("[%s] type: %s <-\r\n", o, o.javaClass.toString()))
            o = referencingObjects[o]
        }
        errorConsumer(errMessage.toString())
        errorsCount++
    }

    private fun saveToProcessIfRequired(processed: Set<Any>, toProcess: Queue<Any>, referrers: MutableMap<Any, Any>, referringObject: Any, o: Any) {
        if (shouldBeProcessed(o, processed)) {
            toProcess.add(o)
            referrers[o] = referringObject
        }
    }

    init {
        val processed = HashSet<Any>()
        val toProcess = LinkedList<Any>()
        toProcess.add(externalProject)

        val fieldsWithModifiedAccessibility = HashSet<Field>()
        try {
            while (!toProcess.isEmpty()) {
                val nextObject = toProcess.poll()
                processed.add(nextObject)
                try {
                    if (isLeakedObject(nextObject)) {
                        reportLeakedObject(nextObject)
                    } else {
                        nextObject.javaClass.declaredFields.forEach {
                            if (!it.isAccessible) {
                                fieldsWithModifiedAccessibility.add(it)
                                it.isAccessible = true
                            }
                            it.get(nextObject)?.let {fieldValue ->
                                when {
                                    fieldValue is Collection<*> -> for (o in (fieldValue as Collection<*>?)!!) {
                                        if (o != null) saveToProcessIfRequired(processed, toProcess, referencingObjects, nextObject, o)
                                    }
                                    fieldValue.javaClass.isArray -> for (i in 0 until Array.getLength(fieldValue)) {
                                        Array.get(fieldValue, i)?.let { arrayObject ->
                                            saveToProcessIfRequired(processed, toProcess, referencingObjects, nextObject, arrayObject)
                                        }

                                    }
                                    else -> saveToProcessIfRequired(processed, toProcess, referencingObjects, nextObject, fieldValue)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    errorConsumer("Could not process object [$nextObject] with type [${nextObject.javaClass}]. ${e.javaClass} Error message: [${e.message}]")
                    errorsCount++
                }
            }
        } finally {
            fieldsWithModifiedAccessibility.forEach { it.isAccessible = false }
        }
    }
}
