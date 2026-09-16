import { test, expect } from '@playwright/test';

test.describe('School Portal - RBAC & Walkthroughs', () => {
  // Sequential execution to prevent state contention on shared backend
  test.describe.configure({ mode: 'serial' });

  // Resilient login helper with Promise.race for conditional MFA
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
        await otpInputs.nth(i).fill((i + 1).toString()); // 123456
      }
      await page.getByRole('button', { name: 'Verify & Sign In' }).click();
    }

    await expect(page).toHaveURL(/\/school/);
  };

  test.describe('Happy Paths', () => {

    test('School Admin: Create Fee & Invite User', async ({ page }) => {
      // Seeded Admin for Al-Rowad Language School
      await loginAs(page, 'admin@rowad.edu.eg', 'Password123!');

      // 1. Fee Management Walkthrough
      await page.goto('/school/fee-management');
      await page.getByRole('button', { name: 'Add Fee' }).click();

      // Scope strictly inside CreateFeeModal to avoid page background date filters
      const feeModal = page.locator('div.fixed').filter({ hasText: 'Add New Fee' });
      await expect(feeModal).toBeVisible();

      // Search student (query length >= 2 triggers search)
      await feeModal.getByPlaceholder('Search by student name or ID...').fill('Youssef');
      const firstStudentOption = feeModal.locator('.p-3.hover\\:bg-gray-50').first();
      await expect(firstStudentOption).toBeVisible();
      await firstStudentOption.click();

      // Fill fee details
      await feeModal.locator('select').first().selectOption({ index: 1 });
      await feeModal.getByPlaceholder('0.00').fill('15000');
      await feeModal.getByPlaceholder('e.g. Tuition - Term 1 2026/27').fill('Tuition 2026/27');
      await feeModal.locator('input[type="date"]').fill('2026-12-31');
      await feeModal.getByRole('button', { name: 'Create Fee' }).click();

      // Assert fee creation modal completed
      await expect(feeModal).toHaveCount(0);

      // 2. Invite School User Walkthrough
      await page.goto('/school/users');
      await page.getByRole('button', { name: 'Add School User' }).click();
      const userModal = page.locator('div.fixed').filter({ hasText: 'Add New School User' });
      await expect(userModal).toBeVisible();
      await userModal.getByPlaceholder('e.g. Dina Fouad').fill('Finance User Test');
      const uniqueEmail = `finance.${Date.now()}@rowad.edu.eg`;
      await userModal.getByPlaceholder('e.g. d.fouad@school.edu.eg').fill(uniqueEmail);
      await userModal.locator('form select').selectOption('School Finance');
      await userModal.getByRole('button', { name: 'Create User' }).click();

      // Assert modal closed
      await expect(userModal).toHaveCount(0);
    });

    test('School Finance: Fee Upload & View Split Allocation', async ({ page }) => {
      // Seeded Finance Admin for Nile International School
      await loginAs(page, 'admin@nis.edu.eg', 'Password123!');

      // 1. Fee Upload with CSV
      await page.goto('/school/fee-upload');
      const csvContent = 'student_id,amount,fee_type,due_date\nSTU-01,1000,TUITION,2026-12-31\nSTU-02,-500,TUITION,2026-12-31\n';
      await page.locator('input[type="file"]').setInputFiles({
        name: 'fees.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(csvContent),
      });

      await page.getByRole('button', { name: 'Upload File' }).click();

      // Assert upload status summary loads
      await expect(page.getByText(/Total Rows|Accepted|Rejected|Upload Details/i).first()).toBeVisible();

      // 2. View Payments Split Allocation
      await page.goto('/school/payments');
      await page.locator('tbody tr').first().click();

      // Scoped detail modal assertions
      const detailModal = page.getByRole('dialog');
      await expect(detailModal).toBeVisible();
      await expect(detailModal.getByText('Payment Detail')).toBeVisible();
      await expect(detailModal.getByText(/Fee Allocation Breakdown/i)).toBeVisible();
      await expect(detailModal.getByRole('columnheader', { name: 'Fee', exact: true })).toBeVisible();
      await expect(detailModal.getByRole('columnheader', { name: 'Allocated' })).toBeVisible();
    });

  });

  test.describe('Negative Paths (Security & Guardrails)', () => {

    test('Zero Refund Policy: No refund buttons exist', async ({ page }) => {
      await loginAs(page, 'admin@nis.edu.eg', 'Password123!');
      await page.goto('/school/payments');

      // Click first payment row to inspect modal
      await page.locator('tbody tr').first().click();
      const detailModal = page.getByRole('dialog');
      await expect(detailModal).toBeVisible();

      // Enforce zero refund policy across the UI
      await expect(detailModal.getByRole('button', { name: /Refund/i })).toHaveCount(0);
      await expect(detailModal.getByRole('button', { name: /Reverse/i })).toHaveCount(0);
      await expect(page.getByRole('button', { name: /Refund/i })).toHaveCount(0);
      await expect(page.getByRole('button', { name: /Reverse/i })).toHaveCount(0);
    });

    test('Cross-Institution Data Bleed: Prevent access to other school resources (403 Forbidden)', async ({ page }) => {
      await loginAs(page, 'admin@rowad.edu.eg', 'Password123!');

      // Extract bearer token from active browser session
      const token = await page.evaluate(() => {
        const raw = localStorage.getItem('tuition.auth.session');
        return raw ? JSON.parse(raw).token : '';
      });

      // Attempt access to an unowned institution's student collection
      const res = await page.request.get(
        'http://localhost:8080/api/v1/institutions/11111111-1111-1111-1111-111111111111/students',
        {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );

      // Verify backend strictly rejects cross-institution bleed
      expect([401, 403]).toContain(res.status());
    });

    test('RBAC Restrictions for School Finance', async ({ page }) => {
      // Mariam Admin has "Finance" role -> mapped to school-finance
      await loginAs(page, 'admin@nis.edu.eg', 'Password123!');

      // Direct URL navigation to School Users module triggers Access Restricted gate
      await page.goto('/school/users');
      await expect(page.getByText('Access Restricted')).toBeVisible();
      await expect(
        page.getByText(/The School Users management module is restricted/i)
      ).toBeVisible();

      // Sidebar navigation items for School Users and Settings are hidden
      await expect(page.locator('nav').getByRole('link', { name: 'School Users' })).toHaveCount(0);
      await expect(page.locator('nav').getByRole('link', { name: 'Settings' })).toHaveCount(0);
    });

  });
});
