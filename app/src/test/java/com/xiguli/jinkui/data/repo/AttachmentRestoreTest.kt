package com.xiguli.jinkui.data.repo

import com.xiguli.jinkui.core.crypto.FieldCipher
import com.xiguli.jinkui.data.model.Attachment
import java.io.IOException
import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AttachmentRestoreTest {
    private fun attachment(id: String) = Attachment(id, "synthetic.txt", "text/plain", 3, 123L)
    private fun ids(vararg values: String): () -> String = values.iterator().let { iterator -> { iterator.next() } }
    private val old = "old synthetic data".toByteArray()
    private val fresh = "new synthetic data".toByteArray()

    @Test fun failedCommitDoesNotOverwriteSameIdOriginal() {
        val directory = Files.createTempDirectory("jinkui-restore-").toFile()
        try {
            val original = File(directory, "shared.bin").apply { writeBytes(old) }
            val failure = IOException("synthetic database failure")
            try {
                AttachmentRestore.run(
                    listOf("shared"), listOf(attachment("shared")), ids("staged"),
                    write = { _, target -> File(directory, "${target.id}.bin").writeBytes(fresh) },
                    commit = { throw failure },
                    delete = { File(directory, "$it.bin").delete(); Unit },
                )
                fail("Expected failure")
            } catch (e: IOException) { assertSame(failure, e) }
            assertArrayEquals(old, original.readBytes())
            assertEquals(listOf("shared.bin"), directory.listFiles()!!.map { it.name })
        } finally { directory.deleteRecursively() }
    }

    @Test fun successfulCommitSeesOldFilesAndPublishesNewIdsWithMatchingAad() {
        val cipher = FieldCipher(ByteArray(32) { (it + 1).toByte() })
        val files = mutableMapOf("shared" to cipher.encryptBytes(old, "attachment:shared"))
        val events = mutableListOf<String>()
        val incoming = attachment("shared")
        AttachmentRestore.run(
            listOf("shared"), listOf(incoming), ids("staged"),
            write = { source, target ->
                assertEquals("shared", source.id)
                files[target.id] = cipher.encryptBytes(fresh, "attachment:${target.id}")
                events += "write"
            },
            commit = { staged ->
                assertArrayEquals(old, cipher.decryptBytes(files.getValue("shared"), "attachment:shared"))
                assertEquals(listOf(incoming.copy(id = "staged")), staged)
                assertArrayEquals(fresh, cipher.decryptBytes(files.getValue(staged.single().id), "attachment:staged"))
                events += "commit"
            },
            delete = { files.remove(it); events += "delete:$it" },
        )
        assertEquals(setOf("staged"), files.keys)
        assertEquals(listOf("write", "commit", "delete:shared"), events)
        cipher.wipe()
    }

    @Test fun secondWriteFailureRemovesPartlyWrittenFileAndEarlierStagingOnly() {
        val files = mutableMapOf("old" to old)
        val failure = IOException("synthetic disk failure")
        var committed = false
        try {
            AttachmentRestore.run(
                listOf("old"), listOf(attachment("a"), attachment("b")), ids("new-a", "new-b"),
                write = { _, target ->
                    files[target.id] = fresh
                    if (target.id == "new-b") throw failure
                },
                commit = { committed = true },
                delete = { files.remove(it); Unit },
            )
            fail("Expected failure")
        } catch (e: IOException) { assertSame(failure, e) }
        assertFalse(committed)
        assertEquals(setOf("old"), files.keys)
        assertArrayEquals(old, files.getValue("old"))
    }

    @Test fun lockDuringWritePreservesOriginalAndNeverCommits() {
        val cipher = FieldCipher(ByteArray(32) { 4 })
        val files = mutableMapOf("shared" to old)
        var committed = false
        try {
            AttachmentRestore.run(
                listOf("shared"), listOf(attachment("shared")), ids("staged"),
                write = { _, target -> cipher.wipe(); files[target.id] = cipher.encryptBytes(fresh, "attachment:${target.id}") },
                commit = { committed = true },
                delete = { files.remove(it); Unit },
            )
            fail("Expected lock failure")
        } catch (_: IllegalStateException) { }
        assertFalse(committed)
        assertEquals(setOf("shared"), files.keys)
        assertArrayEquals(old, files.getValue("shared"))
    }

    @Test fun attachmentIdFromOtherCardIsNeverWrittenOrDeleted() {
        val files = mutableMapOf("other-card" to old)
        AttachmentRestore.run(
            emptyList(), listOf(attachment("other-card")), ids("staged"),
            write = { _, target -> files[target.id] = fresh },
            commit = { assertEquals("staged", it.single().id) },
            delete = { files.remove(it); Unit },
        )
        assertArrayEquals(old, files.getValue("other-card"))
        assertArrayEquals(fresh, files.getValue("staged"))
    }

    @Test fun noAttachmentsStillCommitsBeforeRemovingOldFiles() {
        val events = mutableListOf<String>()
        AttachmentRestore.run(
            listOf("old"), emptyList(),
            write = { _, _ -> fail("No writes expected") },
            commit = { assertTrue(it.isEmpty()); events += "commit" },
            delete = { events += "delete:$it" },
        )
        assertEquals(listOf("commit", "delete:old"), events)
    }

    @Test fun collidingGeneratedIdFailsBeforeAnyWriteOrCommit() {
        for (generated in listOf("old", "incoming", "other-card")) {
            var touched = false
            try {
                AttachmentRestore.run(
                    listOf("old"), listOf(attachment("incoming")), ids(generated),
                    reservedIds = listOf("other-card"),
                    write = { _, _ -> touched = true }, commit = { touched = true }, delete = { touched = true },
                )
                fail("Expected ID rejection")
            } catch (_: IllegalArgumentException) { }
            assertFalse(touched)
        }
    }

    @Test fun duplicateGeneratedIdsFailBeforeAnyWrite() {
        var touched = false
        try {
            AttachmentRestore.run(
                emptyList(), listOf(attachment("a"), attachment("b")), ids("same", "same"),
                write = { _, _ -> touched = true }, commit = { touched = true }, delete = { touched = true },
            )
            fail("Expected ID rejection")
        } catch (_: IllegalArgumentException) { }
        assertFalse(touched)
    }
}
