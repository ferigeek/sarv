import { addCollection } from '@iconify/vue/offline'
import type { IconifyIcon, IconifyJSON } from '@iconify/types'

/* Pixelarticons (MIT, Gerrit Halfmann) vendored locally so the app never
   depends on the Iconify CDN at runtime. Add icons here as the UI needs them. */
const icons: Record<string, IconifyIcon> = {
  smile: {
    body: '<path fill="currentColor" d="M6 20h12v2H6zM6 2h12v2H6zm12 2h2v2h-2zM4 4h2v2H4zm0 14h2v2H4zm14 0h2v2h-2zM2 6h2v12H2zm18 0h2v12h-2zM7 13h2v2H7zm2 2h6v2H9zm6-2h2v2h-2zM8 8h2v2H8zm6 0h2v2h-2z"/>',
  },
  search: {
    body: '<path fill="currentColor" d="M22 22h-2v-2h2zm-2-2h-2v-2h2zm-6-2H6v-2h8zm4 0h-2v-2h2zM6 16H4v-2h2zm10 0h-2v-2h2zM4 14H2V6h2zm14 0h-2V6h2zM6 6H4V4h2zm10 0h-2V4h2zm-2-2H6V2h8z"/>',
  },
  user: {
    body: '<path fill="currentColor" d="M9 2h6v2H9zm0 8h6v2H9zm6-6h2v6h-2zM7 4h2v6H7zM4 18h2v4H4zm14 0h2v4h-2zM8 14h8v2H8zm-2 2h2v2H6zm10 0h2v2h-2z"/>',
  },
  plus: {
    body: '<path fill="currentColor" d="M13 11h7v2h-7v7h-2v-7H4v-2h7V4h2z"/>',
  },
  users: {
    body: '<path fill="currentColor" d="M5 2h6v2H5zm10 0h4v2h-4zM5 10h6v2H5zm10 0h4v2h-4zm4-6h2v6h-2zm-8 0h2v6h-2zM3 4h2v6H3zM0 18h2v4H0zm14 0h2v4h-2zm8 0h2v4h-2zM4 14h8v2H4zm12 0h4v2h-4zM2 16h2v2H2zm10 0h2v2h-2zm8 0h2v2h-2z"/>',
  },
  image: {
    body: '<g fill="currentColor"><path d="M4 2h16v2H4zm0 18h16v2H4zM2 4h2v16H2zm18 0h2v16h-2zm-4 8h2v2h-2zm-2 2h2v2h-2zm4 0h2v2h-2zm-8 0h2v2h-2zm2 2h2v2h-2zm2 2h2v2h-2z"/><path d="M20 16h2v2h-2zM8 16h2v2H8zm-2 2h2v2H6zM8 6h2v2H8zM6 8h2v2H6zm2 2h2v2H8zm2-2h2v2h-2z"/></g>',
  },
  bookmark: {
    body: '<path fill="currentColor" d="M6 2h12v2H6zM4 4h2v18H4zm14 0h2v18h-2zm-2 16h2v2h-2zm-2-2h2v2h-2zm-8 2h2v2H6zm2-2h2v2H8zm2-2h4v2h-4z"/>',
  },
  logout: {
    body: '<g fill="currentColor"><path d="M8 11h12v2H8zm8-2h2v2h-2z"/><path d="M14 7h2v10h-2zm2 6h2v2h-2zM6 2h12v2H6zm0 18h12v2H6zM4 4h2v16H4zm14 0h2v3h-2zm0 13h2v3h-2z"/></g>',
  },
  'thumbs-up': {
    body: '<path fill="currentColor" d="M2 12h2v8H2zm2 8h14v2H4zm14-4h2v4h-2zm2-4h2v4h-2zm-6-2h6v2h-6zm0-2h2v2h-2zm2-4h2v4h-2zm-2-2h2v2h-2zm-2 2h2v2h-2zm-2 2h2v2h-2zM8 8h2v2H8zm-4 2h4v2H4zm2 2h2v8H6z"/>',
  },
  'thumbs-down': {
    body: '<path fill="currentColor" d="M2 12h2V4H2zm2-8h14V2H4zm14 4h2V4h-2zm2 4h2V8h-2zm-6 2h6v-2h-6zm0 2h2v-2h-2zm2 4h2v-4h-2zm-2 2h2v-2h-2zm-2-2h2v-2h-2zm-2-2h2v-2h-2zm-2-2h2v-2H8zm-4-2h4v-2H4zm2-2h2V4H6z"/>',
  },
  eye: {
    body: '<path fill="currentColor" d="M16 20H8v-2h8zm-8-2H4v-2h4zm12 0h-4v-2h4zM4 16H2v-2h2zm10-6h-2v2h2zm6-2h2v2h-2zm-2-2h2v2h-2zm-2 2h2v2h-2zm-2 2h2v2h-2zM8 8h2v2H8zm-4 2h4v2H4zm2 2h2v8H6z"/>',
  },
  comment: {
    body: '<path fill="currentColor" d="M4 2h16v2H4zm0 14h14v2H4zM2 4h2v12H2zm18 0h2v18h-2zm-2 14h2v2h-2z"/>',
  },
  repeat: {
    body: '<g fill="currentColor"><path d="M17 5h2v2h-2zM5 17h2v2H5zm6-14h2v6h-2zM9 1h2v8H9zm0 8h2v2H9zm10 8H9v2h10zM5 7H3v10h2z"/><path d="M13 15h-2v6h2zm2-2h-2v8h2zm0 8h-2v2h2zM5 5h10v2H5zm14 12h2V7h-2z"/></g>',
  },
  share: {
    body: '<path fill="currentColor" d="M20 22H4v-2h16zM4 20H2v-6h2zm18 0h-2v-6h2zM13 4h2v2h2v2h-4v10h-2V8H7V6h2V4h2V2h2zM9 14H4v-2h5zm11 0h-5v-2h5z"/>',
  },
  home: {
    body: '<path fill="currentColor" d="M10 2h4v2h-4zM8 4h8v2H8zM6 6h12v2H6zM4 8h16v2H4zM2 10h20v2H2zm0 2h2v10H2zm18 0h2v10h-2zM4 20h16v2H4z"/>',
  },
  menu: {
    body: '<path fill="currentColor" d="M2 6h20v2H2zm0 6h20v2H2zm0 6h20v2H2z"/>',
  },
  github: {
    body: '<path fill="currentColor" d="M5 2h4v2H7v2H5V2Zm0 10H3V6h2v6Zm2 2H5v-2h2v2Zm2 2v-2H7v2H3v-2H1v2h2v2h4v4h2v-4h2v-2H9Zm0 0v2H7v-2h2Zm6-12v2H9V4h6Zm4 2h-2V4h-2V2h4v4Zm0 6V6h2v6h-2Zm-2 2v-2h2v2h-2Zm-2 2v-2h2v2h-2Zm0 2h-2v-2h2v2Zm0 0h2v4h-2v-4Z"/>',
  },
}

const collection: IconifyJSON = {
  prefix: 'pixelarticons',
  width: 24,
  height: 24,
  icons,
}

let registered = false

export function registerPixelicons(): void {
  if (registered) return
  registered = true
  addCollection(collection)
}