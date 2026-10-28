#!/usr/bin/env node
import { main } from '../dist/cli.js';

// `pg-erd --help | head` closes stdout early; that is not an error
process.stdout.on('error', (e) => {
  if (e.code === 'EPIPE') process.exit(0);
  throw e;
});

process.exitCode = await main(process.argv.slice(2));
