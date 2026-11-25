<#
.SYNOPSIS
  Génère les styles de carte Roadly (core/map/src/main/assets/styles/roadly-dark.json et roadly-light.json)
  depuis les styles OpenFreeMap enregistrés dans core/map/tools/upstream (dark.json, positron.json).
.DESCRIPTION
  Déterministe : mêmes fichiers amont → mêmes assets. Sources, glyphes et sprite restent les URL OpenFreeMap ; tout ce qui
  est dessiné vient des tuiles OpenMapTiles (rien d'inventé). Modifications :
  1. calques routiers amont remplacés par une pile Roadly (tunnels, rues, liseré des ponts, routes principales, autoroutes) ;
  2. tunnels en pointillé à 60 %, ponts avec un liseré plus sombre ; 3. bretelles (ramp = 1) à 75 % de leur classe ;
  4. noms de rues espacés (400) et 1 px plus petits dès le zoom 15 ; points d'intérêt majeurs dès le zoom 14, autres dès 16 ;
  5. eau et parcs teintés vers la palette Roadly ; style sombre : fond « nuit bleue » ($nightBase) et calque des prairies
     et cultures (roadly_landcover_fields) ; 6. bâtiments conservés (extrusion ajoutée à l'exécution par MapController) ;
  7. pile routière insérée à la place de highway_minor (au-dessus des chemins et des jetées) ; calques symboles regroupés
     au-dessus de tous les calques à plat (ordre relatif conservé), car MapController ancre l'extrusion des bâtiments
     sous le premier calque symbole.
  Lancer depuis la racine du dépôt : powershell -NoProfile -ExecutionPolicy Bypass -File core\map\tools\build-roadly-styles.ps1
#>
$ErrorActionPreference = 'Stop'
# PowerShell 5.1 : sans cette ligne, ConvertTo-Json écrit les tableaux imbriqués sous la forme {"value":[…],"Count":n}.
Remove-TypeData System.Array -ErrorAction SilentlyContinue

$RoadlyStyleVersion = '1'
$toolsDir = $PSScriptRoot
$assetsDir = Join-Path (Split-Path -Parent $toolsDir) 'src\main\assets\styles'
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null

# ── Palettes Roadly (couleurs CSS du style) ──
$palettes = @{
    dark  = [ordered]@{
        motorway = '#6478AE'; trunk = '#566999'; primary = '#485A8C'; secondary = '#3D4D7C'; tertiary = '#3D4D7C'; minor = '#33436F'
        casing = '#0A1228'; bridgeCasing = '#060B1A'; tunnel = '#33436F'; water = '#17389A'; park = '#1F5540'
        poiText = '#9AA8C8'; poiHalo = '#0A1228'; fields = '#183F30'
    }
    light = [ordered]@{
        motorway = '#AAB6D2'; trunk = '#B8C2DA'; primary = '#C8D0E2'; secondary = '#D9DEEA'; tertiary = '#D9DEEA'; minor = '#E4E7EE'
        casing = '#C3C9D6'; bridgeCasing = '#959DB0'; tunnel = '#D3D8E2'; water = '#BFD4EC'; park = '#D6E8D4'
        poiText = '#55607A'; poiHalo = '#FFFFFF'
    }
}

# ── Fond « nuit bleue » du style sombre : calque amont → propriétés de peinture remplacées (terre bleu nuit, bois verts,
#    bâtiments et voies ferrées bleutés, noms clairs). Le style clair garde le fond amont. ──
$nightPlaceText = [ordered]@{ 'text-color' = '#C7D3EE'; 'text-halo-color' = '#0A1228' }
$nightBase = [ordered]@{
    'background'               = [ordered]@{ 'background-color' = '#101C3C' }
    'landcover_ice_shelf'      = [ordered]@{ 'fill-color' = '#1A2748' }
    'landcover_glacier'        = [ordered]@{ 'fill-color' = '#1A2748' }
    'landuse_residential'      = [ordered]@{ 'fill-color' = '#18254C' }
    'landcover_wood'           = [ordered]@{ 'fill-color' = '#18452F' }
    'waterway'                 = [ordered]@{ 'line-color' = '#17389A' }
    'building'                 = [ordered]@{ 'fill-color' = '#1A2750'; 'fill-outline-color' = '#2A3B6E' }
    'aeroway-taxiway'          = [ordered]@{ 'line-color' = '#26355C' }
    'aeroway-runway-casing'    = [ordered]@{ 'line-color' = '#0A1228' }
    'aeroway-area'             = [ordered]@{ 'fill-color' = '#14213F' }
    'aeroway-runway'           = [ordered]@{ 'line-color' = '#2E3F6B' }
    'road_area_pier'           = [ordered]@{ 'fill-color' = '#101C3C' }
    'road_pier'                = [ordered]@{ 'line-color' = '#101C3C' }
    'highway_path'             = [ordered]@{ 'line-color' = '#2A3A66' }
    'railway_transit'          = [ordered]@{ 'line-color' = '#2E3F6B' }
    'railway_transit_dashline' = [ordered]@{ 'line-color' = '#101C3C' }
    'railway_minor'            = [ordered]@{ 'line-color' = '#2E3F6B' }
    'railway_minor_dashline'   = [ordered]@{ 'line-color' = '#101C3C' }
    'railway'                  = [ordered]@{ 'line-color' = '#2E3F6B' }
    'railway_dashline'         = [ordered]@{ 'line-color' = '#101C3C' }
    'boundary_state'           = [ordered]@{ 'line-color' = '#3A4B78' }
    'boundary_country_z0-4'    = [ordered]@{ 'line-color' = '#465A8C' }
    'boundary_country_z5-'     = [ordered]@{ 'line-color' = '#465A8C' }
    'water_name'               = [ordered]@{ 'text-color' = '#7FA2EE'; 'text-halo-color' = '#0C2260' }
    'highway_name_other'       = [ordered]@{ 'text-color' = '#A9B8DC'; 'text-halo-color' = '#0A1228' }
    'highway_name_motorway'    = [ordered]@{ 'text-color' = '#C7D3EE' }
}

# Prairies et cultures des tuiles (landcover : grass, farmland), en vert sombre : la campagne se détache de la ville.
function New-FieldsLayer($p) {
    return [ordered]@{
        id = 'roadly_landcover_fields'; type = 'fill'; source = 'openmaptiles'; 'source-layer' = 'landcover'; minzoom = 9
        filter = @('all', @('match', @('geometry-type'), @('MultiPolygon', 'Polygon'), $true, $false), @('match', @('get', 'class'), @('grass', 'farmland'), $true, $false))
        paint = [ordered]@{ 'fill-color' = $p.fields; 'fill-opacity' = @('interpolate', @('linear'), @('zoom'), 9, 0, 11, 0.85) }
    }
}

# ── Expressions communes (aucun opérateur dans les listes : en PowerShell la virgule lie plus fort que +) ──
$isLine          = @('match', @('geometry-type'), @('LineString', 'MultiLineString'), $true, $false)
$isTunnel        = @('==', @('get', 'brunnel'), 'tunnel')
$notTunnel       = @('!=', @('get', 'brunnel'), 'tunnel')
$isBridge        = @('==', @('get', 'brunnel'), 'bridge')
$majorClasses    = @('trunk', 'primary', 'secondary', 'tertiary')
$minorClasses    = @('minor', 'service', 'track')
$bridgeClasses   = @('motorway', 'trunk', 'primary', 'secondary', 'tertiary')
$allClasses      = @('motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor', 'service', 'track')
# Les parkings n'en sont pas : en ville, leurs pictogrammes couvraient la carte (ils restent en texte dès le zoom 16).
$poiMajorClasses = @('hospital', 'fuel', 'railway')
# Bretelles (ramp = 1) : 75 % de la largeur de leur classe.
$ramp         = @('case', @('==', @('get', 'ramp'), 1), 0.75, 1)
# Routes principales : voie rapide > nationale > départementale > locale.
$majorFactor  = @('match', @('get', 'class'), 'trunk', 1.15, 'primary', 1.0, 'secondary', 0.85, 0.7)
# Rues 80 %, voies de service et chemins 55 % de la largeur d'origine.
$minorFactor  = @('match', @('get', 'class'), 'minor', 0.8, 0.55)
# Tunnels : largeur selon la classe.
$classFactor  = @('match', @('get', 'class'), 'motorway', 1.3, 'trunk', 1.15, 'primary', 1.0, 'secondary', 0.85, 'tertiary', 0.7, 0.55)
# Liseré des ponts : plus large que le liseré ordinaire de chaque classe (autoroute : 57 > 52 au zoom 20).
$bridgeFactor = @('match', @('get', 'class'), 'motorway', 1.9, 'trunk', 1.15, 'primary', 1.0, 'secondary', 0.85, 0.7)

function New-RoadLine([string]$Id, [double]$MinZoom, [string]$Cap, [object[]]$Filter, [object]$Color, [object[]]$Width) {
    return [ordered]@{
        id = $Id; type = 'line'; source = 'openmaptiles'; 'source-layer' = 'transportation'; minzoom = $MinZoom; filter = $Filter
        layout = [ordered]@{ 'line-cap' = $Cap; 'line-join' = 'round' }
        paint = [ordered]@{ 'line-color' = $Color; 'line-width' = $Width }
    }
}

# Pile routière Roadly, du dessous vers le dessus ; largeurs : interpolate sur le zoom au premier niveau (exigence MapLibre).
function New-RoadLayers($p) {
    $majorColor = @('match', @('get', 'class'), 'trunk', $p.trunk, 'primary', $p.primary, 'secondary', $p.secondary, $p.tertiary)
    $layers = New-Object System.Collections.ArrayList

    # Tunnels dès le zoom 6, comme les autoroutes (sinon les tunnels autoroutiers disparaissent entre 6 et 10) ;
    # au zoom 6, 1,3 × 1,3 ≈ 1,7 px pour une autoroute, la largeur de roadly_road_motorway. Les tuiles des bas zooms
    # ne contiennent que les grands axes : aucune rue en tunnel n'apparaît avant son zoom d'origine.
    $f = @('all', $isLine, $isTunnel, @('match', @('get', 'class'), $allClasses, $true, $false))
    $w = @('interpolate', @('exponential', 1.3), @('zoom'), 6, @('*', 1.3, $classFactor), 10, @('*', 2, $classFactor), 20, @('*', 20, $classFactor))
    $l = New-RoadLine 'roadly_road_tunnel' 6 'butt' $f $p.tunnel $w
    $l.paint['line-dasharray'] = @(2, 2)
    $l.paint['line-opacity'] = 0.6
    [void]$layers.Add($l)

    $f = @('all', $isLine, $notTunnel, @('match', @('get', 'class'), $minorClasses, $true, $false))
    $w = @('interpolate', @('exponential', 1.55), @('zoom'), 13, @('*', 1.8, $minorFactor), 20, @('*', 20, $minorFactor))
    [void]$layers.Add((New-RoadLine 'roadly_road_minor' 8 'round' $f $p.minor $w))

    $f = @('all', $isLine, $isBridge, @('match', @('get', 'class'), $bridgeClasses, $true, $false))
    $w = @('interpolate', @('exponential', 1.3), @('zoom'), 10, @('*', 4, $bridgeFactor, $ramp), 20, @('*', 30, $bridgeFactor, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_bridge_casing' 11 'butt' $f $p.bridgeCasing $w))

    $f = @('all', $isLine, $notTunnel, @('match', @('get', 'class'), $majorClasses, $true, $false))
    $w = @('interpolate', @('exponential', 1.3), @('zoom'), 10, @('*', 3, $majorFactor, $ramp), 20, @('*', 23, $majorFactor, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_major_casing' 11 'butt' $f $p.casing $w))
    $w = @('interpolate', @('exponential', 1.3), @('zoom'), 10, @('*', 2, $majorFactor, $ramp), 20, @('*', 20, $majorFactor, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_major' 11 'round' $f $majorColor $w))

    $f = @('all', $isLine, $notTunnel, @('==', @('get', 'class'), 'motorway'))
    $w = @('interpolate', @('exponential', 1.4), @('zoom'), 6, @('*', 3.9, $ramp), 20, @('*', 52, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_motorway_casing' 6 'butt' $f $p.casing $w))
    $w = @('interpolate', @('exponential', 1.4), @('zoom'), 6, @('*', 1.7, $ramp), 20, @('*', 39, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_motorway' 6 'round' $f $p.motorway $w))
    return , $layers
}

# Points d'intérêt des tuiles (source-layer poi) : hôpitaux, carburant et gares dès le zoom 14 (icônes du sprite ofm), le reste dès 16.
function New-PoiLayers($p) {
    $text = @('coalesce', @('get', 'name:fr'), @('get', 'name'))
    $font = @('Noto Sans Regular')
    $major = [ordered]@{
        id = 'roadly_poi_major'; type = 'symbol'; source = 'openmaptiles'; 'source-layer' = 'poi'; minzoom = 14
        filter = @('match', @('get', 'class'), $poiMajorClasses, $true, $false)
        layout = [ordered]@{ 'icon-image' = @('get', 'class'); 'text-field' = $text; 'text-font' = $font; 'text-size' = 11; 'text-anchor' = 'top'; 'text-offset' = @(0, 1.1); 'text-optional' = $true }
        paint = [ordered]@{ 'text-color' = $p.poiText; 'text-halo-color' = $p.poiHalo; 'text-halo-width' = 1 }
    }
    $other = [ordered]@{
        id = 'roadly_poi_other'; type = 'symbol'; source = 'openmaptiles'; 'source-layer' = 'poi'; minzoom = 16
        filter = @('match', @('get', 'class'), $poiMajorClasses, $false, $true)
        layout = [ordered]@{ 'text-field' = $text; 'text-font' = $font; 'text-size' = 10; 'text-max-width' = 8 }
        paint = [ordered]@{ 'text-color' = $p.poiText; 'text-halo-color' = $p.poiHalo; 'text-halo-width' = 1 }
    }
    return , @($major, $other)
}

$roadIds = @(
    'highway_minor', 'highway_major_casing', 'highway_major_inner', 'highway_motorway_casing', 'highway_motorway_inner',
    'highway_motorway_bridge_casing', 'highway_motorway_bridge_inner', 'tunnel_motorway_casing', 'tunnel_motorway_inner'
)
$variants = @(
    [ordered]@{ key = 'dark';  upstream = 'dark.json';     asset = 'roadly-dark.json';  name = 'Roadly sombre' },
    [ordered]@{ key = 'light'; upstream = 'positron.json'; asset = 'roadly-light.json'; name = 'Roadly clair' }
)

foreach ($v in $variants) {
    $p = $palettes[$v.key]
    $style = Get-Content -Raw -Encoding UTF8 (Join-Path $toolsDir "upstream\$($v.upstream)") | ConvertFrom-Json
    $out = New-Object System.Collections.ArrayList
    $removed = 0; $roadsAdded = $false; $poiAdded = $false
    foreach ($l in $style.layers) {
        if ($roadIds -contains $l.id) {
            $removed++
            # La pile prend la place de highway_minor : chemins (highway_path) et jetées restent dessous dans les deux styles.
            if ($l.id -eq 'highway_minor') { foreach ($r in (New-RoadLayers $p)) { [void]$out.Add($r) }; $roadsAdded = $true }
            continue
        }
        if (-not $poiAdded -and $l.'source-layer' -eq 'place') { foreach ($r in (New-PoiLayers $p)) { [void]$out.Add($r) }; $poiAdded = $true }
        if ($l.type -eq 'fill' -and $l.'source-layer' -eq 'water') { $l.paint.'fill-color' = $p.water }
        if ($l.type -eq 'fill' -and ($l.'source-layer' -eq 'park' -or $l.id -eq 'landuse_park')) { $l.paint.'fill-color' = $p.park }
        if ($v.key -eq 'dark') {
            $paintOverride = $nightBase[$l.id]
            if ($null -eq $paintOverride -and $l.id -like 'place_*') { $paintOverride = $nightPlaceText }
            if ($null -ne $paintOverride) {
                foreach ($k in $paintOverride.Keys) { $l.paint | Add-Member -Force -NotePropertyName $k -NotePropertyValue $paintOverride[$k] }
            }
            # Le motif amont des bois masquerait leur couleur : aplat vert.
            if ($l.id -eq 'landcover_wood') { $l.paint.PSObject.Properties.Remove('fill-pattern') }
        }
        if ($l.id -eq 'highway_major_subtle') { $l.paint.'line-color' = $p.primary }
        if ($l.id -eq 'highway_motorway_subtle') { $l.paint.'line-color' = $p.motorway }
        if ($l.type -eq 'symbol' -and $l.'source-layer' -eq 'transportation_name' -and $l.id -match 'name') {
            $l.layout | Add-Member -Force -NotePropertyName 'symbol-spacing' -NotePropertyValue 400
            $ts = $l.layout.'text-size'
            if ($ts -is [System.Array]) {
                $lastZoom = [double]$ts[$ts.Count - 2]; $lastSize = [double]$ts[$ts.Count - 1]; $smaller = $lastSize - 1
                if ($ts[0] -eq 'interpolate' -and $lastZoom -lt 15) { $l.layout.'text-size' = @($ts) + @(15, $smaller) }
            } elseif ($null -ne $ts) {
                $size = [double]$ts; $smaller = $size - 1
                $l.layout.'text-size' = @('step', @('zoom'), $size, 15, $smaller)
            }
        }
        [void]$out.Add($l)
        if ($l.id -eq 'landcover_wood' -and $null -ne $p.fields) { [void]$out.Add((New-FieldsLayer $p)) }
    }
    if ($removed -lt 5 -or -not $roadsAdded -or -not $poiAdded) {
        throw "Style amont $($v.upstream) inattendu : $removed calques routiers retirés, points d'intérêt ajoutés : $poiAdded"
    }
    # Calques à plat d'abord, symboles ensuite (ordre relatif conservé dans chaque groupe) : l'extrusion des bâtiments,
    # ancrée par MapController sous le premier symbole, passe ainsi au-dessus des routes, des voies ferrées et des limites.
    $lastFlat = -1
    for ($i = 0; $i -lt $out.Count; $i++) { if ($out[$i].type -ne 'symbol') { $lastFlat = $i } }
    $layers = New-Object System.Collections.ArrayList
    $movedSymbols = 0
    for ($i = 0; $i -lt $out.Count; $i++) { if ($out[$i].type -ne 'symbol') { [void]$layers.Add($out[$i]) } elseif ($i -lt $lastFlat) { $movedSymbols++ } }
    foreach ($l in $out) { if ($l.type -eq 'symbol') { [void]$layers.Add($l) } }
    $style.layers = $layers.ToArray()
    $style | Add-Member -Force -NotePropertyName name -NotePropertyValue $v.name
    $meta = [ordered]@{ roadly = [ordered]@{ version = $RoadlyStyleVersion; base = "openfreemap/$($v.upstream)"; generator = 'core/map/tools/build-roadly-styles.ps1' } }
    $style | Add-Member -Force -NotePropertyName metadata -NotePropertyValue $meta
    $json = $style | ConvertTo-Json -Depth 50 -Compress
    [System.IO.File]::WriteAllText((Join-Path $assetsDir $v.asset), $json, (New-Object System.Text.UTF8Encoding($false)))
    '{0} : {1} calques, {2} calques routiers remplacés par 7, 2 calques de points d''intérêt ajoutés, {3} calques symboles remontés' -f $v.asset, $layers.Count, $removed, $movedSymbols
}
