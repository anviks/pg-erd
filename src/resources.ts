import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

/** Absolute path of a file under the package's `resources/` directory. */
export function resourcePath(rel: string): string {
  return join(dirname(fileURLToPath(import.meta.url)), '..', 'resources', rel);
}

export function resourceText(rel: string): string {
  return readFileSync(resourcePath(rel), 'utf8');
}

export function resourceBase64(rel: string): string {
  return readFileSync(resourcePath(rel)).toString('base64');
}
