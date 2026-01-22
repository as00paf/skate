package scenes

abstract class SceneInitializer {
    abstract fun init(scene:Scene)
    abstract fun loadResources(scene:Scene)
    abstract fun imgui()
}