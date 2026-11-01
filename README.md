# DeckZ

Compagnon stratégique Android pour le jeu de cartes **Dragon Ball Super Card Game Fusion World**.
On choisit un Leader et on obtient les données de la méta pour construire son deck, plus un plan de bataille contre chaque autre leader.

Appli personnelle et non officielle, sans lien avec Bandai. Pas de compte, pas de backend, en français.

## Fonctionnalités

- **Leaders** : grille avec tier, rang, nombre de decks, filtres par couleur et recherche.
- **Fiche leader** : recto/verso animé, condition d'éveil, stats méta, cartes clés (% d'inclusion, copies moyennes), decks gagnants, courbe de coûts.
- **Plans de bataille** : pour un matchup donné, verdict indicatif, plan early / mid / end game, pièges, cartes adverses à surveiller, mulligan, notes de la communauté et lexique débutant. Tout fonctionne hors ligne.
- **Types de deck** : quand un leader a plusieurs builds distincts dans les listes en cache, on peut choisir la variante adverse.
- **Mise à jour** : au premier lancement, chaque semaine en arrière-plan (Wi-Fi + en charge) et via un bouton manuel. Si une source échoue, le cache reste affiché avec un bandeau discret.

## Compiler

Prérequis : JDK 17 ou plus récent, Android SDK avec la plateforme 37. Le wrapper Gradle fournit le reste (Gradle 9.7.1, AGP 9.4.1, Kotlin 2.4.10).

Créer `local.properties` à la racine (fichier non versionné) :

```properties
sdk.dir=C\:\\Android\\Sdk
```

Puis :

```bash
./gradlew assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`. Pour l'installer sur un téléphone branché :

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Signature** : un build debug est signé avec la clé de debug de la machine. Un APK compilé sur un autre PC ne s'installe pas par-dessus l'ancien : il faut désinstaller d'abord, et le cache sera retéléchargé (2 à 3 minutes).

## Organisation du code

| Emplacement | Rôle |
| --- | --- |
| `data/ScrapeConfig.kt` | **Toutes** les URL, clés JSON et réglages de scraping |
| `data/Parsers.kt` | Parsing pur (HTML/flux Next.js et JSON vers modèles), sans Android |
| `data/Scraper.kt` | Réseau : User-Agent explicite, 1 requête/seconde maximum |
| `data/Repository.kt` | Synchronisation incrémentale, un échec de source garde le cache |
| `data/SyncWorker.kt` | Mise à jour hebdomadaire (WorkManager) |
| `data/db/` | Room : entités, DAO, migrations |
| `strategy/` | Plans de bataille : profils, analyse des textes de cartes, variantes, moteur de matchup |
| `ui/` | Écrans Compose et thème |
| `assets/strategy/profiles.json` | Profils de leaders rédigés à la main |
| `DeckZApp.kt` | Conteneur manuel de dépendances (pas de Hilt) |

Stack : Kotlin, Jetpack Compose + Material 3, Navigation Compose, Room, WorkManager, OkHttp, Jsoup, kotlinx.serialization, Coil. `minSdk` 26.

## Sources de données

- **DeckPlanet** (site Next.js) : les leaders, decks et résultats sont lus dans le flux RSC des pages méta (`self.__next_f.push`). Il n'y a pas de `__NEXT_DATA__`.
- **DeckPlanet, API JSON** : cartes clés par leader (`/api/meta/...`), base de cartes et listes de decks (`api.deckplanet.net/items/...`).
- **db-fusionworld.com** : deck profiles communautaires, listés via le sitemap. Bons et mauvais matchups, game plan et notes par matchup.

Les images de cartes sont chargées à l'exécution et ne sont pas dans ce dépôt.

## Si un site change

Tout se répare normalement dans `ScrapeConfig.kt` :

1. Récupérer la page avec curl et vérifier les clés JSON (`"leaders":[`, `"decks":[`, `"events":[`, `"deck_cards":[`), puis corriger les `KEY_*`.
2. Si une URL `/api/meta/...` ou `api.deckplanet.net/items/...` échoue, chercher le nouveau chemin dans les scripts du site (`/_next/static/chunks/*.js`).
3. Si le format de page change, adapter `RSC_PUSH_PREFIX` et `RSC_SCRIPT_MARKER`.
4. Si les images disparaissent, corriger `IMG_BASE` (l'URL complète est dans les `<link rel="preload">` d'une page de deck).
5. Retélécharger les fixtures et lancer le test (voir ci-dessous).

## Mettre à jour les plans de bataille

Les données de méta se mettent à jour seules sur le téléphone. **Pas les textes rédigés** : `assets/strategy/profiles.json` est intégré à l'APK, il faut donc le modifier et publier un nouvel APK à chaque évolution notable de la méta (par exemple une nouvelle extension).

- `leaders` : un profil par numéro de carte leader (archétype, vitesse, tour fort, plan, mulligan, menaces, conseils).
- `matchups` : notes curées, clé `"monLeader|adversaire"`, `avantage` de -2 à 2.

Un leader sans profil garde un plan généré à partir de ses cartes réelles, simplement moins détaillé. Le verdict reste **indicatif** : aucune source ne publie de taux de victoire par matchup. Sans note ni guide sur une paire, il reste en général « Équilibré ».

## Base de données

Tout changement du schéma Room demande d'incrémenter la version dans `AppDb` et d'ajouter une migration (voir `MIGRATION_1_2`, `MIGRATION_2_3`), sinon l'appli efface son cache à la mise à jour.

## Tests

Un seul test : `ParserTest` vérifie les parseurs sur des pages réelles, ainsi que le chargement des profils et le moteur de matchup.

Les fixtures sont des copies de pages de sites tiers : elles ne sont pas versionnées, et le test est ignoré si elles sont absentes. Pour les retélécharger :

```bash
./tools/fetch-fixtures.sh
./gradlew testDebugUnitTest
```

## Crédits

- Dragon Ball et Dragon Ball Super Card Game Fusion World sont des marques de leurs ayants droit (Bandai, etc.).
- Données de méta : [DeckPlanet](https://www.deckplanet.net). Deck profiles : [db-fusionworld.com](https://db-fusionworld.com).
- Polices [Bangers](https://fonts.google.com/specimen/Bangers) et [Inter](https://fonts.google.com/specimen/Inter), sous licence SIL Open Font License 1.1.
- L'icône de l'appli est un dessin original.
