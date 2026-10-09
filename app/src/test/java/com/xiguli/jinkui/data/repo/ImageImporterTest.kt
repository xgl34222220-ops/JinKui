package com.xiguli.jinkui.data.repo

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageImporterTest {
    @Test
    fun readsWithinLimit() {
        val data = ByteArray(200_000) { it.toByte() }
        assertArrayEquals(data, ImageImporter.readLimited(ByteArrayInputStream(data), 200_000))
    }

    @Test
    fun stopsAsSoonAsLimitIsExceeded() {
        val data = ByteArray(200_001)
        assertThrows(IOException::class.java) { ImageImporter.readLimited(ByteArrayInputStream(data), 200_000) }
    }
}
