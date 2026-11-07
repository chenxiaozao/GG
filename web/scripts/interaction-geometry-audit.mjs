import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {launchAuditBrowser} from './audit-browser.mjs';
const snapshot=JSON.parse(await readFile('public/catalog-snapshot.json','utf8'));
const products=snapshot.products;const cleaning=products.filter(p=>p.categoryName==='地面清洁');
const {browser,shutdown}=await launchAuditBrowser({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
await mkdir('../tmp/interaction-geometry',{recursive:true});const results=[];
async function noOverflow(page){assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'page overflow');}
async function quantity(page){const q=page.locator('.quantity-stepper').first();await q.scrollIntoViewIfNeeded();
 const boxes=await q.evaluate(el=>[el,...el.children].map(x=>{const r=x.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,width:r.width,height:r.height};}));
 assert.equal(boxes.length,4);assert.ok(boxes[1].right<=boxes[2].left+1);assert.ok(boxes[2].right<=boxes[3].left+1);assert.ok(boxes[3].right<=boxes[0].right+1,'plus clipped');assert.ok(boxes[1].height>=44&&boxes[3].height>=44);
 const input=q.locator('input');const before=Number(await input.inputValue());await q.locator('button').last().click();await page.waitForTimeout(100);assert.equal(Number(await input.inputValue()),before+1);await q.locator('button').first().click();assert.equal(Number(await input.inputValue()),before);
}
try{for(const width of [1440,1024,820,390])for(const theme of ['classic','midnight']){
 const context=await browser.newContext({viewport:{width,height:1000},reducedMotion:'reduce'});
 let cartQuantity=2;
 await context.addInitScript(theme=>{localStorage.setItem('user',JSON.stringify({id:9999,username:'test',nickname:'测试',role:'USER',theme}));localStorage.setItem('token','isolated');},theme);
 await context.route('**/api/**',async route=>{const url=new URL(route.request().url());const p=url.pathname.replace('/api','');let data=[];
 if(p==='/products'){data=products;const cat=url.searchParams.get('categoryId');if(cat)data=data.filter(p=>String(p.categoryId)===cat);}
 else if(p==='/products/compare')data=products.filter(p=>route.request().postDataJSON().includes(p.id));
 else if(/^\/products\/\d+$/.test(p))data=products.find(x=>x.id===Number(p.split('/').at(-1)));
 else if(p==='/categories')data=snapshot.categories;else if(p==='/brands')data=snapshot.brands;
 else if(p==='/cart')data=[{id:1,quantity:cartQuantity,selectedSku:'标准版',product:cleaning[0]}];
 else if(p.startsWith('/cart/')){cartQuantity=route.request().postDataJSON()?.quantity||cartQuantity;data={};}
 await route.fulfill({json:{code:200,data}});
 });
 const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:5173/products?categoryId='+cleaning[0].categoryId);await page.waitForLoadState('networkidle');
 await page.locator('.product-compare-entry').nth(0).click();assert.ok(page.url().includes('/products?'));assert.equal(await page.locator('.comparison-tray .btn').isDisabled(),true);
 await page.locator('.product-compare-entry').nth(1).click();assert.ok(page.url().includes('/products?'));assert.equal(await page.locator('.comparison-tray li').count(),2);
 await page.screenshot({path:`../tmp/interaction-geometry/tray-${width}-${theme}.png`});
 await noOverflow(page);await page.locator('.comparison-tray .btn').click();await page.locator('.compare-result').waitFor();assert.equal(await page.locator('.compare-selection-chips button').count(),2);
 await page.goto('http://127.0.0.1:5173/product/'+cleaning[0].id);await page.waitForLoadState('networkidle');await quantity(page);await noOverflow(page);await page.screenshot({path:`../tmp/interaction-geometry/detail-${width}-${theme}.png`});
 await page.goto('http://127.0.0.1:5173/cart');await page.waitForLoadState('networkidle');await quantity(page);await noOverflow(page);
 await page.goto('http://127.0.0.1:5173/advisor');await page.getByRole('tab',{name:/按条件推荐/}).click();await page.waitForLoadState('networkidle');
 const currency=await page.locator('.advisor-budget').evaluate(el=>{const a=el.querySelector('i').getBoundingClientRect(),b=el.querySelector('input').getBoundingClientRect();return a.right<=b.left+1;});assert.ok(currency,'currency overlaps input');
 assert.equal(await page.locator('.advisor-scene-grid label.selected').count(),1);await noOverflow(page);await page.locator('.advisor-fields').scrollIntoViewIfNeeded();await page.screenshot({path:`../tmp/interaction-geometry/advisor-${width}-${theme}.png`});
 await page.goto('http://127.0.0.1:5173/trade-in');await page.waitForLoadState('networkidle');await page.locator('.trade-submit').scrollIntoViewIfNeeded();await noOverflow(page);await page.screenshot({path:`../tmp/interaction-geometry/trade-${width}-${theme}.png`});
 const radios=page.locator('.trade-choice-grid');assert.ok(await radios.count()>0);assert.deepEqual(errors,[]);results.push({width,theme,pass:true});await context.close();
}await writeFile('../tmp/interaction-geometry/report.json',JSON.stringify({note:'模拟接口，不调用真实AI或写真实用户数据。测几何位置与点击行为，不等于审美验收。',results},null,2));console.log('PASS '+results.length+' viewport/theme interaction flows');}finally{await shutdown();}
