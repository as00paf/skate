package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.KeyedCommand
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.render.material.MaterialAsset
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File

/**
 * Every editable value of a [Material] (EDT-17). Immutable: vectors are copied in [of] and on [applyTo], so a state
 * kept for undo never aliases the live material. Textures are shared references (they are GPU resources).
 */
data class MaterialState(
    val baseColor: Vector4f = Vector4f(1f),
    val baseColorFactor: Float = 1f,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val emissive: Vector3f = Vector3f(0f),
    val doubleSided: Boolean = false,
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    val alphaCutoff: Float = 0.5f,
    val baseColorTexture: Texture? = null,
    val normalMap: Texture? = null,
    val metallicRoughnessTexture: Texture? = null,
    val aoTexture: Texture? = null,
    val emissiveTexture: Texture? = null,
) {
    /** Clamps to the ranges the PBR shader expects. */
    fun validated() = copy(
        baseColor = Vector4f(
            baseColor.x.coerceAtLeast(0f), baseColor.y.coerceAtLeast(0f), baseColor.z.coerceAtLeast(0f),
            baseColor.w.coerceIn(0f, 1f),
        ),
        baseColorFactor = baseColorFactor.coerceAtLeast(0f),
        metallic = metallic.coerceIn(0f, 1f),
        roughness = roughness.coerceIn(0f, 1f),
        emissive = Vector3f(emissive.x.coerceAtLeast(0f), emissive.y.coerceAtLeast(0f), emissive.z.coerceAtLeast(0f)),
        alphaCutoff = alphaCutoff.coerceIn(0f, 1f),
    )

    fun applyTo(m: Material) {
        m.baseColor.set(baseColor)
        m.baseColorFactor = baseColorFactor
        m.metallicFactor = metallic
        m.roughnessFactor = roughness
        m.emissiveFactor.set(emissive)
        m.doubleSided = doubleSided
        m.alphaMode = alphaMode
        m.alphaCutoff = alphaCutoff
        m.baseColorTexture = baseColorTexture
        m.normalMap = normalMap
        m.metallicRoughnessTexture = metallicRoughnessTexture
        m.aoTexture = aoTexture
        m.emissiveTexture = emissiveTexture
    }

    /** A new, independent material with these values. */
    fun toMaterial(): Material = Material().also(::applyTo)

    companion object {
        fun of(m: Material) = MaterialState(
            Vector4f(m.baseColor), m.baseColorFactor, m.metallicFactor, m.roughnessFactor, Vector3f(m.emissiveFactor),
            m.doubleSided, m.alphaMode, m.alphaCutoff,
            m.baseColorTexture, m.normalMap, m.metallicRoughnessTexture, m.aoTexture, m.emissiveTexture,
        )
    }
}

/** Sets the values of [material] from [before] to [after]; the inspector may already show [after] (live preview). */
class EditMaterialCommand(
    private val material: Material,
    private val before: MaterialState,
    after: MaterialState,
    private val targetName: String? = null,
) : Command {
    private val after = after.validated()

    val isNoOp: Boolean get() = before == after

    override fun execute() = after.applyTo(material)
    override fun undo() = before.applyTo(material)
    override fun getDisplayName(): String = "Edit Material"
    override fun getTargetName(): String? = targetName
}

/**
 * Sets which material a [RenderComponent] uses and the `.skmat` it is linked to. Covers creating and removing the
 * override, loading an asset, linking a freshly saved asset and unlinking.
 */
class AssignMaterialCommand(
    private val component: RenderComponent,
    private val material: Material?,
    private val materialPath: String?,
    private val kind: Kind = Kind.ASSIGN,
) : Command, KeyedCommand {
    /** Which inspector action this is; shown translated in Command History (`command.AssignMaterialCommand.<kind>`). */
    enum class Kind(val englishName: String) {
        ASSIGN("Assign Material"),
        OVERRIDE("Override Material"),
        REMOVE_OVERRIDE("Remove Material Override"),
        UNLINK("Unlink Material Asset"),
        LINK("Link Material Asset"),
        LOAD("Load Material Asset"),
    }

    override val displayNameKey: String get() = "command.AssignMaterialCommand.${kind.name.lowercase()}"

    private val beforeMaterial = component.material
    private val beforePath = component.materialPath

    val isNoOp: Boolean get() = beforeMaterial === material && beforePath == materialPath

    override fun execute() {
        component.material = material
        component.materialPath = materialPath
    }

    override fun undo() {
        component.material = beforeMaterial
        component.materialPath = beforePath
    }

    override fun getDisplayName(): String = kind.englishName
    override fun getTargetName(): String? = component.gameObject?.name
}

/** Writes [material] as a `.skmat` to [file]; files are not deleted on undo (the link is a separate command). */
class SaveMaterialAssetCommand(
    private val material: Material,
    private val file: File,
) : ExecuteOnlyCommand {
    override fun execute() {
        file.parentFile?.mkdirs()
        MaterialAsset.from(material, file.nameWithoutExtension).save(file)
    }

    override fun undo() {}
    override fun getDisplayName(): String = "Save Material Asset"
    override fun getTargetName(): String? = file.name
}
