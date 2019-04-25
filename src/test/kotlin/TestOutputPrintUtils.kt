package org.jetbrains.kotlin.tools.gradleimportcmd

import org.junit.Assert
import org.junit.Test

class TestOutputPrintUtils {

    @Test
    fun testEscape() {
        Assert.assertEquals("Foo|'s |[pass|]|r|nnew line || ||", escapeTcCharacters("Foo's [pass]\r\nnew line | |"))
    }
}