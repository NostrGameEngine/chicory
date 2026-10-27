package com.dylibso.chicory.compiler.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.CodeSizeEvaluator;

public class DispatchChunkTest {
    @Test
    public void propertyBoundsAlwaysProduceSupportedPowerOfTwo() {
        assertEquals(128, Compiler.dispatchChunkSize(8000));
        for (int limit : new int[] {Integer.MIN_VALUE, -1, 0, 1, 39, 40}) {
            assertEquals(1, Compiler.dispatchChunkSize(limit));
        }
        assertEquals(1024, Compiler.dispatchChunkSize(Integer.MAX_VALUE));
    }

    @Test
    public void largeModuleDispatchesAcrossChunksWithinDefaultJitBudget() {
        var module = Parser.parse(module(2000));
        var result = Compiler.builder(module).build().compile();
        AtomicInteger dispatchMethods = new AtomicInteger();
        result.classBytes()
                .forEach(
                        (name, bytes) -> {
                            if (!name.contains("Dispatch_")) {
                                return;
                            }
                            new ClassReader(bytes)
                                    .accept(
                                            new ClassVisitor(Opcodes.ASM9) {
                                                @Override
                                                public MethodVisitor visitMethod(
                                                        int access,
                                                        String method,
                                                        String descriptor,
                                                        String signature,
                                                        String[] exceptions) {
                                                    if (!method.startsWith("call_dispatch_")) {
                                                        return null;
                                                    }
                                                    dispatchMethods.incrementAndGet();
                                                    return new CodeSizeEvaluator(null) {
                                                        @Override
                                                        public void visitEnd() {
                                                            assertTrue(
                                                                    getMaxSize() < 8000,
                                                                    name
                                                                            + "."
                                                                            + method
                                                                            + ": "
                                                                            + getMaxSize());
                                                        }
                                                    };
                                                }
                                            },
                                            0);
                        });
        assertTrue(dispatchMethods.get() > 1);
        var instance =
                Instance.builder(module)
                        .withMachineFactory(MachineFactoryCompiler::compile)
                        .build();
        for (int index : new int[] {0, 127, 128, 511, 512, 1023, 1024, 1999}) {
            assertArrayEquals(
                    new long[] {index + 1}, instance.getMachine().call(index, new long[0]));
        }
    }

    private static byte[] module(int functions) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0, 0x61, 0x73, 0x6d, 1, 0, 0, 0});
        section(out, 1, new byte[] {1, 0x60, 0, 1, 0x7f});
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        unsigned(data, functions);
        for (int i = 0; i < functions; i++) {
            data.write(0);
        }
        section(out, 3, data.toByteArray());
        data.reset();
        unsigned(data, functions);
        for (int i = 0; i < functions; i++) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(0);
            body.write(0x41);
            int value = i + 1;
            int part;
            do {
                part = value & 127;
                value >>= 7;
                boolean done = value == 0 && (part & 64) == 0;
                body.write(done ? part : part | 128);
                if (done) {
                    break;
                }
            } while (true);
            body.write(0x0b);
            unsigned(data, body.size());
            data.writeBytes(body.toByteArray());
        }
        section(out, 10, data.toByteArray());
        return out.toByteArray();
    }

    private static void section(ByteArrayOutputStream out, int id, byte[] bytes) {
        out.write(id);
        unsigned(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void unsigned(ByteArrayOutputStream out, int value) {
        do {
            int part = value & 127;
            value >>>= 7;
            out.write(value == 0 ? part : part | 128);
        } while (value != 0);
    }
}
