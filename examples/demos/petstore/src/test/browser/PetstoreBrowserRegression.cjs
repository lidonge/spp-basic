const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
const artifacts=path.resolve(__dirname,'../../../target/browser-regression');
fs.mkdirSync(artifacts,{recursive:true});
const baseUrl=process.env.SPP_DEMO_URL || 'http://127.0.0.1:8086';
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.SPP_CHROME_BIN || '/usr/bin/google-chrome',args:['--no-sandbox']});
 const page=await browser.newPage(); const steps=[]; const pageErrors=[];
 page.on('pageerror',e=>pageErrors.push(e.message));
 async function act(label,selector){
  await page.locator(selector).click();
  await page.waitForFunction(()=>!document.querySelector('#checkout').disabled);
  await page.waitForLoadState('networkidle');
  const notice=await page.locator('#notice').innerText();
  assert.equal(await page.locator('#notice').evaluate(e=>e.classList.contains('error')),false,label+': '+notice);
  steps.push({label,notice,orderStatus:await page.locator('.badge').allTextContents()});
 }
 try{
 await page.goto(baseUrl,{waitUntil:'networkidle'});
 assert.equal(await page.locator('html').getAttribute('lang'),'en-US');
 assert.equal(await page.locator('#catalog-locale').inputValue(),'en-US');
 assert.equal(await page.locator('[name=name]').inputValue(),'Alex Taylor');
 assert.match(await page.locator('#products').innerText(),/British Shorthair/);
 assert.equal(await page.locator('[data-add]').count(),4);
 await page.locator('[name=membershipNumber]').fill('WEB-REGRESSION-'+Date.now());
 await act('register','#profile button[type=submit]');
 assert.match(await page.locator('#customer').innerText(),/0\.00/);
 await act('fund','#fund-wallet');
 assert.match(await page.locator('#notice').innerText(),/settlement source balance: -\$5,000\.00/);
 assert.match(await page.locator('#customer').innerText(),/\$5,000\.00/);
 await act('receive-stock','#receive-stock');
 for (const stock of await page.locator('.product .stock').allTextContents()) assert.match(stock,/Available 10/);
 assert.match(await page.locator('#notice').innerText(),/CAT-001: -10/);
 assert.match(await page.locator('#customer').innerText(),/5,000/);
 await act('add','[data-add="TOY-001"]');
 assert.equal(await page.locator('[data-quantity]').count(),1);
 await act('preview','#preview');
 assert.match(await page.locator('#quote').innerText(),/20\.35/);
 await act('checkout','#checkout');
 assert.equal(await page.locator('.order').count(),1);
 for(const action of ['Pay','Ship','Confirm delivery','Request return','Approve return']) await act(action,`.order [data-action="${action}"]`);
 assert.equal(await page.locator('.badge').innerText(),'Returned');
 assert.match(await page.locator('#customer').innerText(),/5,000/);
 await act('add-again','[data-add="FOOD-001"]');
 await act('checkout-again','#checkout');
 const unpaid=page.locator('.order').filter({has:page.locator('.badge', {hasText:'Unpaid'})});
 await unpaid.locator('[data-action="Pay"]').click();await page.waitForFunction(()=>!document.querySelector('#checkout').disabled);await page.waitForLoadState('networkidle');
 const paid=page.locator('.order').filter({has:page.locator('.badge', {hasText:'Paid'})});
 await paid.locator('[data-action="Cancel"]').click();await page.waitForFunction(()=>!document.querySelector('#checkout').disabled);await page.waitForLoadState('networkidle');
 assert.equal(await page.locator('.badge', {hasText:'Cancelled'}).count(),1);
 assert.match(await page.locator('#customer').innerText(),/5,000/);
 await act('replay','#replay');assert.match(await page.locator('#receipt').innerText(),/Original receipt/);
 await page.locator('#catalog-locale').selectOption('zh-CN');await page.waitForLoadState('networkidle');
 await page.waitForFunction(()=>document.querySelector('#products').textContent.includes('英短蓝猫'));
 assert.match(await page.locator('#products').innerText(),/英短蓝猫/);
 await page.locator('#catalog-locale').selectOption('en-US');await page.waitForLoadState('networkidle');
 await page.waitForFunction(()=>document.querySelector('#products').textContent.includes('British Shorthair'));
 assert.match(await page.locator('#products').innerText(),/British Shorthair/);
 assert.equal(await page.locator('[data-add]').count(),4);
 await page.locator('#reservation-product').selectOption('CAT-001');
 await act('reservation','#reserve-pet');assert.match(await page.locator('#reservation-facts').innerText(),/Reserved/);
 assert.equal(pageErrors.length,0);
 console.log(JSON.stringify({steps,pageErrors,reservation:await page.locator('#reservation-facts').innerText()},null,2));
 await page.screenshot({path:path.join(artifacts,'flow.png'),fullPage:true});
 }catch(e){await page.screenshot({path:path.join(artifacts,'failure.png'),fullPage:true});console.error(e);console.error('Notice:',await page.locator('#notice').innerText());process.exitCode=1;}
 finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
