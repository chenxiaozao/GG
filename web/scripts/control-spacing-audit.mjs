import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { launchAuditBrowser } from './audit-browser.mjs';
const {browser,shutdown}=await launchAuditBrowser({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const product={id:1,name:'控件测试机器人',enabled:true,stock:10,price:2000,marketPrice:2500,type:'ROBOT',image:'/products/placeholder.svg',skuOptions:'标准版,配件版',parametersJson:'{"续航":"120分钟"}',categoryName:'地面清洁'};
await mkdir('../tmp/control-spacing-audit',{recursive:true});
let checked=0;
try {
 for(const width of [1440,820,390]) for(const theme of ['classic','midnight']) {
  const context=await browser.newContext({viewport:{width,height:1000},reducedMotion:'reduce'});
  await context.addInitScript(({theme})=>{localStorage.setItem('user',JSON.stringify({id:9999,username:'isolated-ui',role:'USER',theme}));localStorage.setItem('token','isolated-ui');},{theme});
  await context.route('**/api/**',route=>{
   const p=new URL(route.request().url()).pathname;
   const data=p==='/api/products/1'?product:p==='/api/cart'?[{id:1,quantity:1,product}]:p.endsWith('ai-status')?{configured:false}:[];
   return route.fulfill({json:{code:200,data}});
  });
  const page=await context.newPage(); const errors=[];page.on('pageerror',e=>errors.push(e.message));
  for(const route of ['/product/1','/cart','/advisor']) {
   await page.goto('http://127.0.0.1:5173'+route);await page.waitForTimeout(350);
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${width} ${theme} ${route} overflow`);
   if(route!=='/advisor') {
    const buttons=page.locator('.quantity-stepper button');await buttons.first().waitFor();
    for(const button of await buttons.all()) {const box=await button.boundingBox();assert.ok(box.width>=43&&box.height>=43,JSON.stringify(box));}
   }
   await page.screenshot({path:`../tmp/control-spacing-audit/${route.replaceAll('/','-')}-${width}-${theme}.png`});checked++;
  }
  assert.deepEqual(errors,[]);await context.close();
 }
 console.log(`PASS: ${checked} page/theme/width combinations; quantity hit area, overflow, runtime errors. Mocked API only.`);
} finally {await shutdown();}
