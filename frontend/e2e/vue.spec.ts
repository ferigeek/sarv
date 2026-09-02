import { test, expect } from '@playwright/test'

test('renders the Sarv boot shell', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByTestId('app-shell')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'SARV' })).toBeVisible()
})