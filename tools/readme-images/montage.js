// Montage d'illustration : overlay Roadly (rendu de test sur aplat uni) posé sur un rendu MapLibre GL JS du style Roadly.
// node montage.js <style.json> <overlay.png> <cléHex | overlay-sur-blanc.png> <sortie.png> <lon> <lat> <mode: nav|navzone|plan|carte>
// nav : tracé et voiture ; navzone : en plus, une zone de danger en cours sur le tracé ; plan : tracé vu de dessus ; carte : carte seule.
// Avec un second rendu sur fond blanc (le premier étant sur fond noir), la transparence est calculée par différence.
const path = require('path');
const fs = require('fs');
const http = require('http');
const BASE = process.env.MAPLIBRE_DIR;
const { chromium } = require('playwright');
const [style, overlay, key, out, lon, lat, mode] = process.argv.slice(2);
const dist = path.join(BASE, 'node_modules/maplibre-gl/dist');
const png = fs.readFileSync(overlay);
const W = png.readUInt32BE(16), H = png.readUInt32BE(20);
const car = fs.readFileSync(path.join(__dirname, "car.svg"), "utf8");
const dark = /dark/.test(style);

const page = `<link rel=stylesheet href=/gl.css><body style="margin:0"><div id=m style="width:100vw;height:100vh"></div><script src=/gl.js></script><script>
const zone=${JSON.stringify(mode)}==='navzone',mode=zone?'nav':${JSON.stringify(mode)},dark=${dark};
const map=new maplibregl.Map({container:'m',style:'/style.json',center:[${lon},${lat}],zoom:14.5,attributionControl:false,fadeDuration:0,preserveDrawingBuffer:true});
const idle=()=>new Promise(r=>map.once('idle',r));
const brg=(a,b)=>{const t=Math.PI/180,y=Math.sin((b[0]-a[0])*t)*Math.cos(b[1]*t),x=Math.cos(a[1]*t)*Math.sin(b[1]*t)-Math.sin(a[1]*t)*Math.cos(b[1]*t)*Math.cos((b[0]-a[0])*t);return Math.atan2(y,x)/t};
(async()=>{await idle();
  if(mode!=='carte'){
    let best=null;const all=[];
    const len=c=>{let s=0;for(let i=1;i<c.length;i++)s+=Math.hypot(c[i][0]-c[i-1][0],c[i][1]-c[i-1][1]);return s};
    for(const f of map.queryRenderedFeatures({layers:['roadly_road_motorway','roadly_road_major']})){
      if(f.geometry.type!=='LineString')continue;
      const n=len(f.geometry.coordinates)*(['motorway','trunk'].includes(f.properties.class)&&f.properties.ramp!==1?5:1);
      all.push(f.geometry.coordinates);if(!best||n>best.n)best={n,c:f.geometry.coordinates};
    }
    let c=best.c.slice();
    // Prolonge le tronçon par ceux qui s'y raccordent bout à bout (les tuiles découpent les routes).
    for(let k=0;k<12;k++){const e=c[c.length-1];const h=brg(c[c.length-2],e);const nx=all.find(a=>a!==best.c&&a.length>1&&Math.hypot(a[0][0]-e[0],a[0][1]-e[1])<2e-5&&Math.abs(((brg(a[0],a[1])-h+540)%360)-180)<25);if(!nx)break;c=c.concat(nx.slice(1));all.splice(all.indexOf(nx),1);}
    const i=Math.max(0,Math.floor(c.length*0.15)),ahead=c.slice(i),cut=Math.max(2,Math.floor(ahead.length*0.6));
    const line=(id,coords,color,w)=>{map.addSource(id,{type:'geojson',data:{type:'Feature',geometry:{type:'LineString',coordinates:coords}}});map.addLayer({id,type:'line',source:id,layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':color,'line-width':w}});};
    line('r0',ahead,dark?'#050814':'#FFFFFF',mode==='nav'?20:11);
    line('r1',ahead,dark?'#4D9BFF':'#2F7BF6',mode==='nav'?13:7);
    if(mode==='nav'){
      if(zone){line('z0',ahead.slice(0,cut),'#FFFFFF',20);line('z1',ahead.slice(0,cut),'#FF4D4F',14);}
      const img=new Image(88,132);img.src='data:image/svg+xml;base64,'+btoa(${JSON.stringify(car)});await img.decode();map.addImage('roadly-car',img,{pixelRatio:2});
      map.addSource('u',{type:'geojson',data:{type:'Feature',geometry:{type:'Point',coordinates:ahead[0]}}});
      map.addLayer({id:'u',type:'symbol',source:'u',layout:{'icon-image':'roadly-car','icon-allow-overlap':true,'icon-size':1.25}});
      map.jumpTo({center:ahead[0],bearing:brg(ahead[0],ahead[1]),pitch:60,zoom:16.6,padding:{top:innerHeight*0.24,bottom:0,left:0,right:0}});
      for(const id of ['roadly_poi_major','roadly_poi_other'])map.setLayoutProperty(id,'visibility','none');
    } else {
      const b=new maplibregl.LngLatBounds();ahead.forEach(p=>b.extend(p));
      map.fitBounds(b,{padding:{top:innerHeight*0.3,bottom:innerHeight*0.5,left:60,right:60},animate:false});
    }
    await idle();
  }
  document.title='pret';})();</script>`;

