import { test, expect } from '@playwright/test'

const USER = {
  id: 1,
  username: 'alice',
  displayName: 'Alice',
  bio: null,
  gender: 'FEMALE',
  location: null,
  profilePictureId: null,
  status: 'ACTIVE',
}

function makePosts(n: number) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    userId: 1,
    postCategory: 'NORMAL',
    content: `post number ${i + 1} with enough text to take vertical space `.repeat(4),
    createdAt: '2026-09-02T10:00:00+00:00',
    updatedAt: null,
    mediaId: null,
    repostOfId: null,
    parentId: null,
    viewCount: i,
    likeCount: 0,
    dislikeCount: 0,
  }))
}

async function mockFeed(page, postCount: number) {
  await page.addInitScript(() => {
    localStorage.setItem('sarv.jwt', 'test-jwt')
  })
  await page.route('**/api/users/me', (route) => route.fulfill({ json: USER }))
  await page.route('**/api/feed/recommended*', (route) =>
    route.fulfill({
      json: {
        content: makePosts(postCount),
        page: { size: 20, number: 0, totalElements: postCount, totalPages: 1 },
      },
    }),
  )
  await page.route('**/api/users/*', (route) => route.fulfill({ json: USER }))
  await page.route('**/api/posts/*/reactions', (route) =>
    route.fulfill({ json: { likeCount: 0, dislikeCount: 0, userReaction: 0 } }),
  )
}

async function expectTabsPinnedToFeedTop(page) {
  const tabs = page.getByTestId('feed-tabs')
  const center = page.getByTestId('app-center')
  await expect(tabs).toBeVisible()
  await expect.poll(async () => center.evaluate((el) => el.scrollTop)).toBeGreaterThan(500)
  const tabsBox = await tabs.boundingBox()
  const centerBox = await center.boundingBox()
  expect(tabsBox).not.toBeNull()
  expect(centerBox).not.toBeNull()
  // Tabs must pin flush to the top of the scrolling feed (directly under the
  // top bar on mobile), with no strip of posts visible above them.
  expect(Math.abs(tabsBox!.y - centerBox!.y)).toBeLessThanOrEqual(1)
}

test('feed tabs stick to the top while scrolling on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockFeed(page, 20)
  await page.goto('/')
  await expect(page.getByTestId('feed-list')).toBeVisible()
  await page.getByTestId('app-center').evaluate((el) => {
    el.scrollTop = el.scrollHeight
  })
  await expectTabsPinnedToFeedTop(page)
})

test('feed tabs stick to the top while scrolling on desktop', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await mockFeed(page, 20)
  await page.goto('/')
  await expect(page.getByTestId('feed-list')).toBeVisible()
  await page.getByTestId('app-center').evaluate((el) => {
    el.scrollTop = el.scrollHeight
  })
  await expectTabsPinnedToFeedTop(page)
})
