// Réduit des PNG à une largeur donnée : node reduire.js <largeur> <fichier.png>...
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [w, ...files] = process.argv.slice(2);
  const b = await chromium.launch();
  const p = await b.newPage();
  for (const f of files) {
    const url = await p.evaluate(async ({ src, w }) => {
      const i = new Image(); i.src = 'data:image/png;base64,' + src; await i.decode();
      const c = document.createElement('canvas'); c.width = w; c.height = Math.round(i.height * w / i.width);
      const x = c.getContext('2d'); x.imageSmoothingQuality = 'high'; x.drawImage(i, 0, 0, c.width, c.height);
      return c.toDataURL('image/png');
    }, { src: fs.readFileSync(f).toString('base64'), w: +w });
    fs.writeFileSync(f, Buffer.from(url.split(',')[1], 'base64'));
  }
  await b.close();
})();
