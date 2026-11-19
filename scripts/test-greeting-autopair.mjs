import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtemp} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {randomBytes, createHash} from 'node:crypto';
import {createServer} from 'node:net';
import {request} from 'node:http';
import {DatabaseSync} from 'node:sqlite';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const directory = await mkdtemp(resolve(tmpdir(), 'greeting-autopair-'));
const database = resolve(directory, 'test.db');
const port = await new Promise(done => { const socket = createServer(); socket.listen(0, '127.0.0.1', () => { const p=socket.address().port; socket.close(()=>done(p)); }); });
const base = `http://127.0.0.1:${port}`;
let server, logs='', checks=0;
function check(value, message) { assert.ok(value, message); checks++; }
async function start(automatic) {
  logs='';
  server=spawn('dotnet',[resolve(root,'LeHoangCloudServer/bin/Release/net8.0/LeHoangCloudServer.dll')], {
    cwd:resolve(root,'LeHoangCloudServer'), windowsHide:true, stdio:['ignore','pipe','pipe'],
    env:{...process.env,DATABASE_PATH:database,ASPNETCORE_URLS:base,ASPNETCORE_ENVIRONMENT:'Production',GREETING_ADMIN_KEY:'',GREETING_LAN_AUTO_PAIRING:automatic?'true':'false'}
  });
  server.stdout.on('data',data=>{logs+=data;}); server.stderr.on('data',data=>{logs+=data;});
  for(let i=0;i<100;i++) {
    if(server.exitCode!==null) throw new Error('Test server exited: '+logs);
    try {if((await fetch(base+'/api/fleet/summary')).ok) return;} catch {}
    await new Promise(done=>setTimeout(done,100));
  }
  throw new Error('Test server did not start: '+logs);
}
async function stop() {
  if(!server || server.exitCode!==null || server.signalCode!==null) return;
  const process=server; server=null; const exited=new Promise(done=>process.once('exit',done)); process.kill(); await exited;
}
async function call(path, method='GET', body, headers={}) {
  if (headers.Host) return new Promise((done,fail) => {
    // fetch normalizes Host; use a raw HTTP request to exercise rebinding protection.
    const req=request(base+'/api/greeting'+path,{method,headers},res=>{
      let data=''; res.on('data',part=>{data+=part;}); res.on('end',()=>{try{done({status:res.statusCode,data:JSON.parse(data)});}catch(error){fail(error);}});
    }); req.on('error',fail); req.end();
  });
  const response=await fetch(base+'/api/greeting'+path,{method,headers:{'Content-Type':'application/json',...headers},body:body===undefined?undefined:JSON.stringify(body)});
  return {status:response.status,data:await response.json()};
}
const id='GREETING-'+randomBytes(16).toString('hex'), token=randomBytes(24).toString('hex');
const device={'X-Greeting-Device-Token':token};
const registration={id,name:'Auto-pair test'};
const enroll=(value=registration,headers=device)=>call('/device/enroll','POST',value,headers);
try {
  await start(false);
  check((await call('/access')).data.managementMode==='key','LAN mode disabled by default');
  check((await enroll()).status===403,'Enrollment requires explicit LAN mode');
  check((await call('/robots')).status===503,'Keyless cloud management stays disabled');
  await stop(); await start(true);
  check((await call('/access')).data.managementMode==='lan','Web discovers automatic management mode');
  check((await call('/robots')).status===200,'LAN Web requires no copied admin code');
  check((await call('/robots','GET',undefined,{Origin:'https://attacker.example'})).status===503,'Cross-site management rejected');
  check((await call('/robots','GET',undefined,{Host:'attacker.example'})).status===503,'DNS-rebinding Host rejected');
  check((await call('/robots','GET',undefined,{Referer:'https://attacker.example/page'})).status===503,'Foreign Referer rejected');
  check((await call('/robots','GET',undefined,{Origin:base})).status===200,'Same-origin Web allowed');
  check((await enroll(registration,{...device,Origin:base})).status===403,'Browser cannot invoke device enrollment');
  check((await enroll(registration,{'X-Greeting-Device-Token':'weak'})).status===400,'Device secret length/format validated');
  check((await enroll({id:'../bad',name:'Invalid'})).status===400,'Automatic identity validated');
  const created=await enroll();
  check(created.status===200 && created.data.id===id,'App creates its robot automatically');
  check(!JSON.stringify(created.data).includes(token),'Enrollment response contains no device secret');
  check((await enroll()).status===200,'Lost enrollment response can be retried idempotently');
  check((await enroll(registration,{'X-Greeting-Device-Token':randomBytes(24).toString('hex')})).status===409,'Another installation cannot take over existing identity');
  check((await call('/robots')).data.length===1,'Retry does not create duplicate robots');
  const snapshot={sdkConnected:true,authorized:true,foreground:true,localized:true,mapName:'Test map',places:[]};
  const sync=()=>call(`/device/${id}/sync`,'POST',{snapshot,results:[]},device);
  check((await sync()).status===200,'Automatically enrolled device synchronizes normally');
  check((await call(`/device/${id}/sync`,'POST',{snapshot})).status===401,'Sync still requires per-device token');
  check((await call('/device/OTHER/sync','POST',{snapshot},device)).status===401,'Device token cannot control another identity');
  const settings={...created.data.settings,voiceAngleOffset:31};
  check((await call(`/robots/${id}/settings`,'PUT',settings,{Origin:base})).status===200,'LAN Web updates compensation without manual pairing');
  check((await sync()).data.settings.voiceAngleOffset===31,'Compensation reaches automatic device');
  await stop(); await start(true);
  check((await enroll()).status===200,'Installation reconnects after server restart');
  check((await sync()).data.settings.voiceAngleOffset===31,'Re-enrollment preserves existing settings');
  const db=new DatabaseSync(database,{readOnly:true});
  try {
    const stored=db.prepare('SELECT TokenHash FROM GreetingRobots WHERE Id=?').get(id).TokenHash;
    check(stored===createHash('sha256').update(token).digest('hex').toUpperCase(),'Database stores only device credential hash');
  } finally {db.close();}
  check(!logs.includes(token),'Server logs do not expose device credential');
  console.log(`PASS: ${checks} automatic-pairing integration checks; isolated data: ${directory}`);
} finally {await stop();}
