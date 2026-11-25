// Lighthouse (escritorio y móvil 4G lento) sobre login y paneles de VITA+.
// Sesión: un login por API por rol; se inyecta en localStorage.sesionRecordada
// (el frontend la restaura al cargar). 3 corridas por página y modo; mediana.
// Uso: node medir.mjs <salida.json>
import fs from 'node:fs';
import puppeteer from 'puppeteer-core';
import lighthouse from 'lighthouse';
import desktopConfig from 'lighthouse/core/config/desktop-config.js';

const BASE = 'https://vita-plus.app';
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const CORRIDAS = 3;
const salida = process.argv[2] || 'lighthouse.json';

const conTti = (base) => ({
  ...base,
  settings: { ...(base.settings || {}), onlyCategories: ['performance'], disableStorageReset: true },
  audits: [...(base.audits || []), 'metrics/interactive'],
});
const MODOS = {
  escritorio: conTti(desktopConfig),
  // Por defecto Lighthouse móvil = Moto G Power, 4G lento simulado
  // (150 ms RTT, 1,6 Mbps) y CPU 4x más lenta.
  movil: conTti({ extends: 'lighthouse:default' }),
};

async function login(correo) {
  const r = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'User-Agent': 'QC-tiempos-respuesta' },
    body: JSON.stringify({ correo, contrasena: 'Vita2025*' }),
  });
  if (r.status !== 200) throw new Error(`Login falló ${correo}: ${r.status}`);
  const s = await r.json();
  console.log('Login OK', correo);
  return { token: s.token, rol: s.rol, idUsuario: String(s.idUsuario), nombreUsuario: s.nombreUsuario, refreshToken: s.refreshToken };
}

const mediana = (xs) => { const o = [...xs].sort((a, b) => a - b); return o[Math.floor(o.length / 2)]; };

async function medirGrupo(paginas, sesion, resultados) {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: true,
    args: ['--no-first-run', '--no-default-browser-check'],
  });
  try {
    const page = await browser.newPage();
    if (sesion) {
      await page.goto(BASE + '/iniciar-sesion', { waitUntil: 'domcontentloaded' });
      await page.evaluate((s) => localStorage.setItem('sesionRecordada', JSON.stringify(s)), sesion);
    }
    const cdp = await page.createCDPSession();
    for (const [nombre, ruta] of paginas) {
      for (const [modo, config] of Object.entries(MODOS)) {
        const corridas = [];
        for (let i = 0; i < CORRIDAS; i++) {
          // Caché HTTP vacía en cada corrida (la sesión en localStorage se conserva).
          await cdp.send('Network.clearBrowserCache');
          const { lhr } = await lighthouse(BASE + ruta, { logLevel: 'error' }, config, page);
          const a = lhr.audits;
          const c = {
            fcp: a['first-contentful-paint'].numericValue,
            lcp: a['largest-contentful-paint'].numericValue,
            tti: a['interactive']?.numericValue ?? null,
            tbt: a['total-blocking-time'].numericValue,
            puntaje: Math.round(lhr.categories.performance.score * 100),
            url_final: lhr.finalDisplayedUrl,
            error: lhr.runtimeError?.message ?? null,
          };
          corridas.push(c);
          console.log(`${nombre} ${modo} #${i + 1}: FCP ${Math.round(c.fcp)} LCP ${Math.round(c.lcp)} TTI ${Math.round(c.tti)} TBT ${Math.round(c.tbt)} score ${c.puntaje} -> ${c.url_final}`);
        }
        resultados.push({
          pagina: nombre, ruta, modo,
          fcp_ms: Math.round(mediana(corridas.map((c) => c.fcp))),
          lcp_ms: Math.round(mediana(corridas.map((c) => c.lcp))),
          tti_ms: Math.round(mediana(corridas.map((c) => c.tti))),
          tbt_ms: Math.round(mediana(corridas.map((c) => c.tbt))),
          puntaje: mediana(corridas.map((c) => c.puntaje)),
          corridas,
        });
        fs.writeFileSync(salida, JSON.stringify(resultados, null, 2));
      }
    }
  } finally {
    await browser.close();
  }
}

const resultados = [];
await medirGrupo([['Iniciar sesión', '/iniciar-sesion']], null, resultados);
await medirGrupo([
  ['Panel persona mayor (inicio)', '/panel/persona-mayor'],
  ['Panel persona mayor (signos vitales)', '/panel/persona-mayor/signos-vitales'],
], await login('pm001@vitaplus.test'), resultados);
await new Promise((r) => setTimeout(r, 7000));
await medirGrupo([['Panel organización (inicio)', '/panel/organizacion']], await login('org01@vitaplus.test'), resultados);
console.log('Guardado en', salida);
