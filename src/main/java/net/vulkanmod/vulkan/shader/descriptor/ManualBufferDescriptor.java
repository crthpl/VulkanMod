package net.vulkanmod.vulkan.shader.descriptor;

import org.lwjgl.system.MemoryUtil;

public class ManualBufferDescriptor extends BufferDescriptor {

    private long srcPtr;
    private int srcSize;

    private boolean update = true;

    public ManualBufferDescriptor(int binding, int stages, int size, int type) {
        super(binding, stages, size * 4, null, type);
    }

    @Override
    public void update(long ptr) {
        // update manually
        if (update)
            MemoryUtil.memCopy(this.srcPtr, ptr, this.srcSize);
    }

    public void setSrc(long ptr, int size) {
        this.srcPtr = ptr;
        this.srcSize = size;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }
}
