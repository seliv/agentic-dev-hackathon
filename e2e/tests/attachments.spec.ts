import { test, expect, Page } from '@playwright/test';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';

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

function createTestFile(name: string, content: string): string {
  const tmpDir = os.tmpdir();
  const filePath = path.join(tmpDir, name);
  fs.writeFileSync(filePath, content);
  return filePath;
}

function createTestImage(name: string): string {
  const pngBuffer = Buffer.from([
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
    0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
    0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00,
    0x00, 0x00, 0x02, 0x00, 0x01, 0xE2, 0x21, 0xBC,
    0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
    0x44, 0xAE, 0x42, 0x60, 0x82,
  ]);
  const tmpPath = path.join(os.tmpdir(), name);
  fs.writeFileSync(tmpPath, pngBuffer);
  return tmpPath;
}

test.describe('Attachments', () => {
  test('upload text file and see attachment in message', async ({ browser }) => {
    const id = unique();
    const page = await browser.newPage();
    await signUp(page, `att_e2e1_${id}@test.com`, `att_e2e1_${id}`);

    const roomName = `att-test-${id}`;
    await createRoom(page, roomName);
    await selectRoom(page, roomName);

    const fileName = `test-${id}.txt`;
    const filePath = createTestFile(fileName, 'Hello from test file');

    // The file input is hidden; set files directly
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(filePath);

    // File tag should appear in the input area before sending
    await expect(page.locator('.ant-tag').filter({ hasText: fileName.substring(0, 20) }).first()).toBeVisible({ timeout: 5_000 });

    await page.getByPlaceholder('Type a message...').fill('Here is a file');
    await page.getByRole('button', { name: 'send' }).click();

    // After sending, the attachment should appear in the chat as a link with the filename
    await expect(page.getByText(fileName, { exact: false }).last()).toBeVisible({ timeout: 10_000 });

    fs.unlinkSync(filePath);
    await page.close();
  });

  test('upload image and see thumbnail', async ({ browser }) => {
    const id = unique();
    const page = await browser.newPage();
    await signUp(page, `att_e2e2_${id}@test.com`, `att_e2e2_${id}`);

    const roomName = `img-test-${id}`;
    await createRoom(page, roomName);
    await selectRoom(page, roomName);

    const imageName = `test-image-${id}.png`;
    const imagePath = createTestImage(imageName);

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(imagePath);

    await page.getByPlaceholder('Type a message...').fill('A photo');
    await page.getByRole('button', { name: 'send' }).click();

    // After sending, an img element with the original filename as alt text should appear
    await expect(page.locator(`img[alt="${imageName}"]`)).toBeVisible({ timeout: 10_000 });

    fs.unlinkSync(imagePath);
    await page.close();
  });
});
