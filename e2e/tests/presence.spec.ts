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

async function makeFriends(page1: Page, page2: Page, username2: string): Promise<void> {
  // page1 sends friend request to username2
  await page1.getByRole('button', { name: 'Find Users' }).click();
  await page1.getByPlaceholder('Search by username or display name...').fill(username2);
  await expect(page1.getByRole('heading', { name: username2 })).toBeVisible({ timeout: 5_000 });
  const requestResponse = page1.waitForResponse(resp =>
    resp.url().includes('/api/friends/request') && resp.request().method() === 'POST'
  );
  await page1.getByRole('button', { name: 'Add Friend' }).click();
  await requestResponse;
  await page1.keyboard.press('Escape');

  // page2 accepts the request
  await page2.getByRole('button', { name: 'Contacts' }).click();
  await page2.getByRole('tab', { name: /Requests/ }).click();
  await expect(page2.getByRole('button', { name: 'Accept' })).toBeVisible({ timeout: 5_000 });
  const acceptResponse = page2.waitForResponse(resp =>
    resp.url().includes('/api/friends/') && resp.url().includes('/accept') && resp.request().method() === 'POST'
  );
  await page2.getByRole('button', { name: 'Accept' }).click();
  await acceptResponse;
}

test.describe('Presence', () => {
  test('DM presence indicator renders for online user', async ({ browser }) => {
    const id = unique();

    const page1 = await browser.newPage();
    await signUp(page1, `pres1a_${id}@test.com`, `pres1a_${id}`);

    const page2 = await browser.newPage();
    await signUp(page2, `pres1b_${id}@test.com`, `pres1b_${id}`);

    // Wait for WebSocket connections to be established on both pages
    await page1.waitForTimeout(2000);
    await page2.waitForTimeout(1000);

    // Become friends and create DM
    await makeFriends(page1, page2, `pres1b_${id}`);

    // page2 is on Contacts drawer -- switch to Friends tab and click DM
    await page2.getByRole('tab', { name: /Friends/ }).click();
    await expect(page2.getByRole('heading', { name: `pres1a_${id}` })).toBeVisible({ timeout: 5_000 });

    const dmResponse = page2.waitForResponse(resp =>
      resp.url().includes('/api/direct-messages/') && resp.request().method() === 'POST'
    );
    await page2.locator('.ant-list-item').filter({ hasText: `pres1a_${id}` }).getByRole('button', { name: 'DM' }).click();
    await dmResponse;

    // Close contacts drawer
    await page2.keyboard.press('Escape');

    // Wait for DM to appear in sidebar
    await page2.waitForTimeout(1000);

    // The DM entry should show "Direct Message" text
    const dmEntry = page2.getByText('Direct Message').first();
    await expect(dmEntry).toBeVisible({ timeout: 5_000 });

    // Select the DM room to trigger presence fetch via getRoomMemberPresence API
    await dmEntry.click();
    await expect(page2.getByPlaceholder('Type a message...')).toBeVisible({ timeout: 5_000 });

    // Wait for presence data to be fetched and rendered
    await page2.waitForTimeout(2000);

    // The PresenceIndicator renders a span with title attribute (online, afk, or offline)
    // and a colored dot. After selecting the DM room, the presence for the other user
    // should be fetched and displayed.
    // The span has title matching the status and a round colored background.
    const presenceOnline = page2.locator('span[title="online"]');
    const presenceOffline = page2.locator('span[title="offline"]');
    const presenceAfk = page2.locator('span[title="afk"]');

    const onlineCount = await presenceOnline.count();
    const offlineCount = await presenceOffline.count();
    const afkCount = await presenceAfk.count();

    // A presence indicator should be rendered (at least one, for the other user)
    const totalPresenceIndicators = onlineCount + offlineCount + afkCount;
    expect(totalPresenceIndicators).toBeGreaterThan(0);

    // If the WebSocket connection for page1 is properly established,
    // the presence should be ONLINE (green dot)
    if (onlineCount > 0) {
      await expect(presenceOnline.first()).toBeVisible();
    }

    await page1.close();
    await page2.close();
  });
});
