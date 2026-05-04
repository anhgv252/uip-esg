/**
 * WCAG 2.1 AA contrast ratio validation.
 * Minimum ratio: 4.5:1 for normal text, 3:1 for large text.
 */

function parseHex(hex: string): [number, number, number] {
  const clean = hex.replace('#', '')
  const r = parseInt(clean.substring(0, 2), 16)
  const g = parseInt(clean.substring(2, 4), 16)
  const b = parseInt(clean.substring(4, 6), 16)
  return [r, g, b]
}

function relativeLuminance(r: number, g: number, b: number): number {
  const [rs, gs, bs] = [r, g, b].map((c) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs
}

export function contrastRatio(fg: string, bg: string): number {
  const [fr, fg_, fb] = parseHex(fg)
  const [br, bg_, bb] = parseHex(bg)
  const l1 = relativeLuminance(fr, fg_, fb)
  const l2 = relativeLuminance(br, bg_, bb)
  const lighter = Math.max(l1, l2)
  const darker = Math.min(l1, l2)
  return (lighter + 0.05) / (darker + 0.05)
}

export function meetsWcagAA(fg: string, bg: string, largeText = false): boolean {
  const ratio = contrastRatio(fg, bg)
  return largeText ? ratio >= 3 : ratio >= 4.5
}
