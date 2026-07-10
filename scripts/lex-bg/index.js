const express = require('express');
const cheerio = require('cheerio');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const cron = require('node-cron');

const app = express();
app.disable('x-powered-by');
app.use(express.json());

const PORT = process.env.PORT || 3200;
const BASE_URL = 'https://lex.bg';
const DELAY_MS = Number.parseInt(process.env.DELAY_MS || '1500', 10);
const CATALOG_TTL_MS = 24 * 60 * 60 * 1000;
const PRELOAD = process.env.PRELOAD === 'true';
const PRELOAD_PATH = process.env.PRELOAD_PATH ? path.resolve(process.env.PRELOAD_PATH) : null;
const PRELOAD_CRON = process.env.PRELOAD_CRON || null;
const PRELOAD_MAX_AGE_DAYS = Number.parseInt(process.env.PRELOAD_MAX_AGE_DAYS || '7', 10);

const FETCH_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (compatible; LawConnector/1.0)',
    'Accept-Language': 'bg,en;q=0.5',
};

// linkClass matches the unquoted class= attribute lex.bg uses on each tree page
const CATEGORIES = [
    { id: 'laws',              linkClass: 'law',      treeUrl: `${BASE_URL}/laws/tree/laws`      },
    { id: 'codes',             linkClass: 'code',     treeUrl: `${BASE_URL}/laws/tree/code`      },
    { id: 'regulations',       linkClass: 'law',      treeUrl: `${BASE_URL}/laws/tree/ords`      },
    { id: 'rules',             linkClass: 'regs',     treeUrl: `${BASE_URL}/laws/tree/regs`      },
    { id: 'rules-application', linkClass: 'reg_laws', treeUrl: `${BASE_URL}/laws/tree/reg_laws`  },
    { id: 'constitution',                             docUrl:  `${BASE_URL}/laws/ldoc/521957377` },
];

// ─── Helpers ─────────────────────────────────────────────────────────────────

function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
}

