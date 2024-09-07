package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

import static net.vulkanmod.vulkan.shader.SPIRVUtils.compileShader;
import static net.vulkanmod.vulkan.shader.SPIRVUtils.compileShaderAbsoluteFile;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class ComputePipeline extends Pipeline {
    private long handle = 0;
    private long computeShaderModule = 0;
    private int framesNum;

    ComputePipeline(Builder builder) {
        super(builder.shaderPath);
        this.descriptors = new ArrayList<>(builder.UBOs);
        this.manualUBO = builder.manualUBO;
        this.imageDescriptors = builder.imageDescriptors;
        this.pushConstants = builder.pushConstants;
        this.framesNum = builder.framesNum;

        createDescriptorSetLayout();
        createPipelineLayout();
        createShaderModules(builder.computeShaderSPIRV);

        createDescriptorSets(framesNum);

        PIPELINES.add(this);
    }

    public long getHandle() {
        if (handle == 0) {
            handle = createComputePipeline();
        }
        return handle;
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

        PIPELINES.remove(this);
        Renderer.getInstance().removeUsedPipeline(this);
    }

    public static class Builder extends Pipeline.Builder {

        public static ComputePipeline createComputePipeline(String path) {
            ComputePipeline.Builder pipelineBuilder = new ComputePipeline.Builder(path);
//            pipelineBuilder.parseBindingsJSON();
            pipelineBuilder.compileShaders();
            return pipelineBuilder.createComputePipeline();
        }

        SPIRVUtils.SPIRV computeShaderSPIRV;
        int framesNum = 1;

        public Builder(String path) {
            super(path);
            this.shaderPath = path;
        }

        public Builder() {
            this(null);
        }

        public ComputePipeline createComputePipeline() {
//            this.imageDescriptors != null && this.UBOs != null
            Validate.isTrue(this.computeShaderSPIRV != null,
                    "Cannot create Pipeline: resources missing");

            if (this.manualUBO != null)
                this.UBOs.add(this.manualUBO);

            return new ComputePipeline(this);
        }

        public void setSPIRVs(SPIRVUtils.SPIRV computeShaderSPIRV) {
            this.computeShaderSPIRV = computeShaderSPIRV;
        }

        public void compileShaders() {
            String resourcePath = SPIRVUtils.class.getResource("/assets/vulkanmod/shaders/").toExternalForm();

            this.computeShaderSPIRV = compileShaderAbsoluteFile(String.format("%s%s.csh", resourcePath, this.shaderPath), SPIRVUtils.ShaderKind.COMPUTE_SHADER);
        }

        public void compileShaders(String name, String csh) {
            this.computeShaderSPIRV = compileShader(String.format("%s.csh", name), csh, SPIRVUtils.ShaderKind.COMPUTE_SHADER);
        }

        public void framesNum(int framesNum) {
            this.framesNum = framesNum;
        }
    }
}
