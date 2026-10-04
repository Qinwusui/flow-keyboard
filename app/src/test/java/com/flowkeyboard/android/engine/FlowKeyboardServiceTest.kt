package com.flowkeyboard.android.engine

import android.app.Application
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import com.flowkeyboard.android.data.repository.DictionaryRepository
import com.flowkeyboard.android.data.repository.SettingsRepository
import com.flowkeyboard.android.di.appModule
import com.flowkeyboard.android.model.KeyboardSettings
import com.flowkeyboard.android.model.TypingSummary
import com.flowkeyboard.android.ui.keyboard.FlowKeyboardService
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FlowKeyboardServiceTest {
    @Test fun `IME view has lifecycle saved state and ViewModel owners and releases engines`() {
        val feedback = mockk<FeedbackEngine>(relaxed = true)
        val settings = mockk<SettingsRepository>()
        val dictionary = mockk<DictionaryRepository>()
        every { settings.settings } returns MutableStateFlow(KeyboardSettings())
        every { dictionary.summary } returns MutableStateFlow(TypingSummary())
        every { dictionary.search(any()) } returns MutableStateFlow(emptyList())
        every { dictionary.predictNext(any()) } returns MutableStateFlow(emptyList())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(appModule, module {
                single { settings }
                single { dictionary }
                factory { feedback }
                single { scope }
            })
        }
        try {
            val controller = Robolectric.buildService(FlowKeyboardService::class.java).create()
            val service = controller.get()
            val view = service.onCreateInputView()
            assertSame(service, view.findViewTreeLifecycleOwner())
            assertSame(service, view.findViewTreeViewModelStoreOwner())
            assertSame(service, view.findViewTreeSavedStateRegistryOwner())
            assertEquals(Lifecycle.State.CREATED, service.lifecycle.currentState)
            service.onStartInputView(EditorInfo(), false)
            assertEquals(Lifecycle.State.RESUMED, service.lifecycle.currentState)
            assertFalse(service.onEvaluateFullscreenMode())
            service.onFinishInputView(true)
            assertEquals(Lifecycle.State.CREATED, service.lifecycle.currentState)
            controller.destroy()
            assertEquals(Lifecycle.State.DESTROYED, service.lifecycle.currentState)
            verify(exactly = 1) { feedback.close() }
        } finally { scope.cancel(); stopKoin() }
    }
}
