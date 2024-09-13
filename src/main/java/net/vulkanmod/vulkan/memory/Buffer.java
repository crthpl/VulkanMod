package net.vulkanmod.vulkan.memory;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public abstract class Buffer {
    protected long id;
    protected long allocation;

    protected int bufferSize;
    protected int usedBytes;
    protected int offset;

    protected MemoryType type;
    protected int usage;
    protected PointerBuffer data;

    protected Buffer(int usage, MemoryType type) {
        //TODO: check usage
        this.usage = usage;
        this.type = type;

    }

    protected void createBuffer(int bufferSize) {
        this.type.createBuffer(this, bufferSize);

        if(this.type.mappable()) {
            this.data = MemoryManager.getInstance().Map(this.allocation);
        }
    }

    public void checkCapacity(int size) {
        if (size > this.bufferSize - this.usedBytes) {
            resizeBuffer((this.bufferSize + size) * 2);
        }
    }

    private void resizeBuffer(int newSize) {
        MemoryManager.getInstance().addToFreeable(this);
        createBuffer(newSize);
    }

    public void updateOffset(int alignedSize) {
        usedBytes += alignedSize;
    }

    public void copyToBuffer(ByteBuffer buffer) {
        int size = buffer.remaining();

        if(size > this.bufferSize - this.usedBytes) {
            throw new RuntimeException("Trying to write buffer beyond max size.");
        }
        else {
            this.type.copyToBuffer(this, size, buffer);
            offset = usedBytes;
            usedBytes += size;
        }
    }

    // TODO: reuse bytebuffer?
    public ByteBuffer copyFromBuffer() {
//        int size = buffer.remaining();

//        if(size > this.bufferSize - this.usedBytes) {
//            throw new RuntimeException("Trying to write buffer beyond max size.");
//        }
//        else {
        ByteBuffer buffer = MemoryUtil.memAlloc(bufferSize);
        this.type.copyFromBuffer(this, bufferSize, buffer);
        return buffer;
//            offset = usedBytes;
//            usedBytes += size;
//        }
    }

    public long getPointer() {
        return this.data.get(0) + usedBytes;
    }


    public void freeBuffer() {
        MemoryManager.getInstance().addToFreeable(this);
    }

    public void reset() { usedBytes = 0; }

    public long getAllocation() { return allocation; }

    public long getUsedBytes() { return usedBytes; }

    public long getOffset() { return offset; }

    public long getId() { return id; }

    public int getBufferSize() { return bufferSize; }

    protected void setBufferSize(int size) { this.bufferSize = size; }

    protected void setId(long id) { this.id = id; }

    protected void setAllocation(long allocation) {this.allocation = allocation; }

    public BufferInfo getBufferInfo() { return new BufferInfo(this.id, this.allocation, this.bufferSize, this.type.getType()); }

    public record BufferInfo(long id, long allocation, long bufferSize, MemoryType.Type type) {

    }
}
