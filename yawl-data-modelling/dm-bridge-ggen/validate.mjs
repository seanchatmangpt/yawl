#!/usr/bin/env node
/**
 * validate.mjs — ggen golden-file validator for dm-bridge-ggen
 *
 * Diffs the committed golden reference files (golden/) against the actual
 * generated source files (../src/main/java/…/datamodelling/bridge/).
 *
 * Usage:
 *   node validate.mjs
 *
 * Exit codes:
 *   0 — golden files match generated source (sync is current)
 *   1 — divergence detected (run: ggen sync, then re-commit)
 */

import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dir = dirname(fileURLToPath(import.meta.url));

const GOLDEN_DIR = join(__dir, 'golden');
const SRC_DIR = join(
  __dir,
  '../src/main/java/org/yawlfoundation/yawl/datamodelling/bridge'
);

const GENERATED_FILES = [
  'DataModellingCapability.java',
  'DataModellingCapabilityRegistry.java',
  'DataModellingL2.java',
  'DataModellingL3.java',
  'DataModellingMapsToCapability.java',
  'DataModellingCapabilityTest.java',
  'DataModellingCapabilityRegistryException.java',
];

let diverged = false;

for (const file of GENERATED_FILES) {
  const goldenPath = join(GOLDEN_DIR, file);
  const srcPath    = join(SRC_DIR,    file);

  if (!existsSync(goldenPath)) {
    console.error(`[MISSING GOLDEN] ${file}`);
    console.error(`  Expected: ${goldenPath}`);
    console.error('  Run: ggen sync (in dm-bridge-ggen/) then copy output to golden/');
    diverged = true;
    continue;
  }

  if (!existsSync(srcPath)) {
    console.error(`[MISSING SOURCE] ${file}`);
    console.error(`  Expected: ${srcPath}`);
    console.error('  Run: ggen sync (in dm-bridge-ggen/) to regenerate');
    diverged = true;
    continue;
  }

  const golden = readFileSync(goldenPath, 'utf8');
  const src    = readFileSync(srcPath,    'utf8');

  if (golden !== src) {
    console.error(`[DIVERGED] ${file}`);
    console.error('  golden/ and src/main/java/…/bridge/ differ.');
    console.error('  Either run `ggen sync` to regenerate, or update golden/ if intentional.');
    diverged = true;
  } else {
    console.log(`[OK] ${file}`);
  }
}

if (diverged) {
  console.error('\nValidation FAILED — golden files diverge from generated source.');
  console.error('Run: cd dm-bridge-ggen && ggen sync');
  process.exit(1);
} else {
  console.log('\nValidation PASSED — all golden files match generated source.');
  process.exit(0);
}
