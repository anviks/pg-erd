import { describe, expect, it } from 'vitest';
import { connectionConfig, globFilter } from '../src/cli.js';

describe('connection settings', () => {
  it('uses the flags, leaving the rest to the driver defaults', () => {
    const cfg = connectionConfig({ host: 'db', port: 5433, dbname: 'app', user: 'me', passwordEnv: 'PGPASSWORD' }, { PGPASSWORD: 's3' });
    expect(cfg).toEqual({ host: 'db', port: 5433, database: 'app', user: 'me', password: 's3', application_name: 'pg-erd' });
    expect(connectionConfig({ dbname: 'app', passwordEnv: 'PGPASSWORD' }, {})).toEqual({ database: 'app', application_name: 'pg-erd' });
  });

  it('lets a URL win over flags and accepts the JDBC form', () => {
    const cfg = connectionConfig({ url: 'jdbc:postgresql://u:pw@h:5555/db', host: 'other', user: 'x', passwordEnv: 'PGPASSWORD' }, { PGPASSWORD: 'env' });
    expect(cfg).toMatchObject({ host: 'h', port: 5555, database: 'db', user: 'u', password: 'pw' });
    const noCreds = connectionConfig({ url: 'postgres://h/db', user: 'x', passwordEnv: 'PW' }, { PW: 'env' });
    expect(noCreds).toMatchObject({ host: 'h', database: 'db', user: 'x', password: 'env' });
  });

  it('takes PGUSER and a custom password variable from the environment', () => {
    expect(connectionConfig({ passwordEnv: 'DB_PW' }, { PGUSER: 'envuser', DB_PW: 'x' })).toMatchObject({ user: 'envuser', password: 'x' });
  });

  it('filters table names with globs', () => {
    const f = globFilter(['ord*', 'cust?mers'], ['*_log']);
    expect(f('orders')).toBe(true);
    expect(f('customers')).toBe(true);
    expect(f('orders_log')).toBe(false);
    expect(f('products')).toBe(false);
    expect(globFilter([], [])('anything')).toBe(true);
  });
});
