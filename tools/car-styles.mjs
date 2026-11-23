// Génère les styles de carte de l'écran de voiture (Android Auto) à partir des styles du téléphone.
// Usage : node tools/car-styles.mjs
//
// Pourquoi : les styles du téléphone sont volontairement discrets (routes mauve pâle sur fond presque blanc). Sur un écran de
// voiture, en plein jour et à bout de bras, la carte paraissait vide (constat en voiture). Les variantes « car » gardent les
// mêmes calques (mêmes identifiants : le code ne change pas) avec un fond plus soutenu, des routes hiérarchisées par couleur et
// par largeur, un liseré sous les petites rues et des étiquettes plus grandes.
import { readFileSync, writeFileSync } from 'node:fs'

const DIR = 'core/map/src/main/assets/styles/'

const DAY = {
  background: '#E9EBF0', residential: '#E1E3EA', park: '#C9E2C2', wood: '#CFE0C8', water: '#A6C9EA',
  building: '#D3D6E0', buildingOutline: '#BFC3D0',
  minor: '#FFFFFF', minorCasing: '#BEC2D0',
  majorCasing: '#A9A6BC', trunk: '#FBDD9C', primary: '#FFF0BF', secondary: '#FFFFFF',
  motorway: '#F7C272', motorwayCasing: '#C98A2E', tunnel: '#C6C9D6', bridgeCasing: '#8E8AA0',
  roadLabel: '#2F3240', placeLabel: '#16181F', halo: '#FFFFFF', poiLabel: '#4A4560',
}
const NIGHT = {
  background: '#12141A', residential: '#161920', park: '#14261B', wood: '#13221A', water: '#0E2740',
  building: '#1D2029', buildingOutline: '#2A2E3A',
  minor: '#474C63', minorCasing: '#20232C',
  majorCasing: '#15171D', trunk: '#7A6233', primary: '#5E6480', secondary: '#4B5068',
  motorway: '#93702F', motorwayCasing: '#3F2F12', tunnel: '#2A2D39', bridgeCasing: '#0B0C10',
  roadLabel: '#D8DBE6', placeLabel: '#F1F2F6', halo: '#0B0C10', poiLabel: '#B9B4CC',
}

const ROAD_WIDTH = 1.3
const LABEL_SIZE = 1.35

/** Multiplie les valeurs de sortie d'une largeur (nombre, ou `interpolate` dont les sorties sont des nombres ou des `["*", n, …]`). */
function scale(expr, k) {
  if (typeof expr === 'number') return +(expr * k).toFixed(2)
  if (!Array.isArray(expr)) return expr
  if (expr[0] === '*' && typeof expr[1] === 'number') return ['*', +(expr[1] * k).toFixed(2), ...expr.slice(2)]
  if (expr[0] === 'interpolate') {
    const out = expr.slice(0, 3)
    for (let i = 3; i < expr.length; i += 2) out.push(expr[i], scale(expr[i + 1], k))
    return out
  }
  return expr
}

function build(source, c) {
  const style = JSON.parse(readFileSync(DIR + source, 'utf8'))
  const layers = []
  for (const l of style.layers) {
    // Points d'intérêt : seuls les carburants et les hôpitaux restent (un « P » par parking couvrait la carte en ville).
    if (l.id === 'roadly_poi_other') continue
    if (l.id === 'roadly_poi_major') l.filter = ['match', ['get', 'class'], ['hospital', 'fuel'], true, false]
    const paint = (l.paint ??= {})
    const layout = (l.layout ??= {})
    switch (l.id) {
      case 'background': paint['background-color'] = c.background; break
      case 'park': paint['fill-color'] = c.park; break
      case 'water': paint['fill-color'] = c.water; break
      case 'waterway': paint['line-color'] = c.water; break
      case 'landuse_residential': paint['fill-color'] = c.residential; break
      case 'landcover_wood': paint['fill-color'] = c.wood; break
      case 'building': paint['fill-color'] = c.building; paint['fill-outline-color'] = c.buildingOutline; break
      case 'roadly_road_tunnel': paint['line-color'] = c.tunnel; break
      case 'roadly_road_minor': {
        // Liseré sous les petites rues : sans lui, une rue blanche sur fond clair ne se lit pas.
        const casing = structuredClone(l)
        casing.id = 'roadly_road_minor_casing'
        casing.layout['line-cap'] = 'butt'
        casing.paint['line-color'] = c.minorCasing
        casing.paint['line-width'] = scale(l.paint['line-width'], ROAD_WIDTH * 1.35)
        casing.minzoom = 13
        layers.push(casing)
        paint['line-color'] = c.minor
        break
      }
      case 'roadly_road_bridge_casing': paint['line-color'] = c.bridgeCasing; break
      case 'roadly_road_major_casing': paint['line-color'] = c.majorCasing; break
      case 'roadly_road_major':
        paint['line-color'] = ['match', ['get', 'class'], 'trunk', c.trunk, 'primary', c.primary, c.secondary]
        break
      case 'roadly_road_motorway_casing': paint['line-color'] = c.motorwayCasing; break
      case 'roadly_road_motorway': paint['line-color'] = c.motorway; break
      case 'highway_major_subtle': paint['line-color'] = c.primary === '#FFF0BF' ? c.majorCasing : c.primary; break
      case 'highway_motorway_subtle': paint['line-color'] = c.motorwayCasing; break
    }
    if (l.type === 'line' && l['source-layer'] === 'transportation' && l.id.startsWith('roadly_road') && paint['line-width'] !== undefined) {
      paint['line-width'] = scale(paint['line-width'], ROAD_WIDTH)
    }
    if (l.type === 'symbol') {
      if (layout['text-size'] !== undefined) layout['text-size'] = scale(layout['text-size'], LABEL_SIZE)
      const sl = l['source-layer']
      if (sl === 'transportation_name' && !l.id.includes('shield')) {
        paint['text-color'] = c.roadLabel; paint['text-halo-color'] = c.halo; paint['text-halo-width'] = 1.6
        layout['text-font'] = ['Noto Sans Bold']
      } else if (sl === 'place') {
        paint['text-color'] = c.placeLabel; paint['text-halo-color'] = c.halo; paint['text-halo-width'] = 1.6
      } else if (sl === 'poi') {
        paint['text-color'] = c.poiLabel; paint['text-halo-color'] = c.halo
      }
      if (l.id.includes('shield') && typeof layout['icon-size'] === 'number') layout['icon-size'] = 1.3
    }
    layers.push(l)
  }
  style.layers = layers
  style.name = (style.name ?? 'Roadly') + ' (voiture)'
  return style
}

for (const [source, target, palette] of [
  ['roadly-light.json', 'roadly-car-light.json', DAY],
  ['roadly-dark.json', 'roadly-car-dark.json', NIGHT],
]) {
  const out = build(source, palette)
  writeFileSync(DIR + target, JSON.stringify(out))
  console.log(target, out.layers.length, 'calques')
}
