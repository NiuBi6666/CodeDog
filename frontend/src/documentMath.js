import katex from "katex";

const MATH_CLASS = "math-formula";
const MAX_LATEX_LENGTH = 4000;
const RAW_LATEX_COMMAND = /\\(?:frac|dfrac|tfrac|sqrt|sum|prod|int|oint|lim|binom|begin|left|right|overline|underline|vec|hat|bar|log|ln|sin|cos|tan|cdot|times|div|leq?|geq?|neq|approx|equiv|infty|pi|alpha|beta|gamma|delta|theta|lambda|mu|sigma|phi|omega|Delta|Sigma|Omega)\b/;
const RAW_TEX_SYNTAX = /(?:\\[A-Za-z]+|[\^_]\{[^{}]{1,200}\})/;
const MATH_SYMBOLS = /[⌊⌋⌈⌉∑∏√≤≥≠≈≡×÷∞∫]/;
const CJK = /[\u3400-\u9fff]/;
const SKIPPED_TAGS = new Set(["CODE", "PRE", "SCRIPT", "STYLE", "TEXTAREA"]);

function stripDelimiters(value) {
  const source = (value || "").trim();
  if (source.startsWith("$$") && source.endsWith("$$")) return { latex: source.slice(2, -2).trim(), display: true };
  if (source.startsWith("\\[") && source.endsWith("\\]")) return { latex: source.slice(2, -2).trim(), display: true };
  if (source.startsWith("\\(") && source.endsWith("\\)")) return { latex: source.slice(2, -2).trim(), display: false };
  if (source.startsWith("$") && source.endsWith("$") && source.length > 2) return { latex: source.slice(1, -1).trim(), display: false };
  return { latex: source, display: false };
}

function findUnescaped(text, token, from) {
  let index = text.indexOf(token, from);
  while (index >= 0) {
    let slashes = 0;
    for (let cursor = index - 1; cursor >= 0 && text[cursor] === "\\"; cursor -= 1) slashes += 1;
    if (slashes % 2 === 0) return index;
    index = text.indexOf(token, index + token.length);
  }
  return -1;
}

function earliestDelimiter(text, from) {
  const delimiters = [
    { open: "$$", close: "$$", display: true },
    { open: "\\[", close: "\\]", display: true },
    { open: "\\(", close: "\\)", display: false },
    { open: "$", close: "$", display: false },
  ];
  let match = null;
  for (const delimiter of delimiters) {
    const index = findUnescaped(text, delimiter.open, from);
    if (index < 0 || (delimiter.open === "$" && text.startsWith("$$", index))) continue;
    if (!match || index < match.index || (index === match.index && delimiter.open.length > match.open.length))
      match = { ...delimiter, index };
  }
  return match;
}

function rawMathSegment(text) {
  const trimmed = text.trim();
  if (!trimmed || trimmed.length > MAX_LATEX_LENGTH) return null;
  let formulaStart = 0;
  if (CJK.test(trimmed)) {
    const command = trimmed.match(RAW_LATEX_COMMAND);
    if (!command || command.index == null) return null;
    formulaStart = command.index;
  }
  let formula = trimmed.slice(formulaStart);
  const punctuation = formula.match(/[，。；：！？]+$/)?.[0] || "";
  if (punctuation) formula = formula.slice(0, -punctuation.length).trimEnd();
  if (!RAW_LATEX_COMMAND.test(formula) && !RAW_TEX_SYNTAX.test(formula) && !MATH_SYMBOLS.test(formula)) return null;
  try { katex.renderToString(formula, { strict: "ignore", throwOnError: true, trust: false }); }
  catch (_error) { return null; }
  const trimmedStart = text.indexOf(trimmed);
  const start = trimmedStart + formulaStart;
  const segments = [];
  if (start > 0) segments.push({ type: "text", value: text.slice(0, start) });
  segments.push({ type: "math", value: formula, display: false });
  if (start + formula.length < text.length) segments.push({ type: "text", value: text.slice(start + formula.length) });
  return segments;
}

