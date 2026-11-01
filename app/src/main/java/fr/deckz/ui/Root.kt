package fr.deckz.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.deckz.AppContainer
import fr.deckz.R
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import kotlinx.coroutines.launch

@Composable
fun DeckZRoot(c: AppContainer) {
    val state by c.repository.state.collectAsStateWithLifecycle()
    if (state.lastSuccess == 0L) {
        FirstSyncScreen(state.running, state.progress, state.step, state.error) { c.scope.launch { c.repository.refresh() } }
        return
    }
    val nav = rememberNavController()
    NavHost(
        nav, startDestination = "leaders", modifier = Modifier.fillMaxSize().background(Night),
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable("leaders") {
            LeaderListScreen(c, onLeader = { nav.navigate("leader/$it") }, onSettings = { nav.navigate("settings") })
        }
        composable("leader/{uid}", listOf(navArgument("uid") { type = NavType.IntType })) { e ->
            LeaderScreen(c, e.arguments!!.getInt("uid"), onBack = { nav.popBackStack() },
                onCard = { nav.navigate("card/$it") }, onDeck = { nav.navigate("deck/$it") }, onMatchups = { nav.navigate("matchups/$it") })
        }
        composable("deck/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
            DeckScreen(c, e.arguments!!.getString("id")!!, onBack = { nav.popBackStack() }, onCard = { nav.navigate("card/$it") })
        }
        composable("card/{uid}", listOf(navArgument("uid") { type = NavType.IntType })) { e ->
            CardScreen(c, e.arguments!!.getInt("uid"), onBack = { nav.popBackStack() })
        }
        composable("matchups/{uid}", listOf(navArgument("uid") { type = NavType.IntType })) { e ->
            val me = e.arguments!!.getInt("uid")
            MatchupPickerScreen(c, me, onBack = { nav.popBackStack() }, onOpponent = { nav.navigate("matchup/$me/$it") })
        }
        composable(
            "matchup/{me}/{opp}",
            listOf(navArgument("me") { type = NavType.IntType }, navArgument("opp") { type = NavType.IntType }),
        ) { e ->
            MatchupScreen(c, e.arguments!!.getInt("me"), e.arguments!!.getInt("opp"), onBack = { nav.popBackStack() },
                onCard = { nav.navigate("card/$it") })
        }
        composable("settings") { SettingsScreen(c, onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun FirstSyncScreen(running: Boolean, progress: Float, step: String, error: String?, start: () -> Unit) {
    LaunchedEffect(Unit) { if (!running) start() }
    Column(
        Modifier.fillMaxSize().background(Night).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(160.dp))
        Text("DeckZ", style = MaterialTheme.typography.displayMedium, color = Orange)
        Text("Compagnon méta Fusion World", color = Muted)
        Spacer(Modifier.height(40.dp))
        if (running || error == null) {
            LinearProgressIndicator(
                progress = { progress }, color = Orange, trackColor = Orange.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text(step.ifBlank { "Préparation…" }, color = Gold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("Premier lancement : récupération des cartes et de la méta (2 à 3 minutes).", color = Muted,
                fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            Text("Impossible de récupérer les données.\n$error", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = start) { Text("Réessayer") }
        }
    }
}
