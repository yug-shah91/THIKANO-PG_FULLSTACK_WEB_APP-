import {
  WifiIcon, SnowIcon, MealsIcon, LaundryIcon, ParkingIcon, FlameIcon, BoltIcon,
  ShieldIcon, TvIcon, BathIcon, FridgeIcon, GymIcon, CheckIcon,
} from './Icons'

// The owner types facilities as free text ("WiFi, AC, attached bath"). We recognise common words
// and give them an icon; anything else still shows, with a plain tick.
const KNOWN = [
  { re: /wi-?fi|internet|broadband/i, label: 'WiFi', Icon: WifiIcon },
  { re: /\bac\b|a\/c|air.?con/i, label: 'AC', Icon: SnowIcon },
  { re: /meal|food|mess|breakfast|dinner|tiffin/i, label: 'Meals', Icon: MealsIcon },
  { re: /laundry|washing/i, label: 'Laundry', Icon: LaundryIcon },
  { re: /parking|garage/i, label: 'Parking', Icon: ParkingIcon },
  { re: /geyser|hot water/i, label: 'Hot water', Icon: FlameIcon },
  { re: /backup|inverter|generator|power/i, label: 'Power backup', Icon: BoltIcon },
  { re: /cctv|security|guard/i, label: 'Security', Icon: ShieldIcon },
  { re: /\btv\b|television/i, label: 'TV', Icon: TvIcon },
  { re: /bath|toilet|washroom/i, label: 'Attached bath', Icon: BathIcon },
  { re: /fridge|refrigerator/i, label: 'Fridge', Icon: FridgeIcon },
  { re: /gym/i, label: 'Gym', Icon: GymIcon },
]

/** "wifi" -> { label: 'WiFi', Icon }. Unknown words keep their own text. */
export function describeAmenity(text) {
  const hit = KNOWN.find((k) => k.re.test(text))
  if (hit) return { label: hit.label, Icon: hit.Icon }
  const clean = text.trim()
  return { label: clean.charAt(0).toUpperCase() + clean.slice(1), Icon: CheckIcon }
}

// Facilities offered in the filter panel (the word is what the search sends to the server).
export const FILTER_AMENITIES = [
  { value: 'wifi', label: 'WiFi', Icon: WifiIcon },
  { value: 'ac', label: 'AC', Icon: SnowIcon },
  { value: 'meal', label: 'Meals', Icon: MealsIcon },
  { value: 'laundry', label: 'Laundry', Icon: LaundryIcon },
  { value: 'parking', label: 'Parking', Icon: ParkingIcon },
  { value: 'geyser', label: 'Hot water', Icon: FlameIcon },
  { value: 'backup', label: 'Power backup', Icon: BoltIcon },
  { value: 'cctv', label: 'CCTV', Icon: ShieldIcon },
]

/** A row of small icons with the name as tooltip and screen-reader text. Shows `max`, then "+N". */
export function AmenityIcons({ items = [], max = 5 }) {
  const seen = new Set()
  const list = []
  for (const raw of items) {
    const d = describeAmenity(raw)
    if (seen.has(d.label)) continue // "ac" and "air conditioning" are one icon
    seen.add(d.label)
    list.push(d)
  }
  if (list.length === 0) return null
  return (
    <ul className="amen" aria-label="Facilities">
      {list.slice(0, max).map(({ label, Icon }) => (
        <li key={label} title={label}><Icon width={16} height={16} /><span className="sr">{label}</span></li>
      ))}
      {list.length > max && <li className="amen-more" title={list.slice(max).map((d) => d.label).join(', ')}>+{list.length - max}</li>}
    </ul>
  )
}
