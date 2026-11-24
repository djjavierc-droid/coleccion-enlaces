// HILO F — ola 2: censo resolver /share/r/ -> canónica. Solo OmniRoute.
import { chat } from '/home/hatch/workspace/omni-client.mjs';
import fs from 'node:fs';

const Q = "Un enlace facebook.com/share/r/XXXX es opaco: no revela su URL canónica ni el ID del video sin sesión. ¿Cómo se resuelve a su URL canónica o ID de video sin sesión, sin servidor y sin pagar? Responde en 3 líneas: método, por qué funciona sin servidor, y su punto débil.";

const MODELS = [
  // ddgw (6)
  'ddgw/gpt-5.4-mini','ddgw/gpt-5.6-luna','ddgw/claude-haiku-4-5','ddgw/mistral-small-2603','ddgw/tinfoil/gpt-oss-120b','ddgw/tinfoil/gemma4-31b',
  // cohere chat (6)
  'cohere/command-a-reasoning-08-2025','cohere/command-a-vision-07-2025','cohere/command-a-03-2025','cohere/command-r7b-12-2024','cohere/command-r-plus-08-2024','cohere/command-r-08-2024',
  // groq chat (5)
  'groq/groq/compound','groq/allam-2-7b','groq/openai/gpt-oss-120b','groq/openai/gpt-oss-20b','groq/qwen/qwen3.6-27b',
  // auto representativos (10)
  'auto/best-fast','auto/best-chat','auto/fast','auto/chat','auto/cheap','auto/best-free','auto/smart','auto/thrifty','auto/reasoning','auto/coding',
];

const OUT = '/home/hatch/workspace/coleccion-enlaces/censo-resolver-share.json';
const results = [];
const t0 = Date.now();

async function one(model){
  const t = Date.now();
  try {
    const r = await chat(model, [{role:'user', content: Q}], { max_tokens: 600, temperature: 0.3, timeout: 20000 });
    const text = r?.choices?.[0]?.message?.content ?? JSON.stringify(r).slice(0,500);
    results.push({ model, ok: true, ms: Date.now()-t, response: text });
    console.log(`OK  ${model} (${Date.now()-t} ms)`);
  } catch (e) {
    results.push({ model, ok: false, ms: Date.now()-t, error: String(e?.message ?? e).slice(0,300) });
    console.log(`FAIL ${model}: ${String(e?.message ?? e).slice(0,120)}`);
  }
}

// lotes de 6 en paralelo
for (let i = 0; i < MODELS.length; i += 6) {
  await Promise.all(MODELS.slice(i, i+6).map(one));
  fs.writeFileSync(OUT, JSON.stringify({ question: Q, started: new Date(t0).toISOString(), finished: new Date().toISOString(), attempted: MODELS.length, results }, null, 2));
}
console.log(`\nHecho: ${results.filter(r=>r.ok).length}/${MODELS.length} respondieron en ${Math.round((Date.now()-t0)/1000)} s. Guardado en ${OUT}`);
