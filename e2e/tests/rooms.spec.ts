import { test, expect, Page, Browser } from '@playwright/test';

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
  // Wait for modal to close
  await expect(page.locator('.ant-modal')).not.toBeVisible({ timeout: 5_000 });
}

async function browseAndJoin(page: Page, roomName: string): Promise<void> {
  await page.getByRole('button', { name: 'Browse' }).click();
  await expect(page.locator('.ant-modal')).toBeVisible();
  await page.getByPlaceholder('Search rooms...').fill(roomName);

  // Wait for search results, then click Join on the matching room's list item
  const roomItem = page.locator('.ant-list-item').filter({ hasText: `#${roomName}` });
  await expect(roomItem).toBeVisible({ timeout: 10_000 });

  const joinResponse = page.waitForResponse(resp =>
    resp.url().includes('/api/rooms/') && resp.url().includes('/join') && resp.request().method() === 'POST'
  );
  await roomItem.getByRole('button', { name: 'Join' }).click();
  await joinResponse;

  // Modal closes automatically after joining
  await expect(page.locator('.ant-modal')).not.toBeVisible({ timeout: 5_000 });
}

test.describe('Chat Rooms', () => {
  test('create room and see it in room list', async ({ page }) => {
    const id = unique();
    await signUp(page, `create_${id}@test.com`, `create_${id}`);

    const roomName = `room-${id}`;
    await createRoom(page, roomName);

    // Verify room appears in sidebar
    await expect(page.getByText(`#${roomName}`).first()).toBeVisible();
  });

  test('browse and join a public room', async ({ page, browser }) => {
    const id = unique();
    const roomName = `browse-${id}`;

    // User 1 creates the room
    await signUp(page, `owner_${id}@test.com`, `owner_${id}`);
    await createRoom(page, roomName);
    await expect(page.getByText(`#${roomName}`).first()).toBeVisible();

    // User 2 in a new page
    const page2 = await browser.newPage();
    await signUp(page2, `joiner_${id}@test.com`, `joiner_${id}`);

    await browseAndJoin(page2, roomName);

    // Verify room appears in sidebar
    await expect(page2.getByText(`#${roomName}`).first()).toBeVisible();

    await page2.close();
  });

  test('leave a room', async ({ page, browser }) => {
    const id = unique();
    const roomName = `leave-${id}`;

    // User 1 creates the room
    await signUp(page, `leaveowner_${id}@test.com`, `leaveowner_${id}`);
    await createRoom(page, roomName);

    // User 2 joins the room
    const page2 = await browser.newPage();
    await signUp(page2, `leaver_${id}@test.com`, `leaver_${id}`);

    await browseAndJoin(page2, roomName);

    // Select the room in sidebar
    await page2.getByText(`#${roomName}`).first().click();

    // Verify room header is visible with Leave button
    await expect(page2.getByRole('button', { name: 'logout Leave' })).toBeVisible({ timeout: 5_000 });

    // Click Leave
    const responsePromise = page2.waitForResponse(resp =>
      resp.url().includes('/api/rooms/') && resp.url().includes('/leave') && resp.request().method() === 'POST'
    );
    await page2.getByRole('button', { name: 'logout Leave' }).click();
    await responsePromise;

    // Verify room disappears from sidebar
    await expect(page2.getByText(`#${roomName}`)).not.toBeVisible({ timeout: 5_000 });

    await page2.close();
  });

  test('duplicate room name shows error', async ({ page }) => {
    const id = unique();
    const roomName = `dup-${id}`;

    await signUp(page, `dup_${id}@test.com`, `dup_${id}`);
    await createRoom(page, roomName);

    // Try creating room with the same name
    await page.getByRole('button', { name: 'plus Create' }).click();
    await expect(page.locator('.ant-modal')).toBeVisible();
    await page.getByPlaceholder('e.g. general').fill(roomName);

    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/rooms') && resp.request().method() === 'POST'
    );
    await page.locator('.ant-modal-footer').getByRole('button', { name: 'OK' }).click();
    const response = await responsePromise;

    // Should get error response
    expect(response.status()).not.toBe(200);

    // Modal should stay open (creation failed)
    await expect(page.locator('.ant-modal')).toBeVisible();
  });
});
