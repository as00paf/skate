package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector3f
import kotlin.random.Random

class CloudSystem(private val count: Int = 20) : Component() {
    
    private val loader = VAOLoader()
    private val cloudTextures = arrayOf(
        "assets/textures/clouds/cloud1.png",
        "assets/textures/clouds/cloud2.png",
        "assets/textures/clouds/cloud3.png"
    )

    override fun start() {
        val scene = SceneManager.getCurrentScene() ?: return
        val random = Random(42) // Seeded for consistency

        for (i in 0 until count) {
            val cloud = GameObject("Cloud_$i")
            val x = random.nextFloat() * 1000f - 500f
            val y = random.nextFloat() * 50f + 100f // High in the sky
            val z = random.nextFloat() * 1000f - 500f
            
            cloud.transform.translation.set(x, y, z)
            val scale = random.nextFloat() * 20f + 20f
            cloud.transform.scale.set(scale, scale * 0.5f, scale)
            
            // Randomly rotate to add variety
            cloud.transform.rotation.y = random.nextFloat() * 360f

            val texPath = cloudTextures[random.nextInt(cloudTextures.size)]
            val model = TexturedModel(AssetPool.getRawModel(ObjLoader.CUBE, loader), AssetPool.getTexture(texPath))
            
            cloud.addComponent(Entity(model = model).apply {
                reflectivity = 0f
                shininess = 0f
            })
            cloud.addComponent(CloudDrift(speed = random.nextFloat() * 2f + 0.5f))
            cloud.addComponent(NonPickable())
            
            scene.addGameObjectToScene(cloud)
        }
    }
}
