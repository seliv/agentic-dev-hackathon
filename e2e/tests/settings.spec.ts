import { test, expect, Page } from '@playwright/test';

const unique = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6);

async function signUp(page: Page, email: string, username: string) {
  await page.goto('/signup');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByPlaceholder('Display Name').fill(`Test ${username}`);
  await page.getByRole('button', { name: 'Sign Up' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

async function signIn(page: Page, email: string) {
  await page.goto('/signin');
  await page.getByPlaceholder('Email').fill(email);
  await page.getByPlaceholder('Password').fill('password123');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL('/', { timeout: 10_000 });
}

test.describe('Settings', () => {
  test('displays profile information', async ({ page }) => {
    const id = unique();
    const email = `profile_${id}@test.com`;
    const username = `profile_${id}`;
    await signUp(page, email, username);

    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();

    // Profile tab should be active by default — verify disabled inputs show user data
    const usernameInput = page.locator('input[disabled]').nth(0);
    const emailInput = page.locator('input[disabled]').nth(1);
    await expect(usernameInput).toHaveValue(username);
    await expect(emailInput).toHaveValue(email);
  });

  test('change password successfully', async ({ page }) => {
    const id = unique();
    const email = `chgpwd_${id}@test.com`;
    const username = `chgpwd_${id}`;
    await signUp(page, email, username);

    await page.goto('/settings');
    await page.getByRole('tab', { name: 'Security' }).click();

    // Fill change password form
    const currentPwdInput = page.getByLabel('Current Password');
    const newPwdInput = page.getByLabel('New Password', { exact: true });
    const confirmPwdInput = page.getByLabel('Confirm New Password');

    await currentPwdInput.fill('password123');
    await newPwdInput.fill('newpassword123');
    await confirmPwdInput.fill('newpassword123');

    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/users/me/password')
    );
    await page.getByRole('button', { name: 'Change Password' }).click();
    const response = await responsePromise;

    expect(response.status()).toBe(200);

    // Form should be reset after success
    await expect(currentPwdInput).toHaveValue('');
  });

  test('change password shows error for wrong current password', async ({ page }) => {
    const id = unique();
    const email = `wrongpwd_${id}@test.com`;
    const username = `wrongpwd_${id}`;
    await signUp(page, email, username);

    await page.goto('/settings');
    await page.getByRole('tab', { name: 'Security' }).click();

    await page.getByLabel('Current Password').fill('wrongpassword');
    await page.getByLabel('New Password', { exact: true }).fill('newpassword123');
    await page.getByLabel('Confirm New Password').fill('newpassword123');

    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/users/me/password')
    );
    await page.getByRole('button', { name: 'Change Password' }).click();
    const response = await responsePromise;

    // Should get error response
    expect(response.status()).not.toBe(200);

    // Form should still have values (not reset on error)
    await expect(page.getByLabel('Current Password')).not.toHaveValue('');
  });

  test('change password validates password match', async ({ page }) => {
    const id = unique();
    const email = `mismatch_${id}@test.com`;
    const username = `mismatch_${id}`;
    await signUp(page, email, username);

    await page.goto('/settings');
    await page.getByRole('tab', { name: 'Security' }).click();

    await page.getByLabel('Current Password').fill('password123');
    await page.getByLabel('New Password', { exact: true }).fill('newpassword123');
    await page.getByLabel('Confirm New Password').fill('differentpassword');

    await page.getByRole('button', { name: 'Change Password' }).click();

    await expect(page.getByText('Passwords do not match')).toBeVisible();
  });

  test('sessions tab shows current session', async ({ page }) => {
    const id = unique();
    const email = `sess_${id}@test.com`;
    const username = `sess_${id}`;
    await signUp(page, email, username);

    await page.goto('/settings');
    await page.getByRole('tab', { name: 'Sessions' }).click();

    // Wait for sessions table to load and verify "Current" tag
    await expect(page.getByText('Current')).toBeVisible({ timeout: 10_000 });
  });

  test('delete account with confirmation', async ({ page }) => {
    const id = unique();
    const email = `delacc_${id}@test.com`;
    const username = `delacc_${id}`;
    await signUp(page, email, username);

    await page.goto('/settings');
    await page.getByRole('tab', { name: 'Security' }).click();

    // Click Delete Account button
    await page.getByRole('button', { name: 'Delete Account' }).click();

    // Modal should appear — fill password
    await page.getByPlaceholder('Enter your password').fill('password123');

    // Click the modal's OK button (labeled "Delete Account")
    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/users/me') && resp.request().method() === 'DELETE'
    );
    await page.locator('.ant-modal-footer').getByRole('button', { name: 'Delete Account' }).click();
    await responsePromise;

    // Should redirect to signin
    await expect(page).toHaveURL(/\/signin/, { timeout: 10_000 });
  });

  test('delete account shows error for wrong password', async ({ page }) => {
    const id = unique();
    const email = `delerr_${id}@test.com`;
    const username = `delerr_${id}`;
    await signUp(page, email, username);

    await page.goto('/settings');
    await page.getByRole('tab', { name: 'Security' }).click();

    // Click Delete Account button
    await page.getByRole('button', { name: 'Delete Account' }).click();

    // Modal should appear — fill wrong password
    await page.getByPlaceholder('Enter your password').fill('wrongpassword');

    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/users/me') && resp.request().method() === 'DELETE'
    );
    await page.locator('.ant-modal-footer').getByRole('button', { name: 'Delete Account' }).click();
    const response = await responsePromise;

    // Should get error and modal should stay open
    expect(response.status()).not.toBe(200);
    await expect(page.locator('.ant-modal')).toBeVisible();
  });
});
