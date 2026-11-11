package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Style
import com.pafoid.skate.editor.imgui.theme.EditorTheme
import com.pafoid.skate.editor.imgui.theme.ThemeColors
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import imgui.ImGui
import imgui.flag.ImGuiCol.Border
import imgui.flag.ImGuiCol.DockingEmptyBg
import imgui.flag.ImGuiCol.DockingPreview
import imgui.flag.ImGuiCol.TableBorderLight
import imgui.flag.ImGuiCol.TableBorderStrong
import imgui.flag.ImGuiCol.TableHeaderBg
import imgui.flag.ImGuiCol.TableRowBg
import imgui.flag.ImGuiCol.TableRowBgAlt
import imgui.flag.ImGuiCol.BorderShadow
import imgui.flag.ImGuiCol.Button
import imgui.flag.ImGuiCol.ButtonActive
import imgui.flag.ImGuiCol.ButtonHovered
import imgui.flag.ImGuiCol.CheckMark
import imgui.flag.ImGuiCol.ChildBg
import imgui.flag.ImGuiCol.DragDropTarget
import imgui.flag.ImGuiCol.FrameBg
import imgui.flag.ImGuiCol.FrameBgActive
import imgui.flag.ImGuiCol.FrameBgHovered
import imgui.flag.ImGuiCol.Header
import imgui.flag.ImGuiCol.HeaderActive
import imgui.flag.ImGuiCol.HeaderHovered
import imgui.flag.ImGuiCol.MenuBarBg
import imgui.flag.ImGuiCol.ModalWindowDimBg
import imgui.flag.ImGuiCol.NavHighlight
import imgui.flag.ImGuiCol.NavWindowingDimBg
import imgui.flag.ImGuiCol.NavWindowingHighlight
import imgui.flag.ImGuiCol.PlotHistogram
import imgui.flag.ImGuiCol.PlotHistogramHovered
import imgui.flag.ImGuiCol.PlotLines
import imgui.flag.ImGuiCol.PlotLinesHovered
import imgui.flag.ImGuiCol.PopupBg
import imgui.flag.ImGuiCol.ResizeGrip
import imgui.flag.ImGuiCol.ResizeGripActive
import imgui.flag.ImGuiCol.ResizeGripHovered
import imgui.flag.ImGuiCol.ScrollbarBg
import imgui.flag.ImGuiCol.ScrollbarGrab
import imgui.flag.ImGuiCol.ScrollbarGrabActive
import imgui.flag.ImGuiCol.ScrollbarGrabHovered
import imgui.flag.ImGuiCol.Separator
import imgui.flag.ImGuiCol.SeparatorActive
import imgui.flag.ImGuiCol.SeparatorHovered
import imgui.flag.ImGuiCol.SliderGrab
import imgui.flag.ImGuiCol.SliderGrabActive
import imgui.flag.ImGuiCol.Tab
import imgui.flag.ImGuiCol.TabActive
import imgui.flag.ImGuiCol.TabHovered
import imgui.flag.ImGuiCol.TabUnfocused
import imgui.flag.ImGuiCol.TabUnfocusedActive
import imgui.flag.ImGuiCol.Text
import imgui.flag.ImGuiCol.TextDisabled
import imgui.flag.ImGuiCol.TextSelectedBg
import imgui.flag.ImGuiCol.TitleBg
import imgui.flag.ImGuiCol.TitleBgActive
import imgui.flag.ImGuiCol.TitleBgCollapsed
import imgui.flag.ImGuiCol.WindowBg

/**
 * Applies editor themes (EDT-07). Themes come from [library] (the `*.theme.json` files); [sync] applies the
 * effective one ([preview] while the settings window shows a choice, else the saved one) whenever it changes, so a
 * switch takes effect on the next frame without a restart.
 */
object ImGuiStyleManager {

    var library: ThemeLibrary = ThemeLibrary(emptyList())

    /** Theme id the settings window is previewing; null when none (the saved theme applies). */
    var preview: String? = null

    /** Id of the theme currently applied to ImGui, if any. */
    var appliedId: String? = null
        private set

    /** The theme that should be showing: the previewed one, else [savedId], resolved through [library]. */
    fun effective(savedId: String): EditorTheme = library.resolve(preview ?: savedId)

    /** The editor's UI scale setting (EDT-57), multiplied with the theme's font scale. */
    var uiScale: () -> Float = { 1f }
    private var appliedScale = 1f

    /** Applies [effective] (and the UI scale) when it differs from what is showing. Call between frames (before `newFrame`). */
    fun sync(savedId: String) {
        val theme = effective(savedId)
        if (theme.id == appliedId && uiScale() == appliedScale) return
        apply(theme)
    }

    /** The font scale ImGui uses: the theme's own times the UI scale setting. */
    fun fontScale(themeScale: Float, uiScale: Float): Float = themeScale * uiScale

