import { useRef, useState } from 'react'

// Stops double submissions. run(fn) ignores any call made while the previous one is still running.
// A ref is used (not only state) because two quick clicks can happen before React re-renders.
//   const [busy, run] = useBusy()
//   const submit = (e) => { e.preventDefault(); return run(() => submitNow(e)) }
//   <button disabled={busy}>Save</button>
export function useBusy() {
  const running = useRef(false)
  const [busy, setBusy] = useState(false)
  const run = async (fn) => {
    if (running.current) return undefined
    running.current = true
    setBusy(true)
    try {
      return await fn()
    } finally {
      running.current = false
      setBusy(false)
    }
  }
  return [busy, run]
}
