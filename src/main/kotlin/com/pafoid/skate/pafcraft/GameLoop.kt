package com.pafoid.skate.pafcraft

import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.Light
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.utils.Color
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.Transform
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*

class GameLoop {

    private val loader = VAOLoader()

    private lateinit var rawModel: RawModel
    private lateinit var texture: Texture
    private lateinit var texturedModel: TexturedModel
    lateinit var entity: Entity

    fun start() {
        texture = AssetPool.getTexture(Texture.WHITE)
        rawModel = ObjLoader().loadObjModel(ObjLoader.CUBE, loader)
        texturedModel = TexturedModel(rawModel, texture)
        entity = Entity(texturedModel, Transform(Vector3f(0f, 0f, -25f)))
    }

    fun tick(dt: Float) {
        entity.rotate(0f, 1f)
    }
}