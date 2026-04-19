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

async function createPrivateRoom(page: Page, roomName: string): Promise<void> {
  await page.getByRole('button', { name: 'plus Create' }).click();
  const modal = page.getByRole('dialog', { name: 'Create Room' });
  await expect(modal).toBeVisible();
  await page.getByPlaceholder('e.g. general').fill(roomName);
  await page.getByLabel('Private (invite-only)').check();
  const responsePromise = page.waitForResponse(resp =>
    resp.url().includes('/api/rooms') && resp.request().method() === 'POST'
  );
  await modal.getByRole('button', { name: 'OK' }).click();
  await responsePromise;
  await expect(modal).not.toBeVisible({ timeout: 5_000 });
}

async function makeFriends(page1: Page, page2: Page, username2: string): Promise<void> {
  await page1.getByRole('button', { name: 'Find Users' }).click();
  await page1.getByPlaceholder('Search by username or display name...').fill(username2);
  await expect(page1.getByRole('heading', { name: username2 })).toBeVisible({ timeout: 5_000 });
  const requestResponse = page1.waitForResponse(resp =>
    resp.url().includes('/api/friends/request') && resp.request().method() === 'POST'
  );
  await page1.getByRole('button', { name: 'Add Friend' }).click();
  await requestResponse;
  await page1.keyboard.press('Escape');

  await page2.getByRole('button', { name: 'Contacts' }).click();
  await page2.getByRole('tab', { name: /Requests/ }).click();
  await expect(page2.getByRole('button', { name: 'Accept' })).toBeVisible({ timeout: 5_000 });
  const acceptResponse = page2.waitForResponse(resp =>
    resp.url().includes('/api/friends/') && resp.url().includes('/accept') && resp.request().method() === 'POST'
  );
  await page2.getByRole('button', { name: 'Accept' }).click();
  await acceptResponse;
  await page2.keyboard.press('Escape');
}

test.describe('Private Rooms', () => {
  test('create private room', async ({ page }) => {
    const id = unique();
    await signUp(page, `pr1_${id}@test.com`, `pr1_${id}`);

    const roomName = `private-room-${id}`;
    await createPrivateRoom(page, roomName);

    // Should see room with lock emoji in sidebar
    await expect(page.getByText(`🔒${roomName}`)).toBeVisible({ timeout: 5_000 });
  });

  test('private room not visible in public browse', async ({ browser }) => {
    const id = unique();

    const page1 = await browser.newPage();
    await signUp(page1, `pr2a_${id}@test.com`, `pr2a_${id}`);

    // Create private room
    const secretRoom = `secret-${id}`;
    await createPrivateRoom(page1, secretRoom);

    // User 2 browses rooms — should not see private room
    const page2 = await browser.newPage();
    await signUp(page2, `pr2b_${id}@test.com`, `pr2b_${id}`);
    await page2.getByRole('button', { name: 'Browse' }).click();
    await expect(page2.locator('.ant-modal')).toBeVisible();
    await page2.getByPlaceholder('Search rooms...').fill(secretRoom);

    // Wait for search results to settle
    await page2.waitForTimeout(1000);
    await expect(page2.getByText(secretRoom)).not.toBeVisible();

    await page1.close();
    await page2.close();
  });

  test('invite friend to private room via invitation', async ({ browser }) => {
    const id = unique();

    const page1 = await browser.newPage();
    await signUp(page1, `pr3a_${id}@test.com`, `pr3a_${id}`);

    const page2 = await browser.newPage();
    await signUp(page2, `pr3b_${id}@test.com`, `pr3b_${id}`);

    await makeFriends(page1, page2, `pr3b_${id}`);

    // User 1 creates private room
    const roomName = `invite-room-${id}`;
    await createPrivateRoom(page1, roomName);

    // Select the room
    await page1.getByText(`🔒${roomName}`).click();

    // Click Invite button in room header
    await expect(page1.getByRole('button', { name: 'Invite' })).toBeVisible({ timeout: 5_000 });
    await page1.getByRole('button', { name: 'Invite' }).click();

    // Wait for invite modal to open and friends to load
    const inviteModal = page1.getByRole('dialog', { name: 'Invite to Room' });
    await expect(inviteModal).toBeVisible({ timeout: 5_000 });
    await expect(page1.getByRole('heading', { name: `pr3b_${id}` })).toBeVisible({ timeout: 5_000 });

    // Click Invite button for the specific friend
    const inviteResponse = page1.waitForResponse(resp =>
      resp.url().includes('/api/rooms/') && resp.url().includes('/invitations') && resp.request().method() === 'POST'
    );
    await page1.locator('.ant-list-item').filter({ hasText: `pr3b_${id}` }).getByRole('button', { name: 'Invite' }).click();
    await inviteResponse;

    // User 2 checks invitations in contacts
    await page2.getByRole('button', { name: 'Contacts' }).click();
    await page2.getByRole('tab', { name: /Invitations/ }).click();
    await expect(page2.getByText(`#${roomName}`)).toBeVisible({ timeout: 5_000 });

    // Accept invitation
    const acceptResponse = page2.waitForResponse(resp =>
      resp.url().includes('/api/invitations/') && resp.url().includes('/accept') && resp.request().method() === 'POST'
    );
    await page2.getByRole('button', { name: 'Accept' }).click();
    await acceptResponse;

    // Reload so the room list is refreshed from the server
    await page2.goto('/');
    // Verify user 2 now sees the room in their sidebar
    await expect(page2.getByText(`🔒${roomName}`)).toBeVisible({ timeout: 5_000 });

    await page1.close();
    await page2.close();
  });
});
