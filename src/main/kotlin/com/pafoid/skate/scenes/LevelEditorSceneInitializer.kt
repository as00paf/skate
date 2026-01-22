package scenes

import components.*
import imgui.ImGui
import imgui.ImVec2
import marki.GameObject
import marki.Prefabs
import marki.Window
import marki.renderer.Shader
import marki.renderer.Texture
import org.joml.Vector2f
import physics2d.components.Box2DCollider
import physics2d.components.RigidBody2D
import physics2d.enums.BodyType
import util.AssetPool
import java.io.File

class LevelEditorSceneInitializer : SceneInitializer() {

    lateinit var peterSprites: SpriteSheet
    lateinit var blocksSprites: SpriteSheet
    lateinit var gizmosSprites: SpriteSheet

    lateinit var levelEditorStuff: GameObject

    override fun init(scene: Scene) {
        levelEditorStuff = scene.createGameObject("LevelEditor")
            .setNoSerialize()
            .addComponent(MouseControls())
            .addComponent(KeyControls())
            .addComponent(GridLines())
            .addComponent(EditorCamera(scene.camera))
            .addComponent(GizmoSystem(gizmosSprites))
        scene.addGameObjectToScene(levelEditorStuff)
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

        AssetPool.getSpriteSheet(Texture.PETER_SPRITE)?.let { peterSprites = it }
        AssetPool.getSpriteSheet(Texture.BLOCKS_DECOS_SPRITE)?.let { blocksSprites = it }
        AssetPool.getSpriteSheet(Texture.GIZMOS_SPRITE)?.let { gizmosSprites = it }

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

    override fun imgui() {
        ImGui.begin("Level Editor Stuff")
        levelEditorStuff.imgui()
        ImGui.end()

        ImGui.begin("Objects")

        if (ImGui.beginTabBar("WindowTabBar")) {
            blocksTab()
            prefabsTab()
            soundTab()

            ImGui.endTabBar()
        }

        ImGui.end()

    }

    private fun blocksTab() {
        if (ImGui.beginTabItem("Blocks")) {
            val windowPos = ImVec2()
            ImGui.getWindowPos(windowPos)
            val windowSize = ImVec2()
            ImGui.getWindowSize(windowSize)
            val itemSpacing = ImVec2()
            ImGui.getStyle().getItemSpacing(itemSpacing)

            val textureScale = 2

            val windowX2 = windowPos.x + windowSize.x
            for (i in 0 until blocksSprites.size()) {
                val addCollider = (i == 3) or (i == 4) or (i == 11) or (i == 12)
                val sprite = blocksSprites.getSprite(i)
                val spriteWidth = sprite.width * textureScale
                val spriteHeight = sprite.height * textureScale
                val id = sprite.getTexId()
                val texCoords = sprite.getTexCoords()

                ImGui.pushID(i)
                if (ImGui.imageButton(
                        id,
                        spriteWidth,
                        spriteHeight,
                        texCoords[2].x,
                        texCoords[0].y,
                        texCoords[0].x,
                        texCoords[2].y
                    )
                ) {
                    println("Button $i clicked")
                    Window.imGuiLayer.propertiesWindow.clearSelected()
                    val block = Prefabs.generateSpriteObject(sprite, 0.25f, 0.25f, 1)
                    if(addCollider) {
                        val rb = RigidBody2D()
                        rb.init(block)
                        rb.bodyType = BodyType.Static
                        block.addComponent(rb)
                        val collider = Box2DCollider()
                        collider.halfSize.set(Vector2f(0.25f, 0.25f))
                        block.addComponent(collider)
                        block.addComponent(Ground())
                    }
                    levelEditorStuff.getComponent(MouseControls::class.java)?.pickUpObject(block)
                }
                ImGui.popID()

                val lastButtonPos = ImVec2()
                ImGui.getItemRectMax(lastButtonPos)
                val lastButtonX2 = lastButtonPos.x
                val nextButtonX2 = lastButtonX2 + itemSpacing.x + spriteWidth
                if (i + 1 < blocksSprites.size() && nextButtonX2 < windowX2) {
                    ImGui.sameLine()
                }

            }
            ImGui.endTabItem()
        }
    }

    private fun prefabsTab() {
        if (ImGui.beginTabItem("Prefabs")) {
            val spriteSheet = AssetPool.getSpriteSheet(Texture.PETER_SPRITE)!!
            val sprite = spriteSheet.getSprite(0)
            val scale = 1
            val spriteWidth = sprite.width * scale
            val spriteHeight = sprite.height * scale
            val id = sprite.getTexId()
            val texCoords = sprite.getTexCoords()

            if (ImGui.imageButton(
                    id,
                    spriteWidth,
                    spriteHeight,
                    texCoords[2].x,
                    texCoords[0].y,
                    texCoords[0].x,
                    texCoords[2].y
                )
            ) {
                Window.imGuiLayer.propertiesWindow.clearSelected()
                val block = Prefabs.generatePeter()
                levelEditorStuff.getComponent(MouseControls::class.java)?.pickUpObject(block)
            }
            ImGui.sameLine()

            ImGui.endTabItem()
        }
    }

    private fun soundTab() {
        if (ImGui.beginTabItem("Sounds")) {
            val sounds = AssetPool.getAllSounds()
            sounds.forEach { sound ->
                val tmp = File(sound.filePath)
                if(ImGui.button(tmp.name)) {
                    if(!sound.isPlaying()) sound.play() else sound.stop()
                }
                val space = ImGui.getContentRegionAvailX()
                if(space > 100) {
                    ImGui.sameLine()
                }
            }

            ImGui.endTabItem()
        }
    }
}