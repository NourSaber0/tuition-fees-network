import { test, expect } from '@playwright/test';

test.describe('Additional Scenarios - Edge Cases & Security', () => {
  test.describe.configure({ mode: 'serial' });

  const loginAs = async (page: any, email: string, password: string = 'Password123!') => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill(email);
    await page.getByPlaceholder('********').fill(password);
    await page.getByRole('button', { name: 'Sign In' }).click();

    const mfaHeading = page.getByText('Two-factor verification');
    const directNav = page.waitForURL(/\/bank/, { timeout: 6000 }).then(() => 'NAV' as const);
    const mfaShown = mfaHeading.waitFor({ state: 'visible', timeout: 6000 }).then(() => 'MFA' as const);

    const outcome = await Promise.race([
      directNav.catch(() => null),
      mfaShown.catch(() => null),
    ]);

    if (outcome === 'MFA') {
      const otpInputs = page.locator('input[inputMode="numeric"]');
      for (let i = 0; i < 6; i++) {
        await otpInputs.nth(i).fill((i + 1).toString()); // 123456
      }
      await page.getByRole('button', { name: 'Verify & Sign In' }).click();
    }

    await expect(page).toHaveURL(/\/bank/);
  };

  test('Authentication Lifecycle: Session Timeout & Redirect', async ({ page }) => {
    await loginAs(page, 'mohamed.ali@cibeg.com', 'CIB@2026');
    await expect(page).toHaveURL(/\/bank/);

    // Simulate token expiration by clearing auth storage
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
      document.cookie.split(';').forEach((c) => {
        document.cookie = c.replace(/^ +/, '').replace(/=.*/, '=;expires=' + new Date().toUTCString() + ';path=/');
      });
    });

    // Navigation to authenticated dashboard should redirect to /login
    await page.goto('/bank/dashboard');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByText(/Sign in to your account/i)).toBeVisible();
  });

  test('Login Rate Limiting (429 Too Many Requests)', async ({ page }) => {
    await page.goto('/login');

    // Intercept auth login to simulate 429 HTTP response from backend rate limiter
    await page.route('**/auth/login', (route) => {
      route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'rate_limited',
          message: 'Too many failed login attempts. Please try again later.',
        }),
      });
    });

    await page.getByPlaceholder('you@example.com').fill('ahmed.ops@cib.eg');
    await page.getByPlaceholder('********').fill('WrongPassword!');
    await page.getByRole('button', { name: 'Sign In' }).click();

    // Assert UI displays the rate limit notification
    await expect(page.getByText(/Too many failed login attempts|Please try again later/i)).toBeVisible();
  });
});
