package com.pafoid.skate.engine.render.rhi.vulkan

/**
 * Texture orientation convention of the Vulkan backend (VK-05, docs/design/VK-05.md).
 *
 * - **Offscreen render targets are stored like GL**: memory row 0 is the bottom row (NDC y = -1). Their passes use a
 *   positive viewport height, so texture coordinates, shadow-map lookups, `gl_FragCoord` and bottom-left scissor and
 *   readback rects all mean what they mean in GL, with no shader change. Seen from Vulkan the image is mirrored,
 *   which mirrors winding: front faces are clockwise there.
 * - **The back buffer is stored top row first**, as the display scans it out. Its passes keep the flipped (negative
 *   height) viewport and counter-clockwise front faces, and a blit from a target to the back buffer mirrors rows.
 *
 * So there is exactly one flip, at presentation. Pure: unit-tested without a device.
 */
internal object VkOrientation {
    /**
     * Vulkan viewport (x, y, width, height) for the bottom-left GL viewport rect: unchanged for a target, mirrored with
     * a negative height for the back buffer ([topFirst]).
     */
    fun viewport(x: Int, y: Int, width: Int, height: Int, targetHeight: Int, topFirst: Boolean): FloatArray =
        if (topFirst) floatArrayOf(x.toFloat(), (targetHeight - y).toFloat(), width.toFloat(), -height.toFloat())
        else floatArrayOf(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())

    /** Top-left offset (x, y) of the bottom-left GL rect in the image's own rows. */
    fun rectOffset(x: Int, y: Int, height: Int, imageHeight: Int, topFirst: Boolean): IntArray =
        if (topFirst) intArrayOf(x, imageHeight - (y + height)) else intArrayOf(x, y)

    /** Whether triangles counter-clockwise in Vulkan framebuffer space are front faces (GL's CCW front faces). */
    fun frontFaceCounterClockwise(topFirst: Boolean): Boolean = topFirst

    /**
     * vkCmdBlitImage offsets for a bottom-left GL [src] rect to a bottom-left GL [dst] rect: (x0, y0, x1, y1) for the
     * source then the destination. Swapping y0/y1 on one side mirrors rows when exactly one image is top-first.
     */
    fun blitOffsets(
        sx: Int, sy: Int, sw: Int, sh: Int, srcHeight: Int, srcTopFirst: Boolean,
        dx: Int, dy: Int, dw: Int, dh: Int, dstHeight: Int, dstTopFirst: Boolean,
    ): IntArray {
        // Each rect as rows in its own image, listed from the GL top row (y + h) down to the GL bottom row (y)
        val srcTop = if (srcTopFirst) srcHeight - (sy + sh) else sy + sh
        val srcBottom = if (srcTopFirst) srcHeight - sy else sy
        val dstTop = if (dstTopFirst) dstHeight - (dy + dh) else dy + dh
        val dstBottom = if (dstTopFirst) dstHeight - dy else dy
        return intArrayOf(sx, srcTop, sx + sw, srcBottom, dx, dstTop, dx + dw, dstBottom)
    }

    /**
     * Index of the image row (within a read rect of [rows] rows) that holds GL row [row] of the rect (0 = bottom):
     * readback returns GL order whatever the storage.
     */
    fun readRow(row: Int, rows: Int, topFirst: Boolean): Int = if (topFirst) rows - 1 - row else row
}
