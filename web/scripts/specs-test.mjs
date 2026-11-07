import ts from 'typescript';
import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const source=await readFile('src/productSpecs.ts','utf8');
const js=ts.transpile(source,{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022});
const {specs,specValue}=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'));
const p=(params,categoryName='地面清洁')=>({categoryName,parametersJson:JSON.stringify(params)});
assert.equal(specValue(p({'吸力':'7kPa'}),'吸力'),'7000Pa');
assert.equal(specValue(p({'机身高度':'98mm'}),'机身高度'),'9.8cm');
assert.equal(specValue(p({'飞行时间':'0.5小时'},'航拍无人机'),'飞行时间'),'30分钟');
assert.equal(specValue(p({'飞行时间':'最长0.5小时'},'航拍无人机'),'飞行时间'),'最长0.5小时');
assert.equal(specValue(p({}),'避障系统'),'未提供');
assert.equal(specValue(p({'避障系统':false}),'避障系统'),'不支持');
assert.equal(specs({parametersJson:'invalid'}).length,0);
assert.equal(specs(p({'其他':'x','吸力':'7000Pa','导航方式':'LDS'}))[0].key,'吸力');
assert.equal(specs(p({'飞行时间':'30分钟','续航时间':'0.5小时'},'航拍无人机')).length,1);
for(const category of ['擦窗机器人','庭院与泳池','四足机器人','人形机器人','商用服务']){
 const rows=specs(p({'其他':'x','安全绳':'支持','清洁范围':'池底/池壁','最大载重':'5kg','关节自由度':'23','配送容量':'三层'},category));
 assert.notEqual(rows[0].key,'其他');assert.ok(rows[0].explanation.length>15);
}
assert.equal(specValue(p({'最大坡度':'30%'},'庭院与泳池'),'最大坡度'),'30%');
assert.equal(specValue(p({'续航时间':'约3小时'},'四足机器人'),'续航时间'),'约3小时');
console.log('PASS 16 parameter normalization, category ordering and unknown/unsupported checks');
