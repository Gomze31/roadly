// Image d'en-tête du README : titre et trois écrans composés. node hero.js <dossier des écrans> <dossier des polices> <sortie.png>
const {chromium}=require('playwright');const fs=require('fs');const path=require('path');
const [dir,fonts,out]=process.argv.slice(2);
const b64=f=>fs.readFileSync(f).toString('base64');
const img=n=>`data:image/png;base64,${b64(path.join(dir,n))}`;
const font=(n,f,w)=>`@font-face{font-family:${n};font-weight:${w};src:url(data:font/ttf;base64,${b64(path.join(fonts,f))})}`;
const html=`<style>${font('Nunito','nunito_black.ttf',900)}${font('Nunito','nunito_extrabold.ttf',800)}${font('Inter','inter_regular.ttf',400)}
body{margin:0;width:2400px;height:1250px;overflow:hidden;background:radial-gradient(1200px 900px at 78% 20%,#1B3F8F 0%,#0E1A3D 45%,#070A18 100%);color:#F5F8FF;font-family:Inter}
h1{font:900 196px/1 Nunito;margin:0;position:absolute;left:128px;top:280px}
.s1{position:absolute;left:128px;top:476px;font:800 56px/1.2 Nunito;color:#73AEFF}.s2{position:absolute;left:128px;top:552px;font:800 56px/1.2 Nunito}
.l{position:absolute;left:128px;top:672px;font:400 39px/1.5 Inter;color:#C7D3EE}
.t{position:absolute;left:128px;top:900px;display:flex;gap:20px}.t span{font:800 34px/1 Nunito;background:#3A84F7;border-radius:40px;padding:16px 30px}
.p{position:absolute;border-radius:64px;border:10px solid #050814;box-shadow:0 40px 90px rgba(0,0,0,.55);overflow:hidden;background:#050814}
.p img{display:block;width:100%}</style>
<h1>Roadly</h1><div class=s1>Le GPS libre pour Android</div><div class=s2>qui respecte votre vie privée.</div>
<div class=l>Guidage vocal · Horizon · Zones de vigilance<br>ZFE · Péages · Coût du trajet · Android Auto<br>Données ouvertes · Aucun traqueur · Apache-2.0</div>
<div class=t><span>Android 10+</span><span>Android Auto</span><span>Open source</span></div>
<div class=p style="left:1090px;top:180px;width:480px;height:1010px"><img src="${img('screen-route.png')}"></div>
<div class=p style="left:1830px;top:70px;width:500px;height:1110px"><img src="${img('screen-home.png')}"></div>
<div class=p style="left:1470px;top:30px;width:540px;height:1180px"><img src="${img('screen-nav.png')}"></div>`;
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:2400,height:1250},deviceScaleFactor:1});await p.setContent(html);await p.evaluate(()=>document.fonts.ready);await p.screenshot({path:out});await b.close()})();
