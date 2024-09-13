package net.vulkanmod.vulkan.shader.layout;

import net.vulkanmod.vulkan.shader.descriptor.BufferDescriptor;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;

public abstract class AlignedStruct {

    protected List<Uniform> uniforms = new ArrayList<>();
    protected int size;

    protected AlignedStruct(List<Uniform.Info> infoList, int size) {
        this.size = size;

        if(infoList == null)
            return;

        for(Uniform.Info info : infoList) {

            Uniform uniform = Uniform.createField(info);
            this.uniforms.add(uniform);
        }
    }

    public void update(long ptr) {
        for(Uniform uniform : this.uniforms) {
            uniform.update(ptr);
        }
    }

    public List<Uniform> getUniforms() {
        return this.uniforms;
    }

    public int getSize() {
        return size;
    }

    public static class Builder {

        final List<Uniform.Info> uniformsInfo = new ArrayList<>();
        protected int currentOffset = 0;

        public void addUniformInfo(String type, String name, int count) {
            Uniform.Info info = Uniform.createUniformInfo(type, name, count);

            this.currentOffset = info.computeAlignmentOffset(this.currentOffset);
            this.currentOffset += info.size;
            this.uniformsInfo.add(info);
        }

        public void addUniformInfo(String type, String name) {
            Uniform.Info info = Uniform.createUniformInfo(type, name);

            this.currentOffset = info.computeAlignmentOffset(this.currentOffset);
            this.currentOffset += info.size;
            this.uniformsInfo.add(info);
        }

        public BufferDescriptor buildUBO(int binding, int stages) {
            //offset is expressed in floats/ints
            return buildDescriptor(binding, stages, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC);
        }

        public BufferDescriptor buildDescriptor(int binding, int stages, int type) {
            //offset is expressed in floats/ints
            return new BufferDescriptor(binding, stages, this.currentOffset * 4, this.uniformsInfo, type);
        }

        public PushConstants buildPushConstant() {
            if(this.uniformsInfo.isEmpty()) return null;
            return new PushConstants(this.uniformsInfo, this.currentOffset * 4);
        }

    }

}
