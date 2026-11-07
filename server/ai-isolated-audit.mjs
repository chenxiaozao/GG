import {readFile,mkdtemp} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {tmpdir} from 'node:os';
import path from 'node:path';
if(!process.argv.includes('--allow-three-live-calls'))throw Error('需要本轮用户明确授权，并传入 --allow-three-live-calls；默认不消耗模型额度。');
const config=await readFile('../.env.local','utf8');
const match=config.match(/^\s*(?:export\s+)?DEEPSEEK_API_KEY=['"]([^'"\r\n]+)['"]\s*$/m);
if(!match || !match[1].trim())throw Error('未找到可安全读取的本机API配置；未发送请求。');
const directory=await mkdtemp(path.join(tmpdir(),'robotmall-live-audit-'));
const base='http://127.0.0.1:18081/api';
let child;
async function stop(){if(!child||child.exitCode!==null)return;const closed=new Promise(resolve=>child.once('exit',resolve));child.kill('SIGTERM');await closed;}
async function request(endpoint,body){const r=await fetch(base+endpoint,{method:body?'POST':'GET',headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(5000)});return r.json();}
try{
 child=spawn('java',['-Xmx384m','-jar',path.resolve('target/robotmall-server-1.0.0.jar'),'--spring.profiles.active=on-demand','--server.port=18081',`--spring.datasource.url=jdbc:h2:file:${directory}/robotmall;MODE=MySQL;DATABASE_TO_LOWER=TRUE`,`--app.avatar-dir=${directory}/avatars`,'--app.bootstrap-admin-password=IsolatedAdmin123!','--app.ai-user-daily-limit=3','--deepseek.base-url=https://api.deepseek.com'],{env:{...process.env,DEEPSEEK_API_KEY:match[1]},stdio:'ignore'});
 let ready=false;
 for(let i=0;i<90;i++){if(child.exitCode!==null)throw Error('隔离后端启动失败');try{const d=await request('/products');if(d.data?.some(p=>p.name==='Enabot EBO Air')){ready=true;break;}}catch{}await new Promise(resolve=>setTimeout(resolve,500));}
 if(!ready)throw Error('隔离后端初始化超时');
 const registration=await request('/auth/register',{username:'isolated_ai_audit',password:'SyntheticAudit123!',nickname:'虚构场景测试'});if(registration.code!==200)throw Error('隔离账号创建失败');
 const login=await request('/auth/login',{username:'isolated_ai_audit',password:'SyntheticAudit123!'});if(!login.data?.token)throw Error('隔离账号登录失败');
 const audit=spawn(process.execPath,['scripts/ai-live-audit.mjs'],{cwd:path.resolve('../web'),env:{...process.env,AUDIT_API_URL:base,AUDIT_TOKEN:login.data.token},stdio:['ignore','pipe','pipe']});
 // The audit only outputs synthetic answers / public product data, never credentials.
 audit.stdout.on('data',data=>process.stdout.write(data));audit.stderr.on('data',()=>{});
 const code=await new Promise(resolve=>audit.once('exit',resolve));if(code!==0)process.exitCode=1;
}finally{await stop();}