export function parseMathText(text) {
  const segments = [];
  let cursor = 0;
  let found = false;
  while (cursor < text.length) {
    const delimiter = earliestDelimiter(text, cursor);
    if (!delimiter) break;
    const contentStart = delimiter.index + delimiter.open.length;
    const close = findUnescaped(text, delimiter.close, contentStart);
    if (close < 0) break;
    if (delimiter.index > cursor) segments.push({ type: "text", value: text.slice(cursor, delimiter.index) });
    const latex = text.slice(contentStart, close).trim();
    if (latex) {
      segments.push({ type: "math", value: latex, display: delimiter.display });
      found = true;
    } else segments.push({ type: "text", value: text.slice(delimiter.index, close + delimiter.close.length) });
    cursor = close + delimiter.close.length;
  }
  if (found) {
    if (cursor < text.length) segments.push({ type: "text", value: text.slice(cursor) });
    return segments;
  }
  return rawMathSegment(text) || [{ type: "text", value: text }];
}

export function createMathElement(doc, source, display = false) {
  const normalized = stripDelimiters(source);
  const span = doc.createElement("span");
  span.className = MATH_CLASS;
  span.dataset.latex = normalized.latex.slice(0, MAX_LATEX_LENGTH);
  span.dataset.display = String(display || normalized.display);
  return span;
}

export function renderMathElement(element) {
  const latex = (element.dataset.latex || "").trim();
  if (!latex) return;
  element.className = MATH_CLASS;
  element.contentEditable = "false";
  element.setAttribute("aria-label", `数学公式：${latex}`);
  katex.render(latex, element, {
    displayMode: element.dataset.display === "true",
    output: "htmlAndMathml",
    strict: "ignore",
    throwOnError: false,
    trust: false,
  });
}

function findFormulaValue(value) {
  if (!value || value.length > 20000) return "";
  try {
    const parsed = JSON.parse(value);
    const queue = [parsed];
    while (queue.length) {
      const item = queue.shift();
      if (!item || typeof item !== "object") continue;
      for (const [key, child] of Object.entries(item)) {
        if (["latex", "tex", "formula", "equation"].includes(key.toLowerCase()) && typeof child === "string") return child;
        if (typeof child === "object") queue.push(child);
      }
    }
  } catch (_error) {
    return "";
  }
  return "";
}

function normalizeEmbeddedFormulas(container) {
  const candidates = [...container.querySelectorAll("[data-latex],[data-formula],[data-equation],[data-tex],[data-card-value]")];
  for (const candidate of candidates) {
    if (candidate.classList.contains(MATH_CLASS) || candidate.closest(`.${MATH_CLASS}`)) continue;
    const source = candidate.getAttribute("data-latex") || candidate.getAttribute("data-formula")
      || candidate.getAttribute("data-equation") || candidate.getAttribute("data-tex")
      || findFormulaValue(candidate.getAttribute("data-card-value"));
    if (!source) continue;
    candidate.replaceWith(createMathElement(container.ownerDocument, source));
  }
  for (const annotation of [...container.querySelectorAll('annotation[encoding="application/x-tex"]')]) {
    const math = annotation.closest("math");
    if (math && annotation.textContent.trim()) math.replaceWith(createMathElement(container.ownerDocument, annotation.textContent));
  }
}

function normalizeTextFormulas(container) {
  const doc = container.ownerDocument;
  const walker = doc.createTreeWalker(container, 4);
  const nodes = [];
  while (walker.nextNode()) nodes.push(walker.currentNode);
  for (const node of nodes) {
    const parent = node.parentElement;
    if (!parent || SKIPPED_TAGS.has(parent.tagName) || parent.closest(`.${MATH_CLASS},.katex`)) continue;
    const segments = parseMathText(node.nodeValue || "");
    if (!segments.some((segment) => segment.type === "math")) continue;
    const fragment = doc.createDocumentFragment();
    for (const segment of segments) fragment.appendChild(segment.type === "math"
      ? createMathElement(doc, segment.value, segment.display) : doc.createTextNode(segment.value));
    node.replaceWith(fragment);
  }
}

export function enhanceDocumentMath(container) {
  if (!container) return;
  normalizeEmbeddedFormulas(container);
  normalizeTextFormulas(container);
  for (const formula of container.querySelectorAll(`.${MATH_CLASS}[data-latex]`)) renderMathElement(formula);
}

export function serializeDocumentContent(container) {
  const clone = container.cloneNode(true);
  for (const formula of clone.querySelectorAll(`.${MATH_CLASS}[data-latex]`)) {
    formula.className = MATH_CLASS;
    formula.removeAttribute("contenteditable");
    formula.removeAttribute("aria-label");
    formula.replaceChildren();
  }
  return clone.innerHTML;
}
