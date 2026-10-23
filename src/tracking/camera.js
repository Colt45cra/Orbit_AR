// Configure the actual capture stream: CSS brightness cannot help detection.
export function cameraError(error) {
  if (error?.name === 'NotAllowedError') return new Error('Camera access was blocked. Allow camera access for Orbit in Chrome site settings, then retry.')
  if (error?.name === 'NotReadableError') return new Error('Camera is busy. Close other camera apps or browser tabs, then retry.')
  if (error?.name === 'NotFoundError') return new Error('No camera was found. Choose another camera and retry.')
  return error instanceof Error ? error : new Error('Camera could not start. Check camera permissions and retry.')
}

export function installOrbitCamera(mindar, { deviceId, signal, onCamera }) {
  // MindAR 1.2.1 has no public stream injection API. Keep its tracking and
  // projection pipeline, replacing only capture startup (which loses errors).
  mindar._startVideo = async function () {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('Camera access requires a supported browser over HTTPS.')
    const video = document.createElement('video')
    this.video = video
    video.autoplay = true
    video.muted = true
    video.playsInline = true
    Object.assign(video.style, { position: 'absolute', top: '0px', left: '0px', zIndex: '-2' })
    this.container.appendChild(video)
    let stream
    const stop = () => stream?.getTracks().forEach(track => track.stop())
    signal?.addEventListener('abort', stop, { once: true })
    try {
      const requested = {
        ...(deviceId ? { deviceId: { exact: deviceId } } : { facingMode: { ideal: 'environment' } }),
        width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30, max: 30 },
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ audio: false, video: requested })
      } catch (error) {
        if (error?.name !== 'OverconstrainedError' || deviceId) throw error
        stream = await navigator.mediaDevices.getUserMedia({ audio: false, video: { facingMode: 'environment' } })
      }
      signal?.throwIfAborted()
      const track = stream.getVideoTracks()[0]
      const capabilities = track.getCapabilities?.() || {}
      let advanced = []
      for (const key of ['focusMode', 'exposureMode', 'whiteBalanceMode']) {
        if (capabilities[key]?.includes('continuous')) advanced.push({ [key]: 'continuous' })
      }
      // Some phones report controls they reject: optional tuning must not block AR.
      for (const setting of advanced) {
        try { await track.applyConstraints({ ...track.getConstraints(), advanced: [setting] }) } catch {}
      }
      video.srcObject = stream
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => done(new Error('Camera did not provide a frame. Close and retry.')), 15000)
        const abort = () => done(new DOMException('Cancelled', 'AbortError'))
        const ready = () => done()
        function done(error) {
          clearTimeout(timer)
          video.removeEventListener('loadedmetadata', ready)
          signal?.removeEventListener('abort', abort)
          error ? reject(error) : resolve()
        }
        video.addEventListener('loadedmetadata', ready, { once: true })
        signal?.addEventListener('abort', abort, { once: true })
        if (video.readyState >= 1) done()
        else if (signal?.aborted) abort()
      })
      await video.play()
      signal?.throwIfAborted()
      video.width = video.videoWidth
      video.height = video.videoHeight
      const devices = await navigator.mediaDevices.enumerateDevices().catch(() => [])
      onCamera?.({
        track, capabilities, settings: track.getSettings(),
        devices: devices.filter(device => device.kind === 'videoinput'),
      })
    } catch (error) {
      stop()
      video.remove()
      throw cameraError(error)
    }
  }
}
