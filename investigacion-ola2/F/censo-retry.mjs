// Reintento único (no cohere: 429 = stop duro). Añade resultados al JSON del censo.
import { chat } from '/home/hatch/workspace/omni-client.mjs';
import fs from 'node:fs';

const Q = "Un enlace facebook.com/share/r/XXXX es opaco: no revela su URL canónica ni el ID del video sin sesión. ¿Cómo se resuelve a su URL canónica o ID de video sin sesión, sin servidor y sin pagar? Responde en 3 líneas: método, por qué funciona sin servidor, y su punto débil.";
const OUT = '/home/hatch/workspace/coleccion-enlaces/censo-resolver-share.json';
const MODELS = ['auto/thrifty','auto/coding','groq/qwen/qwen3.8-27b','groq/openai/gpt-oss-safeguard-20b'];

const data = JSON.parse(fs.readFileSync(OUT, 'utf8'));
for (const model of MODELS) {
  const t = Date.now();
  try {
    const r = await chat(model, [{role:'user', content: Q}], { max_tokens: 600, temperature: 0.3, timeout: 25000 });
    const text = r?.choices?.[0]?.message?.content ?? JSON.stringify(r).slice(0,500);
    data.results.push({ model, ok: true, ms: Date.now()-t, response: text, retry: true });
    console.log(`OK  ${model} (${Date.now()-t} ms)`);
  } catch (e) {
    data.results.push({ model, ok: false, ms: Date.now()-t, error: String(e?.message ?? e).slice(0,300), retry: true });
    console.log(`FAIL ${model}: ${String(e?.message ?? e).slice(0,120)}`);
  }
}
data.finished = new Date().toISOString();
data.attempted = data.results.length;
fs.writeFileSync(OUT, JSON.stringify(data, null, 2));
console.log(`Total: ${data.results.filter(r=>r.ok).length}/${data.results.length} respondieron.`);
