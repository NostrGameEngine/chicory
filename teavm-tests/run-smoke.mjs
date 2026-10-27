import { main } from './target/javascript/runtime-smoke.mjs';

await new Promise((resolve, reject) => {
  const timeout = setTimeout(() => reject(new Error('TeaVM runtime smoke test timed out')), 60000);
  main([], result => {
    clearTimeout(timeout);
    if (result instanceof Error) {
      reject(result);
    } else {
      resolve();
    }
  });
});
console.log('CHICORY_TEAVM_RUNTIME_PASSED');
