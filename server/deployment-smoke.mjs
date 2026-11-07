import { spawn } from 'node:child_process';
import { mkdtemp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
const directory = await mkdtemp(path.join(tmpdir(), 'robotmall-on-demand-'));
const jar = path.resolve('target/robotmall-server-1.0.0.jar');
const base = 'http://127.0.0.1:18080';
const profile = process.argv.includes('--prod') ? 'prod' : 'on-demand';
let child;
async function stop() {
  if (!child || child.exitCode !== null) return;
  const ended = new Promise(resolve => child.once('exit', resolve));
  child.kill('SIGTERM');
  await ended;
}
async function start() {
  child = spawn('java', ['-Xmx384m', '-jar', jar, `--spring.profiles.active=${profile}`, '--server.address=127.0.0.1', '--server.port=18080',
    `--spring.datasource.url=jdbc:h2:file:${directory}/robotmall;MODE=MySQL;DATABASE_TO_LOWER=TRUE`,
    '--spring.datasource.driver-class-name=org.h2.Driver', '--spring.datasource.username=sa', '--spring.datasource.password=',
    `--app.avatar-dir=${directory}/avatars`, '--app.bootstrap-admin-password=IsolatedSmoke123!', '--deepseek.api-key=', '--app.ai-user-daily-limit=2'],
    { env: { ...process.env, DEEPSEEK_API_KEY: '' }, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.resume(); child.stderr.resume();
  for (let i=0;i<90;i++) {
    if (child.exitCode !== null) throw new Error('Isolated backend failed to start');
    try {
      const ready=await fetch(base+'/api/health');
      if(ready.ok && (await ready.json()).data?.status==='UP') {
        const r=await fetch(base+'/api/products');
        if(r.ok && (await r.json()).data?.some(p=>p.name==='Enabot EBO Air'))return;
      }
    } catch {}
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error('Isolated backend startup timeout');
}
async function request(url, method='GET', body, token) {
  const response = await fetch(base+url, { method, headers: { ...(body ? {'Content-Type':'application/json'} : {}), ...(token ? {'X-Token':token}: {}) }, body: body ? JSON.stringify(body):undefined });
  return { status: response.status, body: await response.json() };
}
try {
  await start();
  if (process.argv.includes('--export-catalog')) {
    // HTTP can accept requests before CommandLineRunner has finished seeding.
    // Never replace the public snapshot with an empty or partially seeded catalog.
    let products=[];
    for(let attempt=0;attempt<90;attempt++) {
      products=(await request('/api/products')).body.data;
      if(Array.isArray(products) && products.length>=78 && products.some(p=>p.name==='Enabot EBO Air')) break;
      await new Promise(resolve=>setTimeout(resolve,500));
    }
    assert.ok(products.length>=78 && products.some(p=>p.name==='Enabot EBO Air'),'Catalog seeding did not complete; snapshot not overwritten');
    const categories=(await request('/api/categories')).body.data;
    const brands=(await request('/api/brands')).body.data;
    // Fresh isolated seed database only: no account, address, order or avatar data.
    await writeFile('../web/public/catalog-snapshot.json',JSON.stringify({schemaVersion:1,generatedAt:new Date().toISOString(),source:'isolated-initial-catalog',products,categories,brands},null,2));
    console.log('Exported public catalog from isolated seed database.');
  }
  assert.equal((await request('/api/auth/login','POST',{username:'user',password:'123456'})).body.code,400);
  assert.equal((await request('/api/auth/register','POST',{username:'smokebuyer',password:'SmokePassword123!',nickname:'隔离部署测试'})).body.code,200);
  const login = await request('/api/auth/login','POST',{username:'smokebuyer',password:'SmokePassword123!'});
  const token = login.body.data.token;
  const initialQuota=(await request('/api/advisor/quota','GET',undefined,token)).body.data.remaining;
  await request('/api/advisor/chat','POST',{question:'帮我推荐商品'},token);
  const usedQuota=(await request('/api/advisor/quota','GET',undefined,token)).body.data.remaining;
  assert.equal(usedQuota,initialQuota-1);
  const form = new FormData();
  form.set('file',new Blob([Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5WQAAAAASUVORK5CYII=','base64')],{type:'image/png'}),'avatar.png');
  const uploaded = await fetch(base+'/api/profile/avatar',{method:'POST',headers:{'X-Token':token},body:form}).then(r=>r.json());
  assert.equal(uploaded.code,200);
  const avatar = uploaded.data.avatar;
  const product=(await request('/api/products')).body.data[0];
  assert.equal((await request('/api/favorites/'+product.id,'POST',undefined,token)).body.code,200);
  assert.equal((await request('/api/favorites/notes/'+product.id,'PUT',{note:'重启后仍应保留的私人备注'},token)).body.code,200);
  const planBody={title:'隔离持久化计划',requirement:'预算3500元，养猫',budget:3500,scene:'地面清洁',productIds:[product.id],note:'不发送给模型'};
  const created=(await request('/api/shopping-plans','POST',planBody,token)).body;
  assert.equal(created.code,200);const planId=created.data.id;
  assert.equal((await request('/api/shopping-plans','POST',{...planBody,id:planId,title:'第二版持久化计划'},token)).body.code,200);
  const rejectedCors = await fetch(base+'/api/products',{headers:{Origin:'https://untrusted.invalid'}});
  assert.equal(rejectedCors.status,403);
  assert.equal((await fetch(base+'/h2-console')).status,404);
  await stop(); await start();
  assert.equal((await request('/api/advisor/quota','GET',undefined,token)).body.data.remaining,usedQuota);
  await request('/api/advisor/chat','POST',{question:'帮我推荐商品'},token);
  assert.equal((await request('/api/advisor/chat','POST',{question:'帮我推荐商品'},token)).status,429);
  assert.equal((await request('/api/auth/me','GET',undefined,token)).body.data.username,'smokebuyer');
  assert.equal((await fetch(base+avatar)).status,200);
  const persistedPlans=(await request('/api/shopping-plans','GET',undefined,token)).body.data;
  assert.equal(persistedPlans.find(p=>p.id===planId).title,'第二版持久化计划');
  assert.equal(Number(persistedPlans.find(p=>p.id===planId).budget),3500);
  const versions=(await request('/api/shopping-plans/'+planId+'/versions','GET',undefined,token)).body.data;
  assert.equal(versions.length,2);assert.equal(versions[1].snapshot.title,'隔离持久化计划');
  assert.equal((await request('/api/favorites/notes','GET',undefined,token)).body.data[String(product.id)],'重启后仍应保留的私人备注');
  assert.equal((await request('/api/auth/logout','POST',undefined,token)).body.code,200);
  assert.equal((await request('/api/auth/me','GET',undefined,token)).status,401);
  const again = await request('/api/auth/login','POST',{username:'smokebuyer',password:'SmokePassword123!'});
  const nextToken = again.body.data.token;
  assert.equal((await request('/api/profile/password','PUT',{oldPassword:'SmokePassword123!',newPassword:'ChangedSmoke123!'},nextToken)).body.code,200);
  assert.equal((await request('/api/auth/me','GET',undefined,nextToken)).status,401);
  assert.equal((await request('/api/auth/login','POST',{username:'smokebuyer',password:'ChangedSmoke123!'})).body.code,200);
  console.log(`PASS: ${profile} startup (isolated H2), readiness, no demo users, registration, DB/avatar/plans/versions/favorite notes survive restart, CORS, console disabled, logout invalidates token. No cloud MySQL, public tunnel or AI request.`);
} finally { await stop(); }
