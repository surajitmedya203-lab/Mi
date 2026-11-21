package com.example

import android.content.Intent
import com.example.actions.WhatsAppMessagingUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WhatsAppMessagingUtilTest {

    @Test
    fun `buildActionViewIntent uses ACTION_VIEW with com_whatsapp and encodes formatted Bengali text`() {
        val formattedMessage = "*জরুরি বার্তা*\nআমি ১০ মিনিটে আসছি!"
        val intent = WhatsAppMessagingUtil.buildActionViewIntent(
            phoneNumber = "01712-345678",
            formattedMessage = formattedMessage
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        val dataString = intent.dataString.orEmpty()
        assertTrue(dataString.startsWith("https://api.whatsapp.com/send?phone=8801712345678&text="))
        assertTrue(dataString.contains("%0A"))
        assertEquals(formattedMessage, intent.data?.getQueryParameter("text"))
    }

    @Test
    fun `buildActionViewIntent omits phone query when number is empty`() {
        val intent = WhatsAppMessagingUtil.buildActionViewIntent(
            phoneNumber = "",
            formattedMessage = "Hello from Kotha AI"
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals("Hello from Kotha AI", intent.data?.getQueryParameter("text"))
        assertEquals(null, intent.data?.getQueryParameter("phone"))
    }
}
