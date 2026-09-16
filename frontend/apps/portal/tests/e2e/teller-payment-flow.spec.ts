import { test, expect } from '@playwright/test';

test.describe('Teller Payment Flow', () => {
  test('should lookup customer by National ID and process payment', async ({ page }) => {
    // 1. Authentication
    await page.goto('/login');
    await expect(page.getByText('Sign in to your account')).toBeVisible();

    // Fill login form
    await page.getByPlaceholder('you@example.com').fill('ahmed.ops@cib.eg');
    await page.getByPlaceholder('********').fill('Password123!');
    
    page.on('response', response => console.log('Response:', response.url(), response.status()));
    
    await page.getByRole('button', { name: 'Sign in' }).click();

    // Fill MFA
    await expect(page.getByText('Two-factor verification')).toBeVisible();
    
    // Fill the 6 OTP inputs
    const otpInputs = page.locator('input[type="text"]');
    await otpInputs.nth(0).fill('1');
    await otpInputs.nth(1).fill('2');
    await otpInputs.nth(2).fill('3');
    await otpInputs.nth(3).fill('4');
    await otpInputs.nth(4).fill('5');
    await otpInputs.nth(5).fill('6');

    await page.getByRole('button', { name: 'Verify & Sign In' }).click();

    // Verify successful login
    await expect(page).toHaveURL(/\/bank\/dashboard/);
    await expect(page.getByText('Dashboard').first()).toBeVisible();

    // 2. Customer Lookup via Transactions Page
    await page.goto('/bank/transactions');
    
    // Click "Process Payment" to open the wizard
    await page.getByRole('button', { name: 'Process Payment' }).click();
    await expect(page.getByText('Process Customer Payment')).toBeVisible();

    // Enter National ID
    await page.getByPlaceholder('14-digit National ID').fill('29511020204536');
    await page.getByRole('button', { name: 'Search' }).click();

    // Verify lookup succeeds and shows correct customer name
    await expect(page.getByText('Ahmed Tarek Mahmoud')).toBeVisible();

    // 3. Fee Selection & Payment
    const isSettled = await page.getByText('No Outstanding Fees').isVisible({ timeout: 3000 }).catch(() => false);

    if (isSettled) {
      await expect(page.getByText('All educational and institutional fees are completely settled')).toBeVisible();
    } else {
      const checkbox = page.locator('input[type="checkbox"]').first();
      await checkbox.check();

      // Proceed to Step 3 (Payment Amount)
      await page.getByRole('button', { name: 'Continue to Payment →' }).click();
      await expect(page.getByText('Payment Amount')).toBeVisible();
      
      // Proceed to Step 4 (Review & Payment Method)
      await page.getByRole('button', { name: 'Continue to Review →' }).click();
      await expect(page.getByText('Review & Confirm')).toBeVisible();

      // Select the active Mastercard in the list
      await page.getByText('Mastercard Credit').click();
      
      // Process the payment
      await page.getByRole('button', { name: 'Process Payment' }).click();

      // 5. Confirmation
      await expect(page.getByText('Payment Successful')).toBeVisible();
      await expect(page.getByText('Transaction Ref.')).toBeVisible();
    }
  });
});
