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

async function sendFriendRequest(page: Page, targetUsername: string): Promise<void> {
  await page.getByRole('button', { name: 'Find Users' }).click();
  await page.getByPlaceholder('Search by username or display name...').fill(targetUsername);
  await expect(page.getByRole('heading', { name: targetUsername })).toBeVisible({ timeout: 5_000 });
  const responsePromise = page.waitForResponse(resp =>
    resp.url().includes('/api/friends/request') && resp.request().method() === 'POST'
  );
  await page.getByRole('button', { name: 'Add Friend' }).click();
  await responsePromise;
}

test.describe('Friends', () => {
  test('send friend request via user search and accept', async ({ browser }) => {
    const id = unique();

    // User 1 signs up
    const page1 = await browser.newPage();
    await signUp(page1, `fr1a_${id}@test.com`, `fr1a_${id}`);

    // User 2 signs up
    const page2 = await browser.newPage();
    await signUp(page2, `fr1b_${id}@test.com`, `fr1b_${id}`);

    // User 1 sends friend request to User 2
    await sendFriendRequest(page1, `fr1b_${id}`);

    // User 2 checks contacts for incoming request
    await page2.getByRole('button', { name: 'Contacts' }).click();
    await page2.getByRole('tab', { name: /Requests/ }).click();
    await expect(page2.getByRole('heading', { name: `fr1a_${id}` })).toBeVisible({ timeout: 5_000 });

    // User 2 accepts
    const acceptResponse = page2.waitForResponse(resp =>
      resp.url().includes('/api/friends/') && resp.url().includes('/accept') && resp.request().method() === 'POST'
    );
    await page2.getByRole('button', { name: 'Accept' }).click();
    await acceptResponse;

    // User 2 checks friends tab
    await page2.getByRole('tab', { name: /Friends/ }).click();
    await expect(page2.getByRole('heading', { name: `fr1a_${id}` })).toBeVisible({ timeout: 5_000 });

    await page1.close();
    await page2.close();
  });

  test('remove friend', async ({ browser }) => {
    const id = unique();

    const page1 = await browser.newPage();
    await signUp(page1, `fr2a_${id}@test.com`, `fr2a_${id}`);

    const page2 = await browser.newPage();
    await signUp(page2, `fr2b_${id}@test.com`, `fr2b_${id}`);

    // Become friends: page1 sends request
    await sendFriendRequest(page1, `fr2b_${id}`);

    // Page2 accepts
    await page2.getByRole('button', { name: 'Contacts' }).click();
    await page2.getByRole('tab', { name: /Requests/ }).click();
    await expect(page2.getByRole('heading', { name: `fr2a_${id}` })).toBeVisible({ timeout: 5_000 });
    const acceptResponse = page2.waitForResponse(resp =>
      resp.url().includes('/api/friends/') && resp.url().includes('/accept') && resp.request().method() === 'POST'
    );
    await page2.getByRole('button', { name: 'Accept' }).click();
    await acceptResponse;

    // Remove friend
    await page2.getByRole('tab', { name: /Friends/ }).click();
    await expect(page2.getByRole('heading', { name: `fr2a_${id}` })).toBeVisible({ timeout: 5_000 });
    const removeResponse = page2.waitForResponse(resp =>
      resp.url().includes('/api/friends/') && resp.request().method() === 'DELETE'
    );
    await page2.getByRole('button', { name: 'Remove' }).click();
    await removeResponse;

    // Verify friend is gone
    await expect(page2.getByRole('heading', { name: `fr2a_${id}` })).not.toBeVisible({ timeout: 5_000 });

    await page1.close();
    await page2.close();
  });
});
