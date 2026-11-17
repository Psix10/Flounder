import test from 'node:test'
import assert from 'node:assert/strict'
import { emptySwimmingFields, buildSwimmingSettings } from '../src/lib/swimming-discipline-settings.mjs'
const fields=(changes={})=>({...emptySwimmingFields(),enabled:true,distance:'50',stroke:'FREESTYLE',gender:'MALE',pool:'25',categoryCodes:'M_2014_2016, M_2012_2013',...changes})
test('disabled editor preserves template inheritance',()=>assert.equal(buildSwimmingSettings(emptySwimmingFields()),undefined))
test('explicit swimming metadata has canonical structured fields',()=>assert.deepEqual(buildSwimmingSettings(fields()),{distanceMeters:50,stroke:'FREESTYLE',gender:'MALE',poolLengthMeters:25,allowedCategoryCodes:['M_2014_2016','M_2012_2013']}))
test('blank pool does not erase template pool',()=>assert.equal('poolLengthMeters' in buildSwimmingSettings(fields({pool:''})),false))
test('blank category filter does not overwrite template filter',()=>assert.equal('allowedCategoryCodes' in buildSwimmingSettings(fields({categoryCodes:''})),false))
test('zero distance is rejected',()=>assert.throws(()=>buildSwimmingSettings(fields({distance:'0'}))))
test('negative distance is rejected',()=>assert.throws(()=>buildSwimmingSettings(fields({distance:'-50'}))))
test('fractional distance is rejected',()=>assert.throws(()=>buildSwimmingSettings(fields({distance:'50.5'}))))
test('unknown stroke is rejected by editor',()=>assert.throws(()=>buildSwimmingSettings(fields({stroke:'UNKNOWN'}))))
test('gender choice is mandatory',()=>assert.throws(()=>buildSwimmingSettings(fields({gender:''}))))
test('OPEN is an explicit unrestricted choice',()=>assert.equal(buildSwimmingSettings(fields({gender:'OPEN'})).gender,'OPEN'))
test('duplicate category codes are rejected',()=>assert.throws(()=>buildSwimmingSettings(fields({categoryCodes:'M, M'}))))
test('unsafe category code is rejected',()=>assert.throws(()=>buildSwimmingSettings(fields({categoryCodes:'../M'}))))
test('invalid pool is rejected',()=>assert.throws(()=>buildSwimmingSettings(fields({pool:'NaN'}))))
test('building request does not mutate form state',()=>{const value=fields();const before=JSON.stringify(value);buildSwimmingSettings(value);assert.equal(JSON.stringify(value),before)})

import { filterDisciplineCategoryInspection } from '../src/lib/swimming-discipline-settings.mjs'
const inspection=()=>({state:'ready',message:'base',categories:[{code:'M',seedingGroupCode:'M'},{code:'F',seedingGroupCode:'F'}]})
test('preview uses selected discipline categories',()=>assert.deepEqual(filterDisciplineCategoryInspection(inspection(),{allowedCategoryCodes:['M']}).categories.map(item=>item.code),['M']))
test('unknown selected code fails closed in preview',()=>assert.equal(filterDisciplineCategoryInspection(inspection(),{allowedCategoryCodes:['OTHER']}).state,'invalid'))
test('no category override preserves base inspection',()=>{const base=inspection();assert.equal(filterDisciplineCategoryInspection(base,{}),base)})
test('preview narrowing does not mutate global categories',()=>{const base=inspection();filterDisciplineCategoryInspection(base,{allowedCategoryCodes:['M']});assert.equal(base.categories.length,2)})
test('invalid empty category filter is explicit in preview',()=>assert.equal(filterDisciplineCategoryInspection(inspection(),{allowedCategoryCodes:[]}).state,'invalid'))
