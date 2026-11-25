package com.pafoid.skate.engine.input

/**
 * Applies the project's [ProjectActionSet] to an [ActionMap] (INP-11): defines its valid actions, removes the ones
 * it defined before that are gone, and holds its "active at start" contexts while the scene plays.
 * Code-defined actions always win: a project action with the same id is skipped. Main thread only.
 */
class ProjectActions(
    private val map: ActionMap,
    /** Text of a project asset (`AssetsManager.readText`: the project in the editor, the pack in an export). */
    private val readText: (String) -> String?,
    private val warn: (String) -> Unit = {},
) {
    /** The set as last loaded or applied, invalid entries included (what the editor edits and saves). */
    var current: ProjectActionSet = ProjectActionSet()
        private set

    /** Entries of [current] that were skipped by the last [apply]. */
    var issues: List<ProjectActionIssue> = emptyList()
        private set

    private var applied: Set<String> = emptySet()
    private var validContexts: List<ProjectContext> = emptyList()
    private var startHandles: List<ContextHandle> = emptyList()

    /** Ids of the actions defined by code (engine, presets, game), which project actions may not reuse. */
    fun codeIds(): Set<String> = map.definitions().filter { it.origin != ActionOrigin.PROJECT }.mapTo(HashSet()) { it.id }

    /** Reads [path] (null or missing: no project actions) and applies it. A file that does not parse is reported. */
    fun load(path: String?) {
        val text = path?.let(readText)
        val set = text?.let {
            runCatching { ProjectActionSet.fromJson(it) }
                .onFailure { e -> warn("Input actions '$path' could not be read: ${e.message}") }
                .getOrNull()
        }
        apply(set ?: ProjectActionSet())
    }

    fun apply(set: ProjectActionSet) {
        val codeDefinitions = map.definitions().filter { it.origin != ActionOrigin.PROJECT }
        val validation = set.validate(
            codeIds = codeDefinitions.mapTo(HashSet()) { it.id },
            codeContexts = codeDefinitions.mapTo(HashSet()) { it.context.id },
        )
        current = set
        issues = validation.issues
        issues.forEach { warn(it.toString()) }

        val now = validation.valid.actions.mapTo(LinkedHashSet()) { it.id }
        for (gone in applied - now) {
            if (map.definition(gone)?.origin == ActionOrigin.PROJECT) map.remove(gone)
        }
        for (action in validation.valid.actions) {
            val definition = action.toDefinition()
            if (map.definition(action.id) != definition) map.define(definition)
        }
        applied = now
        validContexts = validation.valid.contexts
    }

    /** The project context [id] (for pushing it by id with its own priority and blocking), or null. */
    fun context(id: String): ProjectContext? = validContexts.firstOrNull { it.id == id }

    /** Pushes the contexts marked "active at start"; does nothing if they are already held. */
    fun pushStartContexts(stack: InputContextStack) {
        if (startHandles.isNotEmpty()) return
        startHandles = validContexts.filter { it.activeAtStart }.map {
            stack.push(InputContext(it.id), it.priority, it.blocking, owner = OWNER)
        }
    }

    fun popStartContexts(stack: InputContextStack) {
        startHandles.forEach { stack.pop(it) }
        startHandles = emptyList()
    }

    val holdsStartContexts: Boolean get() = startHandles.isNotEmpty()

    companion object {
        const val OWNER = "project"
    }
}
