package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.SystemPhase
import com.pafoid.skate.engine.getComponent

/** Resolves canvas scale and element rects; only re-resolves a canvas when its layout inputs changed. */
class UiLayoutSystem(
    private val screen: () -> UiScreenSize,
    private val dpiScale: () -> Float = { 1f },
) : System(priority = ExecutionPriority.LATE) {

    override val phase = SystemPhase.LATE_UPDATE

    private val canvases = query(UICanvasComponent::class)
    private val signatures = HashMap<Int, Int>()

    override fun update(dt: Float) {
        val size = screen()
        canvases.forEach { layoutCanvas(it, size) }
    }

    fun layoutCanvas(canvasGo: GameObject, size: UiScreenSize) {
        val canvas = canvasGo.getComponent<UICanvasComponent>() ?: return
        if (size.width <= 0 || size.height <= 0) return
        val dpi = dpiScale()
        val signature = 31 * UiTree.layoutSignature(canvasGo, size.width, size.height) + dpi.toRawBits()
        if (signatures[canvasGo.uId] == signature) return
        signatures[canvasGo.uId] = signature

        canvas.scale = UiCanvasScaler.scale(
            canvas.scaleMode, canvas.referenceWidth, canvas.referenceHeight,
            size.width.toFloat(), size.height.toFloat(), canvas.match, dpi,
        )
        canvas.width = size.width / canvas.scale
        canvas.height = size.height / canvas.scale
        val rects = UiLayout.resolve(UiTree.spec(canvasGo), canvas.width, canvas.height)
        assign(canvasGo, rects)
    }

    private fun assign(parent: GameObject, rects: Map<Int, UiRectF>) {
        for (child in parent.children) {
            val e = child.getComponent<UIElementComponent>() ?: continue
            rects[child.uId]?.let { e.rect = it }
            assign(child, rects)
        }
    }
}

private fun GameObject.isWidget() =
    getComponent<UIButtonComponent>() != null || getComponent<UISliderComponent>() != null || getComponent<UIListComponent>() != null

/** Hit-testing, hover/press, focus and gamepad/keyboard navigation for the active canvases. */
class UiInputSystem(private val source: UiInputSource) : System(priority = ExecutionPriority.LATE) {

    override val phase = SystemPhase.LATE_UPDATE
    override val runsAfter get() = listOf(UiLayoutSystem::class)

    private val canvases = query(UICanvasComponent::class)

    var focusedId: Int? = null
        private set

    /** True while the pointer is over a widget or a modal canvas is open, so gameplay can ignore that input. */
    var consumedInput = false
        private set

    private var pressedId: Int? = null
    private var sliderId: Int? = null
    private var lastPointer: Pair<Float, Float>? = null

    override fun update(dt: Float) {
        val all = canvases.entities
        // A visible modal canvas (e.g. a pause menu) keeps working while the scene itself is paused.
        val modalOpen = all.any { it.getComponent<UICanvasComponent>()?.let { c -> c.visible && c.modal } == true }
        if (!scene.isRunning && !modalOpen) {
            consumedInput = false
            return
        }
        process(all, source.frame(dt))
    }

    fun process(canvasObjects: List<GameObject>, f: UiInputFrame) {
        val active = activeCanvases(canvasObjects)
        val modal = active.any { it.getComponent<UICanvasComponent>()?.modal == true }
        val nodes = active.flatMap { UiTree.elements(it) }
        val widgets = nodes.filter { it.interactable && it.go.isWidget() }
        val byId = widgets.associateBy { it.go.uId }

        nodes.forEach { it.element.focused = false }
        widgets.forEach { n -> n.go.getComponent<UIButtonComponent>()?.hovered = false }
        if (focusedId !in byId) focusedId = null

        val hit = widgets.lastOrNull { pxRect(it).contains(f.pointerX, f.pointerY) }
        hit?.go?.getComponent<UIButtonComponent>()?.hovered = true
        val moved = lastPointer?.let { it.first != f.pointerX || it.second != f.pointerY } ?: false
        lastPointer = f.pointerX to f.pointerY
        consumedInput = hit != null || modal

        if (hit != null && moved) focusedId = hit.go.uId
        if (focusedId == null && modal) focusedId = UiFocusNavigator.navigate(null, focusables(widgets), UiDirection.DOWN)

        handlePointer(hit, byId, f)
        handleNavigation(widgets, byId, f)

        if (f.cancel) active.lastOrNull { it.getComponent<UICanvasComponent>()?.onCancel != null }
            ?.getComponent<UICanvasComponent>()?.onCancel?.invoke()

        focusedId?.let { id -> byId[id]?.element?.focused = true }
    }

