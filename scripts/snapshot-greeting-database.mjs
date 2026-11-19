import {DatabaseSync, backup} from 'node:sqlite';
import {mkdir, stat} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';

const [sourceArg, destinationArg] = process.argv.slice(2);
if (!sourceArg || !destinationArg) throw new Error('Usage: node snapshot-greeting-database.mjs SOURCE.db NEW_SNAPSHOT.db');
const source = resolve(sourceArg), destination = resolve(destinationArg);
if (source.toLowerCase() === destination.toLowerCase()) throw new Error('Snapshot must be a different file.');
try { await stat(destination); throw new Error('Snapshot already exists; refusing to overwrite it.'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
await mkdir(dirname(destination), {recursive:true});
const db = new DatabaseSync(source, {readOnly:true});
let journalMode;
try {
  journalMode = Object.values(db.prepare('PRAGMA journal_mode').get())[0];
  // SQLite WAL needs same-machine shared memory. Never guess a live WAL snapshot over SMB.
  if (source.startsWith('\\') && journalMode === 'wal') throw new Error('Create this WAL snapshot locally on the server.');
  await backup(db, destination);
} finally { db.close(); }
const copy = new DatabaseSync(destination, {readOnly:true});
let tables;
try {
  if (Object.values(copy.prepare('PRAGMA integrity_check').get())[0] !== 'ok') throw new Error('Snapshot integrity check failed.');
  tables = copy.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").all().map(x => x.name);
} finally { copy.close(); }
const sha256 = createHash('sha256').update(await readFile(destination)).digest('hex');
console.log(JSON.stringify({snapshot:destination,journalMode,tables,sha256}));
