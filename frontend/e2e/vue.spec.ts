import { test, expect } from '@playwright/test'

test('shows login when not authenticated', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByTestId('login-view')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'SARV' })).toBeVisible()
})