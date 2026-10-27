import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const backend = process.argv[2] || 'javascript';
assert(['javascript', 'wasm-gc'].includes(backend), `Unknown TeaVM backend: ${backend}`);
// Read after compilation: these bytes are passed to Chicory's runtime parser.
const binary = await readFile(new URL('./src/main/resources/runtime-smoke.wasm', import.meta.url));
const hex = binary.toString('hex');
const output = [];
const log = console.log;
const info = console.info;
console.log = (...args) => {
  output.push(args.join(' '));
  log(...args);
};
console.info = console.log;

try {
  let run;
  if (backend === 'javascript') {
    const { main } = await import('./target/javascript/runtime-smoke.mjs');
    run = args => new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('TeaVM main timed out')), 60000);
      main(args, result => {
        clearTimeout(timeout);
        result instanceof Error ? reject(result) : resolve();
      });
    });
  } else {
    const { load } = await import('./target/wasm-gc/wasm-runtime.mjs');
    const wasm = await readFile(new URL('./target/wasm-gc/runtime-smoke.wasm', import.meta.url));
    const application = await load(wasm);
    run = args => application.exports.main(args);
  }
  for (const mode of ['interpreter', 'precompiled']) {
    await run([mode, hex]);
    assert(output.includes(`CHICORY_TEAVM_OK ${mode}`), `Missing completion: ${backend}/${mode}`);
    log(`CHICORY_TEAVM_PASSED ${backend}/${mode}`);
  }
} finally {
  console.log = log;
  console.info = info;
}
