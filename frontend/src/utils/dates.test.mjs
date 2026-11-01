// Run with:  npm test        (uses Node's built-in test runner, nothing to install)
import test from 'node:test'
import assert from 'node:assert/strict'
import { formatDate, parseDate } from './dates.js'

const ZONES = ['UTC', 'Asia/Kolkata', 'Pacific/Honolulu', 'America/Los_Angeles', 'Pacific/Kiritimati', 'Europe/London']

test('a date-only value shows the same calendar day in every timezone', () => {
  const original = process.env.TZ
  try {
    for (const tz of ZONES) {
      process.env.TZ = tz
      assert.equal(formatDate('2026-11-01'), '01 Nov 2026', tz)
      assert.equal(formatDate('2026-01-01'), '01 Jan 2026', tz)   // year boundary
      assert.equal(formatDate('2026-12-31'), '31 Dec 2026', tz)
    }
  } finally {
    if (original === undefined) delete process.env.TZ; else process.env.TZ = original
  }
})

test('parseDate builds local midnight from the parts', () => {
  const d = parseDate('2026-03-08')
  assert.equal(d.getFullYear(), 2026)
  assert.equal(d.getMonth(), 2)
  assert.equal(d.getDate(), 8)
  assert.equal(d.getHours(), 0)
})

test('empty and invalid values give a dash', () => {
  assert.equal(formatDate(null), '—')
  assert.equal(formatDate(''), '—')
  assert.equal(formatDate('not a date'), '—')
})
