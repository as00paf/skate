# GPL-17: Deterministic skater prediction step, manual test

`SkaterPredictionStep` (game/net) is a kinematic skater: push, roll, brake, carve and ollie on flat ground, with no Bullet. It reads and writes only replicated state (`Transform` plus `NetState`) and keeps every value on the replication grid, so server and client compute the same state from the same inputs. The Feature Showcase's **Netcode** area runs it through NET-03 prediction over a simulated link.

1. `.\gradlew.bat run`. Create a **new project** and switch to the `FeatureShowcase` scene tab. A dark blue-grey **Netcode** pad sits in the front row between the prefab library and the animated character, with three boxes on it: a translucent green one, a blue one inside it, and an orange one.
2. Press **Play**, then click **Netcode** in the overlay legend. Within about 2 s the rider starts pushing and carving a circle on the pad, and ollies about every 2.5 s.
3. The **blue** box (the owner's prediction) stays on the **green** ghost (the server) and is just ahead of it, by its inputs still in flight. The **orange** box (what another player sees) trails clearly behind, but follows the same circle and the same jumps.
4. In the Hierarchy select `[Netcode] Session`. Properties shows **Latency, one way (ms)** = 120, **Packet Loss (%)** = 0, and live **Prediction Error (m)** / **Interpolation Lag (m)** readouts.
5. Set **Latency** to 400. The demo restarts. Orange now trails much further, while blue still responds at once and stays on the ghost.
6. Set **Packet Loss** to 20. The rider keeps circling. Blue may occasionally ease back onto the server path (an input was lost), and never stays apart.
7. Stop Play. The three boxes return to their edit positions, and nothing keeps running.
8. Switch the editor language to French: the four Properties labels and the legend entry (**Réseau : rangée de devant, au centre…**) are translated.

Automated: `.\gradlew.bat :test --tests "*SkaterPredictionStepTest" --tests "*LoopbackPredictionSystemTest" --tests "*FeatureShowcaseSmokeTest"`. `SkaterNet_ServerAndClientStepToIdenticalStatesFromTheSameInputs` and `SkaterNet_LoopbackClientMatchesTheServerBitForBitUnderLatency` check the client's state against the server's for every input, bit for bit.
