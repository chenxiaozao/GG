import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {launchAuditBrowser} from './audit-browser.mjs';
const base=process.env.AUDIT_APP_URL||'http://127.0.0.1:5173';
const url=path=>process.env.AUDIT_HASH==='1'?`${base}/#${path}`:`${base}${path}`;
const out='../tmp/preferences-ui-audit';await mkdir(out,{recursive:true});
const {browser,shutdown}=await launchAuditBrowser({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const results=[];
try{
 for(const width of [1440,820,390])for(const theme of ['classic','midnight']){
  const context=await browser.newContext({viewport:{width,height:900},reducedMotion:'reduce'});
  await context.addInitScript(({theme})=>{localStorage.setItem('user',JSON.stringify({id:999,username:'ui-admin',role:'ADMIN',theme,nickname:'界面测试'}));localStorage.setItem('token','ui-only');},{theme});
  let profile={collectionEnabled:false,adminSharing:false,confirmed:false,preferences:{}};
  let failConsent=false,phone='未填写',writes=[];
  await context.route('**/api/**',async route=>{
   const req=route.request(),p=new URL(req.url()).pathname.replace('/api',''),method=req.method();let data=[];
   if(p==='/profile/preferences'&&method==='GET')data=profile;
   else if(p==='/profile/preferences/consent'){
    if(failConsent){failConsent=false;return route.fulfill({status:503,json:{code:503,message:'模拟授权保存失败'}});}
    const b=req.postDataJSON();profile={...profile,collectionEnabled:b.enabled,adminSharing:b.enabled&&b.adminSharing,preferences:b.enabled?profile.preferences:{}};data=profile;
   }else if(p==='/profile/preferences'&&method==='PUT'){profile={...profile,confirmed:true,preferences:req.postDataJSON()};data=profile;}
   else if(p==='/profile/preferences'&&method==='DELETE'){profile={collectionEnabled:false,adminSharing:false,confirmed:false,preferences:{}};data=null;}
   else if(p.endsWith('/preferences')&&p.startsWith('/admin/'))data={...profile,visible:profile.collectionEnabled&&profile.adminSharing,preferences:profile.adminSharing?profile.preferences:{}};
   else if(p.endsWith('/audit'))data=[];
   else if(p.endsWith('/reset-password')){writes.push({kind:'reset',reason:req.postDataJSON().reason});data={message:'临时密码已设置，旧登录已撤销；用户需首次登录改密'};}
   else if(p.endsWith('/phone')){phone='138****8000';writes.push({kind:'phone'});data={message:'联系方式已更新，未短信验证，不是登录绑定'};}
   else if(p==='/admin/users')data=[{id:2,username:'test-buyer',nickname:'测试普通用户',role:'USER',enabled:true,maskedPhone:phone,phoneVerified:false}];
   else if(p==='/admin/dashboard')data={dailySales:[],categorySales:{},statusDistribution:{},lowStockProducts:[],totalUsers:1,totalProducts:0,totalOrders:0,totalSales:0};
   else if(p==='/profile/summary')data={orderCount:0,cartCount:0,favoriteCount:0,historyCount:0,pendingPaymentCount:0,pendingReceiptCount:0,recentProducts:[]};
   return route.fulfill({json:{code:200,data}});
  });
  const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  await page.goto(url('/profile#preferences'));
  const collect=page.getByRole('checkbox',{name:'允许从之后的选购咨询整理偏好'});
  await collect.waitFor();assert.equal(await collect.isChecked(),false);
  failConsent=true;await collect.click();await page.getByText('模拟授权保存失败').waitFor();assert.equal(await collect.isChecked(),false);
  await collect.click();await page.getByLabel('关注品类').selectOption('地面清洁');await page.getByLabel('预算参考（元）').fill('3500');
  await page.getByRole('button',{name:'确认并保存我的偏好'}).click();await page.getByText('偏好已由你确认，后续咨询不会悄悄覆盖').waitFor();
  await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:`${out}/preferences-${width}-${theme}.png`,fullPage:true});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
  await page.goto(url('/admin'));await page.getByRole('button',{name:'用户管理',exact:true}).click();
  await page.getByRole('button',{name:'维护账号与偏好'}).click();await page.locator('.account-maintenance-dialog').waitFor();
  await page.getByRole('button',{name:'按用户授权查看'}).click();await page.getByText('用户未授权查看，档案内容不会返回。').waitFor();
  profile.adminSharing=true;await page.getByRole('button',{name:'按用户授权查看'}).click();await page.locator('.preference-values').waitFor();
  await page.getByLabel('你的管理员密码').fill('SyntheticAdminPassword');await page.getByLabel('新临时密码').fill('SyntheticTemp123!');
  await page.getByRole('button',{name:'重置并撤销旧登录'}).click();await page.getByText('临时密码已设置，旧登录已撤销；用户需首次登录改密').waitFor();
  await page.getByLabel('你的管理员密码').fill('SyntheticAdminPassword');await page.getByLabel('新手机号（留空会清除）').fill('13800138000');
  await page.getByRole('button',{name:'保存未验证联系方式'}).click();await page.getByText('联系方式已更新，未短信验证，不是登录绑定').waitFor();
  const dimensions=await page.locator('.account-maintenance-dialog').evaluate(el=>({width:el.scrollWidth,client:el.clientWidth}));assert.ok(dimensions.width<=dimensions.client+1);
  await page.locator('.account-maintenance-dialog').evaluate(el=>el.scrollTop=0);await page.screenshot({path:`${out}/admin-${width}-${theme}.png`});
  await page.getByRole('button',{name:'关闭账户维护'}).click();await page.goto(url('/profile#preferences'));
  await page.getByRole('button',{name:'删除档案与授权'}).click();await page.getByText('档案与授权均已删除').waitFor();assert.equal(await collect.isChecked(),false);
  assert.equal(writes.length,2);assert.deepEqual(errors,[]);results.push({width,theme,pass:true});await context.close();
 }
 const resetContext=await browser.newContext({viewport:{width:390,height:900}});
 await resetContext.addInitScript(()=>{localStorage.setItem('user',JSON.stringify({id:2,username:'temporary-buyer',role:'USER',mustChangePassword:true}));localStorage.setItem('token','temporary-only');});
 await resetContext.route('**/api/**',r=>r.request().url().endsWith('/profile/password')?r.fulfill({json:{code:200,data:null}}):r.fulfill({status:403,json:{code:403,message:'请先修改管理员设置的临时密码'}}));
 const resetPage=await resetContext.newPage();await resetPage.goto(url('/orders'));await resetPage.locator('.password-form').waitFor();
 assert.ok(resetPage.url().endsWith('/profile#security'));
 await resetPage.getByLabel('原密码',{exact:true}).fill('SyntheticTemp123!');await resetPage.getByLabel(/^新密码/).fill('SyntheticPrivate123!');await resetPage.getByLabel('确认新密码',{exact:true}).fill('SyntheticPrivate123!');
 await resetPage.getByRole('button',{name:'修改密码',exact:true}).click();await resetPage.getByRole('heading',{name:'账号登录',exact:true}).waitFor();
 assert.equal(await resetPage.evaluate(()=>localStorage.getItem('token')),null);await resetContext.close();
 await writeFile(`${out}/report.json`,JSON.stringify({scope:'Isolated mocked consent/admin UI; not real user mutations or real SMS',hashMode:process.env.AUDIT_HASH==='1',forcedPasswordChange:true,results},null,2));console.log(JSON.stringify(results));
}finally{await shutdown();}
