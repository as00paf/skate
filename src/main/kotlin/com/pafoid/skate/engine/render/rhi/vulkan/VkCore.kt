package com.pafoid.skate.engine.render.rhi.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.vkCreateDebugUtilsMessengerEXT
import org.lwjgl.vulkan.EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1
import org.lwjgl.vulkan.VkApplicationInfo
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackEXT
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkDeviceCreateInfo
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkInstanceCreateInfo
import org.lwjgl.vulkan.VkLayerProperties
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties
import org.lwjgl.vulkan.VkPhysicalDeviceProperties
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkQueueFamilyProperties
import org.lwjgl.vulkan.VkSubmitInfo

/** Throws when a Vulkan call fails. */
internal fun vk(result: Int, what: String) {
    check(result == VK_SUCCESS) { "Vulkan $what failed: $result" }
}

/**
 * Instance, device and queue of the offscreen Vulkan spike (REN-13): Vulkan 1.1 (negative viewport height), no
 * surface, one graphics queue, one command pool. Set `SKATE_VK_VALIDATION=1` to enable the Khronos validation layer
 * when it is installed (messages go to the `skate.rhi.vulkan` java.util.logging logger).
 */
internal class VkCore private constructor(
    val instance: VkInstance,
    val physical: VkPhysicalDevice,
    val device: VkDevice,
    val queue: VkQueue,
    val queueFamily: Int,
    val commandPool: Long,
    private val messenger: Long,
    val deviceName: String,
    val apiVersion: Int,
    val maxImageDimension2D: Int,
    val maxImageArrayLayers: Int,
    val maxAnisotropy: Float,
    val minUniformAlignment: Long,
    val depthClamp: Boolean,
    val anisotropy: Boolean,
    val wideLines: Boolean,
    val timestampQueries: Boolean,
) : AutoCloseable {
    private val memoryProperties = VkPhysicalDeviceMemoryProperties.calloc().also { vkGetPhysicalDeviceMemoryProperties(physical, it) }

    fun memoryType(typeBits: Int, flags: Int): Int {
        for (i in 0 until memoryProperties.memoryTypeCount()) {
            if (typeBits and (1 shl i) != 0 && memoryProperties.memoryTypes(i).propertyFlags() and flags == flags) return i
        }
        error("No Vulkan memory type for bits $typeBits with flags $flags")
    }

    /** Allocates and binds memory for [buffer] ([flags]: VK_MEMORY_PROPERTY_*). */
    fun bufferMemory(buffer: Long, flags: Int): Long = stackPush().use { s ->
        val req = VkMemoryRequirements.malloc(s)
        vkGetBufferMemoryRequirements(device, buffer, req)
        val memory = allocate(s, req, flags)
        vk(vkBindBufferMemory(device, buffer, memory, 0), "vkBindBufferMemory")
        memory
    }

    fun imageMemory(image: Long, flags: Int): Long = stackPush().use { s ->
        val req = VkMemoryRequirements.malloc(s)
        vkGetImageMemoryRequirements(device, image, req)
        val memory = allocate(s, req, flags)
        vk(vkBindImageMemory(device, image, memory, 0), "vkBindImageMemory")
        memory
    }

    private fun allocate(s: MemoryStack, req: VkMemoryRequirements, flags: Int): Long {
        val info = VkMemoryAllocateInfo.calloc(s).`sType$Default`()
            .allocationSize(req.size())
            .memoryTypeIndex(memoryType(req.memoryTypeBits(), flags))
        val p = s.mallocLong(1)
        vk(vkAllocateMemory(device, info, null, p), "vkAllocateMemory")
        return p[0]
    }

    /** A buffer of [size] bytes in host-visible, coherent memory, mapped for its whole life. */
    fun hostBuffer(size: Long, usage: Int): HostBuffer = stackPush().use { s ->
        val info = VkBufferCreateInfo.calloc(s).`sType$Default`()
            .size(maxOf(size, 16L)).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE)
        val p = s.mallocLong(1)
        vk(vkCreateBuffer(device, info, null, p), "vkCreateBuffer")
        val buffer = p[0]
        val memory = bufferMemory(buffer, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
        val ptr = s.mallocPointer(1)
        vk(vkMapMemory(device, memory, 0, VK_WHOLE_SIZE, 0, ptr), "vkMapMemory")
        HostBuffer(this, buffer, memory, ptr[0], maxOf(size, 16L))
    }

    /** Records [block] into a fresh command buffer, submits it and waits (uploads, layout changes, readback). */
    fun oneShot(block: (VkCommandBuffer) -> Unit) = stackPush().use { s ->
        val cmd = allocateCommandBuffer()
        vk(vkBeginCommandBuffer(cmd, VkCommandBufferBeginInfo.calloc(s).`sType$Default`()
            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "vkBeginCommandBuffer")
        block(cmd)
        submitAndWait(cmd)
        vkFreeCommandBuffers(device, commandPool, cmd)
    }

    fun allocateCommandBuffer(): VkCommandBuffer = stackPush().use { s ->
        val info = VkCommandBufferAllocateInfo.calloc(s).`sType$Default`()
            .commandPool(commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1)
        val p = s.mallocPointer(1)
        vk(vkAllocateCommandBuffers(device, info, p), "vkAllocateCommandBuffers")
        VkCommandBuffer(p[0], device)
    }

    /** Ends, submits and waits for [cmd] (the spike keeps no frames in flight). */
    fun submitAndWait(cmd: VkCommandBuffer) = stackPush().use { s ->
        vk(vkEndCommandBuffer(cmd), "vkEndCommandBuffer")
        val submit = VkSubmitInfo.calloc(s).`sType$Default`().pCommandBuffers(s.pointers(cmd))
        vk(vkQueueSubmit(queue, submit, VK_NULL_HANDLE), "vkQueueSubmit")
        vk(vkQueueWaitIdle(queue), "vkQueueWaitIdle")
    }

    override fun close() {
        vkDeviceWaitIdle(device)
        vkDestroyCommandPool(device, commandPool, null)
        vkDestroyDevice(device, null)
        if (messenger != NULL) vkDestroyDebugUtilsMessengerEXT(instance, messenger, null)
        vkDestroyInstance(instance, null)
        memoryProperties.free()
    }

    companion object {
        private const val VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation"

        /** The first GPU with a graphics queue; throws when there is no Vulkan driver. */
        fun create(): VkCore = stackPush().use { s ->
            val validation = System.getenv("SKATE_VK_VALIDATION") == "1" && hasLayer(s, VALIDATION_LAYER)
            val app = VkApplicationInfo.calloc(s).`sType$Default`()
                .pApplicationName(s.UTF8("Skate")).pEngineName(s.UTF8("Skate")).apiVersion(VK_API_VERSION_1_1)
            val info = VkInstanceCreateInfo.calloc(s).`sType$Default`().pApplicationInfo(app)
            if (validation) {
                info.ppEnabledLayerNames(s.pointers(s.UTF8(VALIDATION_LAYER)))
                info.ppEnabledExtensionNames(s.pointers(s.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)))
            }
            val pInstance = s.mallocPointer(1)
            vk(vkCreateInstance(info, null, pInstance), "vkCreateInstance")
            val instance = VkInstance(pInstance[0], info)
            val messenger = if (validation) messenger(s, instance) else NULL

            val count = s.mallocInt(1)
            vkEnumeratePhysicalDevices(instance, count, null)
            check(count[0] > 0) { "No Vulkan physical device" }
            val devices = s.mallocPointer(count[0])
            vkEnumeratePhysicalDevices(instance, count, devices)
            var chosen: VkPhysicalDevice? = null
            var family = -1
            for (i in 0 until count[0]) {
                val candidate = VkPhysicalDevice(devices[i], instance)
                val f = graphicsFamily(s, candidate)
                if (f >= 0) { chosen = candidate; family = f; break }
            }
            val physical = checkNotNull(chosen) { "No Vulkan device with a graphics queue" }

            val props = VkPhysicalDeviceProperties.calloc(s)
            vkGetPhysicalDeviceProperties(physical, props)
            val supported = VkPhysicalDeviceFeatures.calloc(s)
            vkGetPhysicalDeviceFeatures(physical, supported)
            val features = VkPhysicalDeviceFeatures.calloc(s)
                .depthClamp(supported.depthClamp())
                .samplerAnisotropy(supported.samplerAnisotropy())
                .wideLines(supported.wideLines())

            val queueInfo = VkDeviceQueueCreateInfo.calloc(1, s)
            queueInfo[0].`sType$Default`().queueFamilyIndex(family).pQueuePriorities(s.floats(1f))
            val deviceInfo = VkDeviceCreateInfo.calloc(s).`sType$Default`().pQueueCreateInfos(queueInfo).pEnabledFeatures(features)
            val pDevice = s.mallocPointer(1)
            vk(vkCreateDevice(physical, deviceInfo, null, pDevice), "vkCreateDevice")
            val device = VkDevice(pDevice[0], physical, deviceInfo, VK_API_VERSION_1_1)
            val pQueue = s.mallocPointer(1)
            vkGetDeviceQueue(device, family, 0, pQueue)

            val poolInfo = VkCommandPoolCreateInfo.calloc(s).`sType$Default`()
                .queueFamilyIndex(family).flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
            val pPool = s.mallocLong(1)
            vk(vkCreateCommandPool(device, poolInfo, null, pPool), "vkCreateCommandPool")

            val limits = props.limits()
            VkCore(
                instance, physical, device, VkQueue(pQueue[0], device), family, pPool[0], messenger,
                props.deviceNameString(), props.apiVersion(),
                limits.maxImageDimension2D(), limits.maxImageArrayLayers(), limits.maxSamplerAnisotropy(),
                limits.minUniformBufferOffsetAlignment(),
                supported.depthClamp(), supported.samplerAnisotropy(), supported.wideLines(),
                limits.timestampComputeAndGraphics(),
            )
        }

        private fun graphicsFamily(s: MemoryStack, device: VkPhysicalDevice): Int {
            val n = s.mallocInt(1)
            vkGetPhysicalDeviceQueueFamilyProperties(device, n, null)
            val families = VkQueueFamilyProperties.malloc(n[0], s)
            vkGetPhysicalDeviceQueueFamilyProperties(device, n, families)
            for (i in 0 until n[0]) if (families[i].queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) return i
            return -1
        }

        private fun hasLayer(s: MemoryStack, name: String): Boolean {
            val n = s.mallocInt(1)
            vkEnumerateInstanceLayerProperties(n, null)
            if (n[0] == 0) return false
            val layers = VkLayerProperties.malloc(n[0], s)
            vkEnumerateInstanceLayerProperties(n, layers)
            return (0 until n[0]).any { layers[it].layerNameString() == name }
        }

        /** Validation messages; the RHI has no engine logger, so they go to java.util.logging (stderr by default). */
        private val validationLog = java.util.logging.Logger.getLogger("skate.rhi.vulkan")

        private val debugCallback = VkDebugUtilsMessengerCallbackEXT.create { severity, _, data, _ ->
            val message = VkDebugUtilsMessengerCallbackDataEXT.create(data).pMessageString()
            if (severity >= VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) validationLog.severe(message) else validationLog.warning(message)
            VK_FALSE
        }

        private fun messenger(s: MemoryStack, instance: VkInstance): Long {
            val info = VkDebugUtilsMessengerCreateInfoEXT.calloc(s).`sType$Default`()
                .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                    VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                .pfnUserCallback(debugCallback)
            val p = s.mallocLong(1)
            vk(vkCreateDebugUtilsMessengerEXT(instance, info, null, p), "vkCreateDebugUtilsMessengerEXT")
            return p[0]
        }
    }
}

/** A mapped host-visible buffer. */
internal class HostBuffer(
    private val core: VkCore,
    val buffer: Long,
    private val memory: Long,
    val address: Long,
    val size: Long,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        vkDestroyBuffer(core.device, buffer, null)
        vkUnmapMemory(core.device, memory)
        vkFreeMemory(core.device, memory, null)
    }
}
