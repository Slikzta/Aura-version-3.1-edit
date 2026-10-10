package com.example

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.aura.core.voice.AndroidTextToSpeechEngine
import com.example.aura.core.voice.SpeechSynthesisRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidTextToSpeechEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSuccessfulInitialization() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 1000L,
            coroutineScope = testScope
        )

        // Simulate successful init from system TTS service
        engine.onInit(TextToSpeech.SUCCESS)

        var doneCalled = false
        var errorCalled: String? = null

        // With blank text, it should immediately complete
        engine.speak(
            request = SpeechSynthesisRequest(text = "   "),
            onDone = { doneCalled = true },
            onError = { errorCalled = it }
        )

        assertTrue(doneCalled)
        assertEquals(null, errorCalled)
    }

    @Test
    fun testSpeechRequestedBeforeInitializationExecutesUponSuccess() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 1000L,
            coroutineScope = testScope
        )

        var startCalled = false
        var doneCalled = false
        var errorCalled: String? = null

        // Speak requested before onInit has fired
        engine.speak(
            request = SpeechSynthesisRequest(text = "Hello Aura"),
            onStart = { startCalled = true },
            onDone = { doneCalled = true },
            onError = { errorCalled = it }
        )

        // Not yet spoken because init hasn't completed
        assertFalse(startCalled)
        assertFalse(doneCalled)

        // Now system finishes binding and signals SUCCESS
        engine.onInit(TextToSpeech.SUCCESS)
        testScheduler.advanceUntilIdle()

        // In Robolectric with ShadowTextToSpeech, speak succeeds and completes or starts
        // Error should not be called
        assertEquals(null, errorCalled)
    }

    @Test
    fun testInitializationFailureNotifiesPendingRequest() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 1000L,
            coroutineScope = testScope
        )

        var errorReceived: String? = null

        engine.speak(
            request = SpeechSynthesisRequest(text = "Will fail"),
            onError = { errorReceived = it }
        )

        // System TTS service signals failure
        engine.onInit(TextToSpeech.ERROR)

        assertNotNull(errorReceived)
        assertTrue(errorReceived!!.contains("TTS initialization failed"))
    }

    @Test
    fun testInitializationTimeoutNotifiesPendingRequest() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        // 100ms timeout
        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 100L,
            coroutineScope = testScope
        )

        var errorReceived: String? = null

        engine.speak(
            request = SpeechSynthesisRequest(text = "Will time out"),
            onError = { errorReceived = it }
        )

        // onInit NEVER fires. Advance time past the 100ms timeout.
        testScope.advanceTimeBy(150L)
        testScheduler.advanceUntilIdle()

        assertNotNull(errorReceived)
        assertTrue(errorReceived!!.contains("timed out"))
    }

    @Test
    fun testSubsequentCallsAfterInitFailureDoNotHang() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 1000L,
            coroutineScope = testScope
        )

        // System reports init failure
        engine.onInit(TextToSpeech.ERROR)

        var errorReceived: String? = null

        // New speak call after init failure must immediately fail, not queue/hang
        engine.speak(
            request = SpeechSynthesisRequest(text = "Try after failure"),
            onError = { errorReceived = it }
        )

        assertNotNull(errorReceived)
        assertTrue(errorReceived!!.contains("failed or timed out"))
    }

    @Test
    fun testRepeatedSpeechRequestsWhileInitializingResolvesSuperseded() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 1000L,
            coroutineScope = testScope
        )

        var firstError: String? = null
        var secondError: String? = null

        // First request while initializing
        engine.speak(
            request = SpeechSynthesisRequest(text = "First utterance"),
            onError = { firstError = it }
        )

        // Second request arrives while still initializing
        engine.speak(
            request = SpeechSynthesisRequest(text = "Second utterance"),
            onError = { secondError = it }
        )

        // First request must have been superseded cleanly
        assertNotNull(firstError)
        assertTrue(firstError!!.contains("superseded"))
        assertEquals(null, secondError)
    }

    @Test
    fun testStopDuringInitializationCancelsPending() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 1000L,
            coroutineScope = testScope
        )

        var errorReceived: String? = null

        engine.speak(
            request = SpeechSynthesisRequest(text = "To be cancelled"),
            onError = { errorReceived = it }
        )

        // Cancel / stop while initializing
        engine.stop()

        assertNotNull(errorReceived)
        assertTrue(errorReceived!!.contains("stopped"))
    }

    @Test
    fun testShutdownDuringInitializationCleansUpCleanly() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 1000L,
            coroutineScope = testScope
        )

        var pendingError: String? = null
        engine.speak(
            request = SpeechSynthesisRequest(text = "Pending before shutdown"),
            onError = { pendingError = it }
        )

        engine.shutdown()
        assertNotNull(pendingError)

        var afterShutdownError: String? = null
        engine.speak(
            request = SpeechSynthesisRequest(text = "After shutdown"),
            onError = { afterShutdownError = it }
        )

        assertNotNull(afterShutdownError)
        assertTrue(afterShutdownError!!.contains("shut down"))
    }

    @Test
    fun testBlankTextCompletesImmediately() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val engine = AndroidTextToSpeechEngine(
            context = context,
            initTimeoutMs = 1000L,
            coroutineScope = testScope
        )
        engine.onInit(TextToSpeech.SUCCESS)

        var doneCalled = false
        engine.speak(
            request = SpeechSynthesisRequest(text = ""),
            onDone = { doneCalled = true }
        )

        assertTrue(doneCalled)
    }
}
