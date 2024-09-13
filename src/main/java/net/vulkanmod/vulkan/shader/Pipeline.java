package net.vulkanmod.vulkan.shader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.UniformBuffer;
import net.vulkanmod.vulkan.shader.descriptor.Descriptor;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.ManualBufferDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.BufferDescriptor;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.shader.layout.PushConstants;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.apache.commons.lang3.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public abstract class Pipeline {
    private static final VkDevice DEVICE = Vulkan.getVkDevice();
    protected static final long PIPELINE_CACHE = createPipelineCache();

    private static long createPipelineCache() {
        try (MemoryStack stack = stackPush()) {

            VkPipelineCacheCreateInfo cacheCreateInfo = VkPipelineCacheCreateInfo.calloc(stack);
            cacheCreateInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);

            LongBuffer pPipelineCache = stack.mallocLong(1);

            if (vkCreatePipelineCache(DEVICE, cacheCreateInfo, null, pPipelineCache) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline");
            }

            return pPipelineCache.get(0);
        }
    }

    public static void destroyPipelineCache() {
        vkDestroyPipelineCache(DEVICE, PIPELINE_CACHE, null);
    }

    protected long descriptorSetLayout;
    protected long pipelineLayout;

    public DescriptorSets[] descriptorSets;
    protected List<Descriptor> descriptors;
    // image descriptors are slightly special
    protected List<ImageDescriptor> imageDescriptors;
    //    protected List<BufferDescriptor> buffers;
    protected ManualBufferDescriptor manualUBO;
    protected PushConstants pushConstants;

    public Pipeline(Builder builder) {
        this.descriptors = new ArrayList<>(builder.descriptors);
        this.manualUBO = builder.manualUBO;
        this.imageDescriptors = builder.imageDescriptors;
        this.pushConstants = builder.pushConstants;


        createDescriptorSetLayout();
        createPipelineLayout();
    }

    protected void createDescriptorSetLayout() {
        try (MemoryStack stack = stackPush()) {
            int bindingsSize = this.descriptors.size() + this.imageDescriptors.size();

            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(bindingsSize, stack);

//            for (BufferDescriptor ubo : this.buffers) {
//                VkDescriptorSetLayoutBinding uboLayoutBinding = bindings.get(ubo.getBinding());
//                uboLayoutBinding.binding(ubo.getBinding());
//                uboLayoutBinding.descriptorCount(1);
//                uboLayoutBinding.descriptorType(ubo.getType());
//                uboLayoutBinding.pImmutableSamplers(null);
//                uboLayoutBinding.stageFlags(ubo.getStages());
//            }
//
            for (ImageDescriptor imageDescriptor : this.imageDescriptors) {
                VkDescriptorSetLayoutBinding samplerLayoutBinding = bindings.get(imageDescriptor.getBinding());
                samplerLayoutBinding.binding(imageDescriptor.getBinding());
                samplerLayoutBinding.descriptorCount(1);
                samplerLayoutBinding.descriptorType(imageDescriptor.getType());
                samplerLayoutBinding.pImmutableSamplers(null);
                samplerLayoutBinding.stageFlags(imageDescriptor.getStages());
            }

            for (Descriptor descriptor : this.descriptors) {
                VkDescriptorSetLayoutBinding uboLayoutBinding = bindings.get(descriptor.getBinding());
                uboLayoutBinding.binding(descriptor.getBinding());
                uboLayoutBinding.descriptorCount(1);
                uboLayoutBinding.descriptorType(descriptor.getType());
                uboLayoutBinding.pImmutableSamplers(null);
                uboLayoutBinding.stageFlags(descriptor.getStages());
            }

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack);
            layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            layoutInfo.pBindings(bindings);

            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);

            if (vkCreateDescriptorSetLayout(DeviceManager.vkDevice, layoutInfo, null, pDescriptorSetLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout");
            }

            this.descriptorSetLayout = pDescriptorSetLayout.get(0);
        }
    }

    protected void createPipelineLayout() {
        try (MemoryStack stack = stackPush()) {
            // ===> PIPELINE LAYOUT CREATION <===

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            pipelineLayoutInfo.pSetLayouts(stack.longs(this.descriptorSetLayout));

            if (this.pushConstants != null) {
                VkPushConstantRange.Buffer pushConstantRange = VkPushConstantRange.calloc(1, stack);
                pushConstantRange.size(this.pushConstants.getSize());
                pushConstantRange.offset(0);
                pushConstantRange.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

                pipelineLayoutInfo.pPushConstantRanges(pushConstantRange);
            }

            LongBuffer pPipelineLayout = stack.longs(VK_NULL_HANDLE);

            if (vkCreatePipelineLayout(DEVICE, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout");
            }

            pipelineLayout = pPipelineLayout.get(0);
        }
    }

    protected void createDescriptorSets(int frames) {
        descriptorSets = new DescriptorSets[frames];
        for (int i = 0; i < frames; ++i) {
            descriptorSets[i] = new DescriptorSets(this);
        }
    }

    public void scheduleCleanUp() {
        MemoryManager.getInstance().addFrameOp(this::cleanUp);
    }

    public abstract void cleanUp();

    void destroyDescriptorSets() {
        for (DescriptorSets descriptorSets : this.descriptorSets) {
            descriptorSets.cleanUp();
        }

        this.descriptorSets = null;
    }

    public ManualBufferDescriptor getManualUBO() {
        return this.manualUBO;
    }

    public void resetDescriptorPool(int i) {
        if (this.descriptorSets != null)
            this.descriptorSets[i].resetIdx();

    }

    public PushConstants getPushConstants() {
        return this.pushConstants;
    }

    public long getLayout() {
        return pipelineLayout;
    }

    public List<Descriptor> getDescriptors() {
        return descriptors;
    }

    public List<ImageDescriptor> getImageDescriptors() {
        return imageDescriptors;
    }

    public abstract int getBindPoint();

    public void updateUniformBuffer(UniformBuffer uniformBuffer, int frame) {
        this.descriptorSets[frame].updateUniformBuffer(uniformBuffer);
    }

    public void updateBuffer(Buffer buffer, int frame) {
        this.descriptorSets[frame].updateBuffer(buffer);
    }


    public void bindDescriptorSets(VkCommandBuffer commandBuffer, int frame) {
//        UniformBuffer uniformBuffer = Renderer.getDrawer().getUniformBuffer();
        this.descriptorSets[frame].bindSets(commandBuffer, this.getBindPoint());
    }

//    public void bindDescriptorSets(VkCommandBuffer commandBuffer, /*UniformBuffer uniformBuffer*/, int frame) {
//        this.descriptorSets[frame].bindSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS);
//    }

    static long createShaderModule(ByteBuffer spirvCode) {

        try (MemoryStack stack = stackPush()) {

            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);

            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(spirvCode);

            LongBuffer pShaderModule = stack.mallocLong(1);

            if (vkCreateShaderModule(DEVICE, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create shader module");
            }

            return pShaderModule.get(0);
        }
    }

    public static class DescriptorSets {
        private final Pipeline pipeline;
        private int poolSize = 1;
        private long descriptorPool;
        private LongBuffer sets;
        private long currentSet;
        private int currentIdx = -1;

        private final long[] boundBuffers;
        private final ImageDescriptor.State[] boundTextures;
        private final IntBuffer dynamicOffsets;

        DescriptorSets(Pipeline pipeline) {
            this.pipeline = pipeline;
            this.boundTextures = new ImageDescriptor.State[pipeline.imageDescriptors.size()];
            this.dynamicOffsets = MemoryUtil.memAllocInt(pipeline.descriptors.size());
            this.boundBuffers = new long[pipeline.descriptors.size()];

            Arrays.setAll(boundTextures, i -> new ImageDescriptor.State(0, 0));

            try (MemoryStack stack = stackPush()) {
                this.createDescriptorPool(stack);
                this.createDescriptorSets(stack);
            }
        }

        protected void bindSets(VkCommandBuffer commandBuffer, int bindPoint) {
            try (MemoryStack stack = stackPush()) {
                vkCmdBindDescriptorSets(commandBuffer, bindPoint, pipeline.pipelineLayout,
                        0, stack.longs(currentSet), dynamicOffsets);
            }
        }

        protected void updateBuffer(Buffer buffer) {
            try (MemoryStack stack = stackPush()) {
//                this.updateUniforms(uniformBuffer);
                this.updateDescriptorSet(stack, buffer);
            }
        }

        protected void updateUniformBuffer(UniformBuffer uniformBuffer) {
            try (MemoryStack stack = stackPush()) {
                this.updateBuffers(uniformBuffer);
                this.updateDescriptorSet(stack, uniformBuffer);
            }
        }

        private void updateBuffers(Buffer globalUB) {
            int i = 0;

            for (Descriptor descriptor: pipeline.descriptors) {
                if (descriptor instanceof AlignedStruct) {
                    AlignedStruct ubo = (AlignedStruct)descriptor;
//                    boolean useOwnUB = ubo.getUniformBuffer() != null;
//                    UniformBuffer ub = useOwnUB ? ubo.getUniformBuffer() : globalUB;
                    Buffer ub = globalUB;

                    int currentOffset = (int) ub.getUsedBytes();
                    this.dynamicOffsets.put(i, currentOffset);

                    // TODO: non mappable memory

                    int alignedSize = UniformBuffer.getAlignedSize(ubo.getSize());
                    ub.checkCapacity(alignedSize);

//                    if (!useOwnUB) {
                        ubo.update(ub.getPointer());
                        ub.updateOffset(alignedSize);
//                    }

                    ++i;
                }
            }
        }

        private boolean needsUpdate(Buffer buffer) {
            if (currentIdx == -1)
                return true;

            for (int j = 0; j < pipeline.imageDescriptors.size(); ++j) {
                ImageDescriptor imageDescriptor = pipeline.imageDescriptors.get(j);
                VulkanImage image = imageDescriptor.getImage();
                long view = imageDescriptor.getImageView(image);
                long sampler = image.getSampler();

                if (imageDescriptor.isReadOnlyLayout)
                    image.readOnlyLayout();

                if (!this.boundTextures[j].isCurrentState(view, sampler)) {
                    return true;
                }
            }

            for (int j = 0; j < pipeline.descriptors.size(); ++j) {
//                Descriptor descriptor = pipeline.descriptors.get(j);
//                if (descriptor instanceof BufferDescriptor) {
//                    BufferDescriptor ubo = (BufferDescriptor)descriptor;
////                    UniformBuffer uniformBufferI = ubo.getUniformBuffer();
//
////                    if (uniformBufferI == null)
////                        uniformBufferI = uniformBuffer;

                    if (this.boundBuffers[j] != buffer.getId()) {
                        return true;
                    }
//                }
            }

            return false;
        }

        private void checkPoolSize(MemoryStack stack) {
            if (this.currentIdx >= this.poolSize) {
                this.poolSize *= 2;

                this.createDescriptorPool(stack);
                this.createDescriptorSets(stack);
                this.currentIdx = 0;

                //debug
//                System.out.println("resized descriptor pool to: " + this.poolSize);
            }
        }

        private void updateDescriptorSet(MemoryStack stack, Buffer buffer) {

            //Check if update is needed
            if (!needsUpdate(buffer))
                return;

            this.currentIdx++;

            //Check pool size
            checkPoolSize(stack);

            this.currentSet = this.sets.get(this.currentIdx);

            VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(pipeline.descriptors.size() + pipeline.imageDescriptors.size(), stack);
            VkDescriptorBufferInfo.Buffer[] bufferInfos = new VkDescriptorBufferInfo.Buffer[pipeline.descriptors.size()];

            //TODO maybe ubo update is not needed everytime
            int i = 0;
            for (Descriptor descriptor : pipeline.descriptors) {
                boundBuffers[i] = buffer.getId();

                bufferInfos[i] = VkDescriptorBufferInfo.calloc(1, stack);
                bufferInfos[i].buffer(boundBuffers[i]);
                bufferInfos[i].range(descriptor.getSize());

                VkWriteDescriptorSet descriptorWrite = descriptorWrites.get(i);
                descriptorWrite.sType$Default();
                descriptorWrite.dstBinding(descriptor.getBinding());
                descriptorWrite.dstArrayElement(0);
                descriptorWrite.descriptorType(descriptor.getType());
                descriptorWrite.descriptorCount(1);
                descriptorWrite.pBufferInfo(bufferInfos[i]);
                descriptorWrite.dstSet(currentSet);

                ++i;
            }

            VkDescriptorImageInfo.Buffer[] imageInfo = new VkDescriptorImageInfo.Buffer[pipeline.imageDescriptors.size()];

            for (int j = 0; j < pipeline.imageDescriptors.size(); ++j) {
                ImageDescriptor imageDescriptor = pipeline.imageDescriptors.get(j);
                VulkanImage image = imageDescriptor.getImage();
                long view = imageDescriptor.getImageView(image);
                long sampler = image.getSampler();
                int layout = imageDescriptor.getLayout();

                if (imageDescriptor.isReadOnlyLayout)
                    image.readOnlyLayout();

                imageInfo[j] = VkDescriptorImageInfo.calloc(1, stack);
                imageInfo[j].imageLayout(layout);
                imageInfo[j].imageView(view);

                if (imageDescriptor.useSampler)
                    imageInfo[j].sampler(sampler);

                VkWriteDescriptorSet samplerDescriptorWrite = descriptorWrites.get(i);
                samplerDescriptorWrite.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
                samplerDescriptorWrite.dstBinding(imageDescriptor.getBinding());
                samplerDescriptorWrite.dstArrayElement(0);
                samplerDescriptorWrite.descriptorType(imageDescriptor.getType());
                samplerDescriptorWrite.descriptorCount(1);
                samplerDescriptorWrite.pImageInfo(imageInfo[j]);
                samplerDescriptorWrite.dstSet(currentSet);

                this.boundTextures[j].set(view, sampler);
                ++i;
            }

            vkUpdateDescriptorSets(DEVICE, descriptorWrites, null);
        }

        private void createDescriptorSets(MemoryStack stack) {
            LongBuffer layout = stack.mallocLong(this.poolSize);
//            layout.put(0, descriptorSetLayout);

            for (int i = 0; i < this.poolSize; ++i) {
                layout.put(i, pipeline.descriptorSetLayout);
            }

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack);
            allocInfo.sType$Default();
            allocInfo.descriptorPool(descriptorPool);
            allocInfo.pSetLayouts(layout);

            this.sets = MemoryUtil.memAllocLong(this.poolSize);

            int result = vkAllocateDescriptorSets(DEVICE, allocInfo, this.sets);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate descriptor sets. Result:" + result);
            }
        }

        private void createDescriptorPool(MemoryStack stack) {
            int size = pipeline.descriptors.size() + pipeline.imageDescriptors.size();

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(size, stack);

            int i;
            for (i = 0; i < pipeline.descriptors.size(); ++i) {
                VkDescriptorPoolSize uniformBufferPoolSize = poolSizes.get(i);
//                uniformBufferPoolSize.type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
                uniformBufferPoolSize.type(pipeline.descriptors.get(i).getType());
                uniformBufferPoolSize.descriptorCount(this.poolSize);
            }

            for (; i < pipeline.descriptors.size() + pipeline.imageDescriptors.size(); ++i) {
                VkDescriptorPoolSize textureSamplerPoolSize = poolSizes.get(i);
                textureSamplerPoolSize.type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
                textureSamplerPoolSize.descriptorCount(this.poolSize);
            }

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
            poolInfo.pPoolSizes(poolSizes);
            poolInfo.maxSets(this.poolSize);

            LongBuffer pDescriptorPool = stack.mallocLong(1);

            if (vkCreateDescriptorPool(DEVICE, poolInfo, null, pDescriptorPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor pool");
            }

            if (this.descriptorPool != VK_NULL_HANDLE) {
                final long oldDescriptorPool = this.descriptorPool;
                MemoryManager.getInstance().addFrameOp(() -> {
                    vkDestroyDescriptorPool(DEVICE, oldDescriptorPool, null);
                });
            }

            this.descriptorPool = pDescriptorPool.get(0);
        }

        public void resetIdx() {
            this.currentIdx = -1;
        }

        private void cleanUp() {
            vkResetDescriptorPool(DEVICE, descriptorPool, 0);
            vkDestroyDescriptorPool(DEVICE, descriptorPool, null);

            MemoryUtil.memFree(this.dynamicOffsets);
        }

    }

    public static class Builder {
        String shaderPath;
        List<Descriptor> descriptors;
        ManualBufferDescriptor manualUBO;
        PushConstants pushConstants;
        List<ImageDescriptor> imageDescriptors;
        int nextBinding;

        public Builder() {
            this.descriptors = new ArrayList<>();
            this.imageDescriptors = new ArrayList<>();
        }

        public void setShaderPath(String shaderPath) {
            this.shaderPath = shaderPath;
        }

        public void addDescriptor(Descriptor descriptor) {
            this.descriptors.add(descriptor);
        }

        public void setImageDescriptors(List<ImageDescriptor> imageDescriptors) {
            this.imageDescriptors = imageDescriptors;
        }

        // requires shader path
        public void parseBindingsJSON() {
            Validate.notNull(this.shaderPath, "Cannot parse bindings: shaderPath is null");

            this.descriptors = new ArrayList<>();
            this.imageDescriptors = new ArrayList<>();

            JsonObject jsonObject;

            String resourcePath = String.format("/assets/vulkanmod/shaders/%s.json", this.shaderPath);
            InputStream stream = Pipeline.class.getResourceAsStream(resourcePath);

            if (stream == null)
                throw new NullPointerException(String.format("Failed to load: %s", resourcePath));

            jsonObject = GsonHelper.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));

            JsonArray jsonUbos = GsonHelper.getAsJsonArray(jsonObject, "UBOs", null);
            JsonArray jsonManualUbos = GsonHelper.getAsJsonArray(jsonObject, "ManualUBOs", null);
            JsonArray jsonSamplers = GsonHelper.getAsJsonArray(jsonObject, "samplers", null);
            JsonArray jsonPushConstants = GsonHelper.getAsJsonArray(jsonObject, "PushConstants", null);

            if (jsonUbos != null) {
                for (JsonElement jsonelement : jsonUbos) {
                    this.parseUboNode(jsonelement);
                }
            }

            if (jsonManualUbos != null) {
                this.parseManualUboNode(jsonManualUbos.get(0));
            }

            if (jsonSamplers != null) {
                for (JsonElement jsonelement : jsonSamplers) {
                    this.parseSamplerNode(jsonelement);
                }
            }

            if (jsonPushConstants != null) {
                this.parsePushConstantNode(jsonPushConstants);
            }
        }

        private void parseUboNode(JsonElement jsonelement) {
            JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "BufferDescriptor");
            int binding = GsonHelper.getAsInt(jsonobject, "binding");
            int type = getStageFromString(GsonHelper.getAsString(jsonobject, "type"));
            JsonArray fields = GsonHelper.getAsJsonArray(jsonobject, "fields");

            AlignedStruct.Builder builder = new AlignedStruct.Builder();

            for (JsonElement jsonelement2 : fields) {
                JsonObject jsonobject2 = GsonHelper.convertToJsonObject(jsonelement2, "uniform");
                //need to store some infos
                String name = GsonHelper.getAsString(jsonobject2, "name");
                String type2 = GsonHelper.getAsString(jsonobject2, "type");
                int j = GsonHelper.getAsInt(jsonobject2, "count");

                builder.addUniformInfo(type2, name, j);

            }
            BufferDescriptor ubo = builder.buildUBO(binding, type);

            if (binding >= this.nextBinding)
                this.nextBinding = binding + 1;

            this.descriptors.add(ubo);
        }

        private void parseManualUboNode(JsonElement jsonelement) {
            JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "ManualBufferDescriptor");
            int binding = GsonHelper.getAsInt(jsonobject, "binding");
            int stage = getStageFromString(GsonHelper.getAsString(jsonobject, "type"));
            int size = GsonHelper.getAsInt(jsonobject, "size");

            if (binding >= this.nextBinding)
                this.nextBinding = binding + 1;

            this.manualUBO = new ManualBufferDescriptor(binding, stage, size, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC);
        }

        private void parseSamplerNode(JsonElement jsonelement) {
            JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "Sampler");
            String name = GsonHelper.getAsString(jsonobject, "name");

            int imageIdx = VTextureSelector.getTextureIdx(name);
            this.imageDescriptors.add(new ImageDescriptor(this.nextBinding, "sampler2D", name, imageIdx));
            this.nextBinding++;
        }

        private void parsePushConstantNode(JsonArray jsonArray) {
            AlignedStruct.Builder builder = new AlignedStruct.Builder();

            for (JsonElement jsonelement : jsonArray) {
                JsonObject jsonobject2 = GsonHelper.convertToJsonObject(jsonelement, "PC");

                String name = GsonHelper.getAsString(jsonobject2, "name");
                String type2 = GsonHelper.getAsString(jsonobject2, "type");
                int j = GsonHelper.getAsInt(jsonobject2, "count");

                builder.addUniformInfo(type2, name, j);
            }

            this.pushConstants = builder.buildPushConstant();
        }

        public static int getStageFromString(String s) {
            return switch (s) {
                case "vertex" -> VK_SHADER_STAGE_VERTEX_BIT;
                case "fragment" -> VK_SHADER_STAGE_FRAGMENT_BIT;
                case "all" -> VK_SHADER_STAGE_ALL_GRAPHICS;
                case "compute" -> VK_SHADER_STAGE_COMPUTE_BIT;

                default -> throw new RuntimeException("cannot identify type..");
            };
        }
    }
}
