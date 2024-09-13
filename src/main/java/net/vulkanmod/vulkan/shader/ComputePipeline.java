package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.device.DeviceManager;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static net.vulkanmod.vulkan.shader.SPIRVUtils.compileShader;
import static net.vulkanmod.vulkan.shader.SPIRVUtils.compileShaderAbsoluteFile;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class ComputePipeline extends Pipeline {
    private long handle = 0;
    private long computeShaderModule = 0;

    // maximum amount of concurrent dispatches of the compute pipeline
    // corresponds to parallelism for graphics
    private final int parallelism;

    ComputePipeline(Builder builder) {
        super(builder);

        this.parallelism = builder.parallelism;

        createShaderModules(builder.computeShaderSPIRV);
        createDescriptorSets(parallelism);
    }

    public long getHandle() {
        if (handle == 0) {
            handle = createComputePipeline();
        }
        return handle;
    }

    public int getBindPoint() {
        return VK_PIPELINE_BIND_POINT_COMPUTE;
    }

    private long createComputePipeline() {

        try (MemoryStack stack = stackPush()) {

            ByteBuffer entryPoint = stack.UTF8("main");

            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(1, stack);

            VkPipelineShaderStageCreateInfo computeShaderStageInfo = shaderStages.get(0);

            computeShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
            computeShaderStageInfo.stage(VK_SHADER_STAGE_COMPUTE_BIT);
            computeShaderStageInfo.module(computeShaderModule);
            computeShaderStageInfo.pName(entryPoint);

            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfo.sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO);
            pipelineInfo.flags(0x00);
            pipelineInfo.stage(computeShaderStageInfo);
            pipelineInfo.layout(pipelineLayout);
            pipelineInfo.basePipelineHandle(VK_NULL_HANDLE);
            pipelineInfo.basePipelineIndex(-1);

            LongBuffer pComputePipeline = stack.mallocLong(1);

            if (vkCreateComputePipelines(DeviceManager.vkDevice, PIPELINE_CACHE, pipelineInfo, null, pComputePipeline) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create compute pipeline");
            }

            return pComputePipeline.get(0);
        }
    }

    private void createShaderModules(SPIRVUtils.SPIRV computeSpirv) {
        this.computeShaderModule = createShaderModule(computeSpirv.bytecode());
    }

    public void cleanUp() {
        vkDestroyShaderModule(DeviceManager.vkDevice, computeShaderModule, null);

        destroyDescriptorSets();

        vkDestroyPipeline(DeviceManager.vkDevice, handle, null);
        handle = 0;

        vkDestroyDescriptorSetLayout(DeviceManager.vkDevice, descriptorSetLayout, null);
        vkDestroyPipelineLayout(DeviceManager.vkDevice, pipelineLayout, null);

//        Renderer.getInstance().removeUsedPipeline(this);
    }

    public static class Builder extends Pipeline.Builder {

//        public static ComputePipeline createComputePipeline(String path) {
//            ComputePipeline.Builder pipelineBuilder = new ComputePipeline.Builder(path);
////            pipelineBuilder.parseBindingsJSON();
//            pipelineBuilder.compileShaders();
//            return pipelineBuilder.createComputePipeline();
//        }

        SPIRVUtils.SPIRV computeShaderSPIRV;
        int parallelism = 1;

        public Builder() {
            super();
        }

        public ComputePipeline createComputePipeline() {
//            this.imageDescriptors != null && this.UBOs != null
            Validate.isTrue(this.computeShaderSPIRV != null,
                    "Cannot create Pipeline: resources missing");

            if (this.manualUBO != null)
                this.descriptors.add(this.manualUBO);

            return new ComputePipeline(this);
        }

        public void setSPIRV(SPIRVUtils.SPIRV computeShaderSPIRV) {
            this.computeShaderSPIRV = computeShaderSPIRV;
        }

        public void compileShaders() {
            String resourcePath = SPIRVUtils.class.getResource("/assets/vulkanmod/shaders/").toExternalForm();

            this.computeShaderSPIRV = compileShaderAbsoluteFile(String.format("%s%s.comp", resourcePath, this.shaderPath), SPIRVUtils.ShaderKind.COMPUTE_SHADER);
        }

        // use setSPIRVs if you want to customize this
//        public void compileShaders(String name, String csh) {
//            this.computeShaderSPIRV = compileShader(String.format("%s.csh", name), csh, SPIRVUtils.ShaderKind.COMPUTE_SHADER);
//        }

        public void parallelism(int parallelism) {
            this.parallelism = parallelism;
        }
    }
}
