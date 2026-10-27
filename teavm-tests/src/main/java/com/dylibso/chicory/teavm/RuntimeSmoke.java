package com.dylibso.chicory.teavm;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.WasmRuntimeException;
import com.dylibso.chicory.wasm.Parser;

/**
 * Exercises runtime-loaded and precompiled modules in both NGE TeaVM backends.
 */
public final class RuntimeSmoke {
    private RuntimeSmoke() {}

    public static void main(String[] args) {
        require(args.length == 2, "mode and runtime Wasm bytes are required");
        boolean precompiled = args[0].equals("precompiled");
        require(precompiled || args[0].equals("interpreter"), "unknown execution mode");
        var binary = decodeHex(args[1]);
        run(binary, precompiled, false);
        run(binary, precompiled, true);
        System.out.println("CHICORY_TEAVM_OK " + args[0]);
    }

    private static void run(byte[] binary, boolean precompiled, boolean useByteArrayMemory) {
        var builder =
                Instance.builder(precompiled ? PrecompiledSmoke.load() : Parser.parse(binary));
        if (precompiled) {
            builder.withMachineFactory(PrecompiledSmoke::create);
        }
        if (useByteArrayMemory) {
            builder.withMemoryFactory(ByteArrayMemory::new);
        }
        var instance = builder.build();
        require(instance.export("run").apply(10000)[0] == 10000, "conditional loop");
        require(instance.export("table").apply(10000)[0] == 10000, "table loop");
        // Compiled RETURN_CALL currently uses the host call stack.
        require(instance.export("tail").apply(precompiled ? 128 : 10000)[0] == 37, "tail call");
        require(instance.export("self").apply()[0] == 0, "active element ref.func");
        var memory = instance.memory();
        require(memory.readInt(0) == 10000, "memory round trip");
        memory.writeLong(8, 0x0123456789ABCDEFL);
        require(memory.readLong(8) == 0x0123456789ABCDEFL, "64-bit memory round trip");
        require(memory.grow(1) == 1 && memory.pages() == 2, "memory growth");
        require(memory.readInt(0) == 10000, "memory preserved after growth");
        memory.writeLong(65533, 0x0123456789ABCDEFL);
        require(memory.readLong(65533) == 0x0123456789ABCDEFL, "cross-page memory");
        requireTrap(() -> memory.read(-1));
        requireTrap(() -> memory.readShort(-1));
        requireTrap(() -> memory.readInt(-1));
        requireTrap(() -> memory.readLong(Integer.MIN_VALUE));
        requireTrap(() -> memory.readFloat(memory.pages() * 65536));
        requireTrap(() -> memory.readDouble(Integer.MAX_VALUE));
        requireTrap(() -> memory.writeByte(-1, (byte) 0));
        requireTrap(() -> memory.writeShort(-1, (short) 0));
        requireTrap(() -> memory.writeI32(-1, 0));
        requireTrap(() -> memory.writeLong(Integer.MIN_VALUE, 0));
        requireTrap(() -> memory.writeF32(memory.pages() * 65536, 0));
        requireTrap(() -> memory.writeF64(Integer.MAX_VALUE, 0));
        boolean trapped = false;
        try {
            instance.export("trap").apply();
        } catch (WasmRuntimeException expected) {
            trapped = true;
        }
        require(trapped, "negative-address bounds trap");
    }

    private static void requireTrap(Runnable operation) {
        try {
            operation.run();
        } catch (WasmRuntimeException expected) {
            return;
        }
        throw new AssertionError("missing memory bounds trap");
    }

    private static byte[] decodeHex(String hex) {
        require((hex.length() & 1) == 0, "invalid Wasm hex length");
        var binary = new byte[hex.length() / 2];
        for (int i = 0; i < binary.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            require(high >= 0 && low >= 0, "invalid Wasm hex digit");
            binary[i] = (byte) ((high << 4) | low);
        }
        return binary;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
