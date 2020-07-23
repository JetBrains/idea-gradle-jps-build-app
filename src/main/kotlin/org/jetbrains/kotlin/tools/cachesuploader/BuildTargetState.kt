package org.jetbrains.kotlin.tools.cachesuploader

class BuildTargetState(private val hash: String, private val relativePath: String) {

    fun getHash(): String {
        return hash
    }

    fun getRelativePath(): String? {
        return relativePath
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val state: BuildTargetState = o as BuildTargetState
        if (hash != state.hash) return false
        return relativePath == state.relativePath
    }

    override fun hashCode(): Int {
        var result = hash?.hashCode() ?: 0
        result = 31 * result + (relativePath?.hashCode() ?: 0)
        return result
    }
}