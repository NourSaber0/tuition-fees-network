import { test, expect } from '@playwright/test';

test.describe('Bank Back Office Portal - RBAC & Walkthroughs', () => {
  // Execute sequentially to preserve shared in-memory database state
  test.describe.configure({ mode: 'serial' });

  // Resilient login helper with Promise.race for conditional MFA
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

  test.describe('Happy Paths', () => {

    test('Bank Admin: Modify EPP Configs & Create Ops User', async ({ page }) => {
      await loginAs(page, 'mohamed.ali@cibeg.com', 'CIB@2026');

      // EPP Settings
      await page.goto('/bank/settings');
      await page.getByRole('button', { name: 'EPP Configuration' }).click();
      await page.locator('label:text-is("12-month rate") + div input').fill('15');
      await page.locator('label:text-is("18-month rate") + div input').fill('18');
      await page.getByRole('button', { name: 'Save Changes' }).click();
      await expect(page.getByText('Saved')).toBeVisible();

      // Create Bank Ops User
      await page.goto('/bank/users');
      await page.getByRole('button', { name: 'Add User' }).click();
      await page.getByPlaceholder('e.g. Ahmed Mohamed').fill('Ahmed Ops');
      const uniqueEmail = `ahmed.ops.${Date.now()}@cib.eg`;
      await page.getByPlaceholder('email@cibeg.com').fill(uniqueEmail);
      await page.locator('label:has-text("Role") + select').selectOption('Operations');
      await page.getByRole('button', { name: 'Add Employee' }).click();
      await expect(page.getByText('added successfully')).toBeVisible();
    });

    test('Bank Operations: Process OTC Payment & Review Onboarding', async ({ page }) => {
      await loginAs(page, 'ahmed.ops@cib.eg');

      // Teller Desk
      await page.goto('/bank/transactions');
      await page.getByRole('button', { name: 'Process Payment' }).click();
      await expect(page.getByText('Process Customer Payment')).toBeVisible();

      // Customer Lookup by Mona's National ID (State Isolated for OTC Walkthrough)
      await page.getByPlaceholder('14-digit National ID').fill('29805150101023');
      await page.getByRole('button', { name: 'Search' }).click();
      await expect(page.getByText('Mona Samir Abdelrahman')).toBeVisible();

      // Proceed to payment amount
      const continueBtn = page.getByRole('button', { name: /Continue to Payment/i });
      await expect(continueBtn).toBeVisible();
      await continueBtn.click();
      await expect(page.getByText('Payment Amount')).toBeVisible();

      // Process a partial payment (EGP 500)
      const amountInput = page.getByPlaceholder('Enter amount…');
      await amountInput.clear();
      await amountInput.fill('500');

      // Proceed to review
      await page.getByRole('button', { name: /Continue to Review/i }).click();
      await expect(page.getByText('Review & Confirm')).toBeVisible();

      // Submit payment
      await page.getByRole('button', { name: 'Process Payment' }).click();
      await expect(page.getByText('Payment Successful')).toBeVisible();

      // Onboarding Review
      await page.goto('/bank/schools');
      await expect(page.getByPlaceholder('Search by name, code, or city')).toBeVisible();
      await page.locator('tbody tr').first().click();
      await page.getByRole('button', { name: 'Application' }).click();
      // Scoped locator avoids strict mode ambiguity across school info cards
      await expect(page.locator('dl').getByText('Registration Number').first()).toBeVisible();
    });

    test('Bank Finance: Quote Simulator & Download Settlement Report', async ({ page }) => {
      await loginAs(page, 'finance@cib.eg', 'Finance123!');

      // Extract authorization bearer token to seed a fresh credit card payment
      const token = await page.evaluate(() => {
        const raw = localStorage.getItem('tuition.auth.session');
        if (!raw) return '';
        const session = JSON.parse(raw);
        return session.accessToken || session.token || '';
      });

      // Obtain an active fee for Mona to process an isolated partial payment
      const duesRes = await page.request.get('http://localhost:8080/api/v1/customers/fees?nationalId=29805150101023', {
        headers: { Authorization: `Bearer ${token}` },
      });
      const duesData = await duesRes.json();
      const feeId = duesData.fees?.find((f: any) => f.remainingEGP >= 5000)?.id || duesData.fees[0].id;

      // Settle 5000 EGP partial payment via CIB credit card (card_mona_visa)
      await page.request.post('http://localhost:8080/api/v1/payments', {
        headers: {
          Authorization: `Bearer ${token}`,
          'Idempotency-Key': `IDEMP-EPP-RBAC-${Date.now()}`,
        },
        data: {
          nationalId: '29805150101023',
          feeIds: [feeId],
          amountEGP: 5000,
          method: 'CREDIT_CARD',
          sourceId: 'card_mona_visa'
        },
      });

      // EPP Plans
      await page.goto('/bank/epp');
      await page.getByRole('button', { name: 'Create EPP Plan' }).click();

      // Step 1: Payment Search (scoped inside dialog modal)
      const eppModal = page.getByRole('dialog');
      await expect(eppModal.getByText('Step 1 of 4 — Find Payment')).toBeVisible();
      await eppModal.locator('.divide-y button').first().click();

      // Step 2: Validate Card
      await eppModal.getByPlaceholder('XXXX XXXX XXXX XXXX').fill('4111 1111 1111 1111');
      await eppModal.getByRole('button', { name: 'Validate Card' }).click();
      await expect(eppModal.getByText(/eligible for EPP/i)).toBeVisible();
      await eppModal.getByRole('button', { name: /Continue/i }).click();

      // Step 3: Quote Simulator (Details)
      await expect(eppModal.getByText('Step 3 of 4 — Plan Details')).toBeVisible();
      await eppModal.locator('input[type="number"]').fill('10000');
      await eppModal.getByRole('button', { name: '12m' }).click();
      await expect(eppModal.getByText('Monthly Installment')).toBeVisible();

      // Step 4: Review and Book
      await eppModal.getByRole('button', { name: /Review Plan/i }).click();
      await eppModal.getByRole('button', { name: 'Create EPP Plan' }).click();
      await expect(eppModal.getByText(/EPP Plan Created/i)).toBeVisible({ timeout: 10000 });

      // Settlement Report
      await page.goto('/bank/reports');
      await page.getByText('Payments Report').first().click();
      await page.getByRole('button', { name: /Generate & Download/i }).click();
      await expect(page.getByText(/Report ready/i)).toBeVisible();
    });

    test('Bank Reconciliation: View Runs & Assign Exception', async ({ page }) => {
      await loginAs(page, 'recon@cib.eg', 'Recon123!');

      await page.goto('/bank/reconciliation');
      await expect(page.getByRole('button', { name: 'Reconciliation Runs' })).toBeVisible();

      // View Exceptions
      await page.getByRole('button', { name: /Exceptions/i }).click();
      await page.locator('tbody tr').first().click();

      // Exception Detail Modal
      const detailModal = page.getByRole('dialog').filter({ hasText: 'Exception Detail' });
      await expect(detailModal).toBeVisible();

      // Click Assign in Detail Modal
      await detailModal.getByRole('button', { name: 'Assign' }).click();

      // Assign Modal
      const assignModal = page.getByRole('dialog').filter({ hasText: 'Assign Exception' });
      await expect(assignModal).toBeVisible();
      await assignModal.getByPlaceholder('Type or pick a name').fill('Layla Reconciliation');
      await assignModal.getByRole('button', { name: 'Assign' }).click();
      await expect(page.getByText(/Assigned to Layla Reconciliation/i)).toBeVisible();
    });

  });

  test.describe('Negative Paths (Security & Guardrails)', () => {

    test('Overpayment Guardrail: Prevent processing amount > remainingAmount', async ({ page }) => {
      await loginAs(page, 'ahmed.ops@cib.eg');

      // STATE ISOLATION: Ahmed Tarek (29511020204536) has untouched 18,000 EGP fee dues
      await page.goto('/bank/transactions');
      await page.getByRole('button', { name: 'Process Payment' }).click();
      await page.getByPlaceholder('14-digit National ID').fill('29511020204536');
      await page.getByRole('button', { name: 'Search' }).click();
      await expect(page.getByText('Ahmed Tarek Mahmoud')).toBeVisible();

      // Proceed to payment amount step
      const continueBtn = page.getByRole('button', { name: /Continue to Payment/i });
      await expect(continueBtn).toBeVisible();
      await continueBtn.click();
      await expect(page.getByText('Payment Amount')).toBeVisible();

      // Input an amount strictly exceeding remaining balance
      const amountInput = page.getByPlaceholder('Enter amount…');
      await amountInput.clear();
      await amountInput.fill('99999999');

      // Click Continue to Review and assert visibility of the specific error
      await page.getByRole('button', { name: /Continue to Review/i }).click();
      await expect(page.getByText(/Amount cannot exceed the remaining balance/i)).toBeVisible();
    });

    test('EPP Debit Card Block', async ({ page }) => {
      await loginAs(page, 'finance@cib.eg', 'Finance123!');
      await page.goto('/bank/epp');

      await page.getByRole('button', { name: 'Create EPP Plan' }).click();
      const eppModal = page.getByRole('dialog');
      await eppModal.locator('.divide-y button').first().click();

      await eppModal.getByPlaceholder('XXXX XXXX XXXX XXXX').fill('4000 0566 5566 5556');
      await eppModal.getByRole('button', { name: 'Validate Card' }).click();
      // Strict exact match eliminates duplicate substring violations
      await expect(eppModal.getByText('Not eligible', { exact: true })).toBeVisible();
      await expect(eppModal.getByText(/Debit cards/i)).toBeVisible();
      // Assert Continue button is disabled or absent
      await expect(eppModal.getByRole('button', { name: /Continue/i })).toHaveCount(0);
    });

    test('Idempotency Tamper Protection (409 Conflict)', async ({ page }) => {
      await loginAs(page, 'ahmed.ops@cib.eg');
      await page.goto('/bank/transactions');

      // Open wizard with Ahmed Tarek
      await page.getByRole('button', { name: 'Process Payment' }).click();
      await page.getByPlaceholder('14-digit National ID').fill('29511020204536');
      await page.getByRole('button', { name: 'Search' }).click();
      await expect(page.getByText('Ahmed Tarek Mahmoud')).toBeVisible();

      // Proceed to Step 3
      const continueBtn = page.getByRole('button', { name: /Continue to Payment/i });
      await expect(continueBtn).toBeVisible();
      await continueBtn.click();
      await expect(page.getByText('Payment Amount')).toBeVisible();

      // Input partial amount
      const amountInput = page.getByPlaceholder('Enter amount…');
      await amountInput.clear();
      await amountInput.fill('200');

      // Proceed to Step 4
      await page.getByRole('button', { name: /Continue to Review/i }).click();
      await expect(page.getByText('Review & Confirm')).toBeVisible();

      // Intercept payments API to simulate 409 conflict
      await page.route('**/payments', (route) => {
        route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            error: 'IDEMPOTENCY_CONFLICT',
            message: 'A different request was already processed',
          }),
        });
      });

      await page.getByRole('button', { name: 'Process Payment' }).click();
      await expect(page.getByText('A different request was already processed')).toBeVisible();
    });

    test('RBAC Restrictions for Bank Operations', async ({ page }) => {
      await loginAs(page, 'ahmed.ops@cib.eg');

      // Nav links for Audit Logs and System Settings are hidden from Bank Operations
      await expect(page.locator('nav').getByRole('link', { name: 'Audit Logs' })).toHaveCount(0);
      await expect(page.locator('nav').getByRole('link', { name: 'System Settings' })).toHaveCount(0);
    });

  });
});
