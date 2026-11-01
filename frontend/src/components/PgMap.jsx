import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const price = (n) => (n >= 1000 ? `₹${Math.round(n / 100) / 10}k` : `₹${n}`)

// A map with one price pin per PG (OpenStreetMap tiles, no API key).
// `pgs` need latitude/longitude. `userPos` ({lat,lng}) draws a blue "you are here" dot.
export default function PgMap({ pgs, userPos, selectedId, onSelect, height = 440 }) {
  const el = useRef(null)
  const map = useRef(null)
  const layer = useRef(null)

  // create the map once
  useEffect(() => {
    map.current = L.map(el.current, { scrollWheelZoom: false }).setView([20.59, 78.96], 5) // India
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map.current)
    layer.current = L.layerGroup().addTo(map.current)
    return () => { map.current.remove(); map.current = null }
  }, [])

  // redraw the pins whenever the PGs, the selection or the user's position change
  useEffect(() => {
    if (!map.current) return
    layer.current.clearLayers()
    const points = []
    pgs.filter((p) => p.latitude != null && p.longitude != null).forEach((p) => {
      const free = p.freeBeds > 0
      const icon = L.divIcon({
        className: 'pin-wrap',
        html: `<div class="pin ${free ? '' : 'pin-full'} ${p.id === selectedId ? 'pin-on' : ''}">${price(Number(p.startingRent || 0))}</div>`,
        iconSize: [60, 28], iconAnchor: [30, 14],
      })
      const m = L.marker([p.latitude, p.longitude], { icon, keyboard: true, title: p.name })
      if (onSelect) m.on('click', () => onSelect(p))
      m.addTo(layer.current)
      points.push([p.latitude, p.longitude])
    })
    if (userPos) {
      L.circleMarker([userPos.lat, userPos.lng], { radius: 8, color: '#fff', weight: 3, fillColor: '#0F766E', fillOpacity: 1 })
        .bindTooltip('You are here').addTo(layer.current)
      points.push([userPos.lat, userPos.lng])
    }
    if (points.length === 1) map.current.setView(points[0], 15)
    else if (points.length > 1) map.current.fitBounds(points, { padding: [40, 40], maxZoom: 15 })
  }, [pgs, userPos, selectedId, onSelect])

  return (
    <div className="map-box" style={{ height }}>
      <div ref={el} style={{ height: '100%', width: '100%' }} />
      {pgs.every((p) => p.latitude == null) && !userPos && <div className="map-empty">No PG here has set its location yet.</div>}
    </div>
  )
}