    private fun activeCanvases(all: List<GameObject>): List<GameObject> {
        val visible = all.filter { it.getComponent<UICanvasComponent>()?.visible == true }
            .sortedBy { it.getComponent<UICanvasComponent>()!!.sortOrder }
        val topModal = visible.lastOrNull { it.getComponent<UICanvasComponent>()!!.modal }
        return if (topModal != null) listOf(topModal) else visible
    }

    private fun pxRect(n: UiNodeRef): UiRectF {
        val r = n.element.rect
        val s = n.canvas.scale
        return UiRectF(r.x * s, r.y * s, r.w * s, r.h * s)
    }

    private fun focusables(widgets: List<UiNodeRef>) = widgets.map { UiFocusable(it.go.uId, pxRect(it)) }

    private fun handlePointer(hit: UiNodeRef?, byId: Map<Int, UiNodeRef>, f: UiInputFrame) {
        if (f.pointerPressed && hit != null) {
            focusedId = hit.go.uId
            hit.go.getComponent<UIButtonComponent>()?.let { it.pressed = true; pressedId = hit.go.uId }
            hit.go.getComponent<UISliderComponent>()?.let { it.dragging = true; sliderId = hit.go.uId }
            hit.go.getComponent<UIListComponent>()?.let { list ->
                val row = (((f.pointerY - pxRect(hit).y) / hit.canvas.scale + list.scroll) / list.itemHeight).toInt()
                if (row in list.items.indices) {
                    if (row == list.selectedIndex) list.onActivated?.invoke(row) else list.select(row)
                }
            }
        }
        sliderId?.let { id ->
            val node = byId[id]
            val slider = node?.go?.getComponent<UISliderComponent>()
            if (node != null && slider != null && f.pointerDown) {
                val r = pxRect(node)
                slider.setValueFromUser(
                    UiWidgetMath.valueFromFraction(UiWidgetMath.trackFraction(f.pointerX, r.x, r.w), slider.min, slider.max, slider.step)
                )
            }
        }
        if (f.pointerReleased || !f.pointerDown && (pressedId != null || sliderId != null)) {
            val clicked = pressedId?.takeIf { it == hit?.go?.uId }?.let { byId[it] }
            pressedId?.let { byId[it]?.go?.getComponent<UIButtonComponent>()?.pressed = false }
            sliderId?.let { byId[it]?.go?.getComponent<UISliderComponent>()?.dragging = false }
            pressedId = null
            sliderId = null
            clicked?.go?.getComponent<UIButtonComponent>()?.onClick?.invoke()
        }
        if (f.scrollY != 0f) hit?.go?.getComponent<UIListComponent>()?.let { list ->
            val viewport = hit.element.rect.h
            val max = (list.items.size * list.itemHeight - viewport).coerceAtLeast(0f)
            list.scroll = (list.scroll - f.scrollY * list.itemHeight).coerceIn(0f, max)
        }
    }

    private fun handleNavigation(widgets: List<UiNodeRef>, byId: Map<Int, UiNodeRef>, f: UiInputFrame) {
        val candidates = focusables(widgets)
        for (d in UiDirection.values()) {
            if (d !in f.navigate) continue
            val current = focusedId?.let { byId[it] }
            if (current != null && adjustInPlace(current, d)) continue
            val from = current?.let { UiFocusable(it.go.uId, pxRect(it)) }
            UiFocusNavigator.navigate(from, candidates, d)?.let { focusedId = it }
        }
        if (f.next) focusedId = UiFocusNavigator.cycle(focusedId, candidates, true)
        if (f.previous) focusedId = UiFocusNavigator.cycle(focusedId, candidates, false)
        if (f.confirm) focusedId?.let { id ->
            val go = byId[id]?.go ?: return@let
            go.getComponent<UIButtonComponent>()?.onClick?.invoke()
            go.getComponent<UIListComponent>()?.let { it.onActivated?.invoke(it.selectedIndex) }
        }
    }

    /** Sliders consume left/right and lists consume up/down while there is room to move; returns true if consumed. */
    private fun adjustInPlace(node: UiNodeRef, d: UiDirection): Boolean {
        node.go.getComponent<UISliderComponent>()?.let { s ->
            if (d != UiDirection.LEFT && d != UiDirection.RIGHT) return false
            val step = if (s.step > 0f) s.step else (s.max - s.min) / 20f
            s.setValueFromUser(s.value + if (d == UiDirection.RIGHT) step else -step)
            return true
        }
        node.go.getComponent<UIListComponent>()?.let { l ->
            if (d != UiDirection.UP && d != UiDirection.DOWN) return false
            val target = l.selectedIndex + if (d == UiDirection.DOWN) 1 else -1
            if (target !in l.items.indices) return false
            l.select(target)
            l.scroll = UiWidgetMath.scrollToItem(target, l.items.size, l.itemHeight, node.element.rect.h, l.scroll)
            return true
        }
        return false
    }
}
