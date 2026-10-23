'use strict';
const $ = id => document.getElementById(id);
const labels = {openBankRepaymentAccounts:'Open bank recipient accounts',openDepositAccount:'Open customer account',depositCash:'Cash deposit',previewCashReversal:'Reversal preview',reverseCash:'Cash reversal',readDepositAccount:'Account inquiry',readCashDeposit:'Deposit receipt inquiry',readCashReversal:'Reversal receipt inquiry'};
let templates = {}, busy = false;
const configKeys = ['account','cash','interest','collection','principal','date','original','lookup'];
const today = new Date(); $('date').value = [today.getFullYear(),String(today.getMonth()+1).padStart(2,'0'),String(today.getDate()).padStart(2,'0')].join('-');
try { const saved = JSON.parse(localStorage.getItem('xbod.web.config') || '{}'); for (const key of configKeys) if (saved[key]) $(key).value = saved[key]; } catch (_) { /* Browser storage is optional. */ }
function saveConfig() { try { localStorage.setItem('xbod.web.config',JSON.stringify(Object.fromEntries(configKeys.map(key=>[key,$(key).value])))); } catch (_) {} }
for (const key of configKeys) $(key).addEventListener('change',saveConfig);
function page(name) { document.querySelectorAll('.page').forEach(el=>el.hidden=el.id!==name); document.querySelectorAll('nav button').forEach(el=>el.classList.toggle('active',el.dataset.page===name)); }
document.querySelectorAll('[data-page]').forEach(el=>el.onclick=()=>page(el.dataset.page));
function merge(target,values) { for (const [key,value] of Object.entries(values)) { if (value && typeof value==='object' && !Array.isArray(value)) { target[key] ||= {}; merge(target[key],value); } else target[key]=value; } return target; }
function accounts() { return {DepositAccount:{accountNumber:$('account').value.trim()},AccountEligibility:{accountNumber:$('account').value.trim()},DepositAccrualState:{accountNumber:$('account').value.trim()},CashSettlementAccount:{accountNumber:$('cash').value.trim()},InterestIncomeAccount:{accountNumber:$('interest').value.trim()},CollectionInterestIncomeAccount:{accountNumber:$('collection').value.trim()},PrincipalRecoveryAccount:{accountNumber:$('principal').value.trim()}}; }
function request(service) {
  if (!templates[service]) throw new Error('Request templates are not loaded yet');
  for (const id of ['account','cash','interest','collection','principal','date']) if (!$(id).value.trim()) throw new Error('Enter account numbers and a business date');
  const body=structuredClone(templates[service]), day=Number($('date').value.replaceAll('-',''));
  const refs=accounts(); for (const [key,value] of Object.entries(refs)) if (key in body) merge(body[key],value);
  if (service==='openBankRepaymentAccounts') merge(body,{interestSubject:refs.InterestIncomeAccount,collectionSubject:refs.CollectionInterestIncomeAccount,principalSubject:refs.PrincipalRecoveryAccount});
  if (service==='openDepositAccount') merge(body,{BankAccount:{accountNumber:refs.DepositAccount.accountNumber},AccountEligibility:{openedOn:day}});
  if (service==='depositCash') {
    const amount=$('amount').value.trim(); if (!/^-?\d+(\.\d+)?$/.test(amount)) throw new Error('Enter a numeric amount; use a negative value to test business rejection');
    merge(body,{DepositRequest:{amount,currency:$('currency').value,valueDate:day},BusinessDay:{date:day},DepositInterestPolicy:{normalEnabled:true,normalUnit:1,overdraftUnit:1},InterestCalendar:{convention:'ACT'},AgreementPolicy:{enabled:false,mode:'2',currency:$('currency').value},DepositDetailPolicy:{itemCount:1},CashChannel:{teller:true,channel:'Teller',transactionId:'GSA01102'},CashAuthorizationPolicy:{tellerLimit:'100000'},AuthorizationEvidence:{grantedLevel:$('authorization').value},DepositFeeParty:{accountKind:'NormalSavings',branchNumber:'001',regionCode:'R001'},DepositFeeContext:{channel:'Teller',branchNumber:'001',regionCode:'R001'},DepositOperatorFacts:{branch:'001',area:'R001',province:'P1',accountingCentre:true,operationCode:'1102',transactionPrefix:'SA01102'},DepositBankProfile:{normalSavings:true,depositRange:'All',openingProvince:'P1'}});
    body.AgreementBandArray=[5,4,3,2,1].map(slot=>({slot,threshold:0}));
    body.DepositPostingLineArray=Array.from({length:8},(_,i)=>({slot:i+1,subject:'DEMO-SUBJECT-'+(i+1),side:[1,2,7].includes(i+1)?'Debit':'Credit',amount:0}));
  }
  if (['reverseCash','previewCashReversal'].includes(service)) {
    if (!$('original').value.trim()) throw new Error('Enter the original deposit receipt number');
    merge(body,{CashDepositAgreement:{depositNumber:$('original').value.trim()},CashReversalAgreement:{businessDate:day,newDetailItems:1,overnightMode2:$('overnight').checked},CashChannel:{teller:true,channel:'Teller',transactionId:'GSA01102'},DepositOperatorFacts:{operationCode:'1102',transactionPrefix:'SA01102'}});
  }
  if (service==='readCashDeposit'||service==='readCashReversal') {
    if (!$('lookup').value.trim()) throw new Error('Enter a receipt number to look up');
    merge(body,service==='readCashDeposit'?{CashDepositAgreement:{depositNumber:$('lookup').value.trim()}}:{CashReversalAgreement:{reversalNumber:$('lookup').value.trim()}});
  }
  return body;
}
// Preserve decimal tokens as strings. The browser never calculates or rounds money.
function exactJSON(text) { return JSON.parse(text.replace(/"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g, token=>token[0]==='"'?token:JSON.stringify(token))); }
function failureIn(value) { if (!value || typeof value!=='object') return null; if (value.resultInfo?.failure?.code) return value.resultInfo.failure; for (const child of Object.values(value)) { const found=failureIn(child); if(found)return found; } return null; }
function textElement(tag,text,cls) { const el=document.createElement(tag); el.textContent=text; if(cls)el.className=cls; return el; }
function history() { try{return JSON.parse(localStorage.getItem('xbod.web.history')||'[]');}catch(_){return [];} }
function showHistory() { $('history').replaceChildren(); for (const entry of history()) { const li=document.createElement('li'),button=textElement('button',entry.number); button.append(textElement('small',`${entry.label} · ${entry.account} · ${entry.date}`)); button.onclick=()=>{for(const key of ['account','cash','interest','collection','principal'])if(entry.context?.[key])$(key).value=entry.context[key];if(entry.account)$('account').value=entry.account;$('lookup').value=entry.number; if(entry.type==='deposit')$('original').value=entry.number;$('service').value=entry.type==='deposit'?'readCashDeposit':'readCashReversal';saveConfig();}; li.append(button);$('history').append(li); } }
function remember(number,type,service,participants) { if(!number)return; const items=history().filter(x=>x.number!==number);const context=Object.fromEntries(['account','cash','interest','collection','principal'].map(key=>[key,$(key).value]));const roles={account:'DepositAccount',cash:'CashSettlementAccount',interest:'InterestIncomeAccount',collection:'CollectionInterestIncomeAccount',principal:'PrincipalRecoveryAccount'};for(const [key,role]of Object.entries(roles))if(participants[role]?.accountNumber)context[key]=participants[role].accountNumber;items.unshift({number,type,label:labels[service],account:context.account,context,date:$('date').value});try{localStorage.setItem('xbod.web.history',JSON.stringify(items.slice(0,25)));}catch(_){}showHistory(); }
function display(service,data) {
  const p=data.participants||{}; $('response').textContent=JSON.stringify(data,null,2);
  const failed=['depositCash','reverseCash'].includes(service)?failureIn(p):null;
  const error=data.error || (failed ? `${failed.code} · ${failed.reason||''} · Recovery status: ${failed.recoveryStatus||'Unknown'}` : '');
  $('status').textContent=error?'Not completed':service==='previewCashReversal'?'Preview completed':'Success';$('status').classList.toggle('error',!!error);
  $('message').textContent=error ? `${error}. This response does not confirm balances or settlement. Look up the accounting facts.` : `${labels[service]} completed${data.replayed?' (original result returned for a duplicate request)':''}. ${service==='previewCashReversal'?'Preview only; no funds moved.':''}`;
  const agreement=service==='readCashDeposit'||service==='depositCash'?p.CashDepositAgreement:p.CashReversalAgreement;
  const number=agreement?.reversalNumber||agreement?.depositNumber;
  $('receipt').hidden=!number; $('receipt').textContent=number?`${error?'Related processing reference (settlement status needs inquiry)':'Receipt number'}: ${number}${agreement?.reversed?' · Reversed':''}`:'';
  if (!error && service==='depositCash' && number) { $('original').value=number;$('lookup').value=number;remember(number,'deposit',service,p);saveConfig(); }
  if (!error && service==='reverseCash' && number) { $('lookup').value=number;remember(number,'reversal',service,p);saveConfig(); }
  const currency=agreement?.currency || agreement?.original?.currency || p.CashDepositAgreement?.currency || $('currency').value || 'USD';
  const exactMoney=value=>currency+' '+String(value);
  const facts=[['DepositAccount','interestBase','Customer balance'],['CashSettlementAccount','balance','Cash settlement balance'],['InterestIncomeAccount','balance','Interest income'],['CollectionInterestIncomeAccount','balance','Collection interest income'],['PrincipalRecoveryAccount','balance','Principal recovery'],['DepositAccount','overdraftPrincipal','Outstanding principal']];
  $('balances').replaceChildren();
  for (const [role,key,label] of facts) if(!error && p[role]?.[key]!=null) {const el=textElement('div','','balance');el.append(textElement('small',label),textElement('strong',exactMoney(p[role][key])));$('balances').append(el);}
  const lines=error?[]:(p.CashReversalPostingLineArray||p.DepositPostingLineArray||[]);$('lines').replaceChildren();$('lineCount').textContent=lines.length?`${lines.length} lines`: '—';
  for (const line of lines) {const row=document.createElement('tr');for (const [i,value] of [line.slot,line.subject,line.side==='Debit'?'Debit':line.side==='Credit'?'Credit':line.side,line.amount].entries())row.append(textElement('td',value==null?'—':i===3?exactMoney(value):String(value),i===3?'right':''));$('lines').append(row);}
  if(!lines.length){const row=document.createElement('tr'),cell=textElement('td','No postings returned','empty');cell.colSpan=4;row.append(cell);$('lines').append(row);}
}
async function run(service,body) {
  if(busy)return;
  if(service==='reverseCash'&&!confirm('Submit the cash reversal? This creates a new transaction and moves funds.'))return;
  busy=true;const controls=[...document.querySelectorAll('button,input,select,textarea')].map(el=>[el,el.disabled]);controls.forEach(([el])=>el.disabled=true);$('receipt').hidden=true;$('balances').replaceChildren();$('lines').replaceChildren();$('lineCount').textContent='—';$('status').textContent='Processing';$('status').classList.remove('error');$('message').textContent=`Executing ${labels[service]}…`;
  $('payload').value=JSON.stringify(body,null,2);$('service').value=service;
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),60000);
  try {const res=await fetch('/api/cboddeposit.'+service,{method:'POST',headers:{'Content-Type':'application/json','X-SPP-Request-Serial':globalThis.crypto?.randomUUID?.()||`${Date.now()}-${Math.random()}`},body:JSON.stringify(body),signal:controller.signal}); const text=await res.text();const data=exactJSON(text); if(!res.ok&&!data.error)data.error=`HTTP ${res.status}`;display(service,data);}
  catch(error){$('status').textContent='Outcome unconfirmed';$('status').classList.add('error');$('message').textContent=`Communication failed: ${error.message}. The request may have settled. Look up the account and receipt before submitting again.`;$('response').textContent=String(error);}
  finally {clearTimeout(timer);busy=false;controls.forEach(([el,disabled])=>el.disabled=disabled);}
}
function formRun(service){try{run(service,request(service));}catch(e){$('message').textContent=e.message;$('status').textContent='Check input';$('status').classList.add('error');}}
document.querySelectorAll('[data-service]').forEach(el=>el.onclick=()=>formRun(el.dataset.service));$('refresh').onclick=()=>formRun('readDepositAccount');
for(const [key,label] of Object.entries(labels)){const opt=textElement('option',label);opt.value=key;$('service').append(opt);}
$('prepare').onclick=()=>{try{$('payload').value=JSON.stringify(request($('service').value),null,2);}catch(e){$('message').textContent=e.message;}};
$('execute').onclick=()=>{try{run($('service').value,exactJSON($('payload').value));}catch(e){$('message').textContent='Invalid JSON input: '+e.message;}};
showHistory();fetch('templates.json').then(r=>{if(!r.ok)throw new Error('Failed to load templates');return r.json();}).then(data=>{templates=data;}).catch(e=>{$('message').textContent=e.message;});
