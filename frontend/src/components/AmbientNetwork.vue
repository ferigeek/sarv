<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref<HTMLCanvasElement | null>(null)
let raf = 0
let ro: ResizeObserver | null = null
let mq: MediaQueryList | null = null
let onDprChange: (() => void) | null = null

type Node = {
  x: number
  y: number
  vx: number
  vy: number
  r: number
  phase: number
  pulseSpeed: number
}

type Particle = {
  a: number
  b: number
  t: number
  speed: number
  curvature: number
}

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

onMounted(() => {
  if (typeof navigator !== 'undefined' && /jsdom/i.test(navigator.userAgent)) return
  if (typeof window !== 'undefined' && (window as unknown as { __vitest_worker__?: unknown }).__vitest_worker__ !== undefined) return

  const canvasEl = canvasRef.value
  if (!canvasEl) return
  let ctxEl: CanvasRenderingContext2D | null = null
  try {
    ctxEl = canvasEl.getContext('2d') as CanvasRenderingContext2D | null
  } catch {
    ctxEl = null
  }
  if (!ctxEl) return
  const parentEl = canvasEl.parentElement
  if (!parentEl) return

  const canvas: HTMLCanvasElement = canvasEl
  const ctx: CanvasRenderingContext2D = ctxEl
  const parent: HTMLElement = parentEl

  const reduced = prefersReducedMotion()

  let w = 0
  let h = 0
  let dpr = Math.min(window.devicePixelRatio || 1, 2)

  const NODE_COUNT = 9
  const MAX_DIST = 130
  const nodes: Node[] = []
  const particles: Particle[] = []

  function rand(min: number, max: number) {
    return min + Math.random() * (max - min)
  }

  function initNodes() {
    nodes.length = 0
    for (let i = 0; i < NODE_COUNT; i++) {
      nodes.push({
        x: rand(w * 0.12, w * 0.88),
        y: rand(h * 0.12, h * 0.88),
        vx: rand(-0.18, 0.18),
        vy: rand(-0.18, 0.18),
        r: rand(1.4, 2.6),
        phase: rand(0, Math.PI * 2),
        pulseSpeed: rand(0.0006, 0.0014),
      })
    }
  }

  function initParticles() {
    particles.length = 0
    const count = 5
    for (let i = 0; i < count; i++) {
      particles.push({
        a: Math.floor(rand(0, NODE_COUNT)),
        b: Math.floor(rand(0, NODE_COUNT)),
        t: rand(0, 1),
        speed: rand(0.0009, 0.0019),
        curvature: rand(-18, 18),
      })
      if (particles[i]!.a === particles[i]!.b) {
        particles[i]!.b = (particles[i]!.b + 1) % NODE_COUNT
      }
    }
  }

  function resize() {
    const rect = parent.getBoundingClientRect()
    const nextW = Math.max(1, Math.floor(rect.width))
    const nextH = Math.max(1, Math.floor(rect.height || 160))
    if (nextW === w && nextH === h) return
    w = nextW
    h = nextH
    dpr = Math.min(window.devicePixelRatio || 1, 2)
    canvas.width = w * dpr
    canvas.height = h * dpr
    canvas.style.width = `${w}px`
    canvas.style.height = `${h}px`
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

    if (nodes.length === 0) {
      initNodes()
      initParticles()
    }
    if (!reduced) {
      draw(0)
    }
  }

  function quadraticPoint(
    ax: number,
    ay: number,
    cx: number,
    cy: number,
    bx: number,
    by: number,
    t: number,
  ) {
    const mt = 1 - t
    return {
      x: mt * mt * ax + 2 * mt * t * cx + t * t * bx,
      y: mt * mt * ay + 2 * mt * t * cy + t * t * by,
    }
  }

  function draw(now: number) {
    ctx.clearRect(0, 0, w, h)
    const time = now * 0.001

    if (!reduced) {
      for (const n of nodes) {
        n.x += n.vx
        n.y += n.vy
        if (n.x < 10 || n.x > w - 10) n.vx *= -1
        if (n.y < 10 || n.y > h - 10) n.vy *= -1
        n.x = Math.max(10, Math.min(w - 10, n.x))
        n.y = Math.max(10, Math.min(h - 10, n.y))
        n.vx += rand(-0.006, 0.006)
        n.vy += rand(-0.006, 0.006)
        n.vx = Math.max(-0.22, Math.min(0.22, n.vx))
        n.vy = Math.max(-0.22, Math.min(0.22, n.vy))
        n.phase += n.pulseSpeed
      }
    }

    ctx.lineWidth = 0.7
    ctx.lineCap = 'round'
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i]!
        const b = nodes[j]!
        const dx = a.x - b.x
        const dy = a.y - b.y
        const dist = Math.hypot(dx, dy)
        if (dist > MAX_DIST) continue
        const alpha = 0.13 * (1 - dist / MAX_DIST)
        const mx = (a.x + b.x) / 2
        const my = (a.y + b.y) / 2
        const ux = -dy / (dist || 1)
        const uy = dx / (dist || 1)
        const breathe = Math.sin(time * 0.25 + i * 0.7 + j * 0.4) * 6
        const curve = 10 + breathe
        const cx = mx + ux * curve
        const cy = my + uy * curve

        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.quadraticCurveTo(cx, cy, b.x, b.y)
        ctx.strokeStyle = `rgba(0, 255, 65, ${alpha.toFixed(3)})`
        ctx.stroke()

        ctx.save()
        ctx.globalAlpha = alpha * 0.35
        ctx.lineWidth = 1.8
        ctx.strokeStyle = 'rgba(77, 255, 112, 0.5)'
        ctx.stroke()
        ctx.restore()
      }
    }

    if (!reduced) {
      for (const p of particles) {
        p.t += p.speed
        if (p.t > 1) {
          p.t = 0
          if (Math.random() < 0.7) {
            p.a = Math.floor(rand(0, NODE_COUNT))
            p.b = Math.floor(rand(0, NODE_COUNT))
            if (p.a === p.b) p.b = (p.b + 1) % NODE_COUNT
            p.curvature = rand(-18, 18)
          }
        }
        const a = nodes[p.a]
        const b = nodes[p.b]
        if (!a || !b) continue
        const dx = a.x - b.x
        const dy = a.y - b.y
        const dist = Math.hypot(dx, dy)
        const mx = (a.x + b.x) / 2
        const my = (a.y + b.y) / 2
        const ux = -dy / (dist || 1)
        const uy = dx / (dist || 1)
        const cx = mx + ux * p.curvature
        const cy = my + uy * p.curvature
        const pos = quadraticPoint(a.x, a.y, cx, cy, b.x, b.y, p.t)
        const fade = Math.sin(p.t * Math.PI)
        const alpha = 0.55 * fade + 0.15

        ctx.beginPath()
        ctx.arc(pos.x, pos.y, 1.35, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(180, 255, 190, ${alpha.toFixed(3)})`
        ctx.shadowColor = 'rgba(0, 255, 65, 0.9)'
        ctx.shadowBlur = 6
        ctx.fill()
        ctx.shadowBlur = 0
      }
    }

    for (const n of nodes) {
      const pulse = 0.75 + Math.sin(n.phase) * 0.25
      const r = n.r * (0.9 + pulse * 0.35)
      const alpha = 0.55 + pulse * 0.35

      const glow = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, r * 7)
      glow.addColorStop(0, `rgba(0, 255, 65, ${0.22 * alpha})`)
      glow.addColorStop(0.45, `rgba(0, 255, 65, ${0.08 * alpha})`)
      glow.addColorStop(1, 'rgba(0, 255, 65, 0)')
      ctx.fillStyle = glow
      ctx.beginPath()
      ctx.arc(n.x, n.y, r * 7, 0, Math.PI * 2)
      ctx.fill()

      ctx.beginPath()
      ctx.arc(n.x, n.y, r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(200, 255, 210, ${alpha.toFixed(3)})`
      ctx.shadowColor = 'rgba(0, 255, 65, 0.85)'
      ctx.shadowBlur = 8
      ctx.fill()
      ctx.shadowBlur = 0

      ctx.fillStyle = `rgba(255,255,255, ${0.55 * alpha})`
      ctx.fillRect(n.x - 0.4, n.y - 0.4, 0.8, 0.8)
    }
  }

  function frame(now: number) {
    if (document.hidden) {
      raf = requestAnimationFrame(frame)
      return
    }
    draw(now)
    raf = requestAnimationFrame(frame)
  }

  resize()
  ro = new ResizeObserver(() => resize())
  ro.observe(parent)

  if (reduced) {
    draw(0)
  } else {
    raf = requestAnimationFrame(frame)
  }

  mq = window.matchMedia(`(resolution: ${window.devicePixelRatio}dppx)`)
  onDprChange = () => resize()
  if (typeof mq.addEventListener === 'function') mq.addEventListener('change', onDprChange)
  else (mq as unknown as { addListener: (cb: () => void) => void }).addListener(onDprChange)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  ro?.disconnect()
  if (mq && onDprChange) {
    if (typeof mq.removeEventListener === 'function') mq.removeEventListener('change', onDprChange)
    else (mq as unknown as { removeListener: (cb: () => void) => void }).removeListener(onDprChange)
  }
})
</script>

<template>
  <div class="ambient-network" data-testid="ambient-network" aria-hidden="true">
    <canvas ref="canvasRef" class="ambient-network__canvas" />
    <span class="ambient-network__label">ambient network</span>
  </div>
</template>

<style scoped>
.ambient-network {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 140px;
  overflow: hidden;
  background: var(--sarv-panel);
  background-image:
    linear-gradient(rgba(0, 255, 65, 0.015) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 255, 65, 0.015) 1px, transparent 1px);
  background-size: 14px 14px;
}

.ambient-network__canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.ambient-network__label {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.ambient-network::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(
    to bottom,
    var(--sarv-panel) 0%,
    transparent 18%,
    transparent 82%,
    color-mix(in srgb, var(--sarv-panel) 92%, transparent) 100%
  );
  opacity: 0.9;
}
</style>
