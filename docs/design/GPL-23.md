# GPL-23: a fuller skate demo (design note)

Status: **proposed**, waiting for approval. No code until it is approved.

## In short

Turn the MainScene from "a board, a ledge and a ramp" into a small skatepark you can play for a few minutes:

- **A 40 × 40 m park** with four zones (street, flat-ground, transition, bowl) around a central start plaza.
- **More tricks:** the five board tricks we have, plus grinds, manuals and 180/360 body spins.
- **Combos:** chain tricks without touching flat ground, link them with manuals, bank the points on a clean landing, lose them on a bail.
- **Sessions:** a 2-minute run with six goals and a best score, or free skate with no timer.
- **One HUD** showing score, combo, timer, goals and a balance meter.
- **Spot cameras per zone** for the fixed camera mode, and **control hints** for whichever control scheme is selected.

The decisions I need from you are in §9. Everything else follows from those.

## 1. What exists today

| Area | Today |
|---|---|
| Park | 20 × 20 m asphalt floor, one concrete ledge, a quarter pipe behind the spawn, a funbox. |
| Riding | Push (top speed 6 m/s), ride, ollie (about 0.76 m high, 0.79 s of air), grind on anything tagged grindable, manual with a balance angle, bail. |
| Tricks (GPL-21) | Ollie, kickflip, heelflip, shove-it, 360 shove-it. Each landed trick adds fixed points (100–800). A trick not finished before touchdown is a bail. |
| Control schemes (GPL-21) | EA Skate (default), Session, Skater XL, THPS, chosen in the pause menu. |
| Cameras (GPL-22) | Third person (default), first person, one fixed camera, cycled by the HUD Camera button or C. |
| HUD | Score (top left), Camera button (top right), trick name (bottom centre). The exported game also adds its own HUD with a timer that overlaps the Camera button. |
| Assets | No trick animation clips: the rider holds the same air pose for every trick. Only the board spins. |

## 2. Park layout

The floor grows from 20 × 20 m to **40 × 40 m**. The skater spawns in the centre facing +Z (forward), as today. Each zone holds about one line, a sequence you can skate in one push. At 6 m/s, crossing a zone takes 2–3 s.

```
                         +Z (forward from the spawn)
         ┌──────────────────────────────────────────────┐
         │              STREET LINE (north)              │
         │  Euro-gap deck ─► 5-stair + hubba + handrail  │
         │  manual pad · flat rail · grind box           │
         │                                               │
  -X     │ FLAT-GROUND          START          TRANSITION│   +X
 (west)  │ kicker ─► funbox     PLAZA          half pipe │ (east)
         │ manual pads          (spawn,        bank · QP │
         │ 3-stair + bank       ledge)                   │
         │                                               │
         │              BOWL CORNER (south-east)         │
         │        quarter pipe (today's) · bowl          │
         └──────────────────────────────────────────────┘
                              -Z
```

| Zone | Where (centre, m) | Obstacles (existing prefabs unless marked **new**) | Line it is built for |
|---|---|---|---|
| Start plaza | (0, 0) | Today's concrete ledge, flat space | Warm-up: ollie, flips, a first grind |
| Street line | (0, 12) | **New Euro-gap deck** (bank up to a 0.85 m platform), 5-stair with hubba and handrail at its end, manual pad, flat rail, grind box | Ollie the deck, flip down the 5-stair or grind the hubba, manual the pad, grind the rail |
| Flat-ground | (-12, 0) | Kicker into the funbox, two manual pads, 3-stair with a bank leading up to it | Kicker air, funbox grind, manual-to-manual combos |
| Transition | (12, 0) | Half pipe (3 m wide), bank, quarter pipe | Back-and-forth airs, tricks out of the coping |
| Bowl corner | (10, -10) | Bowl, today's quarter pipe | Carving, flow |

Two layout problems need a fix, not just placement:

