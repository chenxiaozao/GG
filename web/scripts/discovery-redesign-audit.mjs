import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { launchAuditBrowser } from './audit-browser.mjs';
const catalog = JSON.parse(await readFile('public/catalog-snapshot.json', 'utf8'));
const output = '../tmp/discovery-redesign';
await mkdir(output, {recursive:true});
const {browser, shutdown} = await launchAuditBrowser({headless:true, executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const results = [];
try {
  for (const width of [1440, 820, 390]) for (const theme of ['classic','midnight']) {
    const context = await browser.newContext({viewport:{width,height:1000},reducedMotion:'reduce'});
    await context.addInitScript(({theme}) => {
      localStorage.setItem('user', JSON.stringify({id:9999,username:'ui-only',nickname:'小造同学',role:'USER',theme}));
      localStorage.setItem('token','isolated-ui');
    }, {theme});
    await context.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname.replace('/api','');
      const data = ({'/products':catalog.products,'/categories':catalog.categories,'/brands':catalog.brands,
        '/favorites':catalog.products.slice(0,3),'/addresses':[],
        '/profile/summary':{orderCount:2,cartCount:1,favoriteCount:3,historyCount:1,pendingPaymentCount:0,pendingReceiptCount:1,recentProducts:[{product:catalog.products[0],viewTime:'2026-11-05T12:00:00'}]}})[path] ?? [];
      await route.fulfill({json:{code:200,data}});
    });
    const page = await context.newPage();
    const errors=[]; page.on('pageerror', e=>errors.push(e.message));
    for (const path of ['/','/products','/profile']) {
      await page.goto('http://127.0.0.1:5173'+path);
      await page.waitForLoadState('networkidle');
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth+1), `overflow ${path}/${width}/${theme}`);
      if(path==='/') {
        await page.getByRole('button',{name:'多一个小伙伴',exact:false}).click();
        assert.ok(await page.locator('.scene-feature').innerText().then(t=>t.includes('陪伴娱乐')));
        await page.getByRole('button',{name:'把家务交给它',exact:false}).click();
      }
      if(path==='/products') {
        assert.ok(await page.locator('.product-key-specs dd').count()>0);
        const geometry = await page.locator('.product-card').evaluateAll(cards => cards.map(card => {
          const box=card.getBoundingClientRect(), action=card.querySelector('.product-card-actions').getBoundingClientRect();
          return action.bottom<=box.bottom+1 && action.left>=box.left-1 && action.right<=box.right+1;
        }));
        assert.ok(geometry.every(Boolean),'Product actions must stay inside their product, not leak into pagination');
        if(width<=1000) {
          assert.equal(await page.locator('.catalog-sidebar').isVisible(),false);
          await page.locator('.catalog-filter-toggle').click();
          assert.ok(await page.locator('.catalog-sidebar').isVisible());
          const filter=await page.locator('.catalog-sidebar').boundingBox();
          assert.ok(filter.width>width*0.6,'Filters must not squeeze into a narrow desktop column');
          await page.locator('.catalog-filter-toggle').click();
        }
        await page.locator('.product-compare-entry').first().click();
        assert.ok(page.url().includes('/products'));
        assert.equal(await page.locator('.comparison-tray li').count(),1);
        assert.ok(await page.locator('.comparison-tray .btn').isDisabled());
        await page.goto('http://127.0.0.1:5173/products');
        await page.waitForLoadState('networkidle');
      }
      if(path==='/profile') assert.equal(await page.locator('.personal-shelf .product-card').count(),3);
      // Full-page captures do not trigger off-screen lazy images by themselves.
      await page.evaluate(async()=> {
        for(let y=0;y<document.documentElement.scrollHeight;y+=600) {
          scrollTo({top:y,behavior:'instant'}); await new Promise(resolve=>setTimeout(resolve,40));
        }
        await Promise.all([...document.images].map(img=>img.complete?Promise.resolve():new Promise(resolve=>{img.onload=resolve;img.onerror=resolve;setTimeout(resolve,1500);}))); scrollTo({top:0,behavior:'instant'});
      });
      await page.waitForTimeout(150);
      if(path==='/') await page.screenshot({path:`${output}/home-cover-${width}-${theme}.png`});
      await page.screenshot({path:`${output}/${path==='/'?'home':path.slice(1)}-${width}-${theme}.png`,fullPage:true});
      results.push({path,width,theme,pass:true});
    }
    assert.deepEqual(errors,[]);
    await context.close();
  }
  await writeFile(`${output}/report.json`,JSON.stringify({note:'模拟接口验证，不调用真实AI、不写用户数据。',results},null,2));
  console.log(JSON.stringify(results));
} finally { await shutdown(); }
