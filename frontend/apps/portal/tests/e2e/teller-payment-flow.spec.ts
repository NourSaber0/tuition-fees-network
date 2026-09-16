import { test, expect } from '@playwright/test';

test.describe('Teller Payment Flow', () => {
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
        await otpInputs.nth(i).fill((i + 1).toString());
      }
      await page.getByRole('button', { name: 'Verify & Sign In' }).click();
    }

    await expect(page).toHaveURL(/\/bank/);
  };

  test('should lookup customer by National ID and process OTC payment', async ({ page }) => {
    // 1. Authentication as Bank Operations
    await loginAs(page, 'ahmed.ops@cib.eg');

    // 2. Customer Lookup via Transactions Page
    await page.goto('/bank/transactions');
    await page.getByRole('button', { name: 'Process Payment' }).click();
    await expect(page.getByText('Process Customer Payment')).toBeVisible();

    // Lookup citizen Ahmed Tarek Mahmoud (isolated from Mona to avoid concurrent lock contention)
    await page.getByPlaceholder('14-digit National ID').fill('29511020204536');
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(page.getByText('Ahmed Tarek Mahmoud')).toBeVisible();

    // 3. Fee Selection & Partial Amount
    const continueBtn = page.getByRole('button', { name: /Continue to Payment/i });
    await expect(continueBtn).toBeVisible();
    await continueBtn.click();
    await expect(page.getByText('Payment Amount')).toBeVisible();

    // Fill partial amount of EGP 300 to preserve outstanding fee status
    const amountInput = page.getByPlaceholder('Enter amount…');
    await amountInput.clear();
    await amountInput.fill('300');

    // Proceed to Review
    await page.getByRole('button', { name: /Continue to Review/i }).click();
    await expect(page.getByText('Review & Confirm')).toBeVisible();

    // Process payment with pre-selected verified bank payment source
    await page.getByRole('button', { name: 'Process Payment' }).click();

    // Confirmation
    await expect(page.getByText('Payment Successful')).toBeVisible();
    await expect(page.getByText(/Transaction Ref\./i)).toBeVisible();
  });
});
