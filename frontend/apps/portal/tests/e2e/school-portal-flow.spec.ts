import { test, expect } from '@playwright/test';

test.describe('School Portal Navigation & Smoke Tests', () => {
  test('should authenticate as School Admin and navigate through all modules', async ({ page }) => {
    // 1. Authentication
    await page.goto('/login');
    await expect(page.getByText('Sign in to your account')).toBeVisible();

    await page.getByPlaceholder('you@example.com').fill('admin@rowad.edu.eg');
    await page.getByPlaceholder('********').fill('Password123!');
    await page.getByRole('button', { name: 'Sign in' }).click();

    // MFA Verification
    await expect(page.getByText('Two-factor verification')).toBeVisible();
    const otpInputs = page.locator('input[type="text"]');
    await otpInputs.nth(0).fill('1');
    await otpInputs.nth(1).fill('2');
    await otpInputs.nth(2).fill('3');
    await otpInputs.nth(3).fill('4');
    await otpInputs.nth(4).fill('5');
    await otpInputs.nth(5).fill('6');

    await page.getByRole('button', { name: 'Verify & Sign In' }).click();

    // Land on School Dashboard
    await expect(page).toHaveURL(/\/school\/dashboard/);
    await expect(page.getByText('Al-Rowad Language School').first()).toBeVisible();

    // 2. Students Module
    await page.goto('/school/students');
    await expect(page.getByText('Students').first()).toBeVisible();

    // 3. Fee Management Module
    await page.goto('/school/fee-management');
    await expect(page.getByText('Fees').first()).toBeVisible();

    // 4. Payments Module (View-Only)
    await page.goto('/school/payments');
    await expect(page.getByText('Payments').first()).toBeVisible();

    // 5. Reconciliation Module (View-Only)
    await page.goto('/school/reconciliation');
    await expect(page.getByText('view-only').first()).toBeVisible();

    // 6. Reports Module
    await page.goto('/school/reports');
    await expect(page.getByText('Reports').first()).toBeVisible();

    // 7. Notifications Module
    await page.goto('/school/notifications');
    await expect(page.getByText('Notifications').first()).toBeVisible();

    // 8. School Users Module
    await page.goto('/school/users');
    await expect(page.getByText('School Users').first()).toBeVisible();

    // 9. Settings Module
    await page.goto('/school/settings');
    await expect(page.getByText('Settings').first()).toBeVisible();
  });
});
