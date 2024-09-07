package net.vulkanmod.compute;

import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.chunk.buffer.UploadManager;
import net.vulkanmod.vulkan.Drawer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.UniformBuffer;
import net.vulkanmod.vulkan.shader.*;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static net.vulkanmod.vulkan.Vulkan.getCommandPool;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class ComputeManager {
    private static VkDevice device;
    private List<VkCommandBuffer> commandBuffers;
    private UploadManager uploader;

    private ComputePipeline pipeline;

    // the amount of parallelism; the maximum amount of concurrent invocations of the compute task
    private int framesNum;

    private int currentFrame = 0;
    private int lastReset = -1;
    private VkCommandBuffer currentCmdBuffer;

    private long boundPipeline = 0;

    private boolean recordingCmds = false;


    //    private ArrayList<Long> imageAvailableSemaphores;
    // signaled means done or available, unsignaled means pending
    private ArrayList<Long> renderFinishedFences;
    private ArrayList<Long> inFlightFences;

    public ComputeManager(int parallelism) {
        this.framesNum = parallelism;
        device = Vulkan.getVkDevice();

        MemoryManager.createInstance(framesNum);
        // only do once??
//        Vulkan.createStagingBuffers();

        this.uploader = new UploadManager();


        allocateCommandBuffers();
        createSyncObjects();
    }

    public ComputeManager() {
        this(1);
    }

    private void allocateCommandBuffers() {
        if (commandBuffers != null) {
            commandBuffers.forEach(commandBuffer -> vkFreeCommandBuffers(device, Vulkan.getCommandPool(), commandBuffer));
        }

        commandBuffers = new ArrayList<>(framesNum);

        try (MemoryStack stack = stackPush()) {

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(getCommandPool());
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(framesNum);

            PointerBuffer pCommandBuffers = stack.mallocPointer(framesNum);

            if (vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers");
            }

            for (int i = 0; i < framesNum; i++) {
                commandBuffers.add(new VkCommandBuffer(pCommandBuffers.get(i), device));
            }
        }
    }

    private void createSyncObjects() {
        renderFinishedFences = new ArrayList<>(framesNum);
        inFlightFences = new ArrayList<>(framesNum);

        try (MemoryStack stack = stackPush()) {
//
//            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
//            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pFence = stack.mallocLong(1);

            for (int i = 0; i < framesNum; i++) {
//                vkCreateSemaphore(device, semaphoreInfo, null, pImageAvailableSemaphore) != VK_SUCCESS
                if (vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create synchronization objects for the frame " + i);
                }
                renderFinishedFences.add(pFence.get(0));
                if (vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create synchronization objects for the frame " + i);
                }
                inFlightFences.add(pFence.get(0));

            }

        }
    }

    public void setPipeline(ComputePipeline pipeline) {
        this.pipeline = pipeline;
    }

    private void bindPipeline() {
        VkCommandBuffer commandBuffer = currentCmdBuffer;

        final long handle = pipeline.getHandle();

        if (boundPipeline == handle) {
            return;
        }

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, handle);
        boundPipeline = handle;

//        addUsedPipeline(pipeline);
    }


    public void compute(int x, int y, int z) {
        if (recordingCmds) {
            return;
        }
        recordingCmds = true;

        vkWaitForFences(device, inFlightFences.get(currentFrame), true, VUtil.UINT64_MAX);
        MemoryManager.getInstance().initFrame(currentFrame);
        pipeline.resetDescriptorPool(currentFrame);
        boundPipeline = 0;

        currentCmdBuffer = commandBuffers.get(currentFrame);
        vkResetCommandBuffer(currentCmdBuffer, 0);
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc();
        beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

        vkBeginCommandBuffer(currentCmdBuffer, beginInfo);

        bindPipeline();
        pipeline.bindDescriptorSets(currentCmdBuffer, uniformBuffer, currentFrame);
        vkCmdDispatch(currentCmdBuffer, x, y, z);

        int result = vkEndCommandBuffer(currentCmdBuffer);
        if(result != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer:" + result);
        }

        currentFrame = (currentFrame + 1) % framesNum;

    }


}
