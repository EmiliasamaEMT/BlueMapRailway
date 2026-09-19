import { test, expect } from '@playwright/test';

test('mobile panel can be opened and closed without covering the map permanently', async ({page}) => {
  await page.setViewportSize({width:390,height:844});
  await page.goto('/');
  await expect(page.locator('#sidebar')).toBeHidden();
  await page.getByRole('button',{name:'切换管理面板'}).click();
  await expect(page.locator('#sidebar')).toBeVisible();
  await page.getByRole('button',{name:'切换管理面板'}).click();
  await expect(page.locator('#sidebar')).toBeHidden();
  expect(await page.evaluate(()=>document.body.scrollWidth)).toBe(390);
  await page.screenshot({path:'../build/admin-web-0.2.1-mobile.png'});
});

test('zoom controls work and do not trigger map dragging', async ({page}, testInfo) => {
  await page.goto('/');
  await expect(page.locator('#runtime-pill')).toContainText('已更新');
  const before=await page.locator('#zoom-label').textContent();
  await page.getByRole('button',{name:'放大地图'}).click();
  await expect(page.locator('#zoom-label')).not.toHaveText(before);
  await expect(page.locator('#map-stage')).not.toHaveClass(/is-panning/);
  await page.screenshot({path:`../build/admin-web-0.2.1-${testInfo.project.name}.png`});
});

test('large route list has bounded DOM and searchable pagination', async ({page}) => {
  await page.route('**/api/state*',async route=>{
    const response=await route.fetch();const data=await response.json();
    data.routes=Array.from({length:1000},(_,i)=>({id:`r${i}`,name:`Route ${i}`,componentIds:[],color:'#16786a'}));
    await route.fulfill({json:data});
  });
  await page.goto('/');
  await expect(page.locator('#route-list .object-row')).toHaveCount(60);
  await page.getByRole('button',{name:'下一页'}).click();
  await expect(page.locator('#route-list')).toContainText('Route 60');
  await page.locator('#route-search').fill('Route 999');
  await expect(page.locator('#route-list .object-row')).toHaveCount(1);
});

async function login(page) {
  await page.goto('/');
  await page.getByRole('button',{name:'进入管理'}).click();
  await page.locator('#login-token').fill('test-token');
  await page.getByRole('button',{name:'验证并进入'}).click();
  await expect(page.locator('body')).toHaveClass(/admin-mode/);
}

for (const [type,tab,label] of [['station','站点','保存站点'],['mask','隐藏与裁切','保存规则']]) {
  test(`${type} editor validates bounds and preserves endpoint contract`, async ({page}) => {
    await login(page);
    await page.getByRole('tab',{name:tab,exact:true}).click();
    await page.locator(`#${type}-id`).fill('new-area');
    await page.locator(`#${type}-name`).fill('测试区域');
    await page.locator(`#${type}-world`).fill('world');
    await page.getByRole('button',{name:label,exact:true}).click();
    expect(await page.locator(`#${type}-min-x`).evaluate(el=>el.validity.valueMissing)).toBeTruthy();
    for (const [name,value] of [['min-x','10'],['min-z','20'],['max-x','30'],['max-z','40']]) await page.locator(`#${type}-${name}`).fill(value);
    const requestPromise=page.waitForRequest(req=>req.url().includes(`/api/${type}`)&&req.method()==='POST');
    await page.getByRole('button',{name:label,exact:true}).click();
    const body=(await requestPromise).postDataJSON();
    expect(body).toMatchObject({id:'new-area',world:'world',minX:10,minZ:20,maxX:30,maxZ:40});
    await expect(page.locator(`#${type}-dirty`)).toBeHidden();
  });
}

test('refresh preserves an unsaved route draft and selection', async ({page}, testInfo) => {
  await login(page);
  await page.locator('#route-list').getByRole('button',{name:'编辑',exact:true}).first().click();
  await page.locator('#route-name').fill('未保存的线路名称');
  await page.getByRole('button',{name:'刷新数据',exact:true}).click();
  await expect(page.locator('#route-name')).toHaveValue('未保存的线路名称');
  await expect(page.locator('#route-dirty')).toBeVisible();
  await page.screenshot({path:`../build/admin-web-0.2.1-admin-${testInfo.project.name}.png`});
});
