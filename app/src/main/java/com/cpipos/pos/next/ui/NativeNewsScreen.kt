package com.cpipos.pos.next.ui

import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.WebNewsFeed
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.webpos.WebNewsItem
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val newsBlue = Color(0xFF1677E8)

@Composable
internal fun NativeNewsScreen(
    client: WebPosClient, session: WebPosSession,
    onBack: () -> Unit, onTab: (String) -> Unit,
    onUnreadCount: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feed by remember(session.id) { mutableStateOf<WebNewsFeed?>(null) }
    var selected by remember(session.id) { mutableStateOf<WebNewsItem?>(null) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var revision by remember(session.id) { mutableStateOf(0) }

    LaunchedEffect(client, session.id, revision) {
        while (true) {
            try {
                val loaded = client.news(session)
                feed = loaded
                if (selected != null && loaded.items.none { it.id == selected?.id }) selected = null
                onUnreadCount(loaded.unreadCount)
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "load"
            }
            kotlinx.coroutines.delay(30_000L)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF1F7FF)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("‹", fontSize = 26.sp, color = newsBlue)
                }
                Text(nativeUiText("home.news", "ข่าวสาร"), modifier = Modifier.weight(1f),
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF172B4E))
                Text("${feed?.unreadCount ?: 0} " + nativeUiText("news.unread", "ยังไม่อ่าน"),
                    color = newsBlue, fontSize = 11.sp)
            }
            if (error != null) {
                Text(if (error == "read") nativeUiText("news.read_error", "บันทึกสถานะอ่านไม่สำเร็จ")
                    else nativeUiText("news.load_error", "โหลดข่าวสารไม่ได้ กรุณาลองอีกครั้ง"),
                    color = Color(0xFFB42318),
                    modifier = Modifier.padding(horizontal = 18.dp))
                TextButton(onClick = { revision++ }) {
                    Text(nativeUiText("common.refresh", "รีเฟรช"))
                }
            }
            if (feed == null && error == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = newsBlue)
                }
            } else if (feed?.items.isNullOrEmpty() && error == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(nativeUiText("news.empty", "ยังไม่มีข่าวสารจาก CpIPOS IT"),
                        color = Color(0xFF71849B))
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(feed?.items.orEmpty(), key = { it.id }) { item ->
                        Surface(Modifier.fillMaxWidth().clickable {
                            selected = item
                            if (!item.isRead) scope.launch {
                                try {
                                    client.markNewsRead(session, item.id)
                                    revision++
                                } catch (_: Exception) {
                                    error = "read"
                                }
                            }
                        }, shape = RoundedCornerShape(18.dp), color = Color.White,
                            shadowElevation = 2.dp) {
                            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(43.dp), shape = CircleShape,
                                    color = if (item.isRead) Color(0xFFF2F5F8) else Color(0xFFE8F3FF)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        androidx.compose.foundation.Image(
                                            painterResource(R.drawable.ic_nav_news), null,
                                            Modifier.size(24.dp))
                                    }
                                }
                                Spacer(Modifier.size(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.Bold,
                                        color = Color(0xFF172B4E), maxLines = 2)
                                    Text(item.publishedAt.take(10), fontSize = 10.sp,
                                        color = Color(0xFF71849B))
                                    Text(item.body, fontSize = 12.sp, maxLines = 2,
                                        color = Color(0xFF566B84))
                                }
                                Text("›", fontSize = 23.sp, color = newsBlue)
                            }
                        }
                    }
                    item { Spacer(Modifier.size(16.dp)) }
                }
            }
            HomeBottomMenu(selectedTab = "sale", onMenuClick = onTab)
        }
    }
    selected?.let { item ->
        AlertDialog(onDismissRequest = { selected = null },
            containerColor = Color.White, shape = RoundedCornerShape(22.dp),
            title = { Text(item.title, color = Color(0xFF172B4E), fontWeight = FontWeight.Bold) },
            text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.publishedAt.take(10), color = Color(0xFF71849B), fontSize = 11.sp)
                item.imageUrl?.let { NewsImage(it) }
                Text(item.body, color = Color(0xFF172B4E))
                item.linkUrl?.let { link ->
                    if (link.startsWith("https://") || link.startsWith("http://")) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                        }) { Text(nativeUiText("news.open_link", "เปิดลิงก์")) }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { selected = null }) {
                Text(nativeUiText("news.close", "ปิด"), color = newsBlue)
            } })
    }
}

@Composable
private fun NewsImage(url: String) {
    var bitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (!url.startsWith("https://")) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                connection.getInputStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it, null, Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Fit)
    }
}
