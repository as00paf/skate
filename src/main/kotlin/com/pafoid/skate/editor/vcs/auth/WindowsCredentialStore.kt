package com.pafoid.skate.editor.vcs.auth

import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.memAddress
import org.lwjgl.system.MemoryUtil.memByteBuffer
import org.lwjgl.system.MemoryUtil.memGetAddress
import org.lwjgl.system.MemoryUtil.memGetInt
import org.lwjgl.system.MemoryUtil.memPutAddress
import org.lwjgl.system.MemoryUtil.memPutInt
import org.lwjgl.system.MemoryUtil.memSet
import org.lwjgl.system.MemoryUtil.memUTF16
import org.lwjgl.system.Platform
import org.lwjgl.system.Pointer
import org.lwjgl.system.SharedLibrary
import org.lwjgl.system.windows.WindowsLibrary

/**
 * EDT-60 §5: Windows Credential Manager (`advapi32` `CredReadW` / `CredWriteW` / `CredDeleteW`), called through
 * LWJGL's dynamic function calls so the editor needs no JNA. Generic credentials, persisted for the local machine;
 * the secret is stored as UTF-8 bytes. [createOrNull] returns null off Windows or on a 32-bit JVM.
 */
class WindowsCredentialStore private constructor(lib: SharedLibrary) : CredentialStore {
    private val credRead = lib.getFunctionAddress("CredReadW")
    private val credWrite = lib.getFunctionAddress("CredWriteW")
    private val credDelete = lib.getFunctionAddress("CredDeleteW")
    private val credFree = lib.getFunctionAddress("CredFree")

    override val persistent: Boolean = true

    private val usable get() = credRead != 0L && credWrite != 0L && credDelete != 0L && credFree != 0L

    override fun get(key: String): GitCredential? = MemoryStack.stackPush().use { stack ->
        val target = stack.UTF16(key)
        val out = stack.mallocPointer(1)
        if (JNI.callPPI(memAddress(target), CRED_TYPE_GENERIC, 0, memAddress(out), credRead) == 0) return null
        val cred = out.get(0)
        try {
            val size = memGetInt(cred + OFF_BLOB_SIZE)
            val blob = memGetAddress(cred + OFF_BLOB)
            val bytes = ByteArray(size)
            if (size > 0 && blob != 0L) memByteBuffer(blob, size).get(bytes)
            val user = memGetAddress(cred + OFF_USER_NAME).let { if (it == 0L) "" else memUTF16(it) }
            GitCredential(user, String(bytes, Charsets.UTF_8)).also { bytes.fill(0) }
        } finally {
            JNI.callPV(cred, credFree)
        }
    }

    override fun put(key: String, credential: GitCredential) {
        MemoryStack.stackPush().use { stack ->
            val target = stack.UTF16(key)
            val user = stack.UTF16(credential.username)
            val secret = credential.secret.toByteArray(Charsets.UTF_8)
            val size = secret.size
            val blob = stack.malloc(maxOf(1, size))
            blob.put(0, secret)
            secret.fill(0)
            val cred = stack.calloc(Pointer.POINTER_SIZE, CREDENTIAL_SIZE)
            val a = memAddress(cred)
            memPutInt(a + OFF_TYPE, CRED_TYPE_GENERIC)
            memPutAddress(a + OFF_TARGET, memAddress(target))
            memPutInt(a + OFF_BLOB_SIZE, size)
            memPutAddress(a + OFF_BLOB, memAddress(blob))
            memPutInt(a + OFF_PERSIST, CRED_PERSIST_LOCAL_MACHINE)
            memPutAddress(a + OFF_USER_NAME, memAddress(user))
            val ok = JNI.callPI(a, 0, credWrite) != 0
            memSet(blob, 0)
            if (!ok) throw IllegalStateException("CredWriteW failed for $key")
        }
    }

    override fun delete(key: String) {
        MemoryStack.stackPush().use { stack ->
            JNI.callPI(memAddress(stack.UTF16(key)), CRED_TYPE_GENERIC, 0, credDelete)
        }
    }

    companion object {
        private const val CRED_TYPE_GENERIC = 1
        private const val CRED_PERSIST_LOCAL_MACHINE = 2

        // CREDENTIALW on 64-bit Windows.
        private const val CREDENTIAL_SIZE = 80
        private const val OFF_TYPE = 4L
        private const val OFF_TARGET = 8L
        private const val OFF_BLOB_SIZE = 32L
        private const val OFF_BLOB = 40L
        private const val OFF_PERSIST = 48L
        private const val OFF_USER_NAME = 72L

        @Volatile
        private var cached: WindowsCredentialStore? = null

        /** The store, or null when Credential Manager cannot be reached. */
        fun createOrNull(): WindowsCredentialStore? {
            cached?.let { return it }
            if (Platform.get() != Platform.WINDOWS || Pointer.POINTER_SIZE != 8) return null
            return try {
                WindowsCredentialStore(WindowsLibrary("advapi32")).takeIf { it.usable }?.also { cached = it }
            } catch (e: Throwable) {
                null
            }
        }
    }
}
