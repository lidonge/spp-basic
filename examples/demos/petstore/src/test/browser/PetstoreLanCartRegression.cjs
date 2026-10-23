const {chromium} = require('playwright');
const assert = require('node:assert/strict');

// Run against an isolated demo database: this flow creates a customer and an order.
const baseUrl = process.env.SPP_DEMO_URL;
assert.ok(baseUrl, 'Set SPP_DEMO_URL to the isolated LAN HTTP test server');
(async () => {
  const browser = await chromium.launch({headless: true, executablePath: process.env.SPP_CHROME_BIN || '/usr/bin/google-chrome', args: ['--no-sandbox']});
  try {
    const page = await browser.newPage(); const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    async function act(selector) {
      await page.locator(selector).click();
      await page.waitForFunction(() => !document.querySelector('#checkout').disabled);
      await page.waitForLoadState('networkidle');
      assert.equal(await page.locator('#notice').evaluate(element => element.classList.contains('error')), false,
        await page.locator('#notice').innerText());
    }
    await page.goto(baseUrl, {waitUntil: 'networkidle'});
    assert.equal(await page.evaluate(() => isSecureContext), false);
    assert.equal(await page.evaluate(() => typeof crypto.randomUUID), 'undefined');
    assert.equal(await page.locator('[data-add]').count(), 4);
    await page.locator('[name=membershipNumber]').fill('LAN-CART-' + Date.now());
    await act('#profile button[type=submit]');
    await act('#fund-wallet');
    await act('#receive-stock');
    await act('[data-add="FOOD-001"]');
    await act('[data-add="TOY-001"]');
    assert.equal(await page.locator('[data-quantity]').count(), 2);
    await act('#preview');
    assert.match(await page.locator('#quote').innerText(), /50\.25/);
    await act('#checkout');
    assert.equal(await page.locator('.order').count(), 1);
    const lines = await page.locator('.order-lines').innerText();
    assert.match(lines, /Everyday cat food/); assert.match(lines, /Cat teaser wand/);
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({pageErrors: errors, cartItems: 2, orderLines: lines, total: '50.25'}));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
