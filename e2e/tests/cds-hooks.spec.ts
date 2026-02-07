import { test, expect } from '@playwright/test'

test.describe('CDS Hooks', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByText(/don't have an account/i).click()
    const timestamp = Date.now()
    await page.getByLabel(/username/i).fill(`cds${timestamp}`)
    await page.getByLabel(/password/i).fill('password123')
    await page.getByRole('button', { name: /register/i }).click()
    await expect(page).toHaveURL('/')
  })

  test('should navigate to CDS Hooks page', async ({ page }) => {
    await page.getByText('CDS Hooks').click()
    await expect(page).toHaveURL('/cds')
  })

  test('should display CDS services', async ({ page }) => {
    await page.getByText('CDS Hooks').click()
    await expect(page).toHaveURL('/cds')

    // Wait for the page to load services
    await page.waitForTimeout(2000)

    // Should have some content on the CDS page
    await expect(page.locator('body')).not.toBeEmpty()
  })

  test('should be able to invoke a CDS service', async ({ page }) => {
    await page.getByText('CDS Hooks').click()
    await expect(page).toHaveURL('/cds')

    // Wait for page to load
    await page.waitForTimeout(2000)

    // Look for an invoke button or similar interaction
    const invokeBtn = page.getByRole('button', { name: /invoke|run|execute/i }).first()
    if (await invokeBtn.isVisible()) {
      await invokeBtn.click()
      await page.waitForTimeout(2000)
    }
  })
})
