package com.cpipos.pos.next.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.WebPosApiException
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import com.cpipos.pos.next.core.webpos.WebSupportConversationHead
import com.cpipos.pos.next.core.webpos.WebSupportConversationSnapshot
import com.cpipos.pos.next.core.webpos.WebSupportMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val helpInk = Color(0xFF172B4E)
private val helpMuted = Color(0xFF71849B)
private val helpBlue = Color(0xFF176DED)
private val helpBackground = Color(0xFFF5F9FF)
private val helpTimeFormat = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm", Locale.US)
    .withZone(ZoneId.of("Asia/Bangkok"))

private fun supportTime(value: String): String =
    runCatching { helpTimeFormat.format(Instant.parse(value)) }.getOrDefault(value)

private fun supportStatus(value: String): String = when (value.lowercase(Locale.US)) {
    "waiting_it" -> "รอทีม IT ตอบกลับ"
    "waiting_store" -> "รอร้านค้าตอบกลับ"
    "open", "active" -> "กำลังสนทนา"
    "closed" -> "ปิดการสนทนาแล้ว"
    else -> value
}

/**
 * Support is a true chat screen: the header, composer and bottom POS
 * navigation remain fixed. Only the message history scrolls.
 */
@Composable
internal fun NativeHelpCenterScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var heads by remember(session.id) { mutableStateOf<List<WebSupportConversationHead>>(emptyList()) }
    var chat by remember(session.id) { mutableStateOf<WebSupportConversationSnapshot?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var busy by remember(session.id) { mutableStateOf(false) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var notice by remember(session.id) { mutableStateOf<String?>(null) }
    var subject by remember(session.id) { mutableStateOf("") }
    var contactName by remember(session.id) { mutableStateOf(session.cashierName) }
    var draft by remember(session.id) { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                val updatedHeads = client.supportConversations(session)
                heads = updatedHeads
                val active = updatedHeads.firstOrNull { it.status != "closed" }
                chat = active?.let { client.supportConversation(session, it.id) }
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = "โหลดศูนย์ช่วยเหลือไม่สำเร็จ: " +
                    ((failure as? WebPosApiException)?.code ?: "network_error")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(session.id) { refresh() }

    val active = heads.firstOrNull { it.status != "closed" }
    Surface(Modifier.fillMaxSize(), color = helpBackground) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹ ตั้งค่า") }
                Column(Modifier.weight(1f)) {
                    Text("ศูนย์ช่วยเหลือ", color = helpInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (active == null) "CpIPOS Support · LINE · โทรศัพท์" else "แชทกับทีม CpIPOS Support",
                        color = helpMuted, fontSize = 11.sp
                    )
                }
            }

            if (notice != null || error != null) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 4.dp),
                    color = if (error != null) Color(0xFFFFEEF1) else Color(0xFFEAF8F0),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(error ?: notice.orEmpty(), Modifier.padding(11.dp),
                        color = if (error != null) Color(0xFFB33A49) else Color(0xFF178A50),
                        fontSize = 12.sp)
                }
            }

            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = helpBlue)
                }
                active != null -> SupportActiveChatPanel(
                    head = active,
                    messages = chat?.takeIf { it.id == active.id }?.messages.orEmpty(),
                    draft = draft,
                    onDraftChange = { draft = it.take(4000) },
                    busy = busy,
                    onSend = {
                        val message = draft.trim()
                        if (message.isNotBlank() && !busy) {
                            busy = true
                            scope.launch {
                                try {
                                    client.sendSupportMessage(session, active.id, message)
                                    draft = ""
                                    chat = client.supportConversation(session, active.id)
                                    heads = client.supportConversations(session)
                                    error = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = "ส่งข้อความไม่สำเร็จ: " +
                                        ((failure as? WebPosApiException)?.code ?: "network_error")
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onRefresh = { refresh() },
                    onClose = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    client.closeSupportConversation(session, active.id)
                                    draft = ""
                                    chat = null
                                    notice = "จบการสนทนาแล้ว"
                                    val updatedHeads = client.supportConversations(session)
                                    heads = updatedHeads
                                    val next = updatedHeads.firstOrNull { it.status != "closed" }
                                    chat = next?.let { client.supportConversation(session, it.id) }
                                    error = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = "ปิดเรื่องไม่สำเร็จ: " +
                                        ((failure as? WebPosApiException)?.code ?: "network_error")
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                else -> Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("เปิดเรื่องใหม่กับ IT Support", fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp, color = helpInk)
                            OutlinedTextField(subject, { subject = it.take(180) },
                                label = { Text("หัวข้อปัญหา") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(contactName, { contactName = it.take(120) },
                                label = { Text("ชื่อผู้ติดต่อ") }, modifier = Modifier.fillMaxWidth())
                            Button(
                                enabled = !busy && subject.trim().length >= 2 && contactName.trim().length >= 2,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val id = client.createSupportConversation(session, subject, contactName)
                                            heads = client.supportConversations(session)
                                            chat = client.supportConversation(session, id)
                                            subject = ""
                                            notice = "เปิดเรื่อง Support แล้ว"
                                            error = null
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            error = "เปิดเรื่อง Support ไม่สำเร็จ: " +
                                                ((failure as? WebPosApiException)?.code ?: "network_error")
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = helpBlue)
                            ) { Text("เริ่มแชทกับ IT Support") }
                        }
                    }

                    // Contact alternatives are visible only BEFORE starting a
                    // conversation, never under the active chat composer.
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("ช่องทางสำรอง", fontWeight = FontWeight.ExtraBold, color = helpInk)
                            OutlinedButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lin.ee/f1LXpAF"))) }
                            }, modifier = Modifier.fillMaxWidth()) { Text("ติดต่อ LINE Support") }
                            OutlinedButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0985460355"))) }
                            }, modifier = Modifier.fillMaxWidth()) { Text("โทร 098-546-0355") }
                        }
                    }
                }
            }

            // The keyboard can take most of a small device's height. Hide
            // POS navigation during input so it never obscures the composer.
            if (!keyboardVisible) HomeBottomMenu(
                selectedTab = "settings",
                onMenuClick = onTab
            )
        }
    }
}

