/**
 * Shared random utility functions.
 *
 * PAT-255: the source of randomness is swappable. `setSeed(n)` installs a small deterministic
 * PRNG (mulberry32) so a synthetic cohort can be regenerated bit-for-bit — the smoke harness
 * fixture, and the test that detects when the generator drifted away from it, depend on that.
 * `setSeed(null)` restores `Math.random`. Generated data also looks at "now" (ages, default
 * date windows); `setReferenceDate` pins that so a seeded run does not change with the calendar.
 */
let rng: (() => number) | null = null
let referenceDate: Date | null = null

export function setSeed(seed: number | null): void {
  if (seed === null) {
    rng = null
    return
  }
  let a = seed >>> 0
  rng = () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Uniform [0, 1) from the current source (seeded, or `Math.random` looked up per call so tests can spy on it). */
export function random(): number {
  return rng ? rng() : Math.random()
}

/** Pins "now" for generated data (ISO date or Date); null = the real clock. */
export function setReferenceDate(date: Date | string | null): void {
  referenceDate = date === null ? null : new Date(date)
}

export function referenceNow(): Date {
  return referenceDate ? new Date(referenceDate.getTime()) : new Date()
}

export function randomInt(min: number, max: number): number {
  return Math.floor(random() * (max - min + 1)) + min
}

export function randomElement<T>(arr: readonly T[]): T {
  return arr[Math.floor(random() * arr.length)]
}

export function randomFloat(min: number, max: number, decimals = 1): number {
  const val = random() * (max - min) + min
  const factor = Math.pow(10, decimals)
  return Math.round(val * factor) / factor
}

/** Pick `count` random elements using partial Fisher-Yates (O(k)) */
export function pickRandom<T>(arr: readonly T[], count: number): T[] {
  const n = Math.min(count, arr.length)
  const copy = [...arr]
  for (let i = 0; i < n; i++) {
    const j = i + Math.floor(random() * (copy.length - i))
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy.slice(0, n)
}
