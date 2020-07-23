package org.jetbrains.kotlin.tools.cachesuploader

class CompilationOutput(val name: String, val type: String, val hash: String, val path: String) {
    val sourcePath: String = "$type/$name/$hash"
}