    /** Forgets what is applied (a new ImGui context starts with ImGui's default style), so [sync] reapplies. */
    fun invalidate() {
        appliedId = null
    }

    fun apply(theme: EditorTheme) {
        setupStyle(theme.style)
        appliedScale = uiScale()
        ImGui.getIO().fontGlobalScale = fontScale(theme.fontScale, appliedScale)
        appliedId = theme.id
    }

    fun setupStyle(s: Style = Style()) {
        val t = s.theme
        ThemeColors.current = t
        with(ImGui.getStyle()){
            windowRounding = s.rounding
            childRounding = s.rounding
            frameRounding = s.rounding
            grabRounding = s.rounding
            popupRounding = s.rounding
            scrollbarRounding = s.rounding
            tabRounding = s.rounding

            // The setters, not `windowPadding.set(...)`: imgui-java's ImVec2 getters return copies, so writing to
            // them never reached ImGui and every theme ran with ImGui's default spacing (EDT-72).
            setWindowPadding(s.windowPadding.x, s.windowPadding.y)
            setFramePadding(s.framePadding.x, s.framePadding.y)
            setItemSpacing(s.itemSpacing.x, s.itemSpacing.y)
            setItemInnerSpacing(s.itemInnerSpacing.x, s.itemInnerSpacing.y)
            setTouchExtraPadding(s.touchExtraPadding.x, s.touchExtraPadding.y)
            indentSpacing = s.indentSpacing
            scrollbarSize = s.scrollbarSize
            grabMinSize = s.grabMinSize
            windowBorderSize = s.windowBorderSize
            childBorderSize = s.windowBorderSize
            popupBorderSize = s.windowBorderSize
            frameBorderSize = s.frameBorderSize

            // Colors
            setColor(Text, t.text)
            setColor(TextDisabled, t.textDisabled)
            
            setColor(WindowBg, t.background)
            setColor(ChildBg, t.background)
            setColor(PopupBg, t.background)
            
            setColor(Border, t.border)
            setColor(BorderShadow, t.borderShadow)
            
            setColor(FrameBg, t.widgetBg)
            setColor(FrameBgHovered, t.widgetHover)
            setColor(FrameBgActive, t.widgetActive)
            
            setColor(TitleBg, t.background)
            setColor(TitleBgActive, t.background)
            setColor(TitleBgCollapsed, t.background)
            
            setColor(MenuBarBg, t.background)
            
            setColor(ScrollbarBg, t.background)
            setColor(ScrollbarGrab, t.scrollbarGrab)
            setColor(ScrollbarGrabHovered, t.scrollbarGrabHover)
            setColor(ScrollbarGrabActive, t.scrollbarGrabActive)
            
            setColor(CheckMark, t.accent)
            setColor(SliderGrab, t.accent)
            setColor(SliderGrabActive, t.accentHover)
            
            setColor(Button, t.widgetBg)
            setColor(ButtonHovered, t.widgetHover)
            setColor(ButtonActive, t.accent)
            
            setColor(Header, t.header)
            setColor(HeaderHovered, t.headerHover)
            setColor(HeaderActive, t.accent)
            
            setColor(Separator, t.border)
            setColor(SeparatorHovered, t.accent)
            setColor(SeparatorActive, t.accentHover)
            
            setColor(ResizeGrip, t.borderShadow)
            setColor(ResizeGripHovered, t.accent)
            setColor(ResizeGripActive, t.accentHover)
            
            setColor(Tab, t.tabInactive)
            setColor(TabHovered, t.tabHover)
            setColor(TabActive, t.tabActive)
            setColor(TabUnfocused, t.tabInactive)
            setColor(TabUnfocusedActive, t.tabActive)
            
            setColor(PlotLines, t.accent)
            setColor(PlotLinesHovered, t.accentHover)
            setColor(PlotHistogram, t.accent)
            setColor(PlotHistogramHovered, t.accentHover)
            
            setColor(TextSelectedBg, t.selection)
            setColor(DragDropTarget, t.accent)
            setColor(NavHighlight, t.navHighlight)
            setColor(NavWindowingHighlight, t.navWindowingHighlight)
            setColor(NavWindowingDimBg, t.navWindowingDimBg)
            setColor(ModalWindowDimBg, t.modalDimBg)

            // Tables and docking (EDT-40): without these, ImGui's dark defaults showed in light themes.
            setColor(TableHeaderBg, t.header)
            setColor(TableBorderStrong, t.border)
            setColor(TableBorderLight, t.border.withAlpha(0.5f))
            setColor(TableRowBg, t.background.withAlpha(0f))
            setColor(TableRowBgAlt, t.widgetBg.withAlpha(0.35f))
            setColor(DockingPreview, t.accent.withAlpha(0.7f))
            setColor(DockingEmptyBg, t.background)
        }
    }
}

/** Copy of this colour with alpha [a]. */
private fun org.joml.Vector4f.withAlpha(a: Float) = org.joml.Vector4f(x, y, z, a)
