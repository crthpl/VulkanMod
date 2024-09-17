package net.vulkanmod;

import net.vulkanmod.compute.ComputeManager;
import net.vulkanmod.config.Platform;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.shader.ComputePipeline;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.descriptor.BufferDescriptor;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;

public class ComputeTest {
    @Test
    public void main() {
        Platform.init();
        Vulkan.initHeadless();

        ComputeManager computeManager = new ComputeManager();

        ComputePipeline.Builder builder = new ComputePipeline.Builder();
        builder.setShaderPath("add42");
        builder.parallelism(1);

        ByteBuffer dataBuffer = MemoryUtil.memAlloc(4);
        IntBuffer intBuffer = dataBuffer.asIntBuffer();
        intBuffer.put(0, 34);

        StorageBuffer storageBuffer = new StorageBuffer(4);
        storageBuffer.copyToBuffer(dataBuffer);

        AlignedStruct.Builder descriptorBuilder = new AlignedStruct.Builder();
        descriptorBuilder.addUniformInfo("int", "vector");
        BufferDescriptor descriptor = descriptorBuilder.buildDescriptor(0, GraphicsPipeline.Builder.getStageFromString("compute"), VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC);

        builder.addDescriptor(descriptor);
        builder.compileShaders();
        ComputePipeline pipeline = builder.createComputePipeline();

        pipeline.updateBuffer(storageBuffer, 0);

        computeManager.setPipeline(pipeline);

        long fence = computeManager.compute(1, 1, 1);
        computeManager.waitForFrame(fence);

        IntBuffer result = storageBuffer.copyFromBuffer().asIntBuffer();
        int sum = result.get(0);

        assert sum == 34 + 42;
        System.out.println(sum);

        System.out.println("succees");
    }
}
