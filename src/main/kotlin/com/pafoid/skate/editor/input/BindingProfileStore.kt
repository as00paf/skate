package com.pafoid.skate.editor.input

import com.pafoid.skate.engine.input.BindingProfile
import java.io.File

/** Named binding profiles stored as `<name>.json` files in [dir]; copy a file in or out to share a profile. */
class BindingProfileStore(private val dir: File) {

    fun names(): List<String> =
        dir.listFiles { f -> f.isFile && f.extension.equals(EXTENSION, ignoreCase = true) }
            ?.map { it.nameWithoutExtension }?.sortedBy { it.lowercase() } ?: emptyList()

    /** Writes [profile] under its (sanitized) name, overwriting an existing profile of that name. */
    fun save(profile: BindingProfile): File {
        val name = sanitize(profile.name)
        dir.mkdirs()
        return fileOf(name).also { it.writeText(profile.copy(name = name).toJson()) }
    }

    fun load(name: String): Result<BindingProfile> = runCatching {
        val file = fileOf(sanitize(name))
        require(file.isFile) { "Profile '$name' does not exist" }
        BindingProfile.fromJson(file.readText()).copy(name = sanitize(name))
    }

    fun delete(name: String): Boolean = fileOf(sanitize(name)).delete()

    private fun fileOf(name: String) = File(dir, "$name.$EXTENSION")

    companion object {
        const val EXTENSION = "json"
        const val DIRECTORY = "InputProfiles"

        /** Keeps profile names safe as file names. */
        fun sanitize(name: String): String =
            name.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim('.', ' ').ifEmpty { "Profile" }
    }
}
