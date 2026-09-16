import { test, expect } from '@playwright/test';

test.describe('Bank Back-Office Portal - Core Workflows & Guardrails', () => {
  test.describe.configure({ mode: 'serial' });

  // Standardized login helper with Promise.race MFA
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

  test('3-Way Reconciliation: Dashboard aggregates Gateway, Ledger, and Core banking matches', async ({ page }) => {
    await loginAs(page, 'recon@cib.eg', 'Recon123!');
    await page.goto('/bank/reconciliation');

    // 1. Verify summary KPI dashboard cards
    await expect(page.getByRole('paragraph').filter({ hasText: /^Matched$/ })).toBeVisible();
    await expect(page.getByRole('paragraph').filter({ hasText: /^Pending$/ })).toBeVisible();
    await expect(page.getByRole('paragraph').filter({ hasText: /^Open Exceptions$/ })).toBeVisible();
    await expect(page.getByRole('paragraph').filter({ hasText: /^Total Transactions$/ })).toBeVisible();

    // 2. Verify Reconciliation Runs table rendering
    const runsTable = page.locator('table');
    await expect(runsTable).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Institution' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Bank Amount' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'System Amount' })).toBeVisible();

    // 3. Click first reconciliation run row to open detail modal
    const firstRow = page.locator('tbody tr').first();
    await expect(firstRow).toBeVisible();
    await firstRow.click();

    // 4. Assert modal title and 3-way reconciliation amounts
    const modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: 'Reconciliation Run Detail', exact: true })).toBeVisible();
    await expect(modal.getByText(/Bank Amount:/i)).toBeVisible();
    await expect(modal.getByText(/System Amount:/i)).toBeVisible();
  });

  test('EPP Financing: Converts credit card transaction to 12m EPP and blocks debit card BINs', async ({ page }) => {
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
        'Idempotency-Key': `IDEMP-EPP-${Date.now()}`,
      },
      data: {
        nationalId: '29805150101023',
        feeIds: [feeId],
        amountEGP: 5000,
        method: 'CREDIT_CARD',
        sourceId: 'card_mona_visa',
        creditPaymentType: 'full',
      },
    });

    await page.goto('/bank/epp');

    // Open Create EPP Plan wizard
    await page.getByRole('button', { name: 'Create EPP Plan' }).click();
    const modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    await expect(modal.getByRole('heading', { name: 'Create EPP Plan', exact: true })).toBeVisible();

    // Step 1: Select newly created payment from search results
    const paymentButton = modal.locator('.divide-y button').first();
    await expect(paymentButton).toBeVisible();
    await paymentButton.click();

    // Step 2: Negative Path - Fintech Paranoia Guardrail #3: Debit card rejection
    const cardInput = modal.getByPlaceholder('XXXX XXXX XXXX XXXX');
    await expect(cardInput).toBeVisible();
    // 5078 debit card BIN prefix
    await cardInput.fill('5078 1234 5678 9012');
    await modal.getByRole('button', { name: 'Validate Card' }).click();

    // Assert debit card is blocked and Not eligible error is displayed
    await expect(modal.getByText('Not eligible', { exact: true })).toBeVisible();
    await expect(modal.getByRole('button', { name: 'Continue' })).toHaveCount(0);

    // Step 2: Positive Path - Eligible credit card
    await modal.getByRole('button', { name: 'Try Different Card' }).click();
    await cardInput.fill('4111 1111 1111 1111');
    await modal.getByRole('button', { name: 'Validate Card' }).click();
    await expect(modal.getByText(/eligible for EPP/i)).toBeVisible();
    await modal.getByRole('button', { name: /Continue/i }).click();

    // Step 3: Plan Details - Choose 12m Tenor
    await expect(modal.getByText('Step 3 of 4 — Plan Details')).toBeVisible();
    await modal.getByRole('button', { name: '12m' }).click();
    await expect(modal.getByText('Monthly Installment')).toBeVisible();
    await modal.getByRole('button', { name: /Review Plan/i }).click();

    // Step 4: Review & Create Plan
    await expect(modal.getByText('Step 4 of 4 — Review & Confirm')).toBeVisible();
    await expect(modal.getByText('12 months')).toBeVisible();
    await modal.getByRole('button', { name: 'Create EPP Plan' }).click();

    // Assert successful creation
    await expect(modal.getByText(/EPP Plan Created/i)).toBeVisible({ timeout: 10000 });
  });

  test('Global Audit Log: Search queries return privacy-masked records rather than plaintext National IDs', async ({ page }) => {
    await loginAs(page, 'mohamed.ali@cibeg.com', 'CIB@2026');

    // Extract authorization bearer token to seed a customer fees lookup record
    const token = await page.evaluate(() => {
      const raw = localStorage.getItem('tuition.auth.session');
      if (!raw) return '';
      const session = JSON.parse(raw);
      return session.accessToken || session.token || '';
    });

    // Execute lookup to ensure CUSTOMER_FEES_SEARCH entry is logged
    await page.request.get('http://localhost:8080/api/v1/customers/fees?nationalId=29805150101023', {
      headers: { Authorization: `Bearer ${token}` },
    });

    await page.goto('/bank/audit-logs');

    // Assert audit log page is loaded
    await expect(page.getByText('Read-only · Audit records are tamper-evident')).toBeVisible();

    // Search for customer fees searches
    const searchInput = page.getByPlaceholder('User, action, entity or ID…');
    await expect(searchInput).toBeVisible();
    await searchInput.fill('CUSTOMER_FEES_SEARCH');

    // Verify search filter activates
    await expect(page.getByText('"CUSTOMER_FEES_SEARCH"')).toBeVisible();

    // Assert audit log table contains masked entity/resource values (e.g. 298*******1023)
    const auditRows = page.locator('tbody tr');
    await expect(auditRows.first()).toBeVisible();
    await expect(page.getByText('298*******1023').first()).toBeVisible();

    // Verify that plaintext National ID is NEVER exposed in the rendered table text
    const tableText = await page.locator('table').innerText();
    expect(tableText).not.toContain('29805150101023');

    // Expand first entry to verify masked entity detail
    await auditRows.first().click();
    await expect(page.getByText('Entry ID')).toBeVisible();
  });
});
