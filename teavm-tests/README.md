# NGE TeaVM runtime tests

With JDK 25 and Node.js 22 installed, run from the repository root:

```sh
mvn -B -ntp -U -Dteavm-tests -pl teavm-tests -am verify
```

This compiles Chicory's parser and interpreter to JavaScript with
[`NostrGameEngine/teavm`](https://github.com/NostrGameEngine/teavm), using the
`org.ngengine:teavm-maven-plugin` and `org.ngengine:teavm-classlib`
`0.16.0-SNAPSHOT` artifacts from Maven Central snapshots. Override the version
with `-Dteavm.version=...` when testing another published fork version.

The generated JavaScript runs in Node.js and checks conditional/table loops,
10,000 tail calls, active element function references, 32/64-bit memory access,
memory growth and bounds traps with both the default memory and `ByteArrayMemory`.
`verify` fails on compilation errors, failed assertions or a missing completion
callback. The generated module is in `target/javascript/runtime-smoke.mjs`.

The WebAssembly fixture in `src/main/resources/runtime-smoke.wat` is embedded in
`RuntimeSmoke.java`; regenerate its bytes with `wat2wasm --enable-tail-call` when
changing the fixture.
