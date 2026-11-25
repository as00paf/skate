package com.pafoid.skate.editor.ui.windows.vcs

import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.events.VcsSignInRequested
import com.pafoid.skate.editor.imgui.EditorModal
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.theme.ThemeColors
import com.pafoid.skate.editor.vcs.BranchNames
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import com.pafoid.skate.editor.vcs.auth.RemoteUrls
import imgui.flag.ImGuiInputTextFlags
import imgui.type.ImBoolean
import imgui.type.ImString

/**
 * EDT-79 §4.5: the confirmations of the Git workflow, shared by the branch button, the Commit dialog and the Git log
 * window. They are real modals (Q4); each only publishes a [VcsAction]. [render] runs once per frame (status bar).
 */
class VcsModals(strings: StringManager, eventSystem: EventSystem, session: VcsSession) {

    val initialize = object : EditorModal(strings, Icons.CODE_BRANCH) {
        override val titleKey = "vcs.init.title"
        override fun content() {
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 32f)
            ImGui.textWrapped(strings.getString("vcs.init.message"))
            ImGui.bulletText(strings.getString("vcs.init.item.repository"))
            ImGui.bulletText(strings.getString("vcs.init.item.ignore"))
            ImGui.bulletText(strings.getString("vcs.init.item.commit"))
            ImGui.popTextWrapPos()
        }
        override fun buttons() = listOf(
            FooterButton("btn.cancel", FooterButton.Role.CANCEL),
            FooterButton("vcs.initialize", FooterButton.Role.PRIMARY) {
                eventSystem.publish(VcsAction.Initialize(strings.getString("vcs.first_commit_message")))
            },
        )
    }

    val newBranch = NewBranchModal(strings, eventSystem, session)
    val remote = RemoteModal(strings, eventSystem, session)
    val signIn = SignInModal(strings, eventSystem, session)

    init {
        // EDT-60 phase 2: a remote refused the credentials.
        eventSystem.subscribe<VcsSignInRequested> { e -> signIn.show(e.host) }
    }

    private val deleteBranch = object : EditorModal(strings, Icons.TRASH) {
        var branch: String = ""
        override val titleKey = "vcs.delete_branch.title"
        override fun content() {
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 32f)
            ImGui.textWrapped(strings.getString("vcs.delete_branch.message", branch))
            ImGui.popTextWrapPos()
        }
        override fun buttons() = listOf(
            FooterButton("btn.cancel", FooterButton.Role.CANCEL),
            FooterButton("vcs.delete_branch.confirm", FooterButton.Role.DANGER) { eventSystem.publish(VcsAction.DeleteBranch(branch)) },
        )
    }

    private val discard = object : EditorModal(strings, Icons.TRASH) {
        var paths: List<String> = emptyList()
        override val titleKey = "vcs.discard.title"
        override fun content() {
            ImGui.textWrapped(strings.getString("vcs.discard.message", paths.size))
            paths.take(12).forEach { ImGui.bulletText(it) }
            if (paths.size > 12) ImGui.textDisabled(strings.getString("vcs.discard.more", paths.size - 12))
        }
        override fun buttons() = listOf(
            FooterButton("btn.cancel", FooterButton.Role.CANCEL),
            FooterButton("vcs.discard.confirm", FooterButton.Role.DANGER) { eventSystem.publish(VcsAction.Discard(paths)) },
        )
    }

    fun confirmDelete(branch: String) {
        deleteBranch.branch = branch
        deleteBranch.open()
    }

    fun confirmDiscard(paths: List<String>) {
        discard.paths = paths
        discard.open()
    }

    fun render() {
        initialize.render()
        newBranch.render()
        remote.render()
        signIn.render()
        deleteBranch.render()
        discard.render()
    }
}

