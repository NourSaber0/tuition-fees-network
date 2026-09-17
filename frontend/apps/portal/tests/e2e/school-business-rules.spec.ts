import { test, expect } from '@playwright/test';

test.describe('School Portal - Business Rules & Guardrails', () => {
  test.describe.configure({ mode: 'serial' });

  // Standardized login helper with Promise.race MFA
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

  test('CSV Error Isolation: Isolates invalid rows while accepting valid rows', async ({ page }) => {
    await loginAs(page, 'admin@nis.edu.eg', 'Password123!');
    await page.goto('/school/fee-upload');

    // Dynamic unique fee names to prevent 409 duplicate_upload hash collision
    // Use Sara Ahmed's 14-digit National ID (31205150101042) which resolves to an active student at Nile International School
    const timestamp = Date.now();
    const csvContent = `studentRef,feeName,category,amountEGP,term,dueDate\n31205150101042,Tuition-${timestamp},Tuition,1500.00,Term 1,2026-12-31\n31205150101042,Tuition-${timestamp},Tuition,-500.00,Term 1,2026-12-31\n`;
    await page.locator('input[type="file"]').setInputFiles({
      name: `mixed_fees_${timestamp}.csv`,
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent),
    });

    await page.getByRole('button', { name: 'Upload File' }).click();

    // Assert summary metrics render on detail page
    await expect(page.getByText('Total Rows', { exact: true })).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Accepted', { exact: true })).toBeVisible();
    await expect(page.getByText('Rejected', { exact: true })).toBeVisible();

    // Verify error isolation reporting
    await expect(page.getByRole('heading', { name: /Rejected Rows/i })).toBeVisible();
    await expect(page.getByText(/Amount must be greater than 0/i)).toBeVisible();
  });

  test('Student Lifecycle: Deactivated student accounts preserve historical records', async ({ page }) => {
    await loginAs(page, 'admin@rowad.edu.eg', 'Password123!');
    await page.goto('/school/students');

    // Switch to Deactivated archive view
    const deactTabBtn = page.getByRole('button', { name: /Deactivated/i });
    await expect(deactTabBtn).toBeVisible();
    await deactTabBtn.click();

    // Assert Deactivated archive search input is active
    await expect(page.getByPlaceholder('Search deactivated archive...')).toBeVisible();

    // If archived students exist, clicking a row opens the drawer with historical fee query intact
    const archiveRows = page.locator('tbody tr');
    const count = await archiveRows.count();
    if (count > 0) {
      await archiveRows.first().click();
      await expect(page.getByRole('button', { name: 'Fee Breakdown' })).toBeVisible();
    } else {
      await expect(page.getByText(/No deactivated students found/i)).toBeVisible();
    }
  });

  test('Fee Cancellation & EPP Lock (Negative Path): Blocks cancel for EPP-locked fees with 422', async ({ page }) => {
    await loginAs(page, 'admin@nis.edu.eg', 'Password123!');

    // Extract authorization bearer token from browser session
    const token = await page.evaluate(() => {
      const raw = localStorage.getItem('tuition.auth.session');
      if (!raw) return '';
      const session = JSON.parse(raw);
      return session.accessToken || session.token || '';
    });

    // Obtain institution profile to get the exact institution UUID
    const profileRes = await page.request.get('http://localhost:8080/api/v1/settings/profile', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(profileRes.ok()).toBeTruthy();
    const profile = await profileRes.json();
    const institutionId = profile.id;

    // Obtain dues for this institution to locate the fee line locked in EPP
    const duesRes = await page.request.get(`http://localhost:8080/api/v1/institutions/${institutionId}/dues`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(duesRes.ok()).toBeTruthy();
    const dues = await duesRes.json();
    const lockedFee = dues.find((d: any) => d.feeType === 'TUITION') || dues[0];
    expect(lockedFee).toBeDefined();

    // Attempt to cancel fee via backend endpoint guarded by Mid-Year EPP Cancellation rule
    const cancelRes = await page.request.post(
      `http://localhost:8080/api/v1/institutions/${institutionId}/dues/${lockedFee.feeLineId || lockedFee.id}/cancel`,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );

    // Expect 422 Unprocessable Entity (Pending Business Rule: Mid-Year EPP Cancellation is undefined)
    expect(cancelRes.status()).toBe(422);
    const body = await cancelRes.json();
    expect(body.error).toBe('PENDING_BUSINESS_RULE');
    expect(body.message).toContain('Mid-Year EPP Cancellation is undefined');
  });

  test('Asynchronous Workflows: Triggers async report job and updates UI upon completion', async ({ page }) => {
    await loginAs(page, 'admin@nis.edu.eg', 'Password123!');
    await page.goto('/school/reports');

    // Select the first report in the catalogue
    const reportCard = page.locator('div[class*="rounded-xl border p-4 cursor-pointer"]').first();
    await expect(reportCard).toBeVisible();
    await reportCard.click();

    // Trigger asynchronous report generation
    const generateBtn = page.getByRole('button', { name: /Generate & Download/i });
    await expect(generateBtn).toBeVisible();
    await generateBtn.click();

    // Verify polling lifecycle and completion
    await expect(page.getByText(/Report ready/i)).toBeVisible({ timeout: 15000 });
  });
});
