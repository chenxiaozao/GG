// Isolated temporary fixture only. Never initializes the real deployment directory.
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { readFile, copyFile, mkdtemp, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'manage.sh');
execFileSync('bash', ['-n', script]);
const directory = await mkdtemp(path.join(tmpdir(), 'robotmall-deploy-init-'));
const fixture = path.join(directory, 'manage.sh');
await copyFile(script, fixture);
const initial = spawnSync('bash', [fixture, 'init'], { input: 'api.example.com\nadmin@example.com\n', encoding: 'utf8' });
assert.equal(initial.status, 0, 'fixture initialization failed (output withheld)');
const env = await readFile(path.join(directory, '.env'), 'utf8');
assert.equal((await stat(path.join(directory, '.env'))).mode & 0o777, 0o600);
const values = Object.fromEntries(env.trim().split('\n').map(line => {
  const split = line.indexOf('='); return [line.slice(0,split), line.slice(split+1)];
}));
assert.equal(values.API_DOMAIN, 'api.example.com');
assert.equal(values.DEEPSEEK_API_KEY, '');
assert.match(values.DB_PASSWORD, /^[0-9a-f]{48}$/);
assert.match(values.MYSQL_ROOT_PASSWORD, /^[0-9a-f]{48}$/);
assert.match(values.BOOTSTRAP_ADMIN_PASSWORD, /^[0-9a-f]{32}$/);
assert.notEqual(values.DB_PASSWORD, values.MYSQL_ROOT_PASSWORD);
const again = spawnSync('bash', [fixture, 'init'], { input: 'other.example.com\na@b.com\n', encoding: 'utf8' });
assert.notEqual(again.status, 0);
assert.equal(await readFile(path.join(directory,'.env'),'utf8'), env, 'existing private config was overwritten');
for (const domain of ['http://api.example.com','api.example.com/api','a..example.com','api-.example.com','127.0.0.1']) {
  const badDirectory = await mkdtemp(path.join(tmpdir(),'robotmall-deploy-reject-'));
  const badFixture = path.join(badDirectory,'manage.sh');
  await copyFile(script,badFixture);
  const result = spawnSync('bash',[badFixture,'init'],{input:domain+'\na@b.com\n',encoding:'utf8'});
  assert.notEqual(result.status,0,'invalid hostname accepted');
  await assert.rejects(stat(path.join(badDirectory,'.env')));
}
console.log('PASS isolated deployment init: private permissions, unique generated credentials, no overwrite and invalid-domain rejection. No real credentials displayed.');
