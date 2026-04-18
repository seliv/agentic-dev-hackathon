import { test, expect } from '@playwright/test';

const unique = () => Date.now().toString(36);

test.describe('Sign Up', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/signup');
  });

  test('successful signup redirects to home', async ({ page }) => {
    const id = unique();
    await page.getByPlaceholder('Email').fill(`user${id}@test.com`);
    await page.getByPlaceholder('Username').fill(`user_${id}`);
    await page.getByPlaceholder('Password').fill(`password${id}`);
    await page.getByPlaceholder('Display Name').fill(`Test User ${id}`);
    await page.getByRole('button', { name: 'Sign Up' }).click();

    await expect(page).toHaveURL('/', { timeout: 10_000 });
  });

  test('shows error for duplicate email', async ({ page }) => {
    const id = unique();
    const email = `dup${id}@test.com`;

    // First signup
    await page.getByPlaceholder('Email').fill(email);
    await page.getByPlaceholder('Username').fill(`dup_${id}`);
    await page.getByPlaceholder('Password').fill(`password${id}`);
    await page.getByPlaceholder('Display Name').fill(`Dup User ${id}`);
    await page.getByRole('button', { name: 'Sign Up' }).click();

    await expect(page).toHaveURL('/', { timeout: 10_000 });

    // Second signup with same email — should fail and stay on signup page
    await page.goto('/signup');
    await page.getByPlaceholder('Email').fill(email);
    await page.getByPlaceholder('Username').fill(`dup2_${id}`);
    await page.getByPlaceholder('Password').fill(`password${id}`);
    await page.getByPlaceholder('Display Name').fill(`Dup User 2 ${id}`);

    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/users/signup') && !resp.ok()
    );
    await page.getByRole('button', { name: 'Sign Up' }).click();
    await responsePromise;

    // Verify we stay on signup page (error occurred, no redirect)
    await expect(page).toHaveURL(/\/signup/);
  });

  test('shows client-side validation for short password', async ({ page }) => {
    await page.getByPlaceholder('Email').fill('test@test.com');
    await page.getByPlaceholder('Username').fill('testuser');
    await page.getByPlaceholder('Password').fill('short');
    await page.getByPlaceholder('Display Name').fill('Test');
    await page.getByRole('button', { name: 'Sign Up' }).click();

    await expect(page.getByText('Password must be at least 8 characters')).toBeVisible();
  });

  test('shows client-side validation for invalid username', async ({ page }) => {
    await page.getByPlaceholder('Email').fill('test@test.com');
    await page.getByPlaceholder('Username').fill('bad user!');
    await page.getByPlaceholder('Password').fill('password123');
    await page.getByPlaceholder('Display Name').fill('Test');
    await page.getByRole('button', { name: 'Sign Up' }).click();

    await expect(page.getByText('Only letters, numbers, and underscores')).toBeVisible();
  });

  test('navigates to signin page', async ({ page }) => {
    await page.getByRole('link', { name: 'Sign In' }).click();
    await expect(page).toHaveURL('/signin');
  });
});
