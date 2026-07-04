const express = require('express');
const {glob} = require('glob');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const app = express();
app.disable('x-powered-by');
app.use(express.json());

const PORT = process.env.PORT || 3200;

// Immutable root set at startup — callers cannot escape this directory.
const DATA_DIR = path.resolve(process.env.DATA_DIR || path.join(__dirname, 'data'));

const CONNECTOR_INFO = {
    name: 'Filesystem',
    version: '1.0',
    fields: [
        {key: 'directory', label: 'Subdirectory within data root (optional)', required: false},
        {key: 'filePattern', label: 'File pattern (glob)', default: '**/*.txt'},
    ],
};

// Returns true only when `child` is strictly inside `parent`.
function isWithin(parent, child) {
    const rel = path.relative(parent, child);
    return rel.length > 0 && !rel.startsWith('..') && !path.isAbsolute(rel);
}

function resolveDirectory(config) {
    const sub = config.directory || '';
    const resolved = path.resolve(DATA_DIR, sub);
    if (resolved !== DATA_DIR && !isWithin(DATA_DIR, resolved)) {
        throw new Error('Directory is outside the allowed data root');
    }
    return resolved;
}

function md5(content) {
    return 'md5:' + crypto.createHash('md5').update(content).digest('hex');
}

async function listFiles(directory, pattern) {
    const files = await glob(pattern, {cwd: directory, absolute: false});
    return files.sort((a, b) => a.localeCompare(b));
}

// GET /connector/info
app.get('/connector/info', (_req, res) => {
    res.json(CONNECTOR_INFO);
});

// POST /connector/test
app.post('/connector/test', async (req, res) => {
    try {
        const config = req.body.config || {};
        const directory = resolveDirectory(config);

        if (!fs.existsSync(directory)) {
            return res.json({ok: false, message: `Directory not found: ${directory}`});
        }

        const pattern = config.filePattern || '**/*.txt';
        const files = await listFiles(directory, pattern);

        res.json({
            ok: true,
            documentCount: files.length,
            message: `Connected — ${files.length} matching document${files.length === 1 ? '' : 's'} found`,
        });
    } catch (err) {
        res.json({ok: false, message: err.message});
    }
});

// POST /connector/documents
app.post('/connector/documents', async (req, res) => {
    try {
        const config = req.body.config || {};
        const directory = resolveDirectory(config);
        const pattern = config.filePattern || '**/*.txt';
        const files = await listFiles(directory, pattern);

        const documents = files.map(relativePath => {
            const fullPath = path.resolve(directory, relativePath);
            if (fullPath !== directory && !isWithin(directory, fullPath)) {
                throw new Error(`Glob pattern escaped data root: ${relativePath}`);
            }
            const content = fs.readFileSync(fullPath, 'utf8');
            return {
                id: 'file:' + relativePath,
                path: relativePath,
                contentHash: md5(content),
            };
        });

        res.json({documents});
    } catch (err) {
        res.status(500).json({error: err.message});
    }
});

// POST /connector/fetch
app.post('/connector/fetch', (req, res) => {
    try {
        const config = req.body.config || {};
        const documentId = req.body.documentId;

        if (!documentId) {
            return res.status(400).json({error: 'Missing documentId'});
        }

        if (!documentId.startsWith('file:')) {
            return res.status(400).json({error: 'Invalid documentId format — expected "file:<relative-path>"'});
        }

        const directory = resolveDirectory(config);
        const relativePath = documentId.slice('file:'.length);
        const fullPath = path.resolve(directory, relativePath);

        if (!isWithin(directory, fullPath)) {
            return res.status(400).json({error: 'Path traversal not allowed'});
        }

        if (!fs.existsSync(fullPath)) {
            return res.status(404).json({error: 'Document not found'});
        }

        const rawContent = fs.readFileSync(fullPath, 'utf8');
        res.json({path: relativePath, rawContent});
    } catch (err) {
        res.status(500).json({error: err.message});
    }
});

app.listen(PORT, () => {
    console.log(`Filesystem connector running on http://localhost:${PORT}`);
    console.log('  GET  /connector/info');
    console.log('  POST /connector/test');
    console.log('  POST /connector/documents');
    console.log('  POST /connector/fetch');
});