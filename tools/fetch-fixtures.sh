#!/usr/bin/env bash
# Télécharge les fixtures du test de parsing dans app/src/test/resources/ (non versionnées :
# ce sont des copies de pages de sites tiers). Une requête par seconde, comme l'appli.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/src/test/resources
UA="DeckZ/1.0 (fixtures de test)"
SITE=https://www.deckplanet.net
API=https://api.deckplanet.net/items
DECK=c4d767c5-2d41-4cd3-a80b-691671c5f7e0
GUIDE=https://db-fusionworld.com/en/articles/deck-profile-fs11-ssj3-son-goku-meta-fb10-dragon-ball-fusion-world
FIELDS="id,card_number,card_name,card_color,card_type,card_energy_cost,card_power,card_combo_power,card_skill_unstyled,card_traits,card_series,card_rarity,img_link,card_back_name,card_back_power,card_back_skill_unstyled,card_back_traits,variant_of"

mkdir -p "$OUT"
fetch() {
  curl -fsSL -A "$UA" "$2" -o "$OUT/$1"
  echo "$1"
  sleep 1
}

fetch meta_leaders.html "$SITE/en/fusion_world/meta?meta_section=leaders"
fetch meta_decks.html   "$SITE/en/fusion_world/meta?meta_section=decks"
fetch meta_results.html "$SITE/en/fusion_world/meta?meta_section=results"
fetch deck_page.html    "$SITE/en/fusion_world/deck/$DECK"
fetch guide_profile.html "$GUIDE"
fetch api_usage.json    "$SITE/api/meta/fusion_world/standard/community/leaders/4939/colors/Red/cards?limit=30"
fetch api_cards.json    "$API/fusion_world_cards?filter%5Bid%5D%5B_in%5D=4939,5124,5131,4201,4952&fields=$FIELDS"
fetch api_deck.json     "$API/fusion_world_decks/$DECK?fields=id,deck_name,deck_type,date_created,deck_cards.amount_in_deck,deck_cards.card_id"