const srv = http.createServer((q, r) => {
  if (q.url === '/style.json') { r.setHeader('content-type', 'application/json'); return r.end(fs.readFileSync(style)); }
  if (q.url === '/gl.js') return r.end(fs.readFileSync(path.join(dist, 'maplibre-gl.js')));
  if (q.url === '/gl.css') { r.setHeader('content-type', 'text/css'); return r.end(fs.readFileSync(path.join(dist, 'maplibre-gl.css'))); }
  r.setHeader('content-type', 'text/html');
  r.end(page);
}).listen(0, async () => {
  const port = srv.address().port;
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: Math.round(W / 2), height: Math.round(H / 2) }, deviceScaleFactor: 2 });
  p.on('pageerror', e => console.log('ERREUR', e.message));
  await p.goto(`http://127.0.0.1:${port}/`);
  try { await p.waitForFunction(() => document.title === 'pret', null, { timeout: 120000 }); } catch (e) { console.log('délai dépassé'); }
  const carte = (await p.screenshot()).toString('base64');
  const q = await b.newPage();
  const url = await q.evaluate(async ({ carte, ov, key, W, H, white }) => {
    const load = s => new Promise(r => { const i = new Image(); i.onload = () => r(i); i.src = 'data:image/png;base64,' + s; });
    const [m, o] = await Promise.all([load(carte), load(ov)]);
    const c = document.createElement('canvas'); c.width = W; c.height = H; const x = c.getContext('2d');
    const t = document.createElement('canvas'); t.width = W; t.height = H; const y = t.getContext('2d'); y.drawImage(o, 0, 0);
    const d = y.getImageData(0, 0, W, H), k = [parseInt(key.slice(0, 2), 16), parseInt(key.slice(2, 4), 16), parseInt(key.slice(4, 6), 16)];
    if (white) {
      const u = document.createElement('canvas'); u.width = W; u.height = H; const z = u.getContext('2d'); z.drawImage(await load(white), 0, 0);
      const w = z.getImageData(0, 0, W, H).data;
      for (let i = 0; i < d.data.length; i += 4) {
        const a = 1 - ((w[i] - d.data[i]) + (w[i + 1] - d.data[i + 1]) + (w[i + 2] - d.data[i + 2])) / 765;
        if (a <= 0.003) { d.data[i + 3] = 0; continue; }
        for (let j = 0; j < 3; j++) d.data[i + j] = Math.min(255, d.data[i + j] / a);
        d.data[i + 3] = Math.round(a * 255);
      }
    } else for (let i = 0; i < d.data.length; i += 4) { if (Math.abs(d.data[i] - k[0]) + Math.abs(d.data[i + 1] - k[1]) + Math.abs(d.data[i + 2] - k[2]) < 14) d.data[i + 3] = 0; }
    y.putImageData(d, 0, 0); x.drawImage(m, 0, 0, W, H); x.drawImage(t, 0, 0); return c.toDataURL('image/png');
  }, { carte, ov: png.toString('base64'), key, W, H, white: fs.existsSync(key) ? fs.readFileSync(key).toString('base64') : null });
  fs.writeFileSync(out, Buffer.from(url.split(',')[1], 'base64'));
  await b.close(); srv.close();
});
