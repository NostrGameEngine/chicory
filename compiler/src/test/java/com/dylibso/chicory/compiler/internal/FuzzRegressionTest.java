package com.dylibso.chicory.compiler.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.corpus.CorpusResources;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;
import org.junit.jupiter.api.Test;

public class FuzzRegressionTest {
    @Test
    public void referenceBridgesMatchInterpreter() {
        verify(
                "compiler-fuzz-regressions",
                new String[] {"function-reference", "external-reference"},
                new long[] {17, 23});
    }

    @Test
    public void nestedAndUnreachableHandlersMatchInterpreter() {
        verify(
                "compiler-exception-regressions",
                new String[] {"nested-handlers", "unreachable-handler"},
                new long[] {42, 7});
    }

    private static void verify(String fixture, String[] exports, long[] expected) {
        var module = Parser.parse(CorpusResources.getResource("compiled/" + fixture + ".wat.wasm"));
        var interpreted = Instance.builder(module).build();
        var compiled =
                Instance.builder(module)
                        .withMachineFactory(MachineFactoryCompiler::compile)
                        .build();
        for (int i = 0; i < exports.length; i++) {
            long[] reference = interpreted.export(exports[i]).apply();
            assertArrayEquals(reference, compiled.export(exports[i]).apply(), exports[i]);
            assertArrayEquals(
                    new long[] {expected[i]},
                    new long[] {reference[reference.length - 1]},
                    exports[i]);
        }
    }
}
