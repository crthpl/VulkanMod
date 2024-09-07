package net.vulkanmod;

import net.minecraft.util.GsonHelper;
import net.vulkanmod.compute.ComputeManager;
import net.vulkanmod.config.Platform;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.UniformBuffer;
import net.vulkanmod.vulkan.shader.ComputePipeline;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.descriptor.Descriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;

public class ComputeTest {
    @Test
    public static void main(String[] args) throws Exception {
        Platform.init();

        Vulkan.initHeadless();

        ComputeManager computeManager = new ComputeManager();
        ComputePipeline.Builder builder = new ComputePipeline.Builder("test");
        builder.framesNum(1);

        UniformBuffer ub

        AlignedStruct.Builder uboBuilder = new AlignedStruct.Builder();
        uboBuilder.addUniformInfo("vec4", "vector");
        UBO ubo = uboBuilder.buildUBO(0, GraphicsPipeline.Builder.getStageFromString("compute"));

        ubo.update();

        List<Descriptor> descriptors = new ArrayList<>();
        descriptors.add(ubo);
        builder.setDescriptors(descriptors);
        ComputePipeline pipeline = builder.createComputePipeline("test");

        pipeline.bindDescriptorSets(computeManager.);

        computeManager.setPipeline(pipeline);
        computeManager.compute(1, 1, 1);

        System.out.println("sudsaces");
    }
}
