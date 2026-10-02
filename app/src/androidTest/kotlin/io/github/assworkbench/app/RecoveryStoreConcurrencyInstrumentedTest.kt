package io.github.assworkbench.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.SubtitleProject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryStoreConcurrencyInstrumentedTest {
    @Test
    fun multipleInstancesSerializeSharedJournalStagingFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val writerA = RecoveryStore(context)
        val writerB = RecoveryStore(context)
        val clearer = RecoveryStore(context)
        val project = SubtitleProject(title = "Concurrent recovery.ass")

        fun document(prefix: String, index: Int) = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(1_000),
                    end = SubTime(3_000),
                    text = "$prefix-$index",
                ),
            ),
        )

        clearer.clear()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(3)
        try {
            val futures = listOf(
                pool.submit {
                    start.await()
                    repeat(12) { index ->
                        writerA.write(project, document("A", index), AssTextEncoding.UTF8)
                        Thread.yield()
                    }
                },
                pool.submit {
                    start.await()
                    repeat(12) { index ->
                        writerB.write(project, document("B", index), AssTextEncoding.UTF8)
                        Thread.yield()
                    }
                },
                pool.submit {
                    start.await()
                    repeat(12) {
                        clearer.clear()
                        Thread.yield()
                    }
                },
            )

            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }

            if (writerA.exists()) {
                assertNotNull(writerA.read())
            }
            val recoveryDir = File(context.filesDir, "recovery")
            assertFalse(File(recoveryDir, "latest.ass.tmp").exists())
            assertFalse(File(recoveryDir, "latest.meta.tmp").exists())
        } finally {
            pool.shutdownNow()
            clearer.clear()
        }
    }
}
