package com.burry071.evenlight.ui

import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.burry071.evenlight.EvenlightController
import com.burry071.evenlight.core.EveningRecord
import com.burry071.evenlight.data.Settings
import com.burry071.evenlight.data.Store
import com.burry071.evenlight.ui.theme.EvenlightTheme

/**
 * Where the app is.
 *
 * Four destinations and a back gesture, no bottom bar at any width: `androidx.navigation` is not in this repo's
 * Gradle cache and the build is `--offline`, so navigation is this sealed type plus a saved back stack (ledger R2).
 * `Card` is the only destination that carries data, and it carries a date rather than a record because the date is
 * what the card file is named from and what survives a process death.
 */
sealed class Screen {
    data object Tonight : Screen()
    data object Shoot : Screen()
    data object Stack : Screen()
    data object Setup : Screen()
    data class Card(val date: String) : Screen()

    /**
     * The tag is what `rememberSaveable` writes, so it must round-trip: a screen the saver cannot read back is a
     * screen that silently becomes Tonight after the system reclaims the process.
     */
    val tag: String
        get() = when (this) {
            is Tonight -> "Tonight"
            is Shoot -> "Shoot"
            is Stack -> "Stack"
            is Setup -> "Setup"
            is Card -> "Card:$date"
        }

    companion object {
        fun fromTag(tag: String): Screen = when {
            tag == Shoot.tag -> Shoot
            tag == Stack.tag -> Stack
            tag == Setup.tag -> Setup
            tag.startsWith("Card:") -> Card(tag.removePrefix("Card:"))
            else -> Tonight
        }
    }
}

/**
 * The back stack. Kept as a list rather than a single value so `Stack -> Card -> back` lands on the stack with its
 * scroll position, and so the reveal screen can be *replaced* rather than covered: after a shot the viewfinder is
 * behind the card only for as long as the filmstrip runs, and pressing back from a finished card must not return to
 * a shutter.
 */
class Nav internal constructor(internal val stack: SnapshotStateList<Screen>) {

    val current: Screen get() = if (stack.isEmpty()) Screen.Tonight else stack.last()

    /** The screen underneath, which is how the expanded stack knows it has a detail pane to fill. */
    val under: Screen? get() = if (stack.size > 1) stack[stack.size - 2] else null

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun replaceTop(screen: Screen) {
        if (stack.isEmpty()) stack.add(screen) else stack[stack.size - 1] = screen
    }

    /** Tonight and Stack are destinations, not stops on the way to each other. */
    fun goToRoot(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    companion object {
        // `listSaver` hands back a `Saver<T, Any>` - the saved value is a `List<String>`, and the Bundle can hold
        // that list directly, which is why this is a list saver and not a string one.
        private val Saver: Saver<Nav, Any> = listSaver(
            save = { it.stack.map { s -> s.tag } },
            restore = { tags -> Nav(mutableStateListOf<Screen>().apply { addAll(tags.map { Screen.fromTag(it) }) }) }
        )

        @Composable
        fun rememberNav(): Nav = rememberSaveable(saver = Saver) { Nav(mutableStateListOf(Screen.Tonight)) }
    }
}

@Composable
fun Nav.BackHandler() {
    BackHandler(enabled = stack.size > 1) { pop() }
}

/**
 * The night flag is a parameter, not `isSystemInDarkTheme()`: the stack and the detail are dark because they are the
 * night, and they stay dark on a phone set to light mode (spec section 8). The viewfinder is dark too - it is the
 * same hour, seen through a lens - and Tonight and Setup are the paper half of the app.
 */
@Composable
fun EvenlightContent(
    nav: Nav,
    store: Store,
    settings: Settings,
    records: List<EveningRecord>,
    held: Set<String>,
    controller: EvenlightController,
    hasCamera: Boolean,
    onEnsureCamera: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onOpenRaw: (EveningRecord) -> Unit,
    onShareCard: (String) -> Unit,
    onSettingsSaved: (Settings) -> Unit,
    reduceMotion: Boolean
) {
    nav.BackHandler()
    val night = when (nav.current) {
        is Screen.Stack, is Screen.Card, is Screen.Shoot -> true
        else -> false
    }
    EvenlightTheme(dark = night) {
        when (val screen = nav.current) {
            is Screen.Tonight -> {
                if (settings.isSet) {
                    TonightScreen(
                        settings = settings,
                        records = records,
                        held = held,
                        onShoot = { nav.push(Screen.Shoot) },
                        onStack = { nav.goToRoot(Screen.Stack) },
                        onSetup = { nav.push(Screen.Setup) }
                    )
                } else {
                    // Step 4: with no place set, Tonight *is* the setup form. The sun line and the countdown would
                    // otherwise print an altitude computed for the Gulf of Guinea over a sky in Lahore.
                    SetupScreen(
                        store = store,
                        current = settings,
                        onSaved = { s ->
                            onSettingsSaved(s)
                            nav.replaceTop(Screen.Tonight)
                        }
                    )
                }
            }

            is Screen.Setup -> SetupScreen(
                store = store,
                current = settings,
                onSaved = { s ->
                    onSettingsSaved(s)
                    nav.goToRoot(Screen.Tonight)
                }
            )

            is Screen.Shoot -> ShootScreen(
                settings = settings,
                controller = controller,
                hasCamera = hasCamera,
                onEnsureCamera = onEnsureCamera,
                onPreviewReady = onPreviewReady,
                reduceMotion = reduceMotion,
                onFiled = { record -> nav.replaceTop(Screen.Card(record.date)) }
            )

            is Screen.Stack -> {
                val open = nav.under as? Screen.Card
                StackScreen(
                    records = records,
                    held = held,
                    onOpen = { date -> nav.push(Screen.Card(date)) },
                    detail = if (open != null) {
                        {
                            CardPane(
                                store = store,
                                records = records,
                                date = open.date,
                                onOpenRaw = onOpenRaw,
                                onShareCard = onShareCard
                            )
                        }
                    } else {
                        null
                    }
                )
            }

            is Screen.Card -> CardScreen(
                cardFile = store.cardFile(screen.date),
                onRawPhoto = {
                    records.lastOrNull { it.date == screen.date }?.let(onOpenRaw)
                },
                onShare = { onShareCard(screen.date) }
            )
        }
    }
}

/** The right-hand pane of the expanded stack: the same detail the compact stack pushes to. */
@Composable
private fun CardPane(
    store: Store,
    records: List<EveningRecord>,
    date: String,
    onOpenRaw: (EveningRecord) -> Unit,
    onShareCard: (String) -> Unit
) {
    CardScreen(
        cardFile = store.cardFile(date),
        onRawPhoto = { records.lastOrNull { it.date == date }?.let(onOpenRaw) },
        onShare = { onShareCard(date) }
    )
}
