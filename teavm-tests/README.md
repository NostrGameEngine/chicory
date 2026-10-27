# NGE TeaVM runtime tests

With JDK 25 and Node.js 22 installed, run from the repository root:

```sh
# TeaVM JavaScript
mvn -B -ntp -U -Dteavm-tests -pl teavm-tests -am verify
# TeaVM WebAssembly GC
mvn -B -ntp -U -Dteavm-tests -Dteavm.backend=wasm-gc -pl teavm-tests -am verify
```

Both builds use [`NostrGameEngine/teavm`](https://github.com/NostrGameEngine/teavm):
`org.ngengine:teavm-maven-plugin`, `org.ngengine:teavm-classlib` and
`org.ngengine:teavm-extension-spi` version `0.16.0-SNAPSHOT` from Maven Central
snapshots. Override the version with `-Dteavm.version=...` to test another
published fork version. The Wasm GC test runner enables Node's
`--experimental-wasm-exnref` option for the exception handling instructions
emitted by this TeaVM version.

Each backend runs two execution modes:

| TeaVM backend | Runtime-loaded Wasm | Build-time compiled Wasm |
| --- | --- | --- |
| JavaScript | `Parser.parse(byte[])` and interpreter | Generated `PrecompiledSmoke` machine |
| WebAssembly GC | `Parser.parse(byte[])` and interpreter | Generated `PrecompiledSmoke` machine |

For runtime loading, the Node.js runner reads `runtime-smoke.wasm` after the
TeaVM build and passes its bytes to the application. For precompiled loading,
the Chicory Maven plugin generates JVM classes during `generate-sources`, and
TeaVM compiles those classes into the target application. `SmokeResources`
uses the NGE TeaVM resource policy SPI to bundle the generated `.meta` resource,
which `PrecompiledSmoke.load()` reads through the normal classpath API.

Both modes check conditional/table loops, tail calls, active element function
references, 32/64-bit memory access, growth and bounds traps with the default
memory and `ByteArrayMemory`. Interpreter tail-call depth is 10,000; precompiled
tail-call depth is 128 because the current compiler uses the host call stack.
This is a compatibility smoke test, not the complete WebAssembly specification
suite. Runtime JVM bytecode generation is not used in the TeaVM application.

`verify` fails on compilation errors, failed assertions or missing completion.
Generated applications are in `target/javascript/` and `target/wasm-gc/`.
Regenerate the binary fixture from `src/main/resources/runtime-smoke.wat` with
`wat2wasm --enable-tail-call` when changing it.

In an application, runtime-loaded modules use the ordinary API:

```java
var instance = Instance.builder(Parser.parse(downloadedWasmBytes)).build();
```

For build-time modules, run `org.ngengine.chicory:chicory-compiler-maven-plugin`
before TeaVM, bundle the generated classes and `.meta` file, and instantiate
with the generated factory:

```java
var instance = Instance.builder(MyCompiledModule.load())
        .withMachineFactory(MyCompiledModule::create)
        .build();
```

Keep the JVM compiler/ASM dependencies in the build plugin. The TeaVM application
only needs Chicory's runtime/parser and the generated classes. For resource
bundling, adapt `SmokeResources` and its `META-INF/services` registration to the
generated module name; both TeaVM targets use the same resource policy.
