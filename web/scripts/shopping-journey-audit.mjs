import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {launchAuditBrowser} from './audit-browser.mjs';
const catalog=JSON.parse(await readFile('public/catalog-snapshot.json','utf8'));
const appBase=(process.env.AUDIT_APP_URL||'http://127.0.0.1:5173').replace(/\/$/,'');
const appUrl=path=>`${appBase}${process.env.AUDIT_HASH==='1'?'/#':''}${path}`;
const {browser,shutdown}=await launchAuditBrowser({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const results=[];await mkdir('../tmp/shopping-journey',{recursive:true});
let lastPage;
try{for(const width of [1440,390])for(const theme of ['classic','midnight']){
 const context=await browser.newContext({viewport:{width,height:1000},reducedMotion:'reduce'});
 await context.addInitScript(({theme})=>{localStorage.setItem('user',JSON.stringify({id:9999,username:'test',nickname:'测试用户',role:'USER',theme}));localStorage.setItem('token','isolated-ui');},{theme});
 let plans=[],chatBody,notes={},failNote=false,versions={},nextId=1,failList=false;
 await context.route('**/api/**',async route=>{
  const p=new URL(route.request().url()).pathname.replace('/api','');let data=[];
  if(p==='/products')data=catalog.products;
  else if(/^\/products\/\d+$/.test(p))data=catalog.products.find(x=>x.id===Number(p.split('/').at(-1)));
  else if(p==='/categories')data=catalog.categories;
  else if(p==='/brands')data=catalog.brands;
  else if(p==='/favorites')data=catalog.products.slice(0,3);
  else if(p==='/favorites/notes')data=notes;
  else if(p.startsWith('/favorites/notes/')){
    if(failNote)return route.fulfill({status:503,json:{code:503,message:'模拟保存失败'}});
    const id=p.split('/').at(-1);notes[id]=route.request().postDataJSON().note;data={productId:Number(id),note:notes[id]};
  }
  else if(p==='/advisor/ai-status')data={configured:true};
  else if(p==='/advisor/chat'){chatBody=route.request().postDataJSON();data={answer:'测试回复，不调用模型',provider:'测试',products:[]};}
  else if(p==='/shopping-plans'){
   if(failList && route.request().method()==='GET')return route.fulfill({status:503,json:{code:503,message:'模拟列表读取失败'}});
   if(route.request().method()==='POST'){const b=route.request().postDataJSON();const saved={...b,id:b.id||nextId++,productIds:b.productIds.join(','),updatedAt:'2026-11-06T14:00:00'};plans=[saved,...plans.filter(p=>p.id!==saved.id)];versions[saved.id]=[{id:Date.now(),createdAt:saved.updatedAt,snapshot:{...saved}},...(versions[saved.id]||[])].slice(0,20);data=saved;}else data=plans;
  }else if(/^\/shopping-plans\/\d+\/versions$/.test(p)){data=versions[Number(p.split('/')[2])]||[];}
  else if(p.startsWith('/shopping-plans/')){const id=Number(p.split('/').at(-1));plans=plans.filter(p=>p.id!==id);delete versions[id];}
  else if(p==='/profile/summary')data={orderCount:0,cartCount:0,favoriteCount:3,historyCount:0,recentProducts:[]};
  await route.fulfill({json:{code:200,data}});
 });
 const page=await context.newPage();lastPage=page;const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
 await page.goto(appUrl('/product/1'));
 await page.getByRole('link',{name:'问 AI：这款适合我吗？'}).click();
 await page.locator('.context-strip').getByText('正在围绕 1 款候选咨询').waitFor();
 await page.locator('.advisor-chat-input textarea').fill('这款机器人适合养猫家庭吗？预算4000元');
 await page.locator('.advisor-chat-input button').click();
 await page.getByText('测试回复，不调用模型',{exact:true}).waitFor();assert.deepEqual(chatBody.contextProductIds,[1]);
 await page.getByRole('button',{name:'保存为选购计划'}).click();
 await page.getByLabel('计划名称').fill('养猫家庭清洁');
 await page.getByLabel('预算上限（元，可留空）').fill('3500');
 await page.locator('.plan-fields select').selectOption('地面清洁');
 await page.getByLabel('我的备注',{exact:true}).fill('先核对家具高度');
 await page.getByRole('button',{name:'保存计划',exact:true}).click();
 await page.locator('.plan-list h3').getByText('养猫家庭清洁').waitFor();
 await page.reload();await page.locator('.plan-list h3').getByText('养猫家庭清洁').waitFor();
 await page.waitForLoadState('networkidle');
 await page.screenshot({path:`../tmp/shopping-journey/debug-${width}-${theme}.png`,fullPage:true});
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
 await page.screenshot({path:`../tmp/shopping-journey/plans-${width}-${theme}.png`,fullPage:true});
 await page.getByRole('button',{name:'编辑',exact:true}).click();await page.getByLabel('计划名称').fill('更新的计划');failList=true;await page.getByRole('button',{name:'更新计划'}).click();await page.getByText(/计划已保存.*列表刷新失败/).waitFor();assert.equal(plans.length,1);failList=false;await page.getByRole('button',{name:'重新读取计划',exact:true}).click();await page.locator('.plan-list h3').getByText('更新的计划').waitFor();
 assert.equal(Number(plans[0].budget),3500);assert.equal(plans[0].scene,'地面清洁');
 await page.getByRole('button',{name:'版本记录',exact:true}).click();await page.locator('.plan-versions article').nth(1).waitFor();
 await page.locator('.plan-versions article').nth(1).getByRole('button',{name:'载入此版本编辑'}).click();assert.equal(await page.getByLabel('计划名称').inputValue(),'养猫家庭清洁');
 await page.getByRole('button',{name:'更新计划',exact:true}).click();await page.locator('.plan-list h3').getByText('养猫家庭清洁').waitFor();
 await page.getByLabel('计划名称').fill('另一个计划');await page.getByLabel('需求与预算',{exact:true}).fill('比较不同预算的安排');await page.getByLabel('预算上限（元，可留空）').fill('2000');await page.getByRole('button',{name:'保存计划',exact:true}).click();await page.locator('.plan-list h3').getByText('另一个计划').waitFor();
 await page.locator('.plan-compare-pick input').nth(0).check();await page.locator('.plan-compare-pick input').nth(1).check();assert.equal(await page.locator('.plan-comparison-grid article').count(),2);
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
 await page.screenshot({path:`../tmp/shopping-journey/plan-comparison-${width}-${theme}.png`,fullPage:true});
 await page.locator('.plan-list article').filter({has:page.getByRole('heading',{name:'另一个计划',exact:true})}).getByRole('button',{name:'删除',exact:true}).click();await page.locator('.plan-list article').filter({has:page.getByRole('heading',{name:'另一个计划',exact:true})}).waitFor({state:'detached'});
 await page.getByRole('button',{name:'继续助选',exact:true}).click();
 const resumed=page.locator('.advisor-chat-input textarea');await resumed.waitFor();assert.match(await resumed.inputValue(),/预算上限3500元/);assert.match(await resumed.inputValue(),/地面清洁/);assert.ok(!(await resumed.inputValue()).includes('先核对家具高度'));
 await page.goto(appUrl('/profile#plans'));await page.locator('.plan-list h3').getByText('养猫家庭清洁').waitFor();
 await page.getByRole('button',{name:'删除',exact:true}).click();await page.getByText('还没有保存的计划，可以从 AI 助选带入，或在这里新建。').waitFor();
 await page.goto(appUrl('/favorites'));
 const note=page.locator('.favorite-note textarea').first();await note.fill('喜欢它，但要核对高度');
 failNote=true;await page.getByRole('button',{name:'保存备注',exact:true}).first().click();await page.getByText(/备注操作失败/).waitFor();assert.equal(await note.inputValue(),'喜欢它，但要核对高度');
 failNote=false;await page.getByRole('button',{name:'保存备注',exact:true}).first().click();await page.getByText(/的备注已保存/).waitFor();
 await page.reload();await page.waitForLoadState('networkidle');assert.equal(await page.locator('.favorite-note textarea').first().inputValue(),'喜欢它，但要核对高度');
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
 await page.screenshot({path:`../tmp/shopping-journey/favorite-notes-${width}-${theme}.png`,fullPage:true});
 assert.deepEqual(errors,[]);results.push({width,theme,pass:true});await context.close();
}await writeFile('../tmp/shopping-journey/report.json',JSON.stringify({note:'模拟接口，仅验证浏览器交互，不调用模型、不写真实账号。',results},null,2));console.log(results);}catch(e){if(lastPage&&!lastPage.isClosed()){console.error(lastPage.url(),(await lastPage.locator('body').innerText()).slice(0,1600));await lastPage.screenshot({path:'../tmp/shopping-journey/failure.png',fullPage:true});}throw e;}finally{await shutdown();}
