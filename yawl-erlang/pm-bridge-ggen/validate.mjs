#!/usr/bin/env node
// validate.mjs — verify lib/ matches golden/ after gen-ttl runs.
// Exit 0 = all match; Exit 1 = diff found.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = new URL('.', import.meta.url).pathname;
const LIB = join(ROOT, 'lib');
const GOLDEN = join(ROOT, 'golden');

let allOk = true;

function checkDir(rel) {
    const libDir = join(LIB, rel);
    const goldenDir = join(GOLDEN, rel);
    if (!existsSync(libDir)) { console.error(`MISSING lib/${rel}`); allOk = false; return; }
    if (!existsSync(goldenDir)) { console.log(`SKIP golden/${rel} (not yet committed)`); return; }
    for (const f of readdirSync(goldenDir)) {
        const libFile = join(libDir, f);
        const goldenFile = join(goldenDir, f);
        if (!existsSync(libFile)) {
            console.error(`MISSING lib/${rel}/${f}`);
            allOk = false;
            continue;
        }
        const libContent = readFileSync(libFile, 'utf8');
        const goldenContent = readFileSync(goldenFile, 'utf8');
        if (libContent !== goldenContent) {
            console.error(`DIFF lib/${rel}/${f} vs golden/${rel}/${f}`);
            allOk = false;
        } else {
            console.log(`OK   lib/${rel}/${f}`);
        }
    }
}

['java', 'erlang', 'test'].forEach(checkDir);

if (!allOk) { console.error('Validation FAILED'); process.exit(1); }
console.log('All artifacts match golden. Validation PASSED.');
