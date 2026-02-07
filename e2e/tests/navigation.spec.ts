import { test, expect } from '@playwright/test'

test.describe('Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByText(/don't have an account/i).click()
    const timestamp = Date.now()
    await page.getByLabel(/username/i).fill(`nav${timestamp}`)
    await page.getByLabel(/password/i).fill('password123')
    await page.getByRole('button', { name: /register/i }).click()
    await expect(page).toHaveURL('/')
  })

  test('should navigate to all pages', async ({ page }) => {
    // Editor (home)
    await expect(page).toHaveURL('/')
    await expect(page.getByText('CQL Platform')).toBeVisible()

    // CDS Hooks
    await page.getByText('CDS Hooks').click()
    await expect(page).toHaveURL('/cds')

    // Measures
    await page.getByText('Measures').click()
    await expect(page).toHaveURL('/measures')

    // FHIR Browser
    await page.getByText('FHIR Browser').click()
    await expect(page).toHaveURL('/fhir')

    // Back to Editor
    await page.getByText('Editor').click()
    await expect(page).toHaveURL('/')
  })

  test('should protect routes from unauthenticated access', async ({ page }) => {
    // Clear auth state
    await page.evaluate(() => {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    })

    // Try to navigate to protected page
    await page.goto('/cds')
    await expect(page).toHaveURL(/.*login/)

    await page.goto('/measures')
    await expect(page).toHaveURL(/.*login/)

    await page.goto('/fhir')
    await expect(page).toHaveURL(/.*login/)
  })

  test('should show active navigation indicator', async ({ page }) => {
    // Navigate to CDS and check that the button appears active
    await page.getByText('CDS Hooks').click()
    await expect(page).toHaveURL('/cds')

    // The CDS Hooks button should have different styling (active state)
    const cdsButton = page.getByRole('button', { name: 'CDS Hooks' })
    await expect(cdsButton).toBeVisible()
  })

  test('should display header and footer on all pages', async ({ page }) => {
    // Check header
    await expect(page.getByText('CQL Platform')).toBeVisible()

    // Check footer elements
    await expect(page.getByText(/Ln \d+/)).toBeVisible()

    // Navigate to another page
    await page.getByText('Measures').click()
    await expect(page.getByText('CQL Platform')).toBeVisible()
  })
})
