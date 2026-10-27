package com.dylibso.chicory.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.Instruction;
import com.dylibso.chicory.wasm.types.OpCode;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Real binary programs cover built-in direct operands and unchanged extension boundaries.
 */
@Timeout(10)
public class InstructionOperandsTest {
    @Test
    public void builtInOperandsAndListenerBeforeMemoryStoreRemainExact() {
        AtomicReference<Instance> owner = new AtomicReference<>();
        List<OpCode> observed = new ArrayList<>();
        Instance instance =
                Instance.builder(
                                Parser.parse(
                                        module(
                                                0x7f,
                                                true,
                                                new int[] {
                                                    0, 0x41, 8, 0x41, 37, 0x36, 2, 4, 0x41, 8, 0x28,
                                                    2, 4, 0x23, 0, 0x6a, 0x0b
                                                })))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> {
                                    observed.add(instruction.opcode());
                                    if (instruction.opcode() == OpCode.I32_STORE) {
                                        assertEquals(0, owner.get().memory().readInt(12));
                                    }
                                })
                        .build();
        owner.set(instance);
        assertArrayEquals(new long[] {46}, instance.exports().function("run").apply());
        assertEquals(37, instance.memory().readInt(12));
        assertEquals(
                Arrays.asList(
                        OpCode.I32_CONST,
                        OpCode.I32_CONST,
                        OpCode.I32_STORE,
                        OpCode.I32_CONST,
                        OpCode.I32_LOAD,
                        OpCode.GLOBAL_GET,
                        OpCode.I32_ADD,
                        OpCode.END),
                observed);
    }

    @Test
    public void customDefaultAdaptersRetainOriginalInstructionValuesAcrossLaterCalls() {
        List<Instruction> observed = new ArrayList<>();
        AtomicReference<ExtensionMachine> machine = new AtomicReference<>();
        int[] first = vectorConstant(17, 29);
        int[] second = vectorConstant(43, 61);
        Instance instance =
                Instance.builder(
                                Parser.parse(
                                        module(
                                                0x7b,
                                                false,
                                                join(
                                                        new int[] {0},
                                                        first,
                                                        new int[] {0x1a},
                                                        second,
                                                        new int[] {0x0b}))))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> observed.add(instruction))
                        .withMachineFactory(
                                context -> {
                                    ExtensionMachine extension =
                                            new ExtensionMachine(context, observed);
                                    machine.set(extension);
                                    return extension;
                                })
                        .build();
        assertArrayEquals(new long[] {43, 61}, instance.exports().function("run").apply());
        assertArrayEquals(new long[] {43, 61}, instance.exports().function("run").apply());
        assertEquals(4, machine.get().defaults.size());
        for (int index = 0; index < 4; index++) {
            assertEquals(index % 2 == 0 ? 17 : 43, machine.get().defaults.get(index).get(0));
            assertEquals(index % 2 == 0 ? 29 : 61, machine.get().defaults.get(index).get(1));
        }
    }

    @Test
    public void directCallStillInvokesProtectedOverrideWithStableAdapters() {
        AtomicReference<ExtensionMachine> machine = new AtomicReference<>();
        List<Instruction> observed = new ArrayList<>();
        Instance instance =
                Instance.builder(
                                Parser.parse(
                                        module(
                                                0x7f,
                                                false,
                                                new int[] {0, 0x10, 1, 0x10, 2, 0x6a, 0x0b},
                                                new int[] {0, 0x41, 17, 0x0b},
                                                new int[] {0, 0x41, 23, 0x0b})))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> observed.add(instruction))
                        .withMachineFactory(
                                context -> {
                                    ExtensionMachine extension =
                                            new ExtensionMachine(context, observed);
                                    machine.set(extension);
                                    return extension;
                                })
                        .build();
        assertArrayEquals(new long[] {40}, instance.exports().function("run").apply());
        assertArrayEquals(new long[] {40}, instance.exports().function("run").apply());
        assertEquals(4, machine.get().calls.size());
        for (int index = 0; index < 4; index++) {
            assertEquals(index % 2 + 1, machine.get().calls.get(index).get(0));
        }
    }

    @Test
    public void listenerAbortEscapesByIdentityBeforeAnyStoreSideEffect() {
        RuntimeException fatal = new CancellationException("Owned fixture cancellation");
        List<OpCode> observed = new ArrayList<>();
        Instance instance =
                Instance.builder(
                                Parser.parse(
                                        module(
                                                0x7f,
                                                true,
                                                new int[] {
                                                    0, 0x41, 8, 0x41, 37, 0x36, 2, 4, 0x41, 0, 0x0b
                                                })))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> {
                                    observed.add(instruction.opcode());
                                    if (instruction.opcode() == OpCode.I32_STORE) {
                                        throw fatal;
                                    }
                                })
                        .build();
        assertSame(
                fatal,
                assertThrows(
                        CancellationException.class,
                        () -> instance.exports().function("run").apply()));
        assertEquals(0, instance.memory().readInt(12));
        assertEquals(Arrays.asList(OpCode.I32_CONST, OpCode.I32_CONST, OpCode.I32_STORE), observed);
    }

    @Test
    public void everyLoopInstructionStillReachesFiniteFuelAndCurrentnessListeners() {
        for (boolean cancellation : Arrays.asList(false, true)) {
            AtomicInteger executed = new AtomicInteger();
            RuntimeException fatal =
                    cancellation
                            ? new CancellationException("Owned fixture currentness exhausted")
                            : new IllegalStateException("Owned fixture instruction fuel exhausted");
            Instance instance =
                    Instance.builder(
                                    Parser.parse(
                                            module(
                                                    -1,
                                                    false,
                                                    new int[] {
                                                        0, 0x03, 0x40, 0x41, 1, 0x1a, 0x0c, 0, 0x0b,
                                                        0x0b
                                                    })))
                            .withUnsafeExecutionListener(
                                    (instruction, stack) -> {
                                        int count = executed.incrementAndGet();
                                        OpCode expected =
                                                count == 1
                                                        ? OpCode.LOOP
                                                        : (count - 2) % 3 == 0
                                                                ? OpCode.I32_CONST
                                                                : (count - 2) % 3 == 1
                                                                        ? OpCode.DROP
                                                                        : OpCode.BR;
                                        assertEquals(expected, instruction.opcode());
                                        if (count == 130) {
                                            throw fatal;
                                        }
                                    })
                            .build();
            assertSame(
                    fatal,
                    assertThrows(
                            fatal.getClass(), () -> instance.exports().function("run").apply()));
            assertEquals(130, executed.get());
        }
    }

    @Test
    public void memoryTrapAndProtectedStaticPointerAdapterRemainUnchanged() {
        List<OpCode> observed = new ArrayList<>();
        Instance instance =
                Instance.builder(
                                Parser.parse(
                                        module(
                                                0x7f,
                                                true,
                                                new int[] {
                                                    0, 0x41, 0x7f, 0x41, 37, 0x36, 2, 4, 0x41, 0,
                                                    0x0b
                                                })))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> observed.add(instruction.opcode()))
                        .build();
        assertThrows(WasmRuntimeException.class, () -> instance.exports().function("run").apply());
        assertEquals(0, instance.memory().readInt(0));
        assertEquals(Arrays.asList(OpCode.I32_CONST, OpCode.I32_CONST, OpCode.I32_STORE), observed);
        MStack stack = new MStack();
        stack.push(8);
        assertEquals(12, ExtensionMachine.pointer(stack, 4));
        stack.push(-1);
        assertThrows(WasmRuntimeException.class, () -> ExtensionMachine.pointer(stack, 4));
    }

    private static final class ExtensionMachine extends InterpreterMachine {
        final List<Operands> defaults = new ArrayList<>();
        final List<Operands> calls = new ArrayList<>();
        private final List<Instruction> observed;

        ExtensionMachine(Instance instance, List<Instruction> observed) {
            super(instance);
            this.observed = observed;
        }

        @Override
        protected void evalDefault(
                MStack stack,
                Instance instance,
                Deque<StackFrame> frames,
                Instruction instruction,
                Operands operands) {
            assertSame(instruction, observed.get(observed.size() - 1));
            assertEquals(OpCode.V128_CONST, instruction.opcode());
            defaults.add(operands);
            stack.push(operands.get(0));
            stack.push(operands.get(1));
        }

        @Override
        protected void CALL(Operands operands) {
            assertEquals(OpCode.CALL, observed.get(observed.size() - 1).opcode());
            calls.add(operands);
            super.CALL(operands);
        }

        static int pointer(MStack stack, long offset) {
            return readMemPtr(stack, index -> index == 1 ? offset : 0);
        }
    }

    private static int[] vectorConstant(long low, long high) {
        int[] bytes = new int[18];
        bytes[0] = 0xfd;
        bytes[1] = 12;
        for (int index = 0; index < 8; index++) {
            bytes[index + 2] = (int) (low >>> (index * 8)) & 255;
            bytes[index + 10] = (int) (high >>> (index * 8)) & 255;
        }
        return bytes;
    }

    private static int[] join(int[]... parts) {
        int size = 0;
        for (int[] part : parts) {
            size += part.length;
        }
        int[] result = new int[size];
        int at = 0;
        for (int[] part : parts) {
            System.arraycopy(part, 0, result, at, part.length);
            at += part.length;
        }
        return result;
    }

    private static byte[] module(int result, boolean memory, int[]... bodies) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0, 0x61, 0x73, 0x6d, 1, 0, 0, 0});
        section(out, 1, result < 0 ? new int[] {1, 0x60, 0, 0} : new int[] {1, 0x60, 0, 1, result});
        int[] functions = new int[bodies.length + 1];
        functions[0] = bodies.length;
        section(out, 3, functions);
        if (memory) {
            section(out, 5, new int[] {1, 0, 1});
            section(out, 6, new int[] {1, 0x7f, 1, 0x41, 9, 0x0b});
        }
        section(out, 7, new int[] {1, 3, 'r', 'u', 'n', 0, 0});
        ByteArrayOutputStream code = new ByteArrayOutputStream();
        unsigned(code, bodies.length);
        for (int[] body : bodies) {
            unsigned(code, body.length);
            for (int value : body) {
                code.write(value);
            }
        }
        section(out, 10, code.toByteArray());
        return out.toByteArray();
    }

    private static void section(ByteArrayOutputStream out, int id, int[] values) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int value : values) {
            bytes.write(value);
        }
        section(out, id, bytes.toByteArray());
    }

    private static void section(ByteArrayOutputStream out, int id, byte[] values) {
        out.write(id);
        unsigned(out, values.length);
        out.writeBytes(values);
    }

    private static void unsigned(ByteArrayOutputStream out, int value) {
        do {
            int next = value & 127;
            value >>>= 7;
            out.write(value == 0 ? next : next | 128);
        } while (value != 0);
    }
}
