const cheerio = require('cheerio');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const BASE_URL = 'https://lex.bg';
const OUTPUT_DIR = path.resolve(process.env.OUTPUT_DIR || path.join(__dirname, '../../crawlers-data/lex.bg'));
const META_FILE = path.join(OUTPUT_DIR, '.crawl-meta.json');
const DELAY_MS = Number.parseInt(process.env.DELAY_MS || '1500', 10);
const MAX_DOCS = Number.parseInt(process.env.MAX_DOCS || '0', 10);
const RECRAWL_DAYS = Number.parseInt(process.env.RECRAWL_DAYS || '7', 10);

const FETCH_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (compatible; LawCrawler/1.0)',
    'Accept-Language': 'bg,en;q=0.5',
};

const CATEGORIES = [
    { dir: 'laws',              treeUrl: `${BASE_URL}/laws/tree/laws`     },
    { dir: 'codes',             treeUrl: `${BASE_URL}/laws/tree/code`     },
    { dir: 'regulations',       treeUrl: `${BASE_URL}/laws/tree/ords`     },
    { dir: 'rules',             treeUrl: `${BASE_URL}/laws/tree/regs`     },
    { dir: 'rules-application', treeUrl: `${BASE_URL}/laws/tree/reg_laws` },
    { dir: 'constitution',      docUrl:  `${BASE_URL}/laws/ldoc/521957377` },
];

function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
}

function md5(text) {
    return crypto.createHash('md5').update(text).digest('hex');
}

function loadMeta() {
    try {
        return JSON.parse(fs.readFileSync(META_FILE, 'utf8'));
    } catch {
        return {};
    }
}

function saveMeta(meta) {
    fs.writeFileSync(META_FILE, JSON.stringify(meta, null, 2), 'utf8');
}

function isFresh(meta, key) {
    const entry = meta[key];
    if (!entry) return false;
    const ageMs = Date.now() - new Date(entry.crawledAt).getTime();
    return ageMs < RECRAWL_DAYS * 24 * 60 * 60 * 1000;
}

async function fetchHtml(url) {
    const resp = await fetch(url, { headers: FETCH_HEADERS });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const buffer = await resp.arrayBuffer();
    return new TextDecoder('windows-1251').decode(buffer);
}

function docIdFromUrl(url) {
    const m = url.match(/\/laws\/ldoc\/(-?\d+)/);
    return m ? m[1].replace(/^-/, '') : null;
}

async function listCategoryUrls(treeUrl) {
    const urls = [];
    let pageIdx = 0;

    while (true) {
        const pageUrl = pageIdx === 0 ? treeUrl : `${treeUrl}/${pageIdx}`;
        let html;
        try {
            html = await fetchHtml(pageUrl);
        } catch (err) {
            console.error(`  listing error ${pageUrl}: ${err.message}`);
            break;
        }

        const $ = cheerio.load(html);
        const found = [];
        $('a.law[href]').each((_, el) => {
            const href = $(el).attr('href');
            if (!href || !href.includes('/laws/ldoc/')) return;
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

async function crawlDoc(url, outDir, meta) {
    const id = docIdFromUrl(url);
    if (!id) return 'skip';

    const metaKey = `${path.basename(outDir)}/${id}`;

    if (isFresh(meta, metaKey)) {
        return 'recent';
    }

    let html;
    try {
        html = await fetchHtml(url);
    } catch (err) {
        console.error(`  error ${id} — ${err.message}`);
        return 'error';
    }

    const { title, body } = extractDoc(html);
    if (!body) {
        console.log(`  skip ${id} — no content`);
        return 'skip';
    }

    const content = `# ${title || id}\n\n${body}`;
    const contentHash = md5(content);
    const filePath = path.join(outDir, `${id}.md`);
    const existingHash = meta[metaKey]?.hash;

    if (contentHash !== existingHash) {
        fs.writeFileSync(filePath, content, 'utf8');
    }

    meta[metaKey] = { crawledAt: new Date().toISOString(), hash: contentHash };
    saveMeta(meta);

    return contentHash !== existingHash ? 'saved' : 'unchanged';
}

async function main() {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });

    const meta = loadMeta();
    const counts = { saved: 0, unchanged: 0, recent: 0, skip: 0, error: 0 };
    const total = () => Object.values(counts).reduce((a, b) => a + b, 0);

    for (const cat of CATEGORIES) {
        if (MAX_DOCS > 0 && total() >= MAX_DOCS) break;

        const outDir = path.join(OUTPUT_DIR, cat.dir);
        fs.mkdirSync(outDir, { recursive: true });

        let docUrls;
        if (cat.docUrl) {
            docUrls = [cat.docUrl];
        } else {
            console.log(`\n[${cat.dir}] Discovering…`);
            docUrls = await listCategoryUrls(cat.treeUrl);
            console.log(`[${cat.dir}] Found ${docUrls.length} documents`);
        }

        for (const url of docUrls) {
            if (MAX_DOCS > 0 && total() >= MAX_DOCS) break;

            const result = await crawlDoc(url, outDir, meta);
            counts[result]++;

            if (result === 'saved') {
                console.log(`  [${cat.dir}] saved ${docIdFromUrl(url)}`);
            } else if (result === 'unchanged' || result === 'recent') {
                process.stdout.write('.');
            }

            if (result !== 'recent') {
                await sleep(DELAY_MS);
            }
        }
    }

    console.log(`\nDone — saved: ${counts.saved}, unchanged: ${counts.unchanged}, recent: ${counts.recent}, skipped: ${counts.skip}, errors: ${counts.error}`);
}

main().catch(err => {
    console.error(err);
    process.exit(1);
});