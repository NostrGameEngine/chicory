package com.dylibso.chicory.teavm;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.WasmRuntimeException;
import com.dylibso.chicory.wasm.Parser;

/**
 * Exercises the parser and interpreter after compilation to JavaScript by NGE TeaVM.
 */
public final class RuntimeSmoke {
    // Compiled from src/main/resources/runtime-smoke.wat with wat2wasm --enable-tail-call.
    private static final byte[] MODULE = {
        0, 97, 115, 109, 1, 0, 0, 0, 1, 14, 3, 96, 0, 1, 112, 96,
        1, 127, 1, 127, 96, 0, 1, 127, 3, 6, 5, 0, 1, 1, 2, 1,
        4, 4, 1, 112, 0, 1, 5, 3, 1, 0, 1, 7, 45, 6, 6, 109,
        101, 109, 111, 114, 121, 2, 0, 4, 115, 101, 108, 102, 0, 0, 3, 114,
        117, 110, 0, 1, 4, 116, 97, 105, 108, 0, 2, 4, 116, 114, 97, 112,
        0, 3, 5, 116, 97, 98, 108, 101, 0, 4, 9, 7, 1, 0, 65, 0,
        11, 1, 0, 10, 100, 5, 4, 0, -46, 0, 11, 36, 1, 1, 127, 3,
        64, 2, 64, 32, 1, 65, 1, 106, 33, 1, 11, 32, 1, 32, 0, 73,
        13, 0, 11, 65, 0, 32, 1, 54, 2, 0, 65, 0, 40, 2, 0, 11,
        18, 0, 32, 0, 69, 4, 127, 65, 37, 5, 32, 0, 65, 1, 107, 18,
        2, 11, 11, 7, 0, 65, 127, 40, 2, 0, 11, 29, 1, 1, 127, 2,
        64, 3, 64, 32, 1, 65, 1, 106, 33, 1, 32, 1, 32, 0, 73, 69,
        14, 1, 0, 1, 11, 11, 32, 1, 11,
    };

    private RuntimeSmoke() {}

    public static void main(String[] args) {
        run(false);
        run(true);
        System.out.println(
                "CHICORY_TEAVM_JS_OK loops=10000 table=10000 tail=10000 ref.func memory bounds");
    }

    private static void run(boolean useByteArrayMemory) {
        var builder = Instance.builder(Parser.parse(MODULE));
        if (useByteArrayMemory) {
            builder.withMemoryFactory(ByteArrayMemory::new);
        }
        var instance = builder.build();
        require(instance.export("run").apply(10000)[0] == 10000, "conditional loop");
        require(instance.export("table").apply(10000)[0] == 10000, "table loop");
        require(instance.export("tail").apply(10000)[0] == 37, "tail call");
        require(instance.export("self").apply()[0] == 0, "active element ref.func");
        var memory = instance.memory();
        require(memory.readInt(0) == 10000, "memory round trip");
        memory.writeLong(8, 0x0123456789ABCDEFL);
        require(memory.readLong(8) == 0x0123456789ABCDEFL, "64-bit memory round trip");
        require(memory.grow(1) == 1 && memory.pages() == 2, "memory growth");
        require(memory.readInt(0) == 10000, "memory preserved after growth");
        boolean trapped = false;
        try {
            instance.export("trap").apply();
        } catch (WasmRuntimeException expected) {
            trapped = true;
        }
        require(trapped, "negative-address bounds trap");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
