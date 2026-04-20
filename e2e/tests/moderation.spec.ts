import { test, expect, Page } from '@playwright/test';

const unique = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6);

async function signUp(page: Page, email: string, username: string): Promise<void> {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill(`Test ${username}`);
  await page.getByRole('button', { name: 'Sign Up' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

async function createRoom(page: Page, roomName: string): Promise<void> {
  await page.getByRole('button', { name: 'plus Create' }).click();
  await expect(page.locator('.ant-modal')).toBeVisible();
  await page.getByPlaceholder('e.g. general').fill(roomName);
  const responsePromise = page.waitForResponse(resp =>
    resp.url().includes('/api/rooms') && resp.request().method() === 'POST'
  );
  await page.locator('.ant-modal-footer').getByRole('button', { name: 'OK' }).click();
  await responsePromise;
  await expect(page.locator('.ant-modal')).not.toBeVisible({ timeout: 5_000 });
}

async function browseAndJoin(page: Page, roomName: string): Promise<void> {
  await page.getByRole('button', { name: 'Browse' }).click();
  await expect(page.locator('.ant-modal')).toBeVisible();
  await page.getByPlaceholder('Search rooms...').fill(roomName);

  const roomItem = page.locator('.ant-list-item').filter({ hasText: `#${roomName}` });
  await expect(roomItem).toBeVisible({ timeout: 10_000 });

  const joinResponse = page.waitForResponse(resp =>
    resp.url().includes('/api/rooms/') && resp.url().includes('/join') && resp.request().method() === 'POST'
  );
  await roomItem.getByRole('button', { name: 'Join' }).click();
  await joinResponse;
  await expect(page.locator('.ant-modal')).not.toBeVisible({ timeout: 5_000 });
}

test.describe('Moderation', () => {
  test('owner sees manage button and can ban a member', async ({ browser }) => {
    const id = unique();
    const ownerPage = await browser.newPage();
    const memberPage = await browser.newPage();

    await signUp(ownerPage, `modowner_${id}@test.com`, `modowner_${id}`);
    await signUp(memberPage, `modmember_${id}@test.com`, `modmember_${id}`);

    await createRoom(ownerPage, `mod-e2e-${id}`);
    await ownerPage.getByText(`mod-e2e-${id}`).click();

    await browseAndJoin(memberPage, `mod-e2e-${id}`);
    await memberPage.getByText(`mod-e2e-${id}`).click();

    await expect(ownerPage.getByRole('button', { name: 'Manage' })).toBeVisible();
    await ownerPage.getByRole('button', { name: 'Manage' }).click();

    await expect(ownerPage.getByText(`Manage: mod-e2e-${id}`)).toBeVisible();
    await expect(ownerPage.getByText(`modmember_${id}`)).toBeVisible();

    await ownerPage.getByRole('button', { name: 'Ban' }).click();
    await ownerPage.getByRole('button', { name: 'Ban' }).last().click();

    await expect(ownerPage.getByText('User banned')).toBeVisible({ timeout: 5_000 });

    await ownerPage.close();
    await memberPage.close();
  });

  test('owner can promote member to admin and demote', async ({ browser }) => {
    const id = unique();
    const ownerPage = await browser.newPage();
    const memberPage = await browser.newPage();

    await signUp(ownerPage, `promowner_${id}@test.com`, `promowner_${id}`);
    await signUp(memberPage, `prommember_${id}@test.com`, `prommember_${id}`);

    await createRoom(ownerPage, `prom-e2e-${id}`);
    await ownerPage.getByText(`prom-e2e-${id}`).click();

    await browseAndJoin(memberPage, `prom-e2e-${id}`);

    await ownerPage.getByRole('button', { name: 'Manage' }).click();
    await expect(ownerPage.getByText(`Manage: prom-e2e-${id}`)).toBeVisible();

    await ownerPage.getByRole('button', { name: 'Make Admin' }).click();
    await expect(ownerPage.getByText('Role updated to ADMIN')).toBeVisible({ timeout: 5_000 });

    await ownerPage.getByRole('button', { name: 'Remove Admin' }).click();
    await expect(ownerPage.getByText('Role updated to MEMBER')).toBeVisible({ timeout: 5_000 });

    await ownerPage.close();
    await memberPage.close();
  });

  test('owner can update room settings', async ({ page }) => {
    const id = unique();
    await signUp(page, `settings_${id}@test.com`, `settings_${id}`);
    await createRoom(page, `settings-e2e-${id}`);
    await page.getByText(`settings-e2e-${id}`).click();

    await page.getByRole('button', { name: 'Manage' }).click();
    await page.getByRole('tab', { name: 'Settings' }).click();

    const nameInput = page.locator('.ant-modal input').first();
    await nameInput.clear();
    await nameInput.fill(`renamed-${id}`);

    await page.getByRole('button', { name: 'Save Changes' }).click();
    await expect(page.getByText('Room settings updated')).toBeVisible({ timeout: 5_000 });
  });

  test('owner can delete room', async ({ page }) => {
    const id = unique();
    await signUp(page, `delroom_${id}@test.com`, `delroom_${id}`);
    await createRoom(page, `delroom-e2e-${id}`);
    await page.getByText(`delroom-e2e-${id}`).click();

    await page.getByRole('button', { name: 'Manage' }).click();
    await page.getByRole('tab', { name: 'Settings' }).click();

    await page.getByRole('button', { name: 'Delete Room' }).click();
    await page.getByRole('button', { name: 'Delete' }).last().click();

    await expect(page.getByText('Room deleted')).toBeVisible({ timeout: 5_000 });

    await expect(page.getByText(`delroom-e2e-${id}`)).not.toBeVisible({ timeout: 5_000 });
  });

  test('member does not see manage button', async ({ browser }) => {
    const id = unique();
    const ownerPage = await browser.newPage();
    const memberPage = await browser.newPage();

    await signUp(ownerPage, `nomng_owner_${id}@test.com`, `nomng_owner_${id}`);
    await signUp(memberPage, `nomng_member_${id}@test.com`, `nomng_member_${id}`);

    await createRoom(ownerPage, `nomng-e2e-${id}`);

    await browseAndJoin(memberPage, `nomng-e2e-${id}`);
    await memberPage.getByText(`nomng-e2e-${id}`).click();

    await expect(memberPage.getByRole('button', { name: 'Manage' })).not.toBeVisible();

    await ownerPage.close();
    await memberPage.close();
  });
});
