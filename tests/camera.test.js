import { test } from 'node:test'
import assert from 'node:assert/strict'
import { installOrbitCamera } from '../src/tracking/camera.js'

function setup(capture) {
  const video = Object.assign(new EventTarget(), {
    style: {}, readyState: 1, videoWidth: 1280, videoHeight: 720,
    play: async () => {}, remove() { this.removed = true },
  })
  const track = {
    stopped: false, stop() { this.stopped = true },
    getCapabilities: () => ({ focusMode: ['continuous'] }),
    getConstraints: () => ({}), getSettings: () => ({ deviceId: 'rear' }),
    applyConstraints: async () => { throw new Error('Unsupported optional control') },
  }
  const stream = { getTracks: () => [track], getVideoTracks: () => [track] }
  Object.defineProperty(globalThis, 'document', { configurable: true, value: { createElement: () => video } })
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { mediaDevices: {
    getUserMedia: constraints => capture(constraints, stream),
    enumerateDevices: async () => [{ kind: 'videoinput', deviceId: 'rear' }],
  } } })
  const mindar = { container: { appendChild() {} } }
  return { mindar, track, video }
}

test('camera starts despite unsupported optional focus and reports selected stream', async () => {
  let requested, info
  const { mindar, video } = setup(async (constraints, stream) => { requested = constraints; return stream })
  installOrbitCamera(mindar, { deviceId: 'rear', onCamera: value => { info = value } })
  await mindar._startVideo()
  assert.equal(requested.video.deviceId.exact, 'rear')
  assert.equal(video.width, 1280)
  assert.equal(info.devices.length, 1)
})

test('camera permission rejection preserves actionable error', async () => {
  const { mindar, video } = setup(async () => { throw new DOMException('Denied', 'NotAllowedError') })
  installOrbitCamera(mindar, {})
  await assert.rejects(mindar._startVideo(), /Camera access was blocked/)
  assert.equal(video.removed, true)
})

test('default camera falls back when resolution constraints fail', async () => {
  let attempts = 0
  const { mindar } = setup(async (_, stream) => {
    if (++attempts === 1) throw new DOMException('Constraints', 'OverconstrainedError')
    return stream
  })
  installOrbitCamera(mindar, {})
  await mindar._startVideo()
  assert.equal(attempts, 2)
})

test('closing while permission request is pending stops late camera stream', async () => {
  let allow
  const { mindar, track } = setup((_, stream) => new Promise(resolve => { allow = () => resolve(stream) }))
  const abort = new AbortController()
  installOrbitCamera(mindar, { signal: abort.signal })
  const pending = mindar._startVideo()
  abort.abort()
  allow()
  await assert.rejects(pending, { name: 'AbortError' })
  assert.equal(track.stopped, true)
})
