// Run on the development computer. Release contents are an explicit allowlist, not a project copy.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir, mkdtemp, copyFile, chmod, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const deployment = path.dirname(fileURLToPath(import.meta.url));
const server = path.dirname(deployment);
const root = path.dirname(server);
const assets = ['compose.yml', 'Dockerfile', '.dockerignore', '.env.example', 'Caddyfile', 'manage.sh', 'backup.sh', 'README.md'];
execFileSync('node', [path.join(deployment, 'template-test.mjs')], { cwd: server, stdio: 'inherit' });
execFileSync('mvn', ['-B', 'package'], { cwd: server, stdio: 'inherit', env: { ...process.env, DEEPSEEK_API_KEY: '' } });
const releases = path.join(root, 'tmp', 'deployment');
await mkdir(releases, { recursive: true });
const stage = await mkdtemp(path.join(releases, 'stage-'));
const payload = path.join(stage, 'robotmall-backend');
await mkdir(payload);
for (const name of assets) await copyFile(path.join(deployment, name), path.join(payload, name));
await copyFile(path.join(server, 'target', 'robotmall-server-1.0.0.jar'), path.join(payload, 'server.jar'));
for (const name of ['manage.sh', 'backup.sh']) await chmod(path.join(payload, name), 0o755);
const hashes = [];
for (const name of [...assets, 'server.jar']) {
  hashes.push(`${createHash('sha256').update(await readFile(path.join(payload, name))).digest('hex')}  ${name}`);
}
await writeFile(path.join(payload, 'SHA256SUMS'), hashes.join('\n') + '\n');
const files = await readdir(payload);
if (files.some(name => ![...assets, 'server.jar', 'SHA256SUMS'].includes(name))) throw new Error('Unexpected file in release payload');
const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z');
const archive = path.join(releases, `robotmall-backend-${stamp}.tar.gz`);
execFileSync('tar', ['-czf', archive, '-C', stage, 'robotmall-backend']);
console.log(`\n后端专用部署包：${archive}\n没有包含真实 .env、数据库、头像、前端或论文。解压后先读 README.md。`);
