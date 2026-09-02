import { addCollection } from '@iconify/vue/offline'
import type { IconifyIcon, IconifyJSON } from '@iconify/types'

/* Pixelarticons (MIT, Gerrit Halfmann) vendored locally so the app never
   depends on the Iconify CDN at runtime. Add icons here as the UI needs them. */
const icons: Record<string, IconifyIcon> = {
  smile: {
    body: '<path fill="currentColor" d="M6 20h12v2H6zM6 2h12v2H6zm12 2h2v2h-2zM4 4h2v2H4zm0 14h2v2H4zm14 0h2v2h-2zM2 6h2v12H2zm18 0h2v12h-2zM7 13h2v2H7zm2 2h6v2H9zm6-2h2v2h-2zM8 8h2v2H8zm6 0h2v2h-2z"/>',
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