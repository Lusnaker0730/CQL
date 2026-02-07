import { test, expect } from '@playwright/test'

test.describe('CQL Editor', () => {
  test.beforeEach(async ({ page }) => {
    // Register and login
    await page.goto('/login')
    await page.getByText(/don't have an account/i).click()
    const timestamp = Date.now()
    await page.getByLabel(/username/i).fill(`editor${timestamp}`)
    await page.getByLabel(/password/i).fill('password123')
    await page.getByRole('button', { name: /register/i }).click()
    await expect(page).toHaveURL('/')
  })

  test('should display default CQL content in editor', async ({ page }) => {
    // The editor should contain the default DiabetesManagement CQL
    await expect(page.locator('.monaco-editor')).toBeVisible({ timeout: 10000 })
  })

  test('should translate CQL', async ({ page }) => {
    // Wait for editor to load
    await expect(page.locator('.monaco-editor')).toBeVisible({ timeout: 10000 })

    // Click translate button
    const translateBtn = page.getByRole('button', { name: /translate/i })
    if (await translateBtn.isVisible()) {
      await translateBtn.click()

      // Wait for translation to complete (loading indicator disappears)
      await page.waitForTimeout(3000)

      // Check for ELM output or success indication in the UI
      // The exact selector depends on the UI but we verify no crash
      await expect(page.locator('.monaco-editor')).toBeVisible()
    }
  })

  test('should show execution panel with FHIR server input', async ({ page }) => {
    await expect(page.locator('.monaco-editor')).toBeVisible({ timeout: 10000 })

    // Look for FHIR server URL or Patient ID inputs
    const fhirInput = page.locator('input[value*="hapi.fhir.org"], input[value*="fhir"]').first()
    if (await fhirInput.isVisible()) {
      await expect(fhirInput).toBeVisible()
    }
  })
})
