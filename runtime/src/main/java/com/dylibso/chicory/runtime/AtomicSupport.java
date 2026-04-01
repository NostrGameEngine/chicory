package com.dylibso.chicory.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class AtomicSupport {
    private static final Object FENCE_MONITOR = new Object();
    private static final Runnable FULL_FENCE = initFullFence();

    private AtomicSupport() {}

    static void fullFence() {
        FULL_FENCE.run();
    }

    private static Runnable initFullFence() {
        Runnable impl = tryVarHandleFullFence();
        if (impl != null) {
            return impl;
        }

        impl = tryUnsafeFullFence();
        if (impl != null) {
            return impl;
        }

        return AtomicSupport::synchronizedFullFence;
    }

    private static Runnable tryVarHandleFullFence() {
        try {
            Class<?> varHandleClass = Class.forName("java.lang.invoke.VarHandle");
            Method fullFence = varHandleClass.getMethod("fullFence");

            // Probe once now so we fail early if the runtime claims the class exists
            // but cannot actually execute the method.
            fullFence.invoke(null);

            return () -> {
                try {
                    fullFence.invoke(null);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(
                            "ATOMIC_FENCE implementation: failed to invoke VarHandle.fullFence()",
                            e);
                }
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Runnable tryUnsafeFullFence() {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            Object theUnsafe = theUnsafeField.get(null);
            Method fullFence = unsafeClass.getMethod("fullFence");

            // Probe once now.
            fullFence.invoke(theUnsafe);

            return () -> {
                try {
                    fullFence.invoke(theUnsafe);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(
                            "ATOMIC_FENCE implementation: failed to invoke Unsafe.fullFence()",
                            e);
                }
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void synchronizedFullFence() {
        synchronized (FENCE_MONITOR) {
            // Best-effort portable fallback:
            // monitor enter/exit gives acquire/release synchronization semantics.
        }
    }
}