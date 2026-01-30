package com.pafoid.skate.engine.imgui

import com.pafoid.skate.engine.utils.Color
import imgui.ImGui
import imgui.flag.ImGuiCol.*

object ImGuiStyleManager {

    fun setupStyle(s:Style = Style()) {
        with(ImGui.getStyle()){
            windowRounding = s.rounding
            childRounding = s.rounding
            frameRounding = s.rounding
            grabRounding = s.rounding
            popupRounding = s.rounding
            scrollbarRounding = s.rounding

            setColor(Text, s.theme.text)
            setColor(TextDisabled, s.theme.disabledText)
            setColor(WindowBg, s.theme.background)
            setColor(ChildBg, s.theme.background)
            setColor(PopupBg, s.theme.background)
            setColor(Border, s.theme.borders)
            setColor(BorderShadow, Color.TRANSPARENT)
            setColor(FrameBg, 0.21f, 0.22f, 0.26f, 1.00f)
            setColor(FrameBgHovered, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(FrameBgActive, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(TitleBg, s.theme.background)
            setColor(TitleBgActive, s.theme.background)
            setColor(TitleBgCollapsed, s.theme.background)
            setColor(MenuBarBg, s.theme.background)
            setColor(ScrollbarBg, s.theme.background)
            setColor(ScrollbarGrab, 0.31f, 0.31f, 0.31f, 1.00f)
            setColor(ScrollbarGrabHovered, 0.41f, 0.41f, 0.41f, 1.00f)
            setColor(ScrollbarGrabActive, 0.51f, 0.51f, 0.51f, 1.00f)
            setColor(CheckMark, 0.80f, 0.80f, 0.80f, 1.00f)
            setColor(SliderGrab, 0.39f, 0.39f, 0.39f, 1.00f)
            setColor(SliderGrabActive, 0.51f, 0.51f, 0.51f, 1.00f)
            setColor(Button, 0.21f, 0.22f, 0.26f, 1.00f)
            setColor(ButtonHovered, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(ButtonActive, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(Header, 0.21f, 0.22f, 0.26f, 1.00f)
            setColor(HeaderHovered, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(HeaderActive, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(Separator, 0.21f, 0.22f, 0.26f, 1.00f)
            setColor(SeparatorHovered, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(SeparatorActive, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(ResizeGrip, 0.21f, 0.22f, 0.26f, 1.00f)
            setColor(ResizeGripHovered, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(ResizeGripActive, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(Tab, 0.21f, 0.22f, 0.26f, 1.00f)
            setColor(TabHovered, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(TabActive, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(TabUnfocused, 0.21f, 0.22f, 0.26f, 1.00f)
            setColor(TabUnfocusedActive, 0.30f, 0.31f, 0.36f, 1.00f)
            setColor(PlotLines, 0.61f, 0.61f, 0.61f, 1.00f)
            setColor(PlotLinesHovered, 1.00f, 0.43f, 0.35f, 1.00f)
            setColor(PlotHistogram, 0.90f, 0.70f, 0.00f, 1.00f)
            setColor(PlotHistogramHovered, 1.00f, 0.60f, 0.00f, 1.00f)
            setColor(TextSelectedBg, 0.26f, 0.59f, 0.98f, 0.35f)
            setColor(DragDropTarget, 1.00f, 1.00f, 0.00f, 0.90f)
            setColor(NavHighlight, 0.26f, 0.59f, 0.98f, 1.00f)
            setColor(NavWindowingHighlight, 1.00f, 1.00f, 1.00f, 0.70f)
            setColor(NavWindowingDimBg, 0.80f, 0.80f, 0.80f, 0.20f)
            setColor(ModalWindowDimBg, 0.80f, 0.80f, 0.80f, 0.35f)
        }
    }
}