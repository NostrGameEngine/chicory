package com.dylibso.chicory.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.OpCode;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Immutable popped values must survive later control-stack activity, including real guest unwind.
 */
@Timeout(10)
public class ImmutableControlFramesTest {
    @Test
    public void retainedTargetAndCallUnwindStayIndependentOfInactiveCache() {
        StackFrame frame = new StackFrame(emptyInstance(), 0, new long[0]);
        frame.pushCtrl(OpCode.CALL, 0, 0, 0);
        frame.pushCtrl(OpCode.TRY_TABLE, 0, 2, 2, 17);
        CtrlFrame retained = frame.popCtrl();
        frame.pushCtrl(OpCode.BLOCK, 1, 0, 9, 23);
        frame.pushCtrl(OpCode.LOOP, 0, 1, 11);
        assertEquals(OpCode.CALL, frame.popCtrlTillCall().opCode);
        assertEquals(0, frame.ctrlStackSize());
        assertThrows(IndexOutOfBoundsException.class, frame::popCtrl);
        assertEquals(0, frame.ctrlStackSize());
        assertFrame(retained, OpCode.TRY_TABLE, 0, 2, 2, 17);
        frame.pushCtrl(retained);
        MStack values = new MStack();
        for (long value : new long[] {99, 88, 77, 31, 37}) {
            values.push(value);
        }
        StackFrame.doControlTransfer(frame.popCtrl(), values);
        assertEquals(4, values.size());
        assertArrayEquals(
                new long[] {37, 31, 88, 99},
                new long[] {values.pop(), values.pop(), values.pop(), values.pop()});
        assertFrame(retained, OpCode.TRY_TABLE, 0, 2, 2, 17);
    }

    @Test
    public void immutableReuseRequiresAllFieldsAndDeepCacheIsBounded() {
        StackFrame frame = new StackFrame(emptyInstance(), 0, new long[0]);
        for (int changed = 0; changed < 5; changed++) {
            frame.pushCtrl(OpCode.BLOCK, 1, 2, 3, 4);
            CtrlFrame retained = frame.popCtrl();
            frame.pushCtrl(OpCode.BLOCK, 1, 2, 3, 4);
            assertSame(retained, frame.popCtrl());
            OpCode op = changed == 0 ? OpCode.LOOP : OpCode.BLOCK;
            int start = changed == 1 ? 2 : 1;
            int end = changed == 2 ? 3 : 2;
            int height = changed == 3 ? 4 : 3;
            int pc = changed == 4 ? 5 : 4;
            frame.pushCtrl(op, start, end, height, pc);
            CtrlFrame replaced = frame.popCtrl();
            assertNotSame(retained, replaced);
            assertFrame(replaced, op, start, end, height, pc);
            assertFrame(retained, OpCode.BLOCK, 1, 2, 3, 4);
        }
        List<CtrlFrame> retained = new ArrayList<>();
        for (int index = 0; index < 257; index++) {
            frame.pushCtrl(OpCode.BLOCK, 0, 0, index);
        }
        for (int index = 0; index < 257; index++) {
            retained.add(0, frame.popCtrl());
        }
        assertEquals(0, frame.ctrlStackSize());
        for (int index = 0; index < 257; index++) {
            frame.pushCtrl(OpCode.BLOCK, 0, 0, index);
            CtrlFrame current = frame.popCtrl();
            if (index < 64) {
                assertSame(retained.get(index), current);
            } else {
                assertNotSame(retained.get(index), current);
            }
            frame.pushCtrl(current);
        }
        assertEquals(257, frame.ctrlStackSize());
    }

