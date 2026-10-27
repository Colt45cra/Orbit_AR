import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import * as THREE from 'three';
const html=fs.readFileSync(new URL('../public/palm-ar/index.html',import.meta.url),'utf8');
function unit(){return {root:new THREE.Group(),obj:new THREE.Group(),shadow:new THREE.Sprite(),shadowSoft:new THREE.Sprite(),shadowP:new THREE.Vector3(),p:new THREE.Vector3(),dest:new THREE.Vector3(),vel:new THREE.Vector3(),handQ:new THREE.Quaternion(),targetHandQ:new THREE.Quaternion(),lastRelative:new THREE.Quaternion(),neutralUp:new THREE.Vector3(0,1,0),neutralScreenUp:new THREE.Vector2(0,1),neutralSamples:[],rollSamples:[],initialized:false,last:0,neutralReady:false,ecosystem:[]};}
function tracking(){
 const u=unit(),elements=new Map();const $=key=>{if(!elements.has(key))elements.set(key,{textContent:'',classList:{add(){},remove(){},contains(){return false}},pause(){this.paused=true},readyState:1,currentTime:12});return elements.get(key)};
 const cam=new THREE.PerspectiveCamera(46,390/844,.01,100);cam.position.z=5;cam.updateMatrixWorld();
 const c={THREE,units:[u],$,cam,info:$('#info'),v:{videoWidth:1280,videoHeight:720},innerWidth:390,innerHeight:844,facing:'environment',debug:false,two:false,mappedMode:false,cal:{x:0,y:-99,z:-11,toss:100},radians:THREE.MathUtils.degToRad,narrationStarted:true,ecosystemReadyAt:100,introSession:0};
 vm.createContext(c);vm.runInContext(html.slice(html.indexOf('const HAND_EXIT_MS='),html.indexOf('// Seconds are measured')),c);vm.runInContext(html.slice(html.indexOf('function pixel('),html.indexOf('const timer=new THREE.Clock()')),c);return {u,c,$};
}
const landmarks=()=>{const lm=Array.from({length:21},()=>({x:.5,y:.5,z:0}));for(const [i,x,y] of [[0,.5,.7],[5,.4,.45],[9,.48,.42],[13,.55,.44],[17,.62,.47]])lm[i]={x,y,z:0};return lm};
test('palm exit clears pose, momentum and narration; returning hand starts a fresh entrance',()=>{
 const {u,c,$}=tracking();c.process({landmarks:[landmarks()]},1000);assert.equal(u.introAt,1000);assert.equal(u.neutralSamples.length,0);
 u.tossOffset=2;u.tossVelocity=3;u.impact=.2;u.handQ.set(.2,.3,.1,.9);u.neutralReady=true;u.vel.set(4,3,2);
 c.process({landmarks:[]},1100);assert.equal(u.initialized,true,'brief dropout must not restart');
 c.process({landmarks:[]},1130);assert.equal(u.initialized,false);assert.equal(u.tossOffset,0);assert.equal(u.tossVelocity,0);assert.equal(u.impact,0);assert.equal(u.vel.length(),0);assert.equal(u.handQ.w,1);assert.equal(c.narrationStarted,false);assert.equal($('#narration').currentTime,0);
 c.process({landmarks:[landmarks()]},1200);assert.equal(u.introAt,1200);assert.equal(u.initialized,true);assert.equal(u.neutralReady,false);assert.equal(u.neutralSamples.length,0);assert.equal(u.targetHandQ.w,1);
});
test('shadow anchor projects to the palm center for both cameras with no first-frame lag',()=>{
 for(const facing of ['environment','user']){const {u,c}=tracking();c.facing=facing;const lm=landmarks();c.process({landmarks:[lm]},1000);
 const p=u.shadowP.clone().project(c.cam),w=c.pixel(lm[0]),knuckles=[5,9,13,17].map(i=>c.pixel(lm[i]));
 const x=w.x*.5+knuckles.reduce((n,p)=>n+p.x,0)*.125,y=w.y*.5+knuckles.reduce((n,p)=>n+p.y,0)*.125;
 assert(Math.abs((p.x*.5+.5)*390-x)<1e-8);assert(Math.abs((-.5*p.y+.5)*844-y)<1e-8);
 }
 assert(html.includes('shadow.position.z=0;'));
});
test('all product cards open a description dialog with the matching HTTPS destination',()=>{
 const articles=Array.from({length:5},()=>({setAttribute(){}})),els=new Map();const $=key=>{if(!els.has(key))els.set(key,{open:false,showModal(){this.open=true},close(){this.open=false},addEventListener(){},focus(){}});return els.get(key)};
 const c={document:{querySelectorAll:selector=>selector.endsWith(' span')?['Color collectibles','Play the game','Explore the 3D world','Manage rewards','Wallet in your pocket'].map(textContent=>({textContent})):articles},$,ecosystemCues:['Minted','ATM Town','ATM World','Payload','ATM Pay'].map(name=>({name})),exploreBtn:{focus(){}}};vm.createContext(c);
 vm.runInContext(html.slice(html.indexOf('const productLinks='),html.indexOf('let ecosystemTextures=[];')),c);
 for(let i=0;i<5;i++){articles[i].onclick();assert.equal($('#productTitle').textContent,c.ecosystemCues[i].name);assert($('#productLink').href.startsWith('https://'));assert($('#productDescription').textContent.length>5);assert.equal($('#productDialog').open,true);$('#closeProduct').onclick();assert.equal($('#productDialog').open,false);}
});
