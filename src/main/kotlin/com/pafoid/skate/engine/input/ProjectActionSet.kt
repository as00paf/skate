package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A context declared by the project (INP-11). [priority] and [blocking] are used when it is pushed by id. */
@Serializable
data class ProjectContext(
    val id: String,
    val name: String = "",
    val priority: Int = 0,
    val blocking: ContextBlocking = ContextBlocking.CONSUME_BOUND,
    /** Pushed on the context stack while the scene plays, so a game without code can use it. */
    val activeAtStart: Boolean = false,
)

/** An action declared by the project (INP-11); maps 1:1 to an [ActionDefinition] with [ActionOrigin.PROJECT]. */
@Serializable
data class ProjectAction(
    val id: String,
    val name: String = "",
    val context: String = InputContext.GAMEPLAY.id,
    val category: String = "",
    val kind: ActionKind = ActionKind.BUTTON,
    val defaults: List<ActionBinding> = emptyList(),
    /** Publish an `ActionEvent` on each press and release. */
    val events: Boolean = false,
) {
    fun toDefinition(): ActionDefinition = ActionDefinition(
        id = id,
        context = InputContext(context),
        category = category,
        defaults = defaults,
        publishEvents = events,
        origin = ActionOrigin.PROJECT,
        kind = kind,
        displayName = name.ifBlank { null },
    )
}

/** Why a project action or context was skipped. */
enum class ProjectActionProblem {
    /** The id does not match [ProjectActionSet.ID_PATTERN]. */
    INVALID_ID,

    /** Another entry of the file already uses the id. */
    DUPLICATE_ID,

    /** Engine or game code already defines an action with this id; code wins. */
    CODE_ID,

    /** The action's context is neither built in, used by code, nor declared in the file. */
    UNKNOWN_CONTEXT,
}

data class ProjectActionIssue(val id: String, val problem: ProjectActionProblem, val isContext: Boolean = false) {
    override fun toString(): String =
        "${if (isContext) "Input context" else "Input action"} '$id' skipped: ${problem.name.lowercase().replace('_', ' ')}"
}

/** Result of [ProjectActionSet.validate]: the entries that load, and the ones that were skipped. */
data class ProjectActionValidation(val valid: ProjectActionSet, val issues: List<ProjectActionIssue>)

/**
 * The project's own input actions and contexts (INP-11), stored as JSON in a project asset
 * ([DEFAULT_PATH] by default). Pure data; [ProjectActions] applies it to an [ActionMap].
 */
@Serializable
data class ProjectActionSet(
    val version: Int = CURRENT_VERSION,
    val contexts: List<ProjectContext> = emptyList(),
    val actions: List<ProjectAction> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(this)

    fun action(id: String): ProjectAction? = actions.firstOrNull { it.id == id }
    fun context(id: String): ProjectContext? = contexts.firstOrNull { it.id == id }

    /** Copy with [action] replacing the action of the same id, or appended when there is none. */
    fun withAction(action: ProjectAction): ProjectActionSet {
        val at = actions.indexOfFirst { it.id == action.id }
        return copy(actions = if (at < 0) actions + action else actions.toMutableList().also { it[at] = action })
    }

    fun withoutAction(id: String): ProjectActionSet = copy(actions = actions.filter { it.id != id })

    fun withContext(context: ProjectContext): ProjectActionSet {
        val at = contexts.indexOfFirst { it.id == context.id }
        return copy(contexts = if (at < 0) contexts + context else contexts.toMutableList().also { it[at] = context })
    }

    fun withoutContext(id: String): ProjectActionSet = copy(contexts = contexts.filter { it.id != id })

    /**
     * Keeps the entries that can load: valid and unique ids, no clash with [codeIds], and a known context (built in,
     * in [codeContexts], or declared here). The rest is reported in order, so a bad entry never hides the others.
     */
    fun validate(codeIds: Set<String>, codeContexts: Set<String> = emptySet()): ProjectActionValidation {
        val issues = ArrayList<ProjectActionIssue>()
        val seenContexts = HashSet<String>()
        val validContexts = contexts.filter { c ->
            val problem = when {
                !isValidId(c.id) -> ProjectActionProblem.INVALID_ID
                !seenContexts.add(c.id) -> ProjectActionProblem.DUPLICATE_ID
                else -> null
            }
            problem?.let { issues += ProjectActionIssue(c.id, it, isContext = true) }
            problem == null
        }
        val known = BUILT_IN_CONTEXTS + codeContexts + validContexts.map { it.id }
        val seenActions = HashSet<String>()
        val validActions = actions.filter { a ->
            val problem = idProblem(a.id, codeIds, seenActions) ?: if (a.context !in known) ProjectActionProblem.UNKNOWN_CONTEXT else null
            seenActions += a.id
            problem?.let { issues += ProjectActionIssue(a.id, it) }
            problem == null
        }
        return ProjectActionValidation(copy(contexts = validContexts, actions = validActions), issues)
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val DEFAULT_PATH = "Assets/Data/input.actions.json"
        val ID_PATTERN = Regex("[a-z][a-z0-9_.]*")
        val BUILT_IN_CONTEXTS: Set<String> = setOf(InputContext.GAMEPLAY.id, InputContext.UI.id, InputContext.EDITOR.id)

        private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

        fun fromJson(text: String): ProjectActionSet = json.decodeFromString(text)

        fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

        /** Why [id] cannot be used for a new or renamed action, or null when it can (live validation in the editor). */
        fun idProblem(id: String, codeIds: Set<String>, takenIds: Set<String>): ProjectActionProblem? = when {
            !isValidId(id) -> ProjectActionProblem.INVALID_ID
            id in codeIds -> ProjectActionProblem.CODE_ID
            id in takenIds -> ProjectActionProblem.DUPLICATE_ID
            else -> null
        }
    }
}