function isoWeek(date = new Date()) {
    const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
    d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
    const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
    const week = Math.ceil(((d - yearStart) / 86400000 + 1) / 7);
    return `${d.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}

function contentMd5(text) {
    return 'md5:' + crypto.createHash('md5').update(text).digest('hex');
}

function weeklyHash(id) {
    return 'md5:' + crypto.createHash('md5').update(`${id}:${isoWeek()}`).digest('hex');
}

function docIdFromUrl(url) {
    const m = url.match(/\/laws\/ldoc\/(-?\d+)/);
    return m ? m[1].replace(/^-/, '') : null;
}

async function fetchHtml(url) {
    const resp = await fetch(url, { headers: FETCH_HEADERS });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const buffer = await resp.arrayBuffer();
    return new TextDecoder('windows-1251').decode(buffer);
}

async function listCategoryUrls(treeUrl, linkClass) {
    const urls = [];
    let pageIdx = 0;
    while (true) {
        const pageUrl = pageIdx === 0 ? treeUrl : `${treeUrl}/${pageIdx}`;
        let html;
        try {
            html = await fetchHtml(pageUrl);
        } catch {
            break;
        }
        const $ = cheerio.load(html);
        const found = [];
        $(`a.${linkClass}[href]`).each((_, el) => {
            const href = $(el).attr('href');
            if (typeof href !== 'string' || !href.includes('/laws/ldoc/')) return;
            found.push(href.startsWith('http') ? href : `${BASE_URL}${href}`);
        });
        if (found.length === 0) break;
        urls.push(...found);
        pageIdx++;
        await sleep(DELAY_MS);
    }
    return urls;
}

function extractDoc(html) {
    const $ = cheerio.load(html);
    const title = $('#DocumentTitle .Title').first().text().trim();
    const box = $('.boxi.boxinb').first();
    box.find('script, style, div[align], #DocumentTitle, .pager').remove();
    const body = box.text().replace(/[ \t]+/g, ' ').replace(/\n{3,}/g, '\n\n').trim();
    return { title, body };
}

// ─── File cache ──────────────────────────────────────────────────────────────

const PRELOAD_META_FILE = PRELOAD_PATH ? path.join(PRELOAD_PATH, '.preload-meta.json') : null;

function loadPreloadMeta() {
    if (!PRELOAD_META_FILE) return {};
    try {
        return JSON.parse(fs.readFileSync(PRELOAD_META_FILE, 'utf8'));
    } catch {
        return {};
    }
}

function savePreloadMeta(meta) {
    if (!PRELOAD_META_FILE) return;
    try {
        fs.writeFileSync(PRELOAD_META_FILE, JSON.stringify(meta, null, 2), 'utf8');
    } catch { /* non-fatal */ }
}

function cachedFilePath(docId, categoryId) {
    if (!PRELOAD_PATH) return null;
    return path.join(PRELOAD_PATH, categoryId, `${docId}.md`);
}

function readFromPath(docId, categoryId) {
    const filePath = cachedFilePath(docId, categoryId);
    if (!filePath) return null;
    try {
        return fs.readFileSync(filePath, 'utf8');
    } catch {
        return null;
    }
}

function saveToPath(docId, categoryId, rawContent) {
    const filePath = cachedFilePath(docId, categoryId);
    if (!filePath) return;
    try {
        fs.mkdirSync(path.dirname(filePath), { recursive: true });
        fs.writeFileSync(filePath, rawContent, 'utf8');
    } catch { /* non-fatal */ }
}

// ─── Catalog ─────────────────────────────────────────────────────────────────

let catalog = null;
let catalogBuilding = null;
const contentCache = new Map();

// id → { fetchedAt: ISO }
let preloadMeta = loadPreloadMeta();

const preloadStats = {
    enabled: PRELOAD,
    cached: 0,
    total: 0,
    lastRunAt: null,
    running: false,
    cronExpression: PRELOAD_CRON,
};

async function collectCategoryDocs(cat) {
    const docUrls = cat.docUrl
        ? [cat.docUrl]
        : await (async () => {
            console.log(`  [${cat.id}] discovering…`);
            const urls = await listCategoryUrls(cat.treeUrl, cat.linkClass);
            console.log(`  [${cat.id}] ${urls.length} documents`);
            return urls;
        })();

    const docs = [];
    for (const url of docUrls) {
        const docId = docIdFromUrl(url);
        if (!docId) continue;
        const id = `${cat.id}/${docId}`;

        if (!contentCache.has(id)) {
            const cached = readFromPath(docId, cat.id);
            if (cached) contentCache.set(id, cached);
        }

        const contentHash = contentCache.has(id)
            ? contentMd5(contentCache.get(id))
            : weeklyHash(id);

        docs.push({ id, path: `${BASE_URL}/laws/ldoc/${docId}`, contentHash });
    }
    return docs;
}

function buildCatalog() {
    if (catalogBuilding) return catalogBuilding;

    catalogBuilding = (async () => {
        console.log('Building document catalog from lex.bg…');
        const docs = [];
        for (const cat of CATEGORIES) docs.push(...await collectCategoryDocs(cat));

        catalog = docs;
        catalogBuilding = null;
        preloadStats.total = docs.length;
        preloadStats.cached = docs.filter(d => contentCache.has(d.id)).length;
        console.log(`Catalog ready — ${docs.length} documents (${preloadStats.cached} loaded from path)`);

        if (PRELOAD) preloadContent(docs).catch(err => console.error('Preload failed:', err.message));
    })().catch(err => {
        console.error('Catalog build failed:', err.message);
        catalogBuilding = null;
    });

    return catalogBuilding;
}

async function preloadContent(docs) {
    if (preloadStats.running) return;
    preloadStats.running = true;
    preloadStats.lastRunAt = new Date().toISOString();

    const maxAgeMs = PRELOAD_MAX_AGE_DAYS * 24 * 60 * 60 * 1000;
    const now = Date.now();

    const toFetch = docs.filter(doc => {
        const entry = preloadMeta[doc.id];
        const isStale = !entry || now - new Date(entry.fetchedAt).getTime() > maxAgeMs;
        if (isStale) return true;           // always re-fetch stale — replaces cached content too
        return !contentCache.has(doc.id);   // fresh but missing from memory — load it
    });

    if (toFetch.length === 0) {
        console.log('Preload: all documents already cached');
        preloadStats.running = false;
        return;
    }

    console.log(`Preloading ${toFetch.length} documents from lex.bg…`);
    let done = 0;
    for (const doc of toFetch) {
        const slash = doc.id.indexOf('/');
        const categoryId = doc.id.slice(0, slash);
        const docId = doc.id.slice(slash + 1);
        try {
            const html = await fetchHtml(`${BASE_URL}/laws/ldoc/${docId}`);
            const { title, body } = extractDoc(html);
            if (body) {
                const rawContent = title ? `# ${title}\n\n${body}` : body;
                contentCache.set(doc.id, rawContent);
                saveToPath(docId, categoryId, rawContent);
                preloadMeta[doc.id] = { fetchedAt: new Date().toISOString() };
                savePreloadMeta(preloadMeta);
            }
        } catch { /* skip — will fall back to live fetch */ }

        done++;
        if (done % 100 === 0) console.log(`  preload ${done}/${toFetch.length}`);
        await sleep(DELAY_MS);
    }

    preloadStats.cached = contentCache.size;
    preloadStats.running = false;
    console.log(`Preload complete — ${contentCache.size} documents cached`);
}

