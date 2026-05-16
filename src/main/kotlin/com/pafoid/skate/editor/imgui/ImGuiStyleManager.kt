package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Style
import imgui.ImGui
import imgui.flag.ImGuiCol.Border
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

object ImGuiStyleManager {

    fun setupStyle(s: Style = Style()) {
        val t = s.theme
        with(ImGui.getStyle()){
            windowRounding = s.rounding
            childRounding = s.rounding
            frameRounding = s.rounding
            grabRounding = s.rounding
            popupRounding = s.rounding
            scrollbarRounding = s.rounding
            tabRounding = s.rounding

            windowPadding.set(s.windowPadding.x, s.windowPadding.y)
            framePadding.set(s.framePadding.x, s.framePadding.y)
            itemSpacing.set(s.itemSpacing.x, s.itemSpacing.y)
            itemInnerSpacing.set(s.itemInnerSpacing.x, s.itemInnerSpacing.y)
            touchExtraPadding.set(s.touchExtraPadding.x, s.touchExtraPadding.y)
            indentSpacing = s.indentSpacing
            scrollbarSize = s.scrollbarSize
            grabMinSize = s.grabMinSize

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
            setColor(NavHighlight, t.accent)
            setColor(NavWindowingHighlight, t.navWindowingHighlight)
            setColor(NavWindowingDimBg, t.navWindowingDimBg)
            setColor(ModalWindowDimBg, t.modalDimBg)
        }
    }
}
