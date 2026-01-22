package scenes

import components.*
import marki.GameObject
import marki.renderer.Shader
import marki.renderer.Texture
import util.AssetPool

class LevelSceneInitializer : SceneInitializer() {

    override fun init(scene: Scene) {
        val cameraObject = scene.createGameObject("GameCamera")
        cameraObject.addComponent(GameCamera(scene.camera))
        scene.addGameObjectToScene(cameraObject)
        cameraObject.start()
        scene.addGameObjectToScene(cameraObject)
    }

    override fun loadResources(scene: Scene) {
        AssetPool.getShader(Shader.DEFAULT)
        AssetPool.addSpriteSheet(
            Texture.PETER_SPRITE,
            SpriteSheet(AssetPool.getTexture(Texture.PETER_SPRITE), 100, 100, 26, 0)
        )
        AssetPool.addSpriteSheet(
            Texture.BLOCKS_DECOS_SPRITE,
            SpriteSheet(AssetPool.getTexture(Texture.BLOCKS_DECOS_SPRITE), 32, 32, 14, 0)
        )
        AssetPool.addSpriteSheet(
            Texture.GIZMOS_SPRITE,
            SpriteSheet(AssetPool.getTexture(Texture.GIZMOS_SPRITE), 24, 48, 3, 0)
        )

        loadSounds()

        scene.gameObjects.forEach { go ->
            val spriteRenderer = go.getComponent(SpriteRenderer::class.java)
            val texture = spriteRenderer?.getTexture()
            if (texture != null) {
                val path = texture.getFilePath()!!
                spriteRenderer.setTexture(AssetPool.getTexture(path))
            }
        }

        scene.gameObjects.forEach { go ->
            val stateMachine = go.getComponent(StateMachine::class.java)
            stateMachine?.refreshTextures()
        }
    }

    private fun loadSounds() {
        AssetPool.addSound("assets/sounds/main-theme-overworld.ogg", true);
        AssetPool.addSound("assets/sounds/flagpole.ogg", false);
        AssetPool.addSound("assets/sounds/break_block.ogg", false);
        AssetPool.addSound("assets/sounds/bump.ogg", false);
        AssetPool.addSound("assets/sounds/coin.ogg", false);
        AssetPool.addSound("assets/sounds/gameover.ogg", false);
        AssetPool.addSound("assets/sounds/jump-small.ogg", false);
        AssetPool.addSound("assets/sounds/mario_die.ogg", false);
        AssetPool.addSound("assets/sounds/pipe.ogg", false);
        AssetPool.addSound("assets/sounds/powerup.ogg", false);
        AssetPool.addSound("assets/sounds/powerup_appears.ogg", false);
        AssetPool.addSound("assets/sounds/stage_clear.ogg", false);
        AssetPool.addSound("assets/sounds/stomp.ogg", false);
        AssetPool.addSound("assets/sounds/kick.ogg", false);
        AssetPool.addSound("assets/sounds/invincible.ogg", false);
    }

    override fun imgui() {}
}