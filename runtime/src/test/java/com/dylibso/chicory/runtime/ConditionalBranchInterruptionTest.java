package com.dylibso.chicory.runtime;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.OpCode;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Real binary loops whose only backedge is conditional or table-based, with bounded cleanup.
 */
public class ConditionalBranchInterruptionTest {
    @Test
    @Timeout(10)
    public void shouldInterruptConditionalOnlyBackedge() throws InterruptedException {
        // (loop (i32.const 1) (br_if 0)) -- no ordinary BR or host call can hide the bug.
        assertInterrupted(OpCode.BR_IF, new int[] {0, 3, 0x40, 0x41, 1, 0x0d, 0, 0x0b, 0x0b});
    }

    @Test
    @Timeout(10)
    public void shouldInterruptTableOnlyBackedge() throws InterruptedException {
        // (loop (i32.const 0) (br_table 0)) -- empty label vector, default targets the loop.
        assertInterrupted(OpCode.BR_TABLE, new int[] {0, 3, 0x40, 0x41, 0, 0x0e, 0, 0, 0x0b, 0x0b});
    }

    private static void assertInterrupted(OpCode backedge, int[] body) throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        AtomicBoolean cleanup = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        RuntimeException cleanupOnly = new RuntimeException("Controlled failed-test cleanup");
        Instance instance =
                Instance.builder(Parser.parse(module(body)))
                        .withUnsafeExecutionListener(
                                (instruction, stack) -> {
                                    if (cleanup.get()) {
                                        throw cleanupOnly;
                                    }
                                    if (instruction.opcode() == backedge) {
                                        entered.countDown();
                                    }
                                })
                        .build();
        Thread worker =
                new Thread(
                        () -> {
                            try {
                                instance.exports().function("run").apply();
                            } catch (Throwable aborted) {
                                failure.set(aborted);
                            } finally {
                                returned.countDown();
                            }
                        },
                        "conditional-branch-interruption-test");
        worker.start();
        try {
            assertTrue(entered.await(2, SECONDS), "The actual loop must start before interruption");
            worker.interrupt();
            assertTrue(returned.await(2, SECONDS), "The branch backedge must observe interruption");
            assertNotSame(cleanupOnly, failure.get());
            assertInstanceOf(ChicoryInterruptedException.class, failure.get());
        } finally {
            // The original implementation must also leave no live thread after its failed
            // assertion. This listener-only escape is cleanup, never the passing outcome.
            cleanup.set(true);
            worker.interrupt();
            worker.join(2000);
            assertFalse(worker.isAlive(), "The owned test worker must physically return");
        }
    }

    private static byte[] module(int[] body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(new byte[] {0, 0x61, 0x73, 0x6d, 1, 0, 0, 0});
        section(bytes, 1, new int[] {1, 0x60, 0, 0});
        section(bytes, 3, new int[] {1, 0});
        section(bytes, 7, new int[] {1, 3, 'r', 'u', 'n', 0, 0});
        int[] code = new int[body.length + 2];
        code[0] = 1;
        code[1] = body.length;
        System.arraycopy(body, 0, code, 2, body.length);
        section(bytes, 10, code);
        return bytes.toByteArray();
    }

    private static void section(ByteArrayOutputStream bytes, int id, int[] body) {
        bytes.write(id);
        bytes.write(body.length); // These fixed test sections fit one canonical ULEB byte.
        for (int value : body) {
            bytes.write(value);
        }
    }
}
