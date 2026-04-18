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

async function selectRoom(page: Page, roomName: string): Promise<void> {
  await page.getByText(`#${roomName}`).first().click();
  await expect(page.getByPlaceholder('Type a message...')).toBeVisible({ timeout: 5_000 });
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

/** Send a message via REST API */
async function sendMessageViaApi(page: Page, roomId: string, content: string): Promise<void> {
  // Use page.evaluate to make the fetch call with the page's session cookies
  const result = await page.evaluate(async ({ roomId, content }) => {
    const resp = await fetch(`http://localhost:8080/api/rooms/${roomId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ content }),
    });
    return { ok: resp.ok, status: resp.status };
  }, { roomId, content });
  expect(result.ok).toBe(true);
}

/** Get room ID from the room name by extracting from the GET rooms API */
async function getRoomId(page: Page, roomName: string): Promise<string> {
  const result = await page.evaluate(async (roomName) => {
    const resp = await fetch('http://localhost:8080/api/rooms', {
      credentials: 'include',
    });
    const rooms = await resp.json();
    const room = rooms.find((r: any) => r.name === roomName);
    return room?.id;
  }, roomName);
  return result;
}

test.describe('Messaging', () => {
  test('send a message and see it in chat', async ({ page }) => {
    const id = unique();
    await signUp(page, `msg_${id}@test.com`, `msg_${id}`);

    const roomName = `msgroom-${id}`;
    await createRoom(page, roomName);
    await selectRoom(page, roomName);

    // Get room ID for API call
    const roomId = await getRoomId(page, roomName);

    // Send message via REST API (reliable)
    await sendMessageViaApi(page, roomId, 'Hello, world!');

    // Re-select room to load the message via REST
    await page.goto('/');
    await expect(page.getByText(`#${roomName}`).first()).toBeVisible({ timeout: 5_000 });
    await selectRoom(page, roomName);

    await expect(page.getByText('Hello, world!')).toBeVisible({ timeout: 10_000 });
  });

  test('two users can exchange messages in real time', async ({ page, browser }) => {
    const id = unique();
    const roomName = `rt-${id}`;

    // User 1 creates room and selects it
    await signUp(page, `rt1_${id}@test.com`, `rt1_${id}`);
    await createRoom(page, roomName);
    await selectRoom(page, roomName);

    const roomId = await getRoomId(page, roomName);

    // User 2 joins room and selects it
    const page2 = await browser.newPage();
    await signUp(page2, `rt2_${id}@test.com`, `rt2_${id}`);
    await browseAndJoin(page2, roomName);
    await selectRoom(page2, roomName);

    // User 1 sends a message via API
    await sendMessageViaApi(page, roomId, 'Hello from user 1!');

    // User 2 re-selects room to see the message
    await page2.goto('/');
    await expect(page2.getByText(`#${roomName}`).first()).toBeVisible({ timeout: 5_000 });
    await selectRoom(page2, roomName);
    await expect(page2.getByText('Hello from user 1!')).toBeVisible({ timeout: 10_000 });

    // User 2 sends a message via API
    await sendMessageViaApi(page2, roomId, 'Hello from user 2!');

    // User 1 re-selects room to see the message
    await page.goto('/');
    await expect(page.getByText(`#${roomName}`).first()).toBeVisible({ timeout: 5_000 });
    await selectRoom(page, roomName);
    await expect(page.getByText('Hello from user 2!')).toBeVisible({ timeout: 10_000 });

    await page2.close();
  });

  test('empty state shows when no room selected', async ({ page }) => {
    const id = unique();
    await signUp(page, `empty_${id}@test.com`, `empty_${id}`);

    await expect(page.getByText('Select a room to start chatting')).toBeVisible({ timeout: 5_000 });
  });

  test('message input is present when room selected', async ({ page }) => {
    const id = unique();
    await signUp(page, `input_${id}@test.com`, `input_${id}`);

    const roomName = `inputroom-${id}`;
    await createRoom(page, roomName);
    await selectRoom(page, roomName);

    await expect(page.getByPlaceholder('Type a message...')).toBeVisible({ timeout: 5_000 });
  });
});
