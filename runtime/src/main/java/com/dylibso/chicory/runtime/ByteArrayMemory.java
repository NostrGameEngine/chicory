package com.dylibso.chicory.runtime;

import com.dylibso.chicory.runtime.alloc.MemAllocStrategy;
import com.dylibso.chicory.wasm.types.DataSegment;
import com.dylibso.chicory.wasm.types.MemoryLimits;

/**
 * TeaVM does not support the java.lang.invoke APIs used by the original byte[]-based
 * implementation. Keep the public type, but delegate to the ByteBuffer-backed implementation,
 * which relies only on TeaVM-supported java.nio APIs.
 */
public final class ByteArrayMemory implements Memory {
    private final ByteBufferMemory delegate;

    public ByteArrayMemory(MemoryLimits limits) {
        this.delegate = new ByteBufferMemory(limits);
    }

    /**
     * @deprecated The MemAllocStrategy is no longer used since memory is allocated by page.
     *             Use {@link #ByteArrayMemory(MemoryLimits)} instead.
     */
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public ByteArrayMemory(MemoryLimits limits, MemAllocStrategy allocStrategy) {
        this(limits);
    }

    @Override
    public int pages() {
        return delegate.pages();
    }

    @Override
    public int grow(int size) {
        return delegate.grow(size);
    }

    @Override
    public int initialPages() {
        return delegate.initialPages();
    }

    @Override
    public int maximumPages() {
        return delegate.maximumPages();
    }

    @Override
    public boolean shared() {
        return delegate.shared();
    }

    @Override
    @SuppressWarnings("removal")
    public Object lock(int address) {
        return delegate.lock(address);
    }

    @Override
    @SuppressWarnings("removal")
    public int waitOn(int address, int expected, long timeout) {
        return delegate.waitOn(address, expected, timeout);
    }

    @Override
    @SuppressWarnings("removal")
    public int waitOn(int address, long expected, long timeout) {
        return delegate.waitOn(address, expected, timeout);
    }

    @Override
    @SuppressWarnings("removal")
    public int notify(int address, int maxThreads) {
        return delegate.notify(address, maxThreads);
    }

    @Override
    public void initialize(Instance instance, DataSegment[] dataSegments) {
        delegate.initialize(instance, dataSegments);
    }

    @Override
    public void initialize(Instance instance, DataSegment[] dataSegments, int memoryIndex) {
        delegate.initialize(instance, dataSegments, memoryIndex);
    }

    @Override
    public void initPassiveSegment(int segmentId, int dest, int offset, int size) {
        delegate.initPassiveSegment(segmentId, dest, offset, size);
    }

    @Override
    public void write(int addr, byte[] data, int offset, int size) {
        delegate.write(addr, data, offset, size);
    }

    @Override
    public byte read(int addr) {
        return delegate.read(addr);
    }

    @Override
    public byte[] readBytes(int addr, int len) {
        return delegate.readBytes(addr, len);
    }

    @Override
    public void writeI32(int addr, int data) {
        delegate.writeI32(addr, data);
    }

    @Override
    public int readInt(int addr) {
        return delegate.readInt(addr);
    }

    @Override
    public long readI32(int addr) {
        return delegate.readI32(addr);
    }

    @Override
    public void writeLong(int addr, long data) {
        delegate.writeLong(addr, data);
    }

    @Override
    public long readLong(int addr) {
        return delegate.readLong(addr);
    }

    @Override
    public void writeShort(int addr, short data) {
        delegate.writeShort(addr, data);
    }

    @Override
    public short readShort(int addr) {
        return delegate.readShort(addr);
    }

    @Override
    public long readU16(int addr) {
        return delegate.readU16(addr);
    }

    @Override
    public void writeByte(int addr, byte data) {
        delegate.writeByte(addr, data);
    }

    @Override
    public void writeF32(int addr, float data) {
        delegate.writeF32(addr, data);
    }

    @Override
    public long readF32(int addr) {
        return delegate.readF32(addr);
    }

    @Override
    public float readFloat(int addr) {
        return delegate.readFloat(addr);
    }

    @Override
    public void writeF64(int addr, double data) {
        delegate.writeF64(addr, data);
    }

    @Override
    public double readDouble(int addr) {
        return delegate.readDouble(addr);
    }

    @Override
    public long readF64(int addr) {
        return delegate.readF64(addr);
    }

    @Override
    public void zero() {
        delegate.zero();
    }

    @Override
    public void fill(byte value, int fromIndex, int toIndex) {
        delegate.fill(value, fromIndex, toIndex);
    }

    @Override
    public void copy(int dest, int src, int size) {
        delegate.copy(dest, src, size);
    }

    @Override
    public void drop(int segment) {
        delegate.drop(segment);
    }

    @Override
    public void atomicFence() {
        AtomicSupport.fullFence();
    }
}
