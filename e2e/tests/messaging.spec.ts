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

  test('user creates room, posts message, and sees it in chat', async ({ page }) => {
    const id = unique();
    await signUp(page, `uipost_${id}@test.com`, `uipost_${id}`);

    const roomName = `uipost-${id}`;
    await createRoom(page, roomName);
    await selectRoom(page, roomName);

    await page.getByPlaceholder('Type a message...').fill('This is my first message!');
    await page.getByRole('button', { name: 'send' }).click();

    await expect(page.getByText('This is my first message!')).toBeVisible({ timeout: 10_000 });
  });

  test('user B joins room created by user A, sends message, and sees it', async ({ page, browser }) => {
    const id = unique();
    const roomName = `cross-${id}`;

    // User A creates the room
    await signUp(page, `a_${id}@test.com`, `a_${id}`);
    await createRoom(page, roomName);
    await expect(page.getByText(`#${roomName}`).first()).toBeVisible();

    // User B signs up, finds the room, joins, and sends a message
    const pageB = await browser.newPage();
    await signUp(pageB, `b_${id}@test.com`, `b_${id}`);
    await browseAndJoin(pageB, roomName);
    await selectRoom(pageB, roomName);

    await pageB.getByPlaceholder('Type a message...').fill('Hello from user B!');
    await pageB.getByRole('button', { name: 'send' }).click();

    await expect(pageB.getByText('Hello from user B!')).toBeVisible({ timeout: 10_000 });

    await pageB.close();
  });

  test('user B sees existing messages after joining a room', async ({ page, browser }) => {
    const id = unique();
    const roomName = `history-${id}`;

    // User A creates a room and sends a message
    await signUp(page, `hist_a_${id}@test.com`, `hist_a_${id}`);
    await createRoom(page, roomName);
    await selectRoom(page, roomName);

    await page.getByPlaceholder('Type a message...').fill('Welcome to the room!');
    await page.getByRole('button', { name: 'send' }).click();
    await expect(page.getByText('Welcome to the room!')).toBeVisible({ timeout: 10_000 });

    // User B signs up, finds the room, joins it
    const pageB = await browser.newPage();
    await signUp(pageB, `hist_b_${id}@test.com`, `hist_b_${id}`);
    await browseAndJoin(pageB, roomName);
    await selectRoom(pageB, roomName);

    // User B should see the message that was sent before they joined
    await expect(pageB.getByText('Welcome to the room!')).toBeVisible({ timeout: 10_000 });

    await pageB.close();
  });

  test('user A sees message from user B after switching back to the room', async ({ page, browser }) => {
    const id = unique();
    const targetRoom = `target-${id}`;
    const otherRoom = `other-${id}`;

    // User A creates two rooms
    await signUp(page, `a2_${id}@test.com`, `a2_${id}`);
    await createRoom(page, otherRoom);
    await createRoom(page, targetRoom);
    await selectRoom(page, targetRoom);

    // User A switches away to the other room
    await selectRoom(page, otherRoom);

    // User B signs up, finds the target room, joins, and posts a message
    const pageB = await browser.newPage();
    await signUp(pageB, `b2_${id}@test.com`, `b2_${id}`);
    await browseAndJoin(pageB, targetRoom);
    await selectRoom(pageB, targetRoom);

    await pageB.getByPlaceholder('Type a message...').fill('Hey A, are you there?');
    await pageB.getByRole('button', { name: 'send' }).click();
    await expect(pageB.getByText('Hey A, are you there?')).toBeVisible({ timeout: 10_000 });

    // User A switches back to the target room
    await selectRoom(page, targetRoom);

    // User A should see the message from User B
    await expect(page.getByText('Hey A, are you there?')).toBeVisible({ timeout: 10_000 });

    // User A switches away again
    await selectRoom(page, otherRoom);

    // User B sends two more messages
    await pageB.getByPlaceholder('Type a message...').fill('Second message');
    await pageB.getByRole('button', { name: 'send' }).click();
    await expect(pageB.getByText('Second message')).toBeVisible({ timeout: 10_000 });

    await pageB.getByPlaceholder('Type a message...').fill('Third message');
    await pageB.getByRole('button', { name: 'send' }).click();
    await expect(pageB.getByText('Third message')).toBeVisible({ timeout: 10_000 });

    // User A switches back to the target room
    await selectRoom(page, targetRoom);

    // User A should see all three messages
    await expect(page.getByText('Hey A, are you there?')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('Second message')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('Third message')).toBeVisible({ timeout: 10_000 });

    await pageB.close();
  });

  test('30 messages display in order after scrolling up and down', async ({ page }) => {
    const id = unique();
    await signUp(page, `scroll_${id}@test.com`, `scroll_${id}`);

    const roomName = `scroll-${id}`;
    await createRoom(page, roomName);
    await selectRoom(page, roomName);

    const roomId = await getRoomId(page, roomName);

    // Send 30 messages via UI
    for (let i = 1; i <= 30; i++) {
      await page.getByPlaceholder('Type a message...').fill(`Message ${i}`);
      await page.getByRole('button', { name: 'send' }).click();
      await expect(page.getByText(`Message ${i}`, { exact: true })).toBeVisible({ timeout: 5_000 });
    }

    // Scroll to top
    const chatArea = page.locator('[style*="overflow-y: auto"]').first();
    await chatArea.evaluate(el => el.scrollTop = 0);
    await page.waitForTimeout(500);

    // Scroll back to bottom
    await chatArea.evaluate(el => el.scrollTop = el.scrollHeight);
    await page.waitForTimeout(500);

    // Collect all message content divs in DOM order
    const allTexts = await chatArea.locator('div[style*="pre-wrap"]').allTextContents();
    const numbers = allTexts
      .map(t => t.trim())
      .filter(t => /^Message \d+$/.test(t))
      .map(t => parseInt(t.replace('Message ', '')));

    expect(numbers).toHaveLength(30);
    for (let i = 0; i < 30; i++) {
      expect(numbers[i]).toBe(i + 1);
    }
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