- **Reaching the stair sets.** Stairs go down from a top landing, and today nothing leads up to it: you can only hit them from below. I propose a **new "Euro-gap deck" prefab**: a bank up to a flat 0.85 m deck the length of the 5-stair landing (the obstacle list already names it). For the 3-stair, a bank placed against its landing is enough.
- **Speed.** The highest obstacles (5-stair, 0.85 m; half pipe, 1.2 m) are within ollie and push limits. Nothing new is needed in the physics.

All placement lives in `PrefabsGenerator.spawnDefaultsSync`. As with every default scene, old projects are not migrated (rule 8).

## 3. Trick set

| Group | Tricks | Status | How it is done |
|---|---|---|---|
| Board tricks | Ollie, kickflip, heelflip, shove-it, 360 shove-it | Done (GPL-21) | Per control scheme |
| Grinds | 50-50 (any grind) | Works, unnamed | Land on a grindable edge |
| Grinds, named | 50-50, 5-0, nosegrind, boardslide | **New** | Lean the stick while grinding (back = 5-0, forward = nosegrind); a boardslide when the board lands across a rail |
| Manuals | Manual, nose manual | Manual works; nose manual **new** | Manual button; nose manual = manual + stick forward on entry |
| Body spins | FS/BS 180, FS/BS 360 | **New** (strings exist) | Hold left/right while popping; the spin follows the turn already accumulated in the air (the air turn rate rises while spinning) |
| Grabs | Indy, melon | **Out of scope here** | Need grab animations first (see §8) |

Spins combine with board tricks and are named together, e.g. "FS 180 Kickflip".

## 4. Combos and score

A **combo** is every trick done between two touchdowns on flat ground. Grinds and manuals keep it alive.

