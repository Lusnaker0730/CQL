/** Shared random utility functions */

export function randomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

export function randomElement<T>(arr: readonly T[]): T {
  return arr[Math.floor(Math.random() * arr.length)]
}

export function randomFloat(min: number, max: number, decimals = 1): number {
  const val = Math.random() * (max - min) + min
  const factor = Math.pow(10, decimals)
  return Math.round(val * factor) / factor
}

/** Pick `count` random elements using partial Fisher-Yates (O(k)) */
export function pickRandom<T>(arr: readonly T[], count: number): T[] {
  const n = Math.min(count, arr.length)
  const copy = [...arr]
  for (let i = 0; i < n; i++) {
    const j = i + Math.floor(Math.random() * (copy.length - i))
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy.slice(0, n)
}
