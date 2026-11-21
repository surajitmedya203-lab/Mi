package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.DeviceActionExecutor
import com.example.ai.AssistantActionType
import com.example.ai.GeminiAssistantService
import com.example.ai.GeminiLiveVoiceManager
import com.example.ai.LiveTalkState
import com.example.data.local.KothaDatabase
import com.example.data.preferences.AppLanguageMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.AssistantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kotha AI", appName)
  }

  @Test
  fun `readDeviceTelemetry returns bounded battery and media volume`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val telemetry = DeviceActionExecutor.readDeviceTelemetry(context)
    assertTrue(telemetry.batteryPercent in 0..100)
    assertTrue(telemetry.mediaVolumePercent in 0..100)
  }

  @Test
  fun `offline engine resolves flashlight volume and battery status commands`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = KothaDatabase.getInstance(context)
    val repo = AssistantRepository(context, db, GeminiAssistantService())

    val (_, torchMeta) = repo.processOfflineCommand("টর্চ চালু করো", 1L, AppLanguageMode.AUTO)
    assertEquals(AssistantActionType.TOGGLE_FLASHLIGHT, torchMeta.actionType)
    assertEquals("state=on", torchMeta.actionPayload)

    val (_, volumeMeta) = repo.processOfflineCommand("Increase volume", 1L, AppLanguageMode.ENGLISH)
    assertEquals(AssistantActionType.ADJUST_VOLUME, volumeMeta.actionType)
    assertEquals("direction=up", volumeMeta.actionPayload)

    val (batteryReply, batteryMeta) = repo.processOfflineCommand("ব্যাটারি কত?", 1L, AppLanguageMode.BENGALI)
    assertEquals(AssistantActionType.DEVICE_STATUS, batteryMeta.actionType)
    assertTrue(batteryReply.contains("%"))
  }

  @Test
  fun `edge glow preferences default enabled and persist updates`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsRepo = UserPreferencesRepository(context)
    val initial = prefsRepo.preferencesFlow.first()
    assertTrue(initial.edgeGlowEnabled)
    assertTrue(initial.edgeGlowAlwaysOn)

    prefsRepo.setEdgeGlowAlwaysOn(false)
    val updated = prefsRepo.preferencesFlow.first()
    assertTrue(!updated.edgeGlowAlwaysOn)
    prefsRepo.setEdgeGlowAlwaysOn(true)
  }

  @Test
  fun `live voice manager starts idle and requires mic permission`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = GeminiLiveVoiceManager()
    assertEquals(LiveTalkState.Idle, manager.state.value)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    manager.startLiveTalk(
      context = context,
      profile = null,
      memories = emptyList(),
      languageMode = AppLanguageMode.AUTO,
      scope = scope,
      onActionExecuted = {},
      onTranscriptSaved = { _, _ -> }
    )
    assertTrue(manager.state.value is LiveTalkState.Error)
    manager.clearError()
    assertEquals(LiveTalkState.Idle, manager.state.value)
  }
}
