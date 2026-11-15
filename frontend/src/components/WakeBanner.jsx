import { useEffect, useRef, useState } from 'react'
import { WAKING_EVENT } from '../api/wake.js'

// Free hosting puts an idle server to sleep, and waking it takes about a minute.
// This component (1) quietly wakes the server as soon as any page opens, and
// (2) shows a small banner while it is still starting, so visitors see a friendly message
// instead of an error. It disappears by itself once the server answers.

const HEALTH_URL = '/api/public/health'
const POLL_MS = 4000             // ask again every 4 seconds
const ATTEMPT_TIMEOUT_MS = 15000 // give up on one attempt after 15 seconds
const SHOW_AFTER_MS = 2500       // only show the banner if the first answer is slow

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function isAwake() {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), ATTEMPT_TIMEOUT_MS)
  try {
    const res = await fetch(HEALTH_URL, { cache: 'no-store', signal: controller.signal })
    if (!res.ok) return false
    const data = await res.json()   // an HTML error page is not JSON, so it counts as "not awake"
    return data.status === 'ok'
  } catch (e) {
    return false
  } finally {
    clearTimeout(timer)
  }
}

export default function WakeBanner() {
  const [phase, setPhase] = useState('checking') // checking | waking | ready | hidden
  const [round, setRound] = useState(0)
  const phaseRef = useRef(phase)
  phaseRef.current = phase

  // Keep asking the server until it answers.
  useEffect(() => {
    let cancelled = false
    const showTimer = setTimeout(() => {
      if (!cancelled) setPhase('waking')
    }, SHOW_AFTER_MS)

    async function waitForServer() {
      while (!cancelled) {
        if (await isAwake()) break
        await sleep(POLL_MS)
      }
      clearTimeout(showTimer)
      if (!cancelled) setPhase((p) => (p === 'waking' ? 'ready' : 'hidden'))
    }
    waitForServer()

    return () => {
      cancelled = true
      clearTimeout(showTimer)
    }
  }, [round])

  // A failed request told us the server went back to sleep: show the banner and start asking again.
  useEffect(() => {
    function onWaking() {
      if (phaseRef.current === 'waking') return
      setPhase('waking')
      setRound((r) => r + 1)
    }
    window.addEventListener(WAKING_EVENT, onWaking)
    return () => window.removeEventListener(WAKING_EVENT, onWaking)
  }, [])

  // "Server is ready" stays on screen for a moment, then goes away.
  useEffect(() => {
    if (phase !== 'ready') return undefined
    const timer = setTimeout(() => setPhase('hidden'), 2500)
    return () => clearTimeout(timer)
  }, [phase])

  if (phase === 'checking' || phase === 'hidden') return null

  const ready = phase === 'ready'
  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 9999,
        padding: '10px 16px',
        textAlign: 'center',
        fontSize: '14px',
        fontWeight: 600,
        color: ready ? '#064e3b' : '#78350f',
        background: ready ? '#d1fae5' : '#fef3c7',
        borderBottom: ready ? '1px solid #6ee7b7' : '1px solid #fcd34d',
        boxShadow: '0 2px 8px rgba(0,0,0,0.12)',
      }}
    >
      {ready
        ? 'Server is ready. You can continue.'
        : 'Starting the server (free hosting). This can take up to a minute, so please wait.'}
    </div>
  )
}
