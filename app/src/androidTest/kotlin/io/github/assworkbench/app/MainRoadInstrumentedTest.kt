package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.domain.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainRoadInstrumentedTest {
    private lateinit var application: Application
    private lateinit var store: RecoveryStore

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        SafeSaveTestProvider.reset()
        store = RecoveryStore(application)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
        SafeSaveTestProvider.reset()
    }

    @Test
    fun canonicalMainRoadEditsUndoRedoSaveAndReopen() {
        val source = AssDocument(
            styles = listOf(AssStyle(name = "Default", fontName = "Arial", fontSize = 48.0)),
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(1_000),
                    end = SubTime(3_000),
                    text = "Hello",
                ),
                AssEvent(
                    id = 2L,
                    start = SubTime(3_200),
                    end = SubTime(5_000),
                    text = "World",
                ),
            ),
        )
        store.write(
            SubtitleProject(title = "main-road.ass"),
            source,
            AssTextEncoding.UTF8,
        )

        val vm = EditorViewModel(application)
        vm.restoreRecovery()

        vm.focusEvent(1L, seek = false)
        vm.updateEventText(1L, "Main road")
        vm.setStyleFont("Default", "Noto Sans CJK SC")
        vm.setEventPosition(1L, 640.0, 360.0)
        vm.setEventTiming(1L, 1_200L, 3_300L)
        vm.addEventTransform(
            1L,
            AssTransform(
                startMs = 0.0,
                endMs = 500.0,
                accel = 1.0,
                tags = "\\fscx110\\fscy110",
            ),
        )

        val edited = vm.state.value.document
        assertTrue(vm.state.value.dirty)
        assertTrue(edited.events.first().text.contains("\\pos(640,360)"))
        assertTrue(edited.events.first().text.contains("\\t("))
        assertEquals("Noto Sans CJK SC", edited.styles.first().fontName)
        assertEquals(1_200L, edited.events.first().start.millis)
        assertEquals(3_300L, edited.events.first().end.millis)

        vm.undo()
        assertFalse(vm.state.value.document.events.first().text.contains("\\t("))
        vm.redo()
        assertTrue(vm.state.value.document.events.first().text.contains("\\t("))

        vm.setQuery("Main road")
        assertEquals(listOf(1L), vm.state.value.filteredEvents.map { it.id })

        val uri = providerUri("main-road.ass")
        application.contentResolver.openOutputStream(uri, "wt")!!.close()
        assertTrue(vm.saveTo(uri))
        assertFalse(vm.state.value.dirty)

        val bytes = application.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val decoded = AssTextDecoder.decode(bytes)
        val saved = AssCodec.parse(decoded.text)
        val report = AssRoundTripVerifier.compare(
            AssRoundTripVerifier.snapshot(vm.state.value.document),
            AssRoundTripVerifier.snapshot(saved),
        )
        assertTrue(report.summary, report.equivalent)

        val fresh = EditorViewModel(application)
        fresh.openSubtitle(uri)
        val reopened = fresh.state.value.document
        val reopenReport = AssRoundTripVerifier.compare(
            AssRoundTripVerifier.snapshot(saved),
            AssRoundTripVerifier.snapshot(reopened),
        )
        assertTrue(reopenReport.summary, reopenReport.equivalent)
        assertEquals("Noto Sans CJK SC", reopened.styles.first().fontName)
        assertTrue(reopened.events.first().text.contains("\\pos(640,360)"))
        assertTrue(reopened.events.first().text.contains("\\t("))
    }

    private fun providerUri(name: String): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(BuildConfig.APPLICATION_ID + ".safesave")
            .appendPath(name)
            .build()
}
