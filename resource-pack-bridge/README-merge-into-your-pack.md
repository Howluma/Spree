# SpreeCore 1.1 - resource pack model adapter for Minecraft 1.21.11

This is a MERGE-ONLY folder, not a standalone resource pack. Combine its `assets/` folder
with your existing Spree resource pack. Do NOT replace your pack.mcmeta with this zip.

## Why unmodified clients see vanilla items
The plugin adds the custom_model_data numeric float to recognized items but NEVER
changes minecraft:item_model. Players without this pack continue to see their vanilla
Diamond Swords, Diamond Axes, Crossbow, and Heart of the Sea. The JSON files in
`assets/minecraft/items/` select your models only when the client loads the pack,
with a normal Minecraft fallback for every other item.

## Models to replace with your actual Blockbench designs

| Item | Custom model data | Model path |
|---|---:|---|
| tundra (diamond_sword) | 721001 | `assets/spree/models/item/tundra_tanto.json` |
| butcherer (diamond_axe) | 721002 | `assets/spree/models/item/blood_butcherer.json` |
| stormbreaker (diamond_axe) | 721003 | `assets/spree/models/item/stormbreaker.json` |
| aquafier (diamond_sword) | 721004 | `assets/spree/models/item/aquafier.json` |
| darkheart (diamond_sword) | 721005 | `assets/spree/models/item/darkheart.json` |
| hailstorm (crossbow) | 721006 | `assets/spree/models/item/hailstorm.json` |
| bloodsbane (diamond_sword) | 721007 | `assets/spree/models/item/bloods_bane.json` |
| token (heart_of_the_sea) | 721050 | `assets/spree/models/item/spree_token.json` |

Each model file in this adapter is only a SAFE VANILLA-TEXTURE PLACEHOLDER; it will not
look unique until you replace it with your own Blockbench model and texture files.
Put your existing Blockbench model JSON at the indicated path. Ensure `textures`
fields reference the PNGs in your pack. Do not overwrite real model JSON with the
placeholders in this adapter.

If your pack already replaces one of `assets/minecraft/items/*.json`, merge the
custom_model_data range_dispatch cases into that definition instead of blindly
replacing it; otherwise other custom models, bow/crossbow states or animations
could be lost. In particular, the example crossbow fallback is static; merge
Hailstorm into your existing crossbow item-state definition to preserve loaded
crossbow animations.

Edit the selectors in BOTH config.yml (or /spree settings) and this bridge
if you need to change custom_model_data numbers. Do not edit model selectors
without updating the resource pack.

Minecraft 1.21.11 uses assets/minecraft/items/<item>.json item definitions,
not pre-1.21.4 `overrides` inside assets/minecraft/models/item/*.json.
