package ru.workinprogress.mani.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import ru.workinprogress.mani.theme.LocalManiFonts
import kotlin.math.roundToInt

data class Action(val name: String, val icon: ImageVector, val onClick: () -> Unit)

class MainAppBarState {
    private val enabledState = mutableStateOf(false)
    private val contextModeState = mutableStateOf(false)
    private val actionsState = mutableStateOf(emptySet<Action>().toImmutableSet())
    private val backUpActions = mutableStateOf(emptySet<Action>().toImmutableSet())

    val enabled get() = enabledState.value
    val showBack = mutableStateOf(false)
    val title = mutableStateOf("")
    val contextTitle = mutableStateOf("")
    val contextMode get() = contextModeState.value
    val actions get() = actionsState.value
    val empty
        get() = title.value.isEmpty() &&
            actions.isEmpty() &&
            !contextMode

    private fun enable() {
        this.enabledState.value = true
    }

    fun disable() {
        this.enabledState.value = false
    }

    fun showAction(action: Action) {
        if (this.enabled.not()) {
            enable()
        }
        this.actionsState.value = this.actionsState.value
            .filter { action -> action.name == action.name }
            .plus(action)
            .toImmutableSet()
    }

    fun removeAction(action: Action) {
        if (contextMode) {
            this.backUpActions.value = this.backUpActions.value.minus(action).toImmutableSet()
        } else {
            this.actionsState.value = this.actionsState.value.minus(action).toImmutableSet()
        }
    }

    fun showContextMenu(actions: ImmutableSet<Action>) {
        if (this.contextModeState.value) {
            this.actionsState.value = actions.toImmutableSet()
        } else {
            backUpActions.value = this.actionsState.value.toImmutableSet()
            this.actionsState.value = actions.toImmutableSet()
            this.contextModeState.value = true
        }
    }

    fun closeContextMenu() {
        if (contextModeState.value.not()) return
        this.actionsState.value = backUpActions.value.toImmutableSet()
        backUpActions.value = emptySet<Action>().toImmutableSet()
        contextModeState.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManiAppBar(appbarState: MainAppBarState = remember { MainAppBarState() }, onBack: () -> Unit) {
    if (appbarState.enabled) {
        AnimatedVisibility(
            appbarState.empty.not(),
            exit = fadeOut() + slideOut(targetOffset = {
                IntOffset(
                    0,
                    -(it.height / 2f).roundToInt(),
                )
            }),
            enter = fadeIn() + slideIn(initialOffset = {
                IntOffset(
                    0,
                    -(it.height / 2f).roundToInt(),
                )
            }),
        ) {
            TopAppBar(
                modifier = Modifier,
                title = {
                    val text =
                        if (appbarState.contextMode) appbarState.contextTitle.value else appbarState.title.value

                    // На корневом экране заголовок — это словесный знак: точка и «mani»
                    // моноширинным, как в макете. Там, где есть «назад», это уже название
                    // места, куда человек зашёл, и набирается оно обычным текстом.
                    if (!appbarState.showBack.value && !appbarState.contextMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                            Text(
                                text,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = LocalManiFonts.current.mono,
                                    fontWeight = FontWeight.W500,
                                ),
                            )
                        }
                    } else {
                        Text(text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (appbarState.contextMode) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                actions = {
                    AnimatedVisibility(
                        appbarState.actions.isNotEmpty(),
                        enter = fadeIn() + expandIn(expandFrom = Alignment.CenterStart),
                        exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.CenterStart),
                    ) {
                        Row {
                            appbarState.actions.forEach { action ->
                                IconButton(
                                    onClick = {
                                        action.onClick()
                                    },
                                    modifier = Modifier.testTag(action.name.lowercase()),
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.name,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    AnimatedVisibility(
                        appbarState.showBack.value && !appbarState.contextMode,
                        enter = fadeIn() + expandIn(expandFrom = Alignment.CenterEnd),
                        exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.CenterEnd),
                    ) {
                        IconButton(onClick = {
                            onBack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "back",
                            )
                        }
                    }
                    AnimatedVisibility(
                        appbarState.contextMode,
                        enter = fadeIn() + expandIn(expandFrom = Alignment.CenterEnd),
                        exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.CenterEnd),
                    ) {
                        IconButton(onClick = {
                            appbarState.closeContextMenu()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "back",
                            )
                        }
                    }
                },
            )
        }
    }
}
