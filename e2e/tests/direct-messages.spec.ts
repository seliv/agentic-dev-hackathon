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
  // Close the modal
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

test.describe('Direct Messages', () => {
  test('create DM from contacts and send message', async ({ browser }) => {
    const id = unique();

    const page1 = await browser.newPage();
    await signUp(page1, `dm1a_${id}@test.com`, `dm1a_${id}`);

    const page2 = await browser.newPage();
    await signUp(page2, `dm1b_${id}@test.com`, `dm1b_${id}`);

    await makeFriends(page1, page2, `dm1b_${id}`);

    // page2 is on Contacts drawer — switch to Friends tab and click DM
    await page2.getByRole('tab', { name: /Friends/ }).click();
    await expect(page2.getByRole('heading', { name: `dm1a_${id}` })).toBeVisible({ timeout: 5_000 });

    const dmResponse = page2.waitForResponse(resp =>
      resp.url().includes('/api/direct-messages/') && resp.request().method() === 'POST'
    );
    // Find the DM button within the friends list item (ant-btn-sm distinguishes from dropdown trigger)
    await page2.locator('.ant-list-item').filter({ hasText: `dm1a_${id}` }).getByRole('button', { name: 'DM' }).click();
    await dmResponse;

    // Should see DM room selected — room header shows "Direct Message"
    await expect(page2.getByRole('heading', { name: 'Direct Message' })).toBeVisible({ timeout: 5_000 });

    // Send a message
    await page2.getByPlaceholder('Type a message...').fill('Hello via DM!');
    await page2.getByRole('button', { name: 'send' }).click();

    await expect(page2.getByText('Hello via DM!')).toBeVisible({ timeout: 5_000 });

    await page1.close();
    await page2.close();
  });
});
