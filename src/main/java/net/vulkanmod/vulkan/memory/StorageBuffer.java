package net.vulkanmod.vulkan.memory;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

public class StorageBuffer extends Buffer {

    public StorageBuffer(int size) {
        this(size, MemoryTypes.HOST_MEM);
    }

    public StorageBuffer(int size, MemoryType type) {
        super(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, type);
        this.createBuffer(size);

    }

//    private void resizeBuffer(int newSize) {
//        MemoryManager.getInstance().addToFreeable(this);
//        this.createBuffer(newSize);
//
////        System.out.println("resized vertexBuffer to: " + newSize);
//    }

}
