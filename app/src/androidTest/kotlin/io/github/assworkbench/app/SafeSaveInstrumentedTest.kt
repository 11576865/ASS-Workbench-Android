package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssRoundTripVerifier
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.SubtitleProject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeSaveInstrumentedTest {
    private lateinit var application: Application
    private lateinit var store: RecoveryStore
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        SafeSaveTestProvider.reset()
        store = RecoveryStore(application)
        store.clear()
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(1_000),
                    end = SubTime(3_000),
                    text = "{\\bord2}Safe save",
                )
            )
        )
        store.write(
            SubtitleProject(title = "safe-save.ass"),
            document,
            AssTextEncoding.UTF8,
        )
        viewModel = EditorViewModel(application)
        viewModel.restoreRecovery()
    }

    @After
    fun tearDown() {
        store.clear()
        SafeSaveTestProvider.reset()
    }

    @Test
    fun verifiedSaveClearsDirtyOnlyAfterReadBack() {
        viewModel.updateEventText(1L, "{\\bord3}Saved safely")
        val uri = providerUri("verified.ass")
        seedTarget(uri, ByteArray(0))

        assertTrue(viewModel.saveTo(uri))
        assertFalse(viewModel.state.value.dirty)
        assertFalse(store.exists())
        assertTrue(viewModel.state.value.status.contains("回读验证"))

        val bytes = application.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val decoded = AssTextDecoder.decode(bytes)
        val saved = AssCodec.parse(decoded.text)
        assertEquals("{\\bord3}Saved safely", saved.events.single().text)
        assertTrue(AssRoundTripVerifier.verify(viewModel.state.value.document, decoded.text).equivalent)
    }

    @Test
    fun corruptReadBackRollsBackPreviousTargetAndKeepsRecovery() {
        viewModel.updateEventText(1L, "New unsafe candidate")
        val uri = providerUri("rollback.ass", corruptSecondRead = true)

        val previousDocument = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(0),
                    end = SubTime(1_000),
                    text = "LAST KNOWN GOOD",
                )
            )
        )
        val previous = AssTextEncoding.UTF8.encode(AssCodec.write(previousDocument))
        seedTarget(uri, previous)

        assertFalse(viewModel.saveTo(uri))
        assertTrue(viewModel.state.value.dirty)
        assertTrue(store.exists())
        assertTrue(viewModel.state.value.status.contains("已恢复保存前目标内容"))

        val restored = application.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertTrue(previous.contentEquals(restored))
    }

    private fun providerUri(name: String, corruptSecondRead: Boolean = false): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(BuildConfig.APPLICATION_ID + ".safesave")
            .appendPath(name)
            .apply { if (corruptSecondRead) appendQueryParameter("corruptSecondRead", "1") }
            .build()

    private fun seedTarget(uri: Uri, bytes: ByteArray) {
        application.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
    }
}