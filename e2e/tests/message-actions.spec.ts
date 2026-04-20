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

async function selectRoom(page: Page, roomName: string): Promise<void> {
  await page.getByText(`#${roomName}`).first().click();
  await expect(page.getByPlaceholder('Type a message...')).toBeVisible({ timeout: 5_000 });
}

async function getRoomId(page: Page, roomName: string): Promise<string> {
  const result = await page.evaluate(async (roomName) => {
    const resp = await fetch('http://localhost:8080/api/rooms', { credentials: 'include' });
    const rooms = await resp.json();
    const room = rooms.find((r: { name: string; id: string }) => r.name === roomName);
    return room?.id;
  }, roomName);
  return result;
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

async function createRoomAndSendMessage(page: Page, roomName: string, message: string): Promise<void> {
  await createRoom(page, roomName);
  await selectRoom(page, roomName);

  const roomId = await getRoomId(page, roomName);
  await sendMessageViaApi(page, roomId, message);

  // Reload the room to see the message via REST
  await page.goto('/');
  await expect(page.getByText(`#${roomName}`).first()).toBeVisible({ timeout: 5_000 });
  await selectRoom(page, roomName);
  await expect(page.getByText(message)).toBeVisible({ timeout: 10_000 });
}

test.describe('Message Actions', () => {
  test('edit own message shows edited indicator', async ({ browser }) => {
    const id = unique();
    const page = await browser.newPage();
    await signUp(page, `action1_${id}@test.com`, `action1_${id}`);
    await createRoomAndSendMessage(page, `action-room-1-${id}`, 'Original text');

    // Hover over the message bubble to reveal action icons
    const messageBubble = page.getByText('Original text').first();
    await messageBubble.hover();

    // Click the edit (pencil) icon
    await page.locator('[aria-label="edit"]').click();

    // Editing banner should appear
    await expect(page.getByText('Editing message')).toBeVisible({ timeout: 5_000 });

    // The textarea should be pre-filled with the original text; replace it
    const textArea = page.getByPlaceholder('Edit message...');
    await expect(textArea).toBeVisible();
    await textArea.clear();
    await textArea.fill('Edited text');
    await page.getByRole('button', { name: 'send' }).click();

    await expect(page.getByText('Edited text')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText('(edited)')).toBeVisible({ timeout: 10_000 });

    await page.close();
  });

  test('delete own message shows deleted placeholder', async ({ browser }) => {
    const id = unique();
    const page = await browser.newPage();
    await signUp(page, `action2_${id}@test.com`, `action2_${id}`);
    await createRoomAndSendMessage(page, `action-room-2-${id}`, 'Will be deleted');

    // Hover over the message to reveal action icons
    await page.getByText('Will be deleted').first().hover();

    // Click the delete (trash) icon
    await page.locator('[aria-label="delete"]').click();

    // Popconfirm appears — click the Delete confirmation button
    await page.getByRole('button', { name: 'Delete' }).click();

    await expect(page.getByText('This message was deleted')).toBeVisible({ timeout: 10_000 });

    await page.close();
  });

  test('reply to message shows quoted preview', async ({ browser }) => {
    const id = unique();
    const page = await browser.newPage();
    await signUp(page, `action3_${id}@test.com`, `action3_${id}`);
    await createRoomAndSendMessage(page, `action-room-3-${id}`, 'Original message');

    // Hover over the message to reveal action icons
    await page.getByText('Original message').first().hover();

    // Click the reply (message bubble) icon
    await page.locator('[aria-label="message"]').click();

    // Reply preview banner should appear
    await expect(page.getByText('Replying to', { exact: false })).toBeVisible({ timeout: 5_000 });

    await page.getByPlaceholder('Type a message...').fill('This is my reply');
    await page.getByRole('button', { name: 'send' }).click();

    await expect(page.getByText('This is my reply')).toBeVisible({ timeout: 10_000 });

    await page.close();
  });
});
