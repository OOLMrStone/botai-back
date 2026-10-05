import { createRequire } from 'node:module';

const katex = createRequire(process.env.BOTAI_KATEX_PACKAGE)('katex');

let value = '';
for await (const chunk of process.stdin) {
  value += chunk;
  if (Buffer.byteLength(value, 'utf8') > 16_000) process.exit(1);
}
try {
  if (/\\(?:href|url|html\w*|includegraphics|def|gdef|edef|xdef|let|futurelet|newcommand|renewcommand|providecommand|global|csname|input|include|write|openout|read|catcode)\b/.test(value)) process.exit(1);
  katex.renderToString(value, { trust: false, throwOnError: true, strict: 'error', maxExpand: 1000, maxSize: 10, macros: {} });
} catch {
  process.exit(1);
}
