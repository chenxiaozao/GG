import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {launchAuditBrowser} from './audit-browser.mjs';
const snapshot=JSON.parse(await readFile('public/catalog-snapshot.json','utf8'));
const out='../tmp/site-completion-audit';await mkdir(out,{recursive:true});
const {browser,shutdown}=await launchAuditBrowser({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const results=[];
const routes=['/','/products','/product/1','/compare','/compatibility','/advisor','/trade-in','/cart','/orders','/after-sales','/favorites','/history','/profile','/admin','/login'];
function response(path,empty=false) {
 const product=snapshot.products[0];
 const map={
  '/products':empty?[]:snapshot.products,'/categories':snapshot.categories,'/brands':snapshot.brands,
  '/cart':empty?[]:[{id:1,quantity:2,selectedSku:'标准版',product}],'/addresses':[],
  '/orders':[], '/reviews/mine':[], '/after-sales':[], '/favorites':empty?[]:[product],
  '/history':empty?[]:[{product,viewTime:'2026-11-05T12:00:00'}],
  '/profile/summary':{orderCount:0,cartCount:2,favoriteCount:1,historyCount:1,pendingPaymentCount:0,pendingReceiptCount:0,recentProducts:[]},
  '/advisor/ai-status':{configured:true},'/advisor/quota':{remaining:20,limit:20},
  '/admin/dashboard':{totalUsers:1,totalProducts:snapshot.products.length,totalOrders:0,totalSales:0,afterSalePending:0,dailySales:[],categorySales:{},statusDistribution:{},lowStockProducts:[]},
  '/admin/products':empty?[]:snapshot.products,'/admin/users':[], '/admin/orders':[], '/admin/after-sales':[]
 };
 if(path in map)return map[path];
 if(/^\/products\/\d+$/.test(path))return snapshot.products.find(p=>p.id===Number(path.split('/').at(-1)));
 return [];
}
try {
 for(const width of [1440,820,390])for(const theme of ['classic','midnight']) {
  const context=await browser.newContext({viewport:{width,height:1000},reducedMotion:'reduce'});
  await context.addInitScript(({theme})=>{localStorage.setItem('user',JSON.stringify({id:9999,username:'ui-only',nickname:'界面测试',role:'ADMIN',theme}));localStorage.setItem('token','isolated-ui');},{theme});
  let mode='data';
  await context.route('**/api/**',async route=>{
   const p=new URL(route.request().url()).pathname.replace('/api','');
   if(mode==='error')return route.fulfill({status:503,json:{code:503,message:'测试服务暂不可用，请重试'}});
   return route.fulfill({json:{code:200,data:response(p,mode==='empty')}});
  });
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  for(const path of routes) {
   if(path==='/login')await page.evaluate(()=>{localStorage.removeItem('user');localStorage.removeItem('token');});
   await page.goto('http://127.0.0.1:5173'+path);await page.waitForLoadState('networkidle');
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${path}/${width}/${theme} overflow`);
   assert.deepEqual(errors,[],`${path}/${width}/${theme}`);
   if(['/advisor','/compatibility','/trade-in','/admin','/orders','/after-sales'].includes(path))await page.screenshot({path:`${out}/${path.slice(1)}-${width}-${theme}.png`});
   if(path==='/profile')for(const hash of ['account','addresses','security','appearance']) {
    await page.goto('http://127.0.0.1:5173/profile#'+hash);await page.waitForLoadState('networkidle');
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${hash}/${width}/${theme}`);
   }
   if(path==='/admin')for(const name of ['商品管理','用户管理','订单管理','售后管理']) {
    await page.getByRole('button',{name,exact:true}).click();
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${name}/${width}/${theme}`);
   }
   results.push({path,width,theme,state:'data',pass:true});
  }
  // Restore isolated session to exercise protected empty/error pages.
  await page.evaluate(()=>{localStorage.setItem('user',JSON.stringify({id:9999,username:'ui-only',role:'ADMIN'}));localStorage.setItem('token','isolated-ui');});
  for(const state of ['empty','error'])for(const path of ['/products','/cart','/orders','/after-sales','/favorites','/history','/admin']) {
   mode=state;await page.goto('http://127.0.0.1:5173'+path);await page.waitForLoadState('networkidle');
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${state}/${path}/${width}`);
   assert.deepEqual(errors,[]);results.push({path,width,theme,state,pass:true});
  }
  await context.close();console.log(`Checked ${width}/${theme}`);
 }
 await writeFile(`${out}/report.json`,JSON.stringify({scope:'Mocked APIs; layout, panel navigation, empty/error states; no real writes or AI calls',results},null,2));
 console.log(`PASS ${results.length} page-state checks, plus profile/admin panels`);
} finally {await shutdown();}
