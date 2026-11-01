// Thikano logo: a house roofline on a teal tile.
export function BrandMark({ size = 32 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="9" fill="#0F766E" />
      <path d="M7 15.5L16 8l9 7.5" fill="none" stroke="#FFD9CF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10 17.5V24h12v-6.5" fill="none" stroke="#FFFFFF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="16" cy="21" r="1.5" fill="#F26B4F" />
    </svg>
  )
}

export default function Brand({ size = 32 }) {
  return (
    <span className="brand-lockup">
      <BrandMark size={size} />
      <span className="brand-word">Thikano</span>
    </span>
  )
}
