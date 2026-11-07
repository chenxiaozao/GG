import assert from 'node:assert/strict';
import { backendUrl, verifyPublicBackend } from './validate-public-backend.mjs';
assert.equal(backendUrl(''), null);
const url = backendUrl('https://api.example.com/api');
assert.equal(url.origin, 'https://api.example.com');
for (const invalid of ['http://api.example.com/api','https://api.example.com','https://api.example.com/api/','https://secret@api.example.com/api','https://api.example.com/api?key=secret','https://api.example.com/api#x','https://localhost/api']) assert.throws(() => backendUrl(invalid));
const origin = 'https://chenxiaozao.github.io';
const healthy = () => new Response(JSON.stringify({code:200,data:{status:'UP'}}));
const headers = { 'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type,X-Token' };
let count = 0;
await verifyPublicBackend(url, origin, async (_url, options) => {
  count++;
  if (options.method === 'OPTIONS') {
    assert.equal(options.headers.Origin, origin);
    return new Response(null, {headers});
  }
  return healthy();
});
assert.equal(count, 2);
await assert.rejects(verifyPublicBackend(url, origin, async () => new Response(null,{status:503})));
await assert.rejects(verifyPublicBackend(url, origin, async () => new Response(JSON.stringify({code:200,data:[]}))));
await assert.rejects(verifyPublicBackend(url, origin, async (_url, options) => options.method === 'OPTIONS' ? new Response(null,{headers:{...headers,'Access-Control-Allow-Origin':'*'}}) : healthy()));
console.log('PASS public backend URL, readiness and exact Pages CORS checks (mock requests only)');