/** New Branch: a name checked live with [BranchNames], "from" a branch (or the current commit), and "Switch to it". */
class NewBranchModal(
    strings: StringManager,
    private val eventSystem: EventSystem,
    private val session: VcsSession,
) : EditorModal(strings, Icons.PLUS, minWidth = 420f) {
    private val name = ImString(128)
    private val checkout = ImBoolean(true)
    private var from: String? = null
    private var focusName = false

    override val titleKey = "vcs.new_branch.title"

    /** Opens the modal for a branch from [fromBranch], a branch or commit id (null: the current commit). */
    fun show(fromBranch: String?) {
        from = fromBranch
        name.set("")
        checkout.set(true)
        focusName = true
        open()
    }

    private fun problem(): String? = BranchNames.problem(name.get(), session.state.branches?.local.orEmpty())

    override fun content() {
        val source = from?.let { if (it.length == 40 && it.all { c -> c in "0123456789abcdef" }) it.take(7) else it }
            ?: session.state.branches?.current ?: session.state.branches?.head?.take(7) ?: "?"
        ImGui.textDisabled(strings.getString("vcs.new_branch.from", source))
        if (focusName) {
            ImGui.setKeyboardFocusHere()
            focusName = false
        }
        ImGui.setNextItemWidth(-1f)
        ImGui.inputTextWithHint("##vcs_new_branch_name", strings.getString("vcs.branch.name_hint"), name)
        val problem = problem()
        if (problem != null && name.get().isNotEmpty()) {
            textColored(ThemeColors.danger, strings.getString(problem))
        }
        ImGui.checkbox(strings.getString("vcs.new_branch.checkout") + "##vcs_new_branch_checkout", checkout)
    }

    override fun buttons() = listOf(
        FooterButton("btn.cancel", FooterButton.Role.CANCEL),
        FooterButton("vcs.branch.create", FooterButton.Role.PRIMARY, enabled = problem() == null && !session.state.busy) {
            eventSystem.publish(VcsAction.CreateBranch(name.get().trim(), from, checkout.get()))
        },
    )
}

/** EDT-60 phase 2: sets `origin`'s URL. Credentials typed into the URL are moved to the credential store. */
class RemoteModal(
    strings: StringManager,
    private val eventSystem: EventSystem,
    private val session: VcsSession,
) : EditorModal(strings, Icons.LINK, minWidth = 460f) {
    private val url = ImString(512)
    private var focus = false

    override val titleKey = "vcs.remote.title"

    fun show() {
        url.set(session.state.origin?.url.orEmpty())
        focus = true
        open()
    }

    override fun content() {
        ImGui.textDisabled(strings.getString("vcs.remote.message"))
        if (focus) {
            ImGui.setKeyboardFocusHere()
            focus = false
        }
        ImGui.setNextItemWidth(-1f)
        ImGui.inputTextWithHint("##vcs_remote_url", strings.getString("vcs.remote.hint"), url)
        if (RemoteUrls.strip(url.get()).hadCredentials) {
            textColored(ThemeColors.warning, strings.getString("vcs.remote.credentials_moved"))
        }
    }

    override fun buttons() = listOf(
        FooterButton("btn.cancel", FooterButton.Role.CANCEL),
        FooterButton("vcs.remote.save", FooterButton.Role.PRIMARY, enabled = url.get().isNotBlank() && !session.state.busy) {
            eventSystem.publish(VcsAction.SetRemote(url.get()))
        },
    )
}

/**
 * EDT-60 phase 2: sign in to an HTTPS host with a user name and a personal access token, kept in the credential store
 * (never in the project). Browser sign-in for GitHub (`GitHubDeviceFlow`) joins it once the Skate OAuth App exists.
 */
class SignInModal(
    strings: StringManager,
    private val eventSystem: EventSystem,
    private val session: VcsSession,
) : EditorModal(strings, Icons.USER, minWidth = 440f) {
    private val username = ImString(128)
    private val token = ImString(512)
    var host: String = ""
        private set

    override val titleKey = "vcs.signin.title"

    fun show(forHost: String) {
        host = forHost
        username.set("")
        token.set("")
        open()
    }

    override fun content() {
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 30f)
        ImGui.textWrapped(strings.getString("vcs.signin.message", host))
        if (!session.credentials.store.persistent) textColored(ThemeColors.warning, strings.getString("vcs.signin.session_only"))
        ImGui.popTextWrapPos()
        ImGui.setNextItemWidth(-1f)
        ImGui.inputTextWithHint("##vcs_signin_user", strings.getString("vcs.signin.username"), username)
        ImGui.setNextItemWidth(-1f)
        ImGui.inputTextWithHint("##vcs_signin_token", strings.getString("vcs.signin.token"), token, ImGuiInputTextFlags.Password)
    }

    override fun buttons() = buildList {
        add(FooterButton("btn.cancel", FooterButton.Role.CANCEL))
        if (session.credentials.has(host)) {
            add(FooterButton("vcs.signin.sign_out", FooterButton.Role.DANGER) { eventSystem.publish(VcsAction.SignOut(host)) })
        }
        add(
            FooterButton("vcs.signin.save", FooterButton.Role.PRIMARY, enabled = token.get().isNotBlank()) {
                eventSystem.publish(VcsAction.SignIn(host, username.get(), token.get()))
                token.set("")
            },
        )
    }
}
