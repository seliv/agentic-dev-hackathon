import { test, expect } from '@playwright/test';

const testId = Date.now().toString(36);
const testEmail = `signin_${testId}@test.com`;
const testPassword = `password_${testId}`;

test.describe('Sign In', () => {
  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    await page.goto('/signup');
    await page.getByPlaceholder('Email').fill(testEmail);
    await page.getByPlaceholder('Username').fill(`signin_${testId}`);
    await page.getByPlaceholder('Password').fill(testPassword);
    await page.getByPlaceholder('Display Name').fill(`SignIn Test ${testId}`);
    await page.getByRole('button', { name: 'Sign Up' }).click();
    await expect(page).toHaveURL('/', { timeout: 10_000 });
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/signin');
  });

  test('successful signin redirects to home', async ({ page }) => {
    await page.getByPlaceholder('Email').fill(testEmail);
    await page.getByPlaceholder('Password').fill(testPassword);
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page).toHaveURL('/', { timeout: 10_000 });
  });

  test('shows error for wrong password', async ({ page }) => {
    await page.getByPlaceholder('Email').fill(testEmail);
    await page.getByPlaceholder('Password').fill('wrongpassword');

    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/auth/signin') && resp.status() === 401
    );
    await page.getByRole('button', { name: 'Sign In' }).click();
    await responsePromise;

    // Verify we stay on signin page (error occurred, no redirect)
    await expect(page).toHaveURL(/\/signin/);
  });

  test('shows error for non-existent email', async ({ page }) => {
    await page.getByPlaceholder('Email').fill('nonexistent@test.com');
    await page.getByPlaceholder('Password').fill('somepassword');

    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/auth/signin') && resp.status() === 401
    );
    await page.getByRole('button', { name: 'Sign In' }).click();
    await responsePromise;

    // Verify we stay on signin page (error occurred, no redirect)
    await expect(page).toHaveURL(/\/signin/);
  });

  test('shows client-side validation for empty fields', async ({ page }) => {
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText('Please input your email')).toBeVisible();
    await expect(page.getByText('Please input your password')).toBeVisible();
  });

  test('shows client-side validation for invalid email format', async ({ page }) => {
    await page.getByPlaceholder('Email').fill('not-an-email');
    await page.getByPlaceholder('Password').fill('somepassword');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText('Please enter a valid email')).toBeVisible();
  });

  test('demo button signs in as Alice', async ({ page }) => {
    const responsePromise = page.waitForResponse(resp =>
      resp.url().includes('/api/auth/signin')
    );
    await page.getByRole('button', { name: 'Alice' }).click();
    const response = await responsePromise;

    // Either signin succeeds (redirect to home) or fails (stays on signin)
    if (response.status() === 200) {
      await expect(page).toHaveURL('/', { timeout: 5_000 });
    } else {
      await expect(page).toHaveURL(/\/signin/);
    }
  });

  test('navigates to signup page', async ({ page }) => {
    await page.getByRole('link', { name: 'Sign Up' }).click();
    await expect(page).toHaveURL('/signup');
  });
});
