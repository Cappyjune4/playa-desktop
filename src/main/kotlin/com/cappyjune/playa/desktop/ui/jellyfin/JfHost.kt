package com.cappyjune.playa.desktop.ui.jellyfin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A page opened on top of a Jellyfin section: a title's details, a person, a folder, or search. */
sealed interface JfPage {
    data class Item(val id: String) : JfPage
    data class Person(val id: String, val name: String) : JfPage
    data class Folder(val id: String, val name: String, val collectionType: String?) : JfPage
    data object Search : JfPage
}

/** Escape (or the mouse's back button) goes back a page; Shell hands Escape here. */
object JfBack {
    var handler: (() -> Boolean)? = null
    fun back(): Boolean = handler?.invoke() ?: false
}

/**
 * The page stack inside a Jellyfin section: Back pops a page, and the section's own list keeps
 * its scroll position underneath.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JfHost(root: @Composable (open: (JfPage) -> Unit) -> Unit) {
    val stack = remember {
        mutableStateListOf<JfPage>().apply {
            System.getProperty("playa.page")?.let { add(if (it == "search") JfPage.Search else JfPage.Item(it)) }
        }
    }
    val holder = rememberSaveableStateHolder()
    val open: (JfPage) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    JfBack.handler = { if (stack.isNotEmpty()) { back(); true } else false }
    Box(Modifier.fillMaxSize().onPointerEvent(PointerEventType.Press) { if (it.buttons.isBackPressed) back() }) {
        when (val top = stack.lastOrNull()) {
            null -> holder.SaveableStateProvider("root") { root(open) }
            else -> key(stack.size, top) {
                holder.SaveableStateProvider("page-${stack.size}") {
                    when (top) {
                        is JfPage.Item -> ItemScreen(top.id, open, back)
                        is JfPage.Person -> PersonScreen(top.id, top.name, open, back)
                        is JfPage.Folder -> FolderScreen(top.id, top.name, top.collectionType, open, back)
                        JfPage.Search -> SearchScreen(open, back)
                    }
                }
            }
        }
    }
}

/** The bar at the top of a page: back (if it's a page), a title, and actions on the right. */
@Composable
fun PageHeader(back: (() -> Unit)?, title: @Composable () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = if (back == null) 40.dp else 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (back != null) { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }; Spacer(Modifier.width(8.dp)) }
        Box(Modifier.weight(1f)) { title() }
        actions()
    }
}

@Composable
fun HeaderText(text: String) =
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
