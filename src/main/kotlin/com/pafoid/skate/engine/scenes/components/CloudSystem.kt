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
            val x = random.nextFloat() * 2000f - 500f
            val y = random.nextFloat() * 100f + 150f // Higher and more varied
            val z = random.nextFloat() * 2000f - 1000f
            
            cloud.transform.translation.set(x, y, z)
            val scaleX = random.nextFloat() * 50f + 50f
            val scaleY = scaleX * (random.nextFloat() * 0.3f + 0.3f)
            cloud.transform.scale.set(scaleX, scaleY, 1f) // 2D scale since billboarding
            
            val texPath = cloudTextures[random.nextInt(cloudTextures.size)]
            val model = TexturedModel(AssetPool.getRawModel(ObjLoader.CUBE, loader), AssetPool.getTexture(texPath))
            
            cloud.addComponent(Entity(model = model).apply {
                reflectivity = 0f
                shininess = 0f
                isCloud = true
            })
            cloud.addComponent(CloudDrift(
                speed = random.nextFloat() * 5f + 2f,
                resetX = 1500f,
                startX = -1500f
            ))
            cloud.addComponent(NonPickable())
            
            scene.addGameObjectToScene(cloud)
        }
    }
}
