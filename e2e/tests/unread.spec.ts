import { test, expect, Page } from '@playwright/test';

const unique = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6);

async function signUp(page: Page, email: string, username: string): Promise<void> {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill(`Display ${username}`);
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

async function selectRoom(page: Page, roomName: string): Promise<void> {
  await page.getByText(`#${roomName}`).first().click();
  await expect(page.getByPlaceholder('Type a message...')).toBeVisible({ timeout: 5_000 });
}

async function sendMessageViaApi(page: Page, roomId: string, content: string): Promise<void> {
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

test.describe('Unread Badges', () => {
  test('shows unread badge when message arrives in non-selected room', async ({ browser }) => {
    const id = unique();

    const page1 = await browser.newPage();
    await signUp(page1, `ubadge1a_${id}@test.com`, `ubadge1a_${id}`);

    const page2 = await browser.newPage();
    await signUp(page2, `ubadge1b_${id}@test.com`, `ubadge1b_${id}`);

    // User1 creates two rooms
    await createRoom(page1, `room-a-${id}`);
    await createRoom(page1, `room-b-${id}`);

    // User2 joins both rooms
    await browseAndJoin(page2, `room-a-${id}`);
    await browseAndJoin(page2, `room-b-${id}`);

    // User2 selects room-a (so room-b is non-selected)
    await selectRoom(page2, `room-a-${id}`);

    // User1 sends a message to room-b via API
    const roomBId = await getRoomId(page1, `room-b-${id}`);
    await sendMessageViaApi(page1, roomBId, 'Hello room B!');

    // Reload page2 to fetch fresh unread counts from the server
    await page2.waitForTimeout(500);
    await page2.goto('/');
    await expect(page2.getByText(`#room-b-${id}`)).toBeVisible({ timeout: 5_000 });

    // The UnreadBadge renders a red pill span (background-color: rgb(255, 77, 79)) with count text.
    // Look for it next to the room-b entry in the sidebar.
    const roomBText = page2.getByText(`#room-b-${id}`);
    const roomBRow = roomBText.locator('..');
    const badge = roomBRow.locator('span[style*="background-color: rgb(255, 77, 79)"]');
    await expect(badge).toBeVisible({ timeout: 10_000 });
    await expect(badge).toContainText('1');

    await page1.close();
    await page2.close();
  });

  test('unread badge clears when room is selected', async ({ browser }) => {
    const id = unique();

    const page1 = await browser.newPage();
    await signUp(page1, `ubadge2a_${id}@test.com`, `ubadge2a_${id}`);

    const page2 = await browser.newPage();
    await signUp(page2, `ubadge2b_${id}@test.com`, `ubadge2b_${id}`);

    // User1 creates two rooms
    await createRoom(page1, `clear-badge-${id}`);
    await createRoom(page1, `other-room-${id}`);

    // User2 joins both rooms
    await browseAndJoin(page2, `clear-badge-${id}`);
    await browseAndJoin(page2, `other-room-${id}`);

    // User2 selects other-room
    await selectRoom(page2, `other-room-${id}`);

    // User1 sends a message in clear-badge room via API
    const roomId = await getRoomId(page1, `clear-badge-${id}`);
    await sendMessageViaApi(page1, roomId, 'Unread test');

    // Reload page2 to fetch fresh unread counts
    await page2.waitForTimeout(500);
    await page2.goto('/');
    await expect(page2.getByText(`#clear-badge-${id}`)).toBeVisible({ timeout: 5_000 });

    // Verify unread badge is visible
    const roomText = page2.getByText(`#clear-badge-${id}`);
    const roomRow = roomText.locator('..');
    const badge = roomRow.locator('span[style*="background-color: rgb(255, 77, 79)"]');
    await expect(badge).toBeVisible({ timeout: 10_000 });

    // User2 clicks on the room -- unread should clear
    await selectRoom(page2, `clear-badge-${id}`);
    await expect(page2.getByText('Unread test')).toBeVisible({ timeout: 5_000 });

    // Badge should no longer be visible after selecting room
    await page2.waitForTimeout(500);
    await expect(badge).not.toBeVisible({ timeout: 5_000 });

    await page1.close();
    await page2.close();
  });
});
