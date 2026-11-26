package fr.deckz.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Traduction automatique anglais → français des textes de cartes, faite sur le téléphone
 * (Google ML Kit). Le modèle (~30 Mo) est téléchargé une seule fois, puis tout marche hors ligne.
 *
 * Les mots-clés entre crochets ([Blocker], [On Play]…) sont d'abord remplacés par leur nom
 * français fixe, pour qu'ils soient toujours traduits pareil d'une carte à l'autre.
 */
object CardTranslator {
    private val client by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.FRENCH)
                .build()
        )
    }
    private val cache = ConcurrentHashMap<String, String>()
    @Volatile private var modelReady = false

    /** Mots-clés officiels du jeu → nom français utilisé partout dans l'appli. */
    private val keywords = linkedMapOf(
        "Activate: Main" to "Activation : Principale",
        "Activate: Battle" to "Activation : Combat",
        "On Play" to "À l'entrée en jeu",
        "When Attacking" to "Quand il attaque",
        "When Blocking" to "Quand il bloque",
        "On KO" to "Quand il est KO",
        "Once per turn" to "Une fois par tour",
        "Auto" to "Auto",
        "Permanent" to "Permanent",
        "Counter" to "Riposte",
        "Awaken" to "Éveil",
        "Blocker" to "Bloqueur",
        "Double Strike" to "Double frappe",
        "Triple Strike" to "Triple frappe",
        "Critical" to "Critique",
        "Barrier" to "Barrière",
        "Fusion Evolve" to "Évolution fusion",
        "Evolve" to "Évolution",
        "Dual Attack" to "Double attaque",
        "Deflect" to "Déviation",
        "Ki" to "Ki",
    )
    private val bracket = Regex("""\[([^\[\]]+)]""")

    /** Traduction déjà connue (sinon null) : permet un affichage immédiat sans clignotement. */
    fun cached(text: String): String? = cache[text]

    /** Traduit [text] ; renvoie null si la traduction est impossible (pas de réseau au 1er usage…). */
    suspend fun translate(text: String): String? {
        if (text.isBlank()) return text
        cache[text]?.let { return it }
        return runCatching {
            if (!modelReady) {
                client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                modelReady = true
            }
            text.lines().joinToString("\n") { line ->
                if (line.isBlank()) line else client.translate(protectKeywords(line)).await()
            }
        }.getOrNull()?.also { cache[text] = it }
    }

    private fun protectKeywords(line: String) = bracket.replace(line) { m ->
        val inner = m.groupValues[1].trim()
        val fr = keywords.entries.firstOrNull { it.key.equals(inner, ignoreCase = true) }?.value
        if (fr != null) "[$fr]" else m.value
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
