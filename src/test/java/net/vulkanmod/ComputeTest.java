package net.vulkanmod;

import net.vulkanmod.compute.ComputeManager;
import net.vulkanmod.config.Platform;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.shader.ComputePipeline;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.descriptor.Descriptor;
import net.vulkanmod.vulkan.shader.descriptor.BufferDescriptor;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;

public class ComputeTest {
    @Test
    public static void main(String[] args) throws Exception {
        Platform.init();
        Vulkan.initHeadless();

        ComputeManager computeManager = new ComputeManager();

        ComputePipeline.Builder builder = new ComputePipeline.Builder();
        builder.setShaderPath("add42");
        builder.parallelism(1);

        ByteBuffer dataBuffer = MemoryUtil.memAlloc(4*4);
        IntBuffer intBuffer = dataBuffer.asIntBuffer();
        for (int i = 0; i < 4; i++) {
            intBuffer.put(i, i+1);
        }

        StorageBuffer storageBuffer = new StorageBuffer(4*4);
        storageBuffer.copyToBuffer(dataBuffer);

        AlignedStruct.Builder descriptorBuilder = new AlignedStruct.Builder();
        descriptorBuilder.addUniformInfo("int", "vector");
        BufferDescriptor descriptor = descriptorBuilder.buildDescriptor(0, GraphicsPipeline.Builder.getStageFromString("compute"), VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC);


        builder.addDescriptor(descriptor);
        builder.compileShaders();
        ComputePipeline pipeline = builder.createComputePipeline();

        pipeline.updateBuffer(storageBuffer, 0);
//        pipeline.bindDescriptorSets(computeManager.);

        computeManager.setPipeline(pipeline);
        long fence = computeManager.compute(1, 1, 1);
        computeManager.waitForFrame(fence);
        IntBuffer result = storageBuffer.copyFromBuffer().asIntBuffer();
        System.out.println(result.get(0));
//        System.

        System.out.println("sudsaces");
    }
}
