# Procedural brushed-steel texture for assets/textures/metal.png (AST-10). Deterministic, no third-party source.
import random, sys
from PIL import Image, ImageFilter
N = 1024
rng = random.Random(20261105)
img = Image.new("L", (N, N))
px = img.load()
# Horizontal brushing: per-row base tone + per-pixel grain, then a strong horizontal blur.
rows = [rng.gauss(0, 6) for _ in range(N)]
for y in range(N):
    for x in range(N):
        px[x, y] = max(0, min(255, int(160 + rows[y] + rng.gauss(0, 14))))
img = img.filter(ImageFilter.BoxBlur(0)).resize((N, N))
streaks = img.transform((N, N), Image.AFFINE, (1, 0, 0, 0, 1, 0)).filter(ImageFilter.GaussianBlur(radius=0.6))
# Blur along x only: shrink x, then scale back up.
brushed = streaks.resize((N // 32, N), Image.BILINEAR).resize((N, N), Image.BICUBIC)
fine = Image.blend(brushed, streaks, 0.25)
# Make it tile: blend with a half-offset copy across the seams.
from PIL import ImageChops
off = ImageChops.offset(fine, N // 2, N // 2)
mask = Image.new("L", (N, N))
m = mask.load()
for y in range(N):
    for x in range(N):
        d = min(x, N - 1 - x, y, N - 1 - y)
        m[x, y] = 255 if d > 64 else int(255 * d / 64)
tiled = Image.composite(fine, off, mask)
# Cool steel tint.
rgb = Image.merge("RGB", (tiled.point(lambda v: int(v * 0.96)), tiled.point(lambda v: int(v * 0.98)), tiled))
rgb.save(sys.argv[1], optimize=True)
