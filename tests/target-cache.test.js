import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createTargetCache, startupError } from '../src/tracking/target-cache.js'

test('lens changes reuse compilation but cannot revoke another session target', async()=>{
  let calls=0
  const cached=createTargetCache(async()=>{calls++;return {blob:new Blob(['target']),imageWidth:300,imageHeight:200}})
  const file={}
  const [first,second]=await Promise.all([cached(file),cached(file)])
  assert.equal(calls,1)
  assert.notEqual(first.url,second.url)
  URL.revokeObjectURL(first.url)
  assert.equal(await (await fetch(second.url)).text(),'target')
  URL.revokeObjectURL(second.url)
})
test('failed compilation is retried, not permanently cached',async()=>{
  let calls=0
  const cached=createTargetCache(async()=>{if(++calls===1)throw new Error('GPU error');return {blob:new Blob(['ok'])}})
  const file={}
  await assert.rejects(cached(file),/GPU error/)
  const next=await cached(file)
  assert.equal(calls,2)
  URL.revokeObjectURL(next.url)
})
test('browser events without messages include failing stage',()=>{
  assert.match(startupError({type:'error'},'Loading video').message,/Loading video: error/)
})
