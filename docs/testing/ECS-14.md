# ECS-14 — Seeded deterministic RNG service

1. Create a new project, open the `FeatureShowcase` scene and press Play: top right, under **Cycle fog**, a line reads "Random seed N, dice a b c d e" (the console also logs it).
2. Stop and press Play again: the seed and the five dice values are exactly the same.
3. Open Project Settings > **Random**: the seed field shows the same N. Type `1234` and press Enter, then Play: the line shows seed 1234 and different dice. Repeat Play: same dice again.
4. Click **Randomize**: the seed changes. Press Ctrl+Z: the previous seed (1234) comes back. Command History shows "Set Random Seed".
5. Type `abc` in the seed field and press Enter: the field goes back to the current seed (invalid input is ignored).
6. Click OK, close and reopen the project: the seed is kept.
7. Start the editor with `-Dskate.seed=7`: Project Settings > Random shows a warning that 7 overrides the seed, and Play shows seed 7.
8. Export the project and run it: the showcase shows the project's seed. Add `"randomSeed": 99` to `game.settings.json` next to `game.bin` and run again: it shows seed 99; Restart from the pause menu shows the same dice.
