package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.assets.Sprite
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.editor.PropertiesWindow

class ScaleGizmo(scaleSprite: Sprite, propertiesWindow: PropertiesWindow): Gizmo(scaleSprite, propertiesWindow) {

    override fun editorUpdate(dt: Float) {
        val go = activeGameObject
        if(go != null) {
            if(xAxisActive && !yAxisActive) go.transform.scale.x -= MouseListener.getWorldDx()
            if(yAxisActive) go.transform.scale.y -= MouseListener.getWorldDy()
        }

        super.editorUpdate(dt)
    }
}