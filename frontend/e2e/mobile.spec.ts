import { test, expect } from '@playwright/test'

async function expectNoHorizontalOverflow(page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
  expect(overflow).toBeLessThanOrEqual(1)
}

test('login view fits a 390px mobile viewport without horizontal scroll', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await expect(page.getByTestId('login-view')).toBeVisible()
  await expect(page.getByTestId('login-submit')).toBeVisible()
  await expectNoHorizontalOverflow(page)
})

test('register view fits a 360px mobile viewport without horizontal scroll', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await page.goto('/register')
  await expect(page.getByTestId('register-view')).toBeVisible()
  await expect(page.getByTestId('register-submit')).toBeVisible()
  await expectNoHorizontalOverflow(page)
})
