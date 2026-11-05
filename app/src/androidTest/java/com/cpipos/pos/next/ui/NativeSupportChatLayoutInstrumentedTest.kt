package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cpipos.pos.next.core.webpos.WebSupportConversationHead
import com.cpipos.pos.next.core.webpos.WebSupportMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Layout-only test; never opens a live support ticket or calls production. */
@RunWith(AndroidJUnit4::class)
class NativeSupportChatLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val head = WebSupportConversationHead(
        id = "thread-1",
        subject = "LINE OA Support",
        contactName = "Test cashier",
        status = "waiting_it",
        assignedUserName = "CpIPOS IT Administrator",
        latestMessageAt = null,
        latestMessagePreview = null,
        latestSenderType = null,
        unreadStoreCount = 0
    )

    @Test
    fun longThreadScrollsWithoutMovingComposerOrDisplayingPhoneContact() {
        val messages = (1..45).map { number ->
            WebSupportMessage(
                id = "message-$number",
                senderType = if (number % 2 == 0) "store" else "it",
                senderName = if (number % 2 == 0) "ร้านค้า" else "CpIPOS Support",
                senderRole = null,
                body = "ข้อความ $number",
                createdAt = "2026-11-05T08:33:43Z"
            )
        }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 360.dp, height = 570.dp)) {
                    SupportActiveChatPanel(
                        head = head, messages = messages, draft = "",
                        onDraftChange = {}, busy = false, onSend = {},
                        onRefresh = {}, onClose = {}, modifier = Modifier
                    )
                }
            }
        }
        compose.onNodeWithTag("support-message-composer").assertIsDisplayed()
        compose.onNodeWithTag("support-close-conversation").assertIsDisplayed()
        compose.onNodeWithText("โทร 098-546-0355").assertDoesNotExist()
        compose.onNodeWithTag("support-message-list").performScrollToIndex(0)
        compose.onNodeWithTag("support-message-composer").assertIsDisplayed()
        compose.onNodeWithTag("support-close-conversation").assertIsDisplayed()
        compose.onNodeWithTag("support-message-list").performScrollToIndex(44)
        compose.onNodeWithTag("support-message-composer").assertIsDisplayed()
    }
}
