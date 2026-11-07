import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {launchAuditBrowser} from './audit-browser.mjs';
const catalog=JSON.parse(await readFile('public/catalog-snapshot.json','utf8'));
const names=['Sphero Mini','Sphero indi','Makeblock mBot Ranger','Makeblock Codey Rocky','Enabot EBO SE','Enabot EBO Air'];
const products=names.map(name=>{const p=catalog.products.find(p=>p.name===name);assert.equal(p?.imageKind,'OFFICIAL_VISUAL');assert.ok(!p.image.endsWith('.svg'));return p;});
await mkdir('../tmp/new-product-images',{recursive:true});
const {browser,shutdown}=await launchAuditBrowser({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const results=[];
try{for(const width of [1440,390])for(const theme of ['classic','midnight']){
 const context=await browser.newContext({viewport:{width,height:900},reducedMotion:'reduce'});
 await context.addInitScript(theme=>localStorage.setItem('robotmall-theme',JSON.stringify({theme})),theme);
 await context.route('**/api/**',async route=>{
  const p=new URL(route.request().url()).pathname.replace('/api','');
  const data=/^\/products\/\d+$/.test(p)?catalog.products.find(x=>x.id===Number(p.split('/').at(-1))):p==='/products'?catalog.products:p==='/categories'?catalog.categories:p==='/brands'?catalog.brands:[];
  await route.fulfill({json:{code:200,data}});
 });
 const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 for(const product of products){await page.goto('http://127.0.0.1:5173/product/'+product.id);await page.waitForLoadState('networkidle');
  assert.ok(await page.evaluate(image=>[...document.images].some(img=>img.getAttribute('src')?.includes(image)&&img.complete&&img.naturalWidth>0),product.image),product.name+' image failed');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));assert.deepEqual(errors,[]);
  results.push({name:product.name,width,theme,pass:true});
 }
 await page.screenshot({path:`../tmp/new-product-images/detail-${width}-${theme}.png`});
 await page.goto('http://127.0.0.1:5173/products');await page.waitForLoadState('networkidle');
 await page.screenshot({path:`../tmp/new-product-images/catalog-${width}-${theme}.png`});
 await context.close();
}await writeFile('../tmp/new-product-images/report.json',JSON.stringify({note:'模拟API，加载项目内真实素材文件；非真机测试。',results},null,2));console.log('PASS '+results.length+' image/detail states');}finally{await shutdown();}
