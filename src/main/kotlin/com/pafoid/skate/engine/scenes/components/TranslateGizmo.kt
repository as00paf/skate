package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.assets.Sprite
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.editor.PropertiesWindow

class TranslateGizmo(arrowSprite: Sprite, propertiesWindow: PropertiesWindow): Gizmo(arrowSprite, propertiesWindow) {

    override fun editorUpdate(dt: Float) {
        val go = activeGameObject
        if(go != null) {
            if(xAxisActive && !yAxisActive) go.transform.translation.x -= MouseListener.getWorldDx()
            if(yAxisActive) go.transform.translation.y -= MouseListener.getWorldDy()
        }

        super.editorUpdate(dt)
    }
}