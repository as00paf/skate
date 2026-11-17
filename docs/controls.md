# Skateboarding controls (GPL-21)

Pick a scheme in the pause menu (**Esc** → **Controls: …**). Each click picks the next scheme, and the choice is saved in `game.settings.json` (the project folder in the editor, next to `game.bin` in the exported game). On foot, every scheme walks the same way: WASD / left stick, Shift / LT sprint, Space / A jump, E / Y mounts and dismounts the board.

## Core trick set

Every scheme can do all five tricks. A flip or shove-it needs air time to finish: land before the board is round (0.4 s flip, 0.3 s shove-it, 0.5 s 360 shove-it) and you bail. Landed tricks score points (ollie 100, shove-it 300, flip 500, 360 shove-it 800), and the HUD shows the trick's name. Times and points are in the skater config under `tricks`: `Assets/Data/skater.character.json` in a project (edit it in the Properties window), else the engine default `engine:Data/skater.character.json` (GPL-26).

| Trick | EA Skate (default) | Session | Skater XL | THPS |
|---|---|---|---|---|
| Ollie | Pull the right stick back, then flick it up | Back foot (right stick) down, then front foot (left stick) up | Same as Session | **A** |
| Kickflip | Back, then up-left | Back foot down, front foot up-left | Same as Session | **X** + left |
| Heelflip | Back, then up-right | Back foot down, front foot up-right | Same as Session | **X** + right |
| Shove-it | Half circle from the back round to the top | Back foot half circle from down | Same as Session | **X** + down |
| 360 shove-it | Full circle from the back | Back foot full circle from down | Same as Session | **X** + up |

After a trick, the sticks must come back to the centre before the next one.

## Riding

| | EA Skate | Session | Skater XL | THPS |
|---|---|---|---|---|
| Steer | Left stick | Front foot (left stick) | **LT** / **RT** | Left stick |
| Push | Left stick forward, **LT** | Front foot forward, **LT** | Front foot forward, **A** | Left stick forward, **LT** |
| Manual | **LB** (balance: left stick) | **LB** (balance: front foot) | **LB** (balance: front foot) | **LB** (balance: left stick) |
| Quick ollie | **A** | none | none | **A** |

THPS can also flip after the ollie: press **X** + a direction in the air to turn the ollie into that flip.

## Keyboard

| Gamepad | Keyboard |
|---|---|
| Right stick (trick stick / back foot) | **I** up, **K** down, **J** left, **L** right |
| Left stick as front foot (Session, Skater XL) | **W A S D** |
| LT / RT turn (Skater XL) | **U** / **O** |
| A ollie, X flip (THPS) | **Space**, **J** |
| LT push | **Left Shift** (Skater XL: also **A** on the pad) |
| LB manual | **Left Ctrl** |

On the board, the riding context takes these keys and buttons over, so they don't sprint, crouch or jump. The camera's right-stick orbit pauses while riding in the stick schemes. Menus keep their keys and buttons (`ui` sits above the riding context, INP-10). Custom bindings (Key Bindings → Skateboard) apply on top of every scheme.

Code: `game/input/ControlSchemes.kt` (definitions), `SkateTrickInput.kt` (gestures), `FlickRecognizer.kt`. Tests: `ControlSchemesTest`, and `SkateTricksTest` (every scheme lands every core trick).