    @Test
    public void branchTableLoopAndMultiValueTransferKeepOriginalResults() {
        List<OpCode> trace = new ArrayList<>();
        Instance loop =
                Instance.builder(
                                Parser.parse(
                                        module(
                                                new byte[][] {type(new int[0], 0x7f)},
                                                new int[] {0},
                                                false,
                                                body(
                                                        1, 0x41, 0, 0x21, 0, 0x02, 0x40, 0x03, 0x40,
                                                        0x02, 0x7f, 0x41, 7, 0x41, 0, 0x0e, 0, 0, 0,
                                                        0x0b, 0x1a, 0x20, 0, 0x41, 1, 0x6a, 0x22, 0,
                                                        0x41, 32, 0x49, 0x0d, 0, 0x0b, 0x0b, 0x20,
                                                        0, 0x0b))))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> trace.add(instruction.opcode()))
                        .build();
        assertArrayEquals(new long[] {32}, loop.export("run").apply());
        List<OpCode> first = List.copyOf(trace);
        trace.clear();
        assertArrayEquals(new long[] {32}, loop.export("run").apply());
        assertEquals(first, trace);
        assertEquals(32, trace.stream().filter(op -> op == OpCode.BR_TABLE).count());
        assertEquals(32, trace.stream().filter(op -> op == OpCode.BR_IF).count());
        Instance values =
                Instance.builder(
                                Parser.parse(
                                        module(
                                                new byte[][] {
                                                    type(new int[0], 0x7f),
                                                    type(new int[0], 0x7f, 0x7e)
                                                },
                                                new int[] {0},
                                                false,
                                                body(
                                                        0, 0x02, 1, 0x41, 42, 0x42, 9, 0x0c, 0,
                                                        0x0b, 0x1a, 0x0b))))
                        .build();
        assertArrayEquals(new long[] {42}, values.export("run").apply());
    }

    @Test
    public void sameFunctionTailCallsReuseDetachedCallTargetsWithoutGrowingDepth() {
        AtomicReference<DepthMachine> machine = new AtomicReference<>();
        AtomicInteger deepest = new AtomicInteger();
        AtomicInteger tails = new AtomicInteger();
        Instance instance =
                Instance.builder(
                                Parser.parse(
                                        module(
                                                new byte[][] {type(new int[] {0x7f}, 0x7f)},
                                                new int[] {0},
                                                false,
                                                body(
                                                        0, 0x20, 0, 0x45, 0x04, 0x7f, 0x41, 37,
                                                        0x05, 0x20, 0, 0x41, 1, 0x6b, 0x12, 0, 0x0b,
                                                        0x0b))))
                        .withMachineFactory(
                                owner -> {
                                    DepthMachine value = new DepthMachine(owner);
                                    machine.set(value);
                                    return value;
                                })
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> {
                                    deepest.accumulateAndGet(machine.get().depth(), Math::max);
                                    if (instruction.opcode() == OpCode.RETURN_CALL) {
                                        tails.incrementAndGet();
                                    }
                                })
                        .build();
        assertArrayEquals(new long[] {37}, instance.export("run").apply(128));
        assertEquals(128, tails.get());
        assertEquals(1, deepest.get());
        assertEquals(0, machine.get().depth());
    }

    @Test
    public void repeatedTryTableUnwindKeepsHandlerAndFatalIdentity() {
        byte[] binary =
                module(
                        new byte[][] {type(new int[0]), type(new int[0], 0x7f)},
                        new int[] {1},
                        true,
                        body(
                                1, 0x03, 0x40, 0x02, 0x40, 0x1f, 0x40, 1, 2, 0, 0x02, 0x40, 0x20, 0,
                                0x41, 1, 0x6a, 0x21, 0, 0x08, 0, 0x0b, 0x0b, 0x0b, 0x20, 0, 0x41,
                                32, 0x49, 0x0d, 0, 0x0b, 0x20, 0, 0x0b));
        AtomicInteger handlers = new AtomicInteger();
        Instance guest =
                Instance.builder(Parser.parse(binary))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> {
                                    if (instruction.opcode() == OpCode.TRY_TABLE) {
                                        handlers.incrementAndGet();
                                    }
                                })
                        .build();
        assertArrayEquals(new long[] {32}, guest.export("run").apply());
        assertArrayEquals(new long[] {32}, guest.export("run").apply());
        assertEquals(64, handlers.get());
        CancellationException fatal = new CancellationException("Owned control-stack cancellation");
        Instance cancelled =
                Instance.builder(Parser.parse(binary))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> {
                                    if (instruction.opcode() == OpCode.THROW) {
                                        throw fatal;
                                    }
                                })
                        .build();
        assertSame(
                fatal,
                assertThrows(CancellationException.class, () -> cancelled.export("run").apply()));
    }

    private static final class DepthMachine extends InterpreterMachine {
        DepthMachine(Instance instance) {
            super(instance);
        }

        int depth() {
            return callStack.size();
        }
    }

    private static void assertFrame(
            CtrlFrame value, OpCode op, int start, int end, int height, int pc) {
        assertEquals(op, value.opCode);
        assertEquals(start, value.startValues);
        assertEquals(end, value.endValues);
        assertEquals(height, value.height);
        assertEquals(pc, value.pc);
    }

    private static Instance emptyInstance() {
        return Instance.builder(
                        Parser.parse(
                                module(
                                        new byte[][] {type(new int[0])},
                                        new int[] {0},
                                        false,
                                        body(0, 0x0b))))
                .build();
    }

    private static byte[] type(int[] parameters, int... results) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x60);
        unsigned(out, parameters.length);
        for (int value : parameters) {
            out.write(value);
        }
        unsigned(out, results.length);
        for (int value : results) {
            out.write(value);
        }
        return out.toByteArray();
    }

    private static byte[] body(int locals, int... instructions) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        unsigned(out, locals == 0 ? 0 : 1);
        if (locals != 0) {
            unsigned(out, locals);
            out.write(0x7f);
        }
        for (int value : instructions) {
            out.write(value);
        }
        return out.toByteArray();
    }

    private static byte[] module(byte[][] types, int[] functions, boolean tag, byte[]... bodies) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0, 0x61, 0x73, 0x6d, 1, 0, 0, 0});
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        unsigned(data, types.length);
        for (byte[] value : types) {
            data.writeBytes(value);
        }
        section(out, 1, data);
        data = new ByteArrayOutputStream();
        unsigned(data, functions.length);
        for (int value : functions) {
            unsigned(data, value);
        }
        section(out, 3, data);
        if (tag) {
            data = new ByteArrayOutputStream();
            data.writeBytes(new byte[] {1, 0, 0});
            section(out, 13, data);
        }
        data = new ByteArrayOutputStream();
        data.writeBytes(new byte[] {1, 3, 'r', 'u', 'n', 0, 0});
        section(out, 7, data);
        data = new ByteArrayOutputStream();
        unsigned(data, bodies.length);
        for (byte[] value : bodies) {
            unsigned(data, value.length);
            data.writeBytes(value);
        }
        section(out, 10, data);
        return out.toByteArray();
    }

    private static void section(ByteArrayOutputStream out, int id, ByteArrayOutputStream data) {
        out.write(id);
        unsigned(out, data.size());
        out.writeBytes(data.toByteArray());
    }

    private static void unsigned(ByteArrayOutputStream out, int value) {
        do {
            int next = value & 127;
            value >>>= 7;
            out.write(value == 0 ? next : next | 128);
        } while (value != 0);
    }
}