buildCatalog();
setInterval(buildCatalog, CATALOG_TTL_MS);

if (PRELOAD_CRON) {
    if (!cron.validate(PRELOAD_CRON)) {
        console.error(`Invalid PRELOAD_CRON expression: "${PRELOAD_CRON}"`);
    } else {
        console.log(`Preload scheduled: ${PRELOAD_CRON}`);
        cron.schedule(PRELOAD_CRON, () => {
            buildCatalog().then(() => {
                if (catalog) preloadContent(catalog).catch(err => console.error('Scheduled preload failed:', err.message));
            }).catch(err => console.error('Scheduled catalog build failed:', err.message));
        });
    }
}

// ─── Routes ──────────────────────────────────────────────────────────────────

app.get('/connector/info', (_req, res) => {
    res.json({
        name: 'lex.bg',
        version: '1.0',
        fields: [],
    });
});

app.get('/connector/status', (_req, res) => {
    res.json({
        ...preloadStats,
        cached: contentCache.size,
        total: catalog?.length ?? 0,
    });
});

app.post('/connector/test', async (_req, res) => {
    try {
        const resp = await fetch(BASE_URL, { headers: FETCH_HEADERS });
        if (!resp.ok) return res.json({ ok: false, message: `lex.bg returned HTTP ${resp.status}` });
        res.json({
            ok: true,
            documentCount: catalog?.length ?? null,
            message: catalog
                ? `Connected — ${catalog.length} documents indexed`
                : 'Connected — catalog is still being built',
        });
    } catch (err) {
        res.json({ ok: false, message: err.message });
    }
});

app.post('/connector/documents', async (_req, res) => {
    if (!catalog) {
        await buildCatalog();
        if (!catalog) return res.status(503).json({ error: 'Catalog not yet available — try again shortly' });
    }
    const documents = catalog.map(doc => ({
        ...doc,
        contentHash: contentCache.has(doc.id) ? contentMd5(contentCache.get(doc.id)) : weeklyHash(doc.id),
    }));
    res.json({ documents });
});

app.post('/connector/fetch', async (req, res) => {
    const { documentId } = req.body;
    if (!documentId) return res.status(400).json({ error: 'Missing documentId' });

    const slash = documentId.indexOf('/');
    if (slash === -1) return res.status(400).json({ error: 'Invalid documentId — expected "{category}/{docId}"' });

    const cached = contentCache.get(documentId);
    if (cached) {
        const docId = documentId.slice(slash + 1);
        return res.json({ path: `${BASE_URL}/laws/ldoc/${docId}`, rawContent: cached });
    }

    const categoryId = documentId.slice(0, slash);
    const docId = documentId.slice(slash + 1);
    const url = `${BASE_URL}/laws/ldoc/${docId}`;

    let html;
    try {
        html = await fetchHtml(url);
    } catch (err) {
        return res.status(404).json({ error: `Failed to fetch document: ${err.message}` });
    }

    const { title, body } = extractDoc(html);
    if (!body) return res.status(404).json({ error: 'Document has no content' });

    const rawContent = title ? `# ${title}\n\n${body}` : body;
    contentCache.set(documentId, rawContent);
    saveToPath(docId, categoryId, rawContent);
    preloadMeta[documentId] = { fetchedAt: new Date().toISOString() };
    savePreloadMeta(preloadMeta);

    res.json({ path: url, rawContent });
});

app.listen(PORT, () => {
    console.log(`lex.bg connector running on http://localhost:${PORT}`);
    console.log(`  preload path: ${PRELOAD_PATH ?? 'none'}`);
    console.log('  GET  /connector/info');
    console.log('  GET  /connector/status');
    console.log('  POST /connector/test');
    console.log('  POST /connector/documents');
    console.log('  POST /connector/fetch');
});