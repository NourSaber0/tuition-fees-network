import { test, expect } from '@playwright/test';

test.describe('School Portal Navigation & Smoke Tests', () => {
  test.describe.configure({ mode: 'serial' });

  const loginAs = async (page: any, email: string, password: string = 'Password123!') => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill(email);
    await page.getByPlaceholder('********').fill(password);
    await page.getByRole('button', { name: 'Sign In' }).click();

    const mfaHeading = page.getByText('Two-factor verification');
    const directNav = page.waitForURL(/\/school/, { timeout: 6000 }).then(() => 'NAV' as const);
    const mfaShown = mfaHeading.waitFor({ state: 'visible', timeout: 6000 }).then(() => 'MFA' as const);

    const outcome = await Promise.race([
      directNav.catch(() => null),
      mfaShown.catch(() => null),
    ]);

    if (outcome === 'MFA') {
      const otpInputs = page.locator('input[inputMode="numeric"]');
      for (let i = 0; i < 6; i++) {
        await otpInputs.nth(i).fill((i + 1).toString());
      }
      await page.getByRole('button', { name: 'Verify & Sign In' }).click();
    }

    await expect(page).toHaveURL(/\/school/);
  };

  test('should authenticate as School Admin and navigate through all modules', async ({ page }) => {
    // 1. Authentication
    await loginAs(page, 'admin@rowad.edu.eg', 'Password123!');
    await expect(page.getByText('Al-Rowad Language School').first()).toBeVisible();

    // 2. Students Module
    await page.goto('/school/students');
    await expect(page.getByRole('heading', { name: 'Students', exact: true })).toBeVisible();

    // 3. Fee Management Module
    await page.goto('/school/fee-management');
    await expect(page.getByRole('heading', { name: 'Fees', exact: true })).toBeVisible();

    // 4. Payments Module (View-Only)
    await page.goto('/school/payments');
    await expect(page.getByRole('heading', { name: 'Payments', exact: true })).toBeVisible();

    // 5. Reconciliation Module (View-Only)
    await page.goto('/school/reconciliation');
    await expect(page.getByText(/view-only/i).first()).toBeVisible();

    // 6. Reports Module
    await page.goto('/school/reports');
    await expect(page.getByRole('heading', { name: 'Reports', exact: true })).toBeVisible();

    // 7. Notifications Module
    await page.goto('/school/notifications');
    await expect(page.getByRole('heading', { name: 'Notifications', exact: true })).toBeVisible();

    // 8. School Users Module
    await page.goto('/school/users');
    await expect(page.getByRole('heading', { name: 'School Users', exact: true })).toBeVisible();

    // 9. Settings Module
    await page.goto('/school/settings');
    await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible();
  });
});
