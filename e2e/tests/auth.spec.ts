import { test, expect } from '@playwright/test'

test.describe('Authentication', () => {
  test('should redirect to login when not authenticated', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/.*login/)
  })

  test('should register a new user', async ({ page }) => {
    await page.goto('/login')

    // Switch to register mode
    await page.getByText(/don't have an account/i).click()

    // Fill registration form
    const timestamp = Date.now()
    await page.getByLabel(/username/i).fill(`e2euser${timestamp}`)
    await page.getByLabel(/password/i).fill('password123')
    await page.getByLabel(/email/i).fill(`e2e${timestamp}@test.com`)

    // Submit
    await page.getByRole('button', { name: /register/i }).click()

    // Should redirect to editor page
    await expect(page).toHaveURL('/')
    await expect(page.getByText('CQL Platform')).toBeVisible()
  })

  test('should login with credentials', async ({ page }) => {
    // First register
    await page.goto('/login')
    await page.getByText(/don't have an account/i).click()

    const timestamp = Date.now()
    const username = `loginuser${timestamp}`
    await page.getByLabel(/username/i).fill(username)
    await page.getByLabel(/password/i).fill('password123')
    await page.getByRole('button', { name: /register/i }).click()
    await expect(page).toHaveURL('/')

    // Logout (clear storage)
    await page.evaluate(() => {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    })
    await page.goto('/login')

    // Login
    await page.getByLabel(/username/i).fill(username)
    await page.getByLabel(/password/i).fill('password123')
    await page.getByRole('button', { name: /sign in/i }).click()

    await expect(page).toHaveURL('/')
  })

  test('should show error for invalid credentials', async ({ page }) => {
    await page.goto('/login')

    await page.getByLabel(/username/i).fill('nonexistent')
    await page.getByLabel(/password/i).fill('wrongpassword')
    await page.getByRole('button', { name: /sign in/i }).click()

    // Should show error message
    await expect(page.getByRole('alert')).toBeVisible()
  })
})
