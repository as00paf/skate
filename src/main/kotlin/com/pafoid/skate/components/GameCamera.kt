package components

import marki.Camera
import marki.GameObject
import marki.Window
import org.joml.Vector4f
import java.lang.Float.max

class GameCamera(private val gameCamera: Camera):Component() {
    @Transient var player: GameObject? = null

    @Transient private var highestX = Float.MIN_VALUE
    @Transient private var undergroundYLevel = 0.0f
    @Transient private val cameraBuffer = 1.5f
    @Transient private val playerBuffer = 0.25f

    private val skyColor = Vector4f(92.0f / 255.0f, 148.0f / 255.0f, 252.0f / 255.0f, 1.0f)
    private val undergroundColor = Vector4f(0f, 0f, 0f, 1f)

    override fun start() {
        player = Window.currentScene.getGameObjectWith(PlayerController::class.java)
        gameCamera.clearColor.set(skyColor)
        undergroundYLevel = gameCamera.position.y - gameCamera.getProjectionSize().y - cameraBuffer
    }

    override fun update(dt: Float) {
        val player = player ?: return
        if (player.getComponent(PlayerController::class.java)?.hasWon() == false) {
            gameCamera.position.x = Math.max(player.transform.position.x - 2.5f, highestX)
            highestX = max(highestX, gameCamera.position.x)
            if (player.transform.position.y < -playerBuffer) {
                gameCamera.position.y = undergroundYLevel
                gameCamera.clearColor.set(undergroundColor)
            } else if (player.transform.position.y >= 0.0f) {
                gameCamera.position.y = 0.0f
                gameCamera.clearColor.set(skyColor)
            }
        }
    }
}