# Profiler

The Profiler tells you how fast the editor and your game are running, and where the time goes. Use it when the viewport feels slow or jerky.

## What it is

A game draws many images per second. Each image is a **frame**. The Profiler measures:

- **Frame time**: how many milliseconds one frame takes. Lower is better. 16.7 ms is 60 frames per second.
- **FPS**: frames per second. Higher is better.
- **Draw calls**: how many times per frame the engine asks the graphics card to draw something. Many objects usually means many draw calls.
- **Physics step**: the time spent moving physics bodies and checking collisions.
- **RAM usage**: how much memory the editor uses.
- **Threads**: the CPU time of each thread of the program.

With **Show Graphs** on, each number is drawn as a graph over the last seconds, so you can see spikes.

## How to use it

1. Leave the Profiler open while you work or play the scene.
2. Watch the frame time graph. A flat line is good; tall spikes are the moments the game stutters.
3. Click **Freeze** to stop the graphs and read them calmly. Click **Unfreeze** to go live again.
4. Click **Reset Stats** to clear the history, for example before you test a change.
5. Turn on **Show Memory Stats** to watch memory. A line that keeps climbing can mean something is never released.

If the frame time is high, open the [Render Graph](render_graph.md) to see which render pass costs the most.

## Related windows

- [Render Graph](render_graph.md): the time of each render pass.
- [Console](console.md): warnings that often explain a slowdown.
- [Game Viewport](game_viewport.md): the scene being measured.
