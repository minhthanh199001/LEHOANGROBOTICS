import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash, randomBytes} from 'node:crypto';
import {createServer} from 'node:net';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dir = await mkdtemp(resolve(tmpdir(), 'greeting-integration-'));
const port = await new Promise(resolvePort => {
  const s = createServer(); s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => resolvePort(p)); });
});
const base = `http://127.0.0.1:${port}`;
const admin = randomBytes(24).toString('hex');
const env = {...process.env, GREETING_ADMIN_KEY: admin, DATABASE_PATH: resolve(dir, 'test.db'), ASPNETCORE_URLS: base, ASPNETCORE_ENVIRONMENT:'Production'};
const sourceDatabase = process.argv[2] ? resolve(process.argv[2]) : null;
let sourceHash, originalTables, sqlite;
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
function fingerprint(db) {
  return Object.fromEntries(db.prepare("SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").all().map(table => {
    const quotedName = '"' + table.name.replaceAll('"','""') + '"';
    const statement = db.prepare('SELECT * FROM ' + quotedName);
    statement.setReadBigInts(true);
    const rows = statement.all().map(row => JSON.stringify(row, (_, value) => typeof value === 'bigint' ? value.toString() : value)).sort();
    return [table.name, hash(JSON.stringify({schema:table.sql,rows}))];
  }));
}
let server, logs = '';
async function start() {
  server = spawn('dotnet', [resolve(root, 'LeHoangCloudServer/bin/Release/net8.0/LeHoangCloudServer.dll')], {cwd:resolve(root,'LeHoangCloudServer'), env, windowsHide:true, stdio:['ignore','pipe','pipe']});
  server.stdout.on('data', data => { logs += data; }); server.stderr.on('data', data => { logs += data; });
  for (let i=0;i<100;i++) {
    if (server.exitCode !== null) throw new Error('Server exited: ' + logs);
    try { if ((await fetch(base + '/api/fleet/summary')).ok) return; } catch {}
    await new Promise(r => setTimeout(r, 100));
  }
  throw new Error('Server did not start: ' + logs);
}
async function stop() {
  const running = server;
  server = null;
  if (!running || running.exitCode !== null || running.signalCode !== null) return;
  const exited = new Promise(r => running.once('exit', r)); running.kill(); await exited;
}
async function call(path, method='GET', body, key=admin, device=false) {
  const res = await fetch(base+'/api/greeting'+path, {method, headers:{'Content-Type':'application/json', [device?'X-Greeting-Device-Token':'X-Greeting-Admin-Key']:key}, body:body===undefined?undefined:JSON.stringify(body)});
  return {status:res.status, data:await res.json().catch(()=>null)};
}
let checks = 0;
function check(condition, message) { assert.ok(condition, message); checks++; }
try {
  if (sourceDatabase) {
    // Exercise migrations against an isolated SQLite backup; never start the server on the source file.
    sqlite = await import('node:sqlite');
    sourceHash = hash(await readFile(sourceDatabase));
    const source = new sqlite.DatabaseSync(sourceDatabase, {readOnly:true});
    try { await sqlite.backup(source, env.DATABASE_PATH); } finally { source.close(); }
    const copy = new sqlite.DatabaseSync(env.DATABASE_PATH, {readOnly:true});
    try { originalTables = fingerprint(copy); } finally { copy.close(); }
  }
  await start();
  check((await call('/robots','GET',undefined,'wrong-key')).status===401, 'Anonymous/wrong admin rejected');
  check((await call('/robots','POST',{id:'../bad',name:'Bad'})).status===400, 'Robot IDs validated');
  const registration = await call('/robots','POST',{id:'TEST-GREETING',name:'Integration robot'});
  check(registration.status===200 && registration.data.deviceToken.length===48, 'Pairing token generated');
  const token=registration.data.deviceToken;
  check((await call('/robots','POST',{id:'TEST-GREETING',name:'Duplicate'})).status===409, 'Duplicate registration does not rotate token');
  const robotPath='/robots/TEST-GREETING', syncPath='/device/TEST-GREETING/sync';
  const command=(type='navigate',placeName='Reception',mapName='Ground')=>({type,placeName,mapName});
  check((await call(robotPath+'/commands','POST',command())).status===409, 'Offline robot rejects movement');
  const snapshot={sdkConnected:true,authorized:true,foreground:true,suspended:false,localized:true,mapName:'Ground',places:[{name:'Reception',x:1,y:2,theta:0,status:0},{name:'Blocked',x:3,y:4,theta:0,status:1}],navigationState:'idle',microphoneState:'idle',message:'Ready'};
  const sync=(state=snapshot,results=[],key=token)=>call(syncPath,'POST',{snapshot:state,results},key,true);
  check((await sync(snapshot,[],'bad-token')).status===401, 'Wrong device token rejected');
  check((await call('/device/OTHER/sync','POST',{snapshot},token,true)).status===401, 'Device token isolated by robot');
  check((await sync()).status===200, 'Authenticated device heartbeat');
  const list=await call('/robots');
  check(list.data.find(r=>r.id==='TEST-GREETING').online && !JSON.stringify(list.data).includes(token) && !JSON.stringify(list.data).includes('tokenHash'), 'Fleet online without leaking credentials');
  check((await call(robotPath+'/commands','POST',command('navigate','Reception','OldMap'))).status===409, 'Stale map rejected');
  check((await call(robotPath+'/commands','POST',command('navigate','Blocked'))).status===409, 'Unreachable point rejected');
  check((await call(robotPath+'/commands','POST',command('navigate','Missing'))).status===409, 'Unknown point rejected');
  check((await call(robotPath+'/commands','POST',command('factory_reset'))).status===400, 'Unsupported command rejected');
  await sync({...snapshot,foreground:false});
  check((await call(robotPath+'/commands','POST',command())).status===409, 'Background app rejects control');
  await sync({...snapshot,suspended:true});
  check((await call(robotPath+'/commands','POST',command())).status===409, 'System suspension rejects control');
  await sync({...snapshot,authorized:false});
  check((await call(robotPath+'/commands','POST',command())).status===409, 'SDK authorization required');
  await sync({...snapshot,localized:false});
  check((await call(robotPath+'/commands','POST',command())).status===409, 'Localization required');
  await sync();
  const first=await call(robotPath+'/commands','POST',command());
  check(first.status===202, 'Reachable current-map movement queued');
  check((await call(robotPath+'/commands','POST',command())).status===409, 'Duplicate pending navigation rejected');
  const delivered=await sync();
  check(delivered.data.command.id===first.data.id && delivered.data.command.status==='delivered', 'Device receives queued command');
  check((await sync()).data.command===null, 'Lost response does not cause command replay');
  await sync(snapshot,[{id:first.data.id,status:'completed',message:'Arrived'}]);
  await sync(snapshot,[{id:first.data.id,status:'running',message:'Late duplicate'}]);
  check((await call(robotPath)).data.commands.find(c=>c.id===first.data.id).status==='completed', 'Late ACK cannot regress terminal result');
  const pending=await call(robotPath+'/commands','POST',command());
  const stopRequest=await call(robotPath+'/commands','POST',command('stop_navigation'));
  check(stopRequest.status===202, 'Stop accepted');
  const stopDelivery=await sync();
  check(stopDelivery.data.command.type==='stop_navigation', 'Stop takes priority and cancels queued navigation');
  check((await call(robotPath)).data.commands.find(c=>c.id===pending.data.id).status==='failed', 'Queued motion cancelled by stop');
  await sync(snapshot,[{id:stopRequest.data.id,status:'completed',message:'Stopped'}]);
  const settings={linearSpeed:0.25,angularSpeed:0.6,arrivalTolerance:0.15,obstacleTimeoutMs:20000,microphoneSeconds:3,playbackVolume:40,voiceAngleOffset:10};
  check((await call(robotPath+'/settings','PUT',{...settings,linearSpeed:5})).status===400, 'Invalid movement speed rejected');
  check((await call(robotPath+'/settings','PUT',{...settings,voiceAngleOffset:91})).status===400, 'Invalid sound angle calibration rejected');
  check((await call(robotPath+'/settings','PUT',settings)).status===200, 'Settings persisted');
  check((await sync()).data.settings.microphoneSeconds===3, 'Settings delivered to device');
  check((await sync()).data.settings.voiceAngleOffset===10, 'Sound angle offset delivered to device');
  const mic=await call(robotPath+'/commands','POST',command('microphone_test'));
  check(mic.status===202 && (await sync()).data.command.type==='microphone_test', 'Microphone test delivered');
  await sync({...snapshot,microphoneState:'recording'},[{id:mic.data.id,status:'running',message:'Recording'}]);
  check((await call(robotPath+'/commands','POST',command('microphone_test'))).status===409, 'Concurrent microphone test rejected');
  await sync(snapshot,[{id:mic.data.id,status:'completed',message:'Replayed'}]);
  await stop(); await start();
  const restored=await call(robotPath);
  check(restored.data.settings.linearSpeed===0.25 && restored.data.commands.find(c=>c.id===mic.data.id).status==='completed', 'Settings and command results survive restart');
  check((await sync()).status===200, 'Pairing survives server restart');
  const lost=await call(robotPath+'/commands','POST',command());
  check((await sync()).data.command.id===lost.data.id, 'Deliver command whose response will be lost');
  const stale=await call(robotPath+'/commands','POST',command('refresh_places'));
  await new Promise(r=>setTimeout(r,16100));
  check((await call(robotPath+'/commands','POST',command('refresh_places'))).status===409, 'Stale heartbeat becomes offline');
  check((await sync()).data.command===null, 'Expired queued command never delivered after reconnect');
  const expiredHistory=(await call(robotPath)).data.commands;
  check(expiredHistory.find(c=>c.id===stale.data.id).status==='expired', 'Queued expiry visible to operator');
  check(expiredHistory.find(c=>c.id===lost.data.id).status==='unconfirmed', 'Lost response marked unconfirmed without replay');
  check((await call(robotPath+'/commands','POST',command())).status===202, 'Fresh idle device can recover after unconfirmed delivery');
  const assets=await Promise.all(['/','/js/greeting.js','/css/greeting.css'].map(p=>fetch(base+p)));
  check(assets.every(r=>r.status===200), 'Existing dashboard and Greetingbot assets served');
  if (sourceDatabase) {
    await stop();
    const copy = new sqlite.DatabaseSync(env.DATABASE_PATH, {readOnly:true});
    try {
      const after = fingerprint(copy);
      check(Object.entries(originalTables).every(([name,digest])=>after[name]===digest), 'All original table schemas and rows preserved');
      check(!!after.GreetingRobots && !!after.GreetingCommands, 'Greetingbot tables created in the same database');
    } finally { copy.close(); }
    check(hash(await readFile(sourceDatabase))===sourceHash, 'Source database file unchanged');
  }
  console.log(`PASS: ${checks} Greetingbot integration checks (isolated ${sourceDatabase ? 'backup of supplied' : 'SQLite'} database; no physical movement).`);
} finally {
  await stop();
  await rm(dir,{recursive:true,force:true});
}
