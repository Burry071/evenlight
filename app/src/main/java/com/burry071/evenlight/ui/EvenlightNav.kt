package com.burry071.evenlight.ui

import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    canReadRaw: (EveningRecord) -> Boolean,
    onSettingsSaved: (Settings) -> Unit,
    reloadData: () -> Unit,
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
                onFiled = { record ->
                    // `records`/`held` are the activity's snapshot of the filesystem, and this is the only moment in
                    // the session the app itself makes that snapshot wrong. Without the reload tonight's row keeps
                    // rendering through the hole branch as "no photo" next to a card that exists - the gaps rule
                    // honest in reverse - and it stays wrong until the process restarts.
                    reloadData()
                    nav.replaceTop(Screen.Card(record.date))
                }
            )

            is Screen.Stack -> StackScreen(
                records = records,
                held = held,
                onOpen = { date -> nav.push(Screen.Card(date)) },
                detail = null
            )

            is Screen.Card -> {
                val overStack = nav.under is Screen.Stack
                // The pane is chosen here, not inside StackScreen, because at compact width a card is a full screen
                // and at expanded width it is the right 62 % of the stack it was opened from - and only this branch
                // knows the card is on the stack. The earlier shape of this file asked `nav.under` for a Card while
                // `current` was the Stack, which is the one arrangement the navigation can never produce: opening a
                // card pushes it *over* the stack, so the 38/62 split was dead code and every foldable opened a
                // full-screen card instead (Step 9).
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (overStack && maxWidth >= 840.dp) {
                        StackScreen(
                            records = records,
                            held = held,
                            onOpen = { date -> nav.replaceTop(Screen.Card(date)) },
                            detail = {
                                CardPane(
                                    store = store,
                                    records = records,
                                    date = screen.date,
                                    canReadRaw = canReadRaw,
                                    onOpenRaw = onOpenRaw,
                                    onShareCard = onShareCard
                                )
                            }
                        )
                    } else {
                        val record = records.lastOrNull { it.date == screen.date }
                        CardScreen(
                            cardFile = store.cardFile(screen.date),
                            hasRawPhoto = record != null && canReadRaw(record),
                            onRawPhoto = { record?.let(onOpenRaw) },
                            onShare = { onShareCard(screen.date) }
                        )
                    }
                }
            }
        }
    }
}

/** The right-hand pane of the expanded stack: the same detail the compact stack pushes to. */
@Composable
private fun CardPane(
    store: Store,
    records: List<EveningRecord>,
    date: String,
    canReadRaw: (EveningRecord) -> Boolean,
    onOpenRaw: (EveningRecord) -> Unit,
    onShareCard: (String) -> Unit
) {
    val record = records.lastOrNull { it.date == date }
    CardScreen(
        cardFile = store.cardFile(date),
        hasRawPhoto = record?.let(canReadRaw) ?: false,
        onRawPhoto = { record?.let(onOpenRaw) },
        onShare = { onShareCard(date) },
        // The expanded pane's card is the design system's 420 dp; below that threshold a card is full-bleed at the
        // same screen's 340 dp cap, so the two numbers are the two boxes the mockup drew rather than two rules.
        cardMaxWidth = 420.dp
    )
}