@Composable
internal fun SupportActiveChatPanel(
    head: WebSupportConversationHead,
    messages: List<WebSupportMessage>,
    draft: String,
    onDraftChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(head.id, messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    Column(modifier.fillMaxWidth().testTag("support-active-chat")) {
        Surface(
            color = Color.White, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(head.subject, fontWeight = FontWeight.ExtraBold,
                        color = helpInk, fontSize = 15.sp, maxLines = 2)
                    Text(
                        supportStatus(head.status) +
                            (head.assignedUserName?.let { " · $it" } ?: ""),
                        fontSize = 11.sp, color = helpMuted
                    )
                }
                TextButton(onClick = onRefresh, enabled = !busy) { Text("รีเฟรช") }
            }
        }

        // The ONLY vertically scrolling region when a conversation is open.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("support-message-list"),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = message.senderType == "store"
                Row(Modifier.fillMaxWidth(), horizontalArrangement =
                    if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (mine) Color(0xFFE8F1FF) else Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(message.senderName.ifBlank { if (mine) "ร้านค้า" else "IT Support" },
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = if (mine) helpBlue else helpInk)
                            Text(message.body, color = helpInk, fontSize = 13.sp)
                            if (message.createdAt.isNotBlank()) {
                                Text(supportTime(message.createdAt), color = helpMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Composer is a sibling of LazyColumn, never a child of its scroll.
        Surface(color = Color.White, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        placeholder = { Text("พิมพ์ข้อความถึง IT Support") },
                        modifier = Modifier.weight(1f).testTag("support-message-composer"),
                        minLines = 1,
                        maxLines = 3
                    )
                    Button(
                        enabled = !busy && draft.isNotBlank(),
                        onClick = onSend,
                        colors = ButtonDefaults.buttonColors(containerColor = helpBlue),
                        modifier = Modifier.padding(bottom = 3.dp).testTag("support-send-button")
                    ) { Text("ส่ง") }
                }
                TextButton(
                    onClick = onClose, enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .testTag("support-close-conversation")
                ) { Text("จบการสนทนา", fontSize = 12.sp) }
            }
        }
    }
}
