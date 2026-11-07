import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { launchAuditBrowser } from './audit-browser.mjs';
const out = '../tmp/defense-build/screens';
await mkdir(out, { recursive: true });
const base = 'http://127.0.0.1:5173';
const apiBase = 'http://127.0.0.1:8080/api';
const get = async p => (await (await fetch(apiBase + p)).json()).data;
const login = async (username, password) => (await (await fetch(apiBase + '/auth/login', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})})).json()).data;
const catalog = await get('/products'), categories = await get('/categories');
const user = await login('user','123456'), admin = await login('admin','admin123');
for (const entry of [user, admin]) if (entry.user) { Object.assign(entry, entry.user); delete entry.user; }
const robot = catalog.find(p => p.name.includes('Curv')) || catalog[0];
const robot2 = catalog.find(p => p.categoryId === robot.categoryId && p.id !== robot.id);
const orders = ['PENDING','PAID','SHIPPED','COMPLETED','CANCELLED'].map((status,i)=>({order:{id:9001+i,orderNo:`DEMO20260918${i+1}`,userId:2,status,totalAmount:robot.price,createdAt:'2026-09-18T09:30:00',receiverInfo:'演示收货人，13800000000，广东省广州市天河区演示路1号',logisticsCompany:'演示物流',logisticsNo:'DEMO1001'},items:[{id:9101+i,productId:robot.id,productName:robot.name,price:robot.price,quantity:1,selectedSku:'标准版'}]}));
const address = {id:9201,receiver:'演示收货人',phone:'13800000000',province:'广东省',city:'广州市',district:'天河区',detail:'演示路1号',defaultAddress:true};
const services = [{id:9301,serviceNo:'DEMO-AS-001',orderId:9003,orderItemId:9103,productName:robot.name,type:'RETURN',reason:'商品故障',description:'设备无法正常启动，已按提示排查电源。',status:'WAITING_RETURN',requestedAmount:robot.price,createdAt:'2026-09-18T10:00:00',adminReply:'请填写寄回物流信息，收到商品后检测。'},{id:9302,serviceNo:'DEMO-AS-002',orderId:9004,orderItemId:9104,productName:robot.name,type:'REPAIR',reason:'功能异常',description:'充电过程出现中断，申请维修检查。',status:'SUBMITTED',requestedAmount:0,createdAt:'2026-09-18T10:10:00',adminReply:''}];
const {browser,shutdown} = await launchAuditBrowser({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const report = {generatedAt:new Date().toISOString(),catalogCount:catalog.length,categories:categories.map(x=>x.name),screens:[],notes:'订单、地址和售后使用虚构浏览器演示记录。不下单、不保存个人资料、不上传图片。其他商品/算法页面读取现有实现。'};
const only = process.env.DEFENSE_ONLY?.split(',');
if (only) report.screens = JSON.parse(await readFile('../tmp/defense-build/capture-report.json','utf8')).screens.filter(s=>!only.includes(s.name));
async function contextFor(account, viewport={width:1440,height:1000}) {
  const context=await browser.newContext({viewport,reducedMotion:'reduce'});
  if(account) await context.addInitScript(account=>{localStorage.setItem('token',account.token);localStorage.setItem('user',JSON.stringify({...account,phone:'',email:'',theme:'classic',interfaceLayout:'standard',accentColor:'#d9363e',backgroundColor:'#f5f6f8'}));},account);
  await context.route('**/api/**', async route=>{
    const req=route.request(),p=new URL(req.url()).pathname.replace('/api','');
    const fixture=p==='/orders'?orders:p==='/admin/orders'?orders.map(x=>x.order):p==='/addresses'?[address]:p==='/after-sales'||p==='/admin/after-sales'?services:p==='/reviews/mine'?[]:null;
    if(req.method()==='GET'&&fixture) return route.fulfill({json:{code:200,message:'success',data:fixture}});
    if(!['GET','HEAD'].includes(req.method())&&!['/advisor/recommend','/advisor/chat','/trade-in/estimate','/compatibility/analyze','/products/compare','/auth/login'].includes(p)) return route.fulfill({json:{code:200,message:'演示截图不执行保存',data:null}});
    return route.continue();
  });
  return context;
}
const context=await contextFor(user), page=await context.newPage();
page.setDefaultTimeout(10000);
async function go(path) {await page.goto(base+path,{waitUntil:'networkidle'});await page.locator('.workspace-content > *').first().waitFor();}
async function shot(name, selector=null, maxHeight=null) {
  await page.addStyleTag({content:'.site-header,.back-to-top{visibility:hidden!important}.compat-action-panel{position:static!important}'});
  if(selector && maxHeight) {const b=await page.locator(selector).first().boundingBox();await page.screenshot({path:`${out}/${name}.png`,fullPage:true,clip:{x:b.x,y:b.y+await page.evaluate(()=>scrollY),width:b.width,height:Math.min(b.height,maxHeight)}});}
  else if(selector) await page.locator(selector).first().screenshot({path:`${out}/${name}.png`});
  else {const b=await page.locator('.workspace-content').boundingBox();await page.screenshot({path:`${out}/${name}.png`,fullPage:true,clip:{x:b.x,y:await page.evaluate(()=>scrollY)+80,width:b.width,height:760}});}
  report.screens.push({name,path:page.url(),passed:true});console.log(name);
}
async function section(name,fn){if(only&&!only.includes(name))return;try{await fn();}catch(error){report.screens.push({name,passed:false,error:error.message});console.log('FAILED',name,error.message);}await writeFile('../tmp/defense-build/capture-report.json',JSON.stringify(report,null,2));}
try {
  await section('home',async()=>{await go('/');await shot('home');});
  await section('catalog',async()=>{await go('/products');await shot('catalog');});
  await section('detail',async()=>{await go(`/product/${robot.id}`);await shot('detail','.product-detail');});
  await section('compare',async()=>{await go('/compare');await page.locator('.compare-products input[type="checkbox"]').nth(0).check();await page.locator('.compare-products input[type="checkbox"]').nth(1).check();await page.locator('.compare-picker-head .btn').click();await page.locator('.compare-result').waitFor();await shot('compare','.compare-result',640);});
  await section('compatibility',async()=>{await go('/compatibility');await page.locator('.compat-product-grid button').first().click();await page.locator('.compat-section').nth(1).locator('button').first().waitFor();await page.locator('.compat-section').nth(1).locator('button').first().click();await page.getByRole('button',{name:'检测适配关系',exact:true}).click();await page.locator('.compat-analysis').waitFor();await shot('compatibility','.compat-analysis');await page.locator('.compat-section').nth(1).evaluate(el=>el.setAttribute('data-defense-candidates',''));await shot('compatibility-form','[data-defense-candidates]');});
  await section('rule',async()=>{await go('/advisor');await page.getByRole('tab',{name:/按条件推荐/}).click();await page.locator('.advisor-form button[type="submit"]').click();await page.locator('.advisor-results').waitFor();await shot('rule-result','.advisor-results');await page.locator('.advisor-form').screenshot({path:`${out}/rule-form.png`});});
  await section('ai',async()=>{await go('/advisor');await page.locator('.advisor-chat-input textarea').fill('演示家庭：90平方米，有猫，预算5000元，请从商城里推荐最多两款扫拖机器人并简要说明理由。');await page.locator('.advisor-chat-input button').click();await page.locator('.ai-product-card').first().waitFor({timeout:50000});await shot('ai','.advisor-chat');});
  await section('trade',async()=>{await go('/trade-in');await page.locator('.trade-brand-grid button').first().click();await page.locator('.trade-category-pills button').first().click();await page.locator('.trade-model-grid button').first().click();await page.locator('.trade-submit').click();await page.locator('.trade-result-panel h2').waitFor();await shot('trade','.trade-result-panel');await page.locator('.trade-cascade-block').nth(2).evaluate(el=>el.setAttribute('data-defense-models',''));await shot('trade-form','[data-defense-models]');});
  await section('cart',async()=>{await go('/cart');await shot('cart');});
  await section('orders',async()=>{await go('/orders');await shot('orders');await page.getByRole('button',{name:/^已完成/}).click();await shot('review');});
  await section('after-sales',async()=>{await go('/after-sales');await shot('after-sales');await shot('after-sales-progress','.service-list');});
  await section('favorites',async()=>{await go('/favorites');await shot('favorites');});
  await section('history',async()=>{await go('/history');await shot('history');});
  for(const [id,name] of [['','overview'],['account','profile'],['addresses','addresses'],['security','security'],['appearance','appearance']]) await section(name,async()=>{await go('/profile'+(id?'#'+id:''));await page.locator('#'+(id||'overview')).waitFor({state:'visible'});await shot(name,id?'#'+id:null,id==='addresses'?780:null);});
  await section('avatar-crop',async()=>{await go('/profile#account');await page.locator('input[type="file"]').setInputFiles('../server/data/avatars/2-1789529033854.jpg');await page.locator('.avatar-crop-dialog').waitFor();await shot('avatar-crop','.avatar-crop-dialog');await page.getByRole('button',{name:'取消',exact:true}).click();});
  const guest=await contextFor(null), gp=await guest.newPage();
  await gp.goto(base+'/login',{waitUntil:'networkidle'});await gp.locator('.form').screenshot({path:`${out}/login.png`});await gp.getByRole('button',{name:'切换注册',exact:true}).click();await gp.locator('.form').screenshot({path:`${out}/register.png`});report.screens.push({name:'login-register',passed:true});await guest.close();
  await context.close();
  const ac=await contextFor(admin), ap=await ac.newPage();await ap.goto(base+'/admin',{waitUntil:'networkidle'});
  for(const [tab,name] of [['工作台','admin-dashboard'],['商品管理','admin-products'],['用户管理','admin-users'],['订单管理','admin-orders'],['售后管理','admin-services']]) {
    await section(name,async()=>{await ap.locator('.page-tabs').getByRole('button',{name:tab,exact:false}).click();await ap.locator('.admin-page').waitFor();await ap.addStyleTag({content:'.site-header,.back-to-top{visibility:hidden!important}'});if(name==='admin-orders'){await ap.locator('.admin-order-list select').selectOption('PAID');await ap.getByRole('button',{name:'填写物流并发货',exact:true}).click();await ap.locator('.shipping-form').waitFor();await ap.locator('.admin-order-list').screenshot({path:`${out}/${name}.png`});}else if(name==='admin-services'){await ap.locator('.admin-service-card').first().screenshot({path:`${out}/${name}.png`});}else{const b=await ap.locator('.workspace-content').boundingBox();await ap.screenshot({path:`${out}/${name}.png`,clip:{x:b.x,y:await ap.evaluate(()=>scrollY)+80,width:b.width,height:760}});}report.screens.push({name,passed:true});console.log(name);});
  }
  await ac.close();
} finally {await writeFile('../tmp/defense-build/capture-report.json',JSON.stringify(report,null,2));await shutdown();}
process.exit(report.screens.some(s=>!s.passed)?1:0);