| Rule | Value (all in the skater config, tunable) |
|---|---|
| Base points | Ollie 100, shove-it 300, flip 500, 360 shove-it 800 (today's). Spin 180 +200, 360 +500. |
| Grinds and manuals | 200 on entry, then +100 per second (grind) or +50 per second (manual) |
| Repeats in one combo | The same trick scores 100 %, then 50 %, 25 %, 10 % |
| Multiplier | Number of tricks in the combo (an ollie on its own is ×1) |
| Combo value | (sum of trick points) × multiplier |
| Link window | After landing on flat ground you have **0.3 s** to start a manual (or pop again from a manual) before the combo banks |
| Bank | When the link window passes, the combo value adds to the score |
| Bail | The combo is lost (HUD shows it in red, struck through) |

Example: kickflip (500) into a 50-50 for 2 s (200 + 200) into a manual for 1 s (200 + 50), then a heelflip out (500) gives (500 + 400 + 250 + 500) × 4 = **6 600**.

This logic is pure (no ECS or GL) and fully unit-tested, like the trick state machine.

## 5. Sessions and goals

Two modes, picked in the pause menu like the control scheme:

- **Free skate** (no timer): score and combos only.
- **Session**: a **2-minute** run. The timer stops while paused. The run ends with a summary panel (score, best combo, goals done), and Restart starts a new one.

Six session goals, shown as a checklist. Each one teaches a zone or a mechanic:

| # | Goal | Zone |
|---|---|---|
| 1 | Score 10 000 | Any |
| 2 | Kickflip the 5-stair | Street |
| 3 | Grind the hubba for 1 s | Street |
| 4 | Manual the length of the manual pad | Flat-ground |
| 5 | Land a 4-trick combo | Any |
| 6 | 360 shove-it out of the half pipe | Transition |

The **best score** is saved per project in `game.settings.json`, next to the control scheme. Goals use the trick names and zones above, so they are data (a list in a config file) and easy to change.

## 6. HUD

One HUD, built by `PrefabsGenerator` in the MainScene, used by both editor Play and the exported game. The exported game's separate HUD (`GameUi`, with its overlapping timer) goes away.

```
┌──────────────────────────────────────────────────────────────┐
│ SCORE 12 400             01:37                    [Camera]   │
│ Best 18 200        FS 180 Kickflip + 50-50 + Manual  ×3       │
│ □ Score 10 000                                                │
│ ☑ Kickflip the 5-stair                                        │
│ □ Grind the hubba 1 s                                         │
│ …                                                             │
│                                                               │
│                       ◄────●──────►   (balance, grind/manual) │
│                        HEELFLIP                               │
│              Back, flick up-right  (hint, current scheme)     │
└──────────────────────────────────────────────────────────────┘
```

| Element | Where | Shown when |
|---|---|---|
| Score and best | Top left | Always |
| Timer | Top centre | Session mode |
| Camera button | Top right | Always (as today) |
| Combo line (trick list + multiplier) | Under the timer | During a combo; it flashes green on bank and red on bail |
| Goals checklist | Left | Session mode; can be hidden from the pause menu |
| Balance meter | Bottom centre | During a grind or manual |
| Trick name | Bottom centre | As today |
| Control hint | Under the trick name | For the next unfinished goal, in the selected scheme's terms, e.g. "Back, flick up-left" (EA Skate) or "X + Left" (THPS). Can be turned off. |

The widgets (text, list, meter) need the runtime UI from skate-gameui. The balance meter is the only new widget type (a progress bar exists, so it may be enough).

## 7. Cameras and control schemes in the demo

- **Third person** stays the default.
- **First person** works as today. It suits the street line.
- **Fixed cameras become spot cameras.** Instead of one `FixedCamera`, each zone gets a camera placed like a filmer's angle. In fixed mode the view switches to the camera of the zone the skater is in, and it turns to follow the skater (position fixed, aim follows). The Camera button still cycles the three modes.
- **Control schemes** need no change. The control hints (§6) and the goal texts read the selected scheme, so every goal is explained in the scheme the player picked.

## 8. Plan

Each step builds, keeps the demo playable and ships with unit tests. The order allows a playable result after each step.

| Step | Content | Size | Needs |
|---|---|---|---|
| 1 | Park layout: 40 m floor, zones, new Euro-gap deck prefab | M | none |
| 2 | Combo and score system (pure logic and tests), HUD combo line | M | none |
| 3 | Named grinds, nose manual, body spins | M | none |
| 4 | Session mode, goals, summary, best score | M | GUI (summary panel, checklist) |
| 5 | Unified HUD (timer, balance meter, hints); `GameUi` HUD removed | S–M | GUI review |
| 6 | Spot cameras per zone | S | none |
| Later | Trick and grab animation clips | | ANI (clips), then grabs |

Out of scope: grabs (no clips), multiplayer sessions, online leaderboards, new sounds (a separate AUD request: board pop, grind, landing).

## 9. Decisions for you

| # | Question | My recommendation |
|---|---|---|
| D1 | Park size: 40 × 40 m with four zones, or keep it small (30 × 30 m, street and transition only)? | **40 × 40 m**: room for flow and for the goals |
| D2 | Add the new Euro-gap deck prefab so the stair sets can be skated from the top? | **Yes**; otherwise the 5-stair goal is not doable |
| D3 | Combo rules: link window and manual-linking as in §4 (Tony Hawk-like), or simpler (each landed trick scores on its own, no combos)? | **Combos as in §4**; it is what makes a session fun |
| D4 | Session length: 2 minutes, and free skate as the default mode? | **2 min, free skate by default** (the demo opens without pressure) |
| D5 | Are these six goals right? Add or drop any? | As listed |
| D6 | Control hints on screen by default? | **On**, with a pause menu switch to turn them off |
| D7 | Spot cameras that follow the skater per zone, replacing the single fixed camera? | **Yes** |
| D8 | Drop the exported game's separate HUD in favour of the one MainScene HUD? | **Yes**; one HUD in both hosts, no overlap |
