# ANI-20 — Video to Animation: playback and trim range

Needs the ANI-17 setup (Python with MediaPipe) and a local video of a few seconds.

1. **Video to Animation > Choose video...**: the first frame and the video info show; "Preparing playback… n%" appears
   for a moment.
2. Press **Play** (or Space): the video plays in the preview and loops. **Pause**, then the arrow buttons (or arrow keys)
   step one frame; the label shows "Frame n / total (seconds)". Click or drag on the bar under the preview to scrub.
3. Go to a frame near the start of the move, click **Set start** (I); go to its end, click **Set end** (O). The bar
   shows the kept part in blue and the label "Frames a to b: x.xx s". Play now loops inside that part.
4. **Create animation**: the progress bar runs over the kept part only (faster than the whole video). The new clip's
   length (Animation Editor, Length field) equals the x.xx s shown in step 3. Scrub inside the kept part: the green
   detected pose matches the person; outside it no pose is drawn.
5. **Whole video**, Create again: a second clip as long as the whole video.
