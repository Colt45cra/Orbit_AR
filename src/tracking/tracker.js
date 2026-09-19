import * as THREE from 'three'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { MindARThree } from 'mind-ar/dist/mindar-image-three.prod.js'
import { Compiler } from 'mind-ar/src/image-target/compiler.js'

async function fileToImage(file) {
  return await new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = (err) => {
      URL.revokeObjectURL(url)
      reject(err)
    }
    img.src = url
  })
}

export async function compileOrbitTarget(file, onProgress = () => {}) {
  const image = await fileToImage(file)
  const compiler = new Compiler()
  await compiler.compileImageTargets([image], (progress) => onProgress(Math.round(progress)))
  const buffer = await compiler.exportData()
  const blob = new Blob([buffer], { type: 'application/octet-stream' })
  return {
    url: URL.createObjectURL(blob),
    imageWidth: image.naturalWidth,
    imageHeight: image.naturalHeight,
  }
}

function placement(transform) {
  return {
    x: (transform.x - 50) / 100,
    y: (50 - transform.y) / 100,
    scale: transform.scale,
    rotation: -THREE.MathUtils.degToRad(transform.rotation),
    tilt: THREE.MathUtils.degToRad(transform.tilt ?? 0),
    opacity: transform.opacity,
  }
}

function applySRGB(texture) {
  if ('colorSpace' in texture && THREE.SRGBColorSpace) texture.colorSpace = THREE.SRGBColorSpace
  if ('encoding' in texture && THREE.sRGBEncoding) texture.encoding = THREE.sRGBEncoding
  texture.needsUpdate = true
}

function makeUnlitMaterial(map, opacity) {
  return new THREE.MeshBasicMaterial({
    map,
    transparent: true,
    opacity,
    side: THREE.DoubleSide,
    toneMapped: false,
    color: 0xffffff,
  })
}

async function addImage(group, asset, transform) {
  const texture = await new THREE.TextureLoader().loadAsync(asset.url)
  applySRGB(texture)
  const img = texture.image
  const aspect = img?.width && img?.height ? img.width / img.height : 1
  const p = placement(transform)
  const width = 0.72 * p.scale
  const height = width / aspect
  const pivot = new THREE.Group()
  pivot.position.set(p.x, p.y, 0.01)
  pivot.rotation.x = p.tilt
  pivot.rotation.z = p.rotation
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), makeUnlitMaterial(texture, p.opacity))
  mesh.position.set(0, height / 2, 0)
  pivot.add(mesh)
  group.add(pivot)
  return { media: null }
}

async function addVideo(group, asset, transform) {
  const video = document.createElement('video')
  video.src = asset.url
  video.loop = true
  video.muted = true
  video.playsInline = true
  video.preload = 'auto'
  await new Promise((resolve) => {
    if (video.readyState >= 1) return resolve()
    video.onloadedmetadata = () => resolve()
  })
  const aspect = video.videoWidth && video.videoHeight ? video.videoWidth / video.videoHeight : 16 / 9
  const texture = new THREE.VideoTexture(video)
  applySRGB(texture)
  const p = placement(transform)
  const width = 0.72 * p.scale
  const height = width / aspect
  const pivot = new THREE.Group()
  pivot.position.set(p.x, p.y, 0.01)
  pivot.rotation.x = p.tilt
  pivot.rotation.z = p.rotation
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), makeUnlitMaterial(texture, p.opacity))
  mesh.position.set(0, height / 2, 0)
  pivot.add(mesh)
  group.add(pivot)
  return { media: video }
}

async function addModel(group, asset, transform) {
  const loader = new GLTFLoader()
  const gltf = await loader.loadAsync(asset.url)
  const p = placement(transform)
  const root = gltf.scene
  const box = new THREE.Box3().setFromObject(root)
  const size = new THREE.Vector3()
  box.getSize(size)
  const max = Math.max(size.x, size.y, size.z) || 1
  const normalized = (0.62 / max) * p.scale
  root.scale.setScalar(normalized)
  const scaledBox = new THREE.Box3().setFromObject(root)
  const pivot = new THREE.Group()
  pivot.position.set(p.x, p.y, 0.04)
  pivot.rotation.x = p.tilt
  pivot.rotation.z = p.rotation
  root.position.y -= scaledBox.min.y
  root.traverse((node) => {
    if (node.material) {
      node.material.transparent = p.opacity < 1 || node.material.transparent
      node.material.opacity = p.opacity
      node.material.toneMapped = false
    }
  })
  pivot.add(root)
  group.add(pivot)
  return { media: null }
}

function createAnchorMatrixStabilizer(tiltDegrees = 0) {
  const upright = THREE.MathUtils.clamp(Math.abs(tiltDegrees) / 90, 0, 1)
  const rawPosition = new THREE.Vector3()
  const rawQuaternion = new THREE.Quaternion()
  const rawScale = new THREE.Vector3()
  const smoothPosition = new THREE.Vector3()
  const smoothQuaternion = new THREE.Quaternion()
  const smoothScale = new THREE.Vector3()
  const composed = new THREE.Matrix4()
  const previousRawPosition = new THREE.Vector3()
  const previousRawQuaternion = new THREE.Quaternion()
  const predictedPosition = new THREE.Vector3()
  const predictedQuaternion = new THREE.Quaternion()
  const deltaQuaternion = new THREE.Quaternion()
  const predictionStep = new THREE.Quaternion()
  const identityQuaternion = new THREE.Quaternion()
  let initialized = false
  let quietFrames = 0
  let settledLocked = false
  let previousUpdateTime = 0

  return {
    reset() {
      initialized = false
      quietFrames = 0
      settledLocked = false
      previousUpdateTime = 0
    },
    update(group) {
      group.matrix.decompose(rawPosition, rawQuaternion, rawScale)

      const now = performance.now()

      if (!initialized) {
        smoothPosition.copy(rawPosition)
        smoothQuaternion.copy(rawQuaternion)
        smoothScale.copy(rawScale)
        previousRawPosition.copy(rawPosition)
        previousRawQuaternion.copy(rawQuaternion)
        previousUpdateTime = now
        initialized = true
      } else {
        const dt = THREE.MathUtils.clamp((now - previousUpdateTime) / 1000, 1 / 120, 0.08)
        const rawTravel = previousRawPosition.distanceTo(rawPosition)
        const rawTurn = previousRawQuaternion.angleTo(rawQuaternion)
        const linearSpeed = rawTravel / dt
        const angularSpeed = rawTurn / dt

        const moving = linearSpeed > 0.10 || angularSpeed > 0.45
        predictedPosition.copy(rawPosition)
        predictedQuaternion.copy(rawQuaternion)

        if (moving) {
          // Compensate for one small camera/render interval of tracking latency.
          // Prediction is tightly capped to avoid overshoot on noisy detections.
          const predictionSeconds = Math.min(0.018, dt * 0.75)
          const velocityScale = predictionSeconds / dt
          predictedPosition.addScaledVector(
            rawPosition.clone().sub(previousRawPosition),
            Math.min(velocityScale, 0.75)
          )

          deltaQuaternion.copy(previousRawQuaternion).invert().multiply(rawQuaternion)
          predictionStep.copy(identityQuaternion).slerp(deltaQuaternion, Math.min(velocityScale, 0.65))
          predictedQuaternion.multiply(predictionStep).normalize()

          settledLocked = false
          quietFrames = 0
        }

        const targetPosition = moving ? predictedPosition : rawPosition
        const targetQuaternion = moving ? predictedQuaternion : rawQuaternion
        const positionDelta = smoothPosition.distanceTo(targetPosition)
        const rotationDelta = smoothQuaternion.angleTo(targetQuaternion)
        const scaleDelta = smoothScale.distanceTo(rawScale)

        // Small pose changes are mostly feature/camera noise. Larger changes
        // are followed progressively faster so deliberate phone motion stays responsive.
        const positionDeadZone = THREE.MathUtils.lerp(0.0025, 0.0032, upright)
        const rotationDeadZone = THREE.MathUtils.degToRad(THREE.MathUtils.lerp(0.30, 0.48, upright))
        const scaleDeadZone = THREE.MathUtils.lerp(0.0025, 0.0030, upright)

        // Once the target has remained very quiet for several updates, enter
        // a short-lived settled state that rejects sub-pixel pose buzz.
        const quietPosition = THREE.MathUtils.lerp(0.0055, 0.0070, upright)
        const quietRotation = THREE.MathUtils.degToRad(THREE.MathUtils.lerp(0.7, 1.0, upright))
        const quietScale = THREE.MathUtils.lerp(0.0050, 0.0065, upright)
        const isQuiet = positionDelta < quietPosition && rotationDelta < quietRotation && scaleDelta < quietScale

        const releasePosition = THREE.MathUtils.lerp(0.012, 0.015, upright)
        const releaseRotation = THREE.MathUtils.degToRad(THREE.MathUtils.lerp(1.5, 2.0, upright))
        const releaseScale = THREE.MathUtils.lerp(0.010, 0.013, upright)

        if (!settledLocked) {
          if (isQuiet) quietFrames = Math.min(quietFrames + 1, 20)
          else quietFrames = 0

          if (quietFrames >= 5) settledLocked = true
        } else if (
          positionDelta > releasePosition ||
          rotationDelta > releaseRotation ||
          scaleDelta > releaseScale
        ) {
          // Hysteresis: stay locked through ordinary micro-noise and release
          // only when a clearly deliberate pose change exceeds the wider threshold.
          settledLocked = false
          quietFrames = 0
          smoothPosition.lerp(rawPosition, 0.35)
          smoothQuaternion.slerp(rawQuaternion, 0.30)
          smoothScale.lerp(rawScale, 0.25)
        }

        if (!settledLocked && positionDelta > positionDeadZone) {
          const smallAlpha = THREE.MathUtils.lerp(0.075, 0.055, upright)
          const alpha = moving
            ? (positionDelta > 0.07 ? 0.90 : positionDelta > 0.025 ? 0.78 : 0.62)
            : (positionDelta > 0.07 ? 0.50 : positionDelta > 0.025 ? 0.28 : smallAlpha)
          smoothPosition.lerp(targetPosition, alpha)
        }

        if (!settledLocked && rotationDelta > rotationDeadZone) {
          // Upright planes visually amplify tiny angular noise at their top edge.
          // During real movement, switch to a high-response path to prevent visible lag.
          const smallAlpha = THREE.MathUtils.lerp(0.07, 0.04, upright)
          const mediumAlpha = THREE.MathUtils.lerp(0.25, 0.20, upright)
          const alpha = moving
            ? (rotationDelta > 0.14 ? 0.88 : rotationDelta > 0.045 ? 0.76 : 0.58)
            : (rotationDelta > 0.14 ? 0.48 : rotationDelta > 0.045 ? mediumAlpha : smallAlpha)
          smoothQuaternion.slerp(targetQuaternion, alpha)
        }

        if (!settledLocked && scaleDelta > scaleDeadZone) {
          const smallAlpha = THREE.MathUtils.lerp(0.08, 0.06, upright)
          const alpha = moving ? 0.45 : (scaleDelta > 0.045 ? 0.35 : smallAlpha)
          smoothScale.lerp(rawScale, alpha)
        }

        previousRawPosition.copy(rawPosition)
        previousRawQuaternion.copy(rawQuaternion)
        previousUpdateTime = now
      }

      composed.compose(smoothPosition, smoothQuaternion, smoothScale)
      group.matrix.copy(composed)
      group.matrixWorldNeedsUpdate = true
    },
  }
}

export async function startOrbitTracking({ container, targetFile, asset, transform, onProgress, onFound, onLost, onStatus }) {
  if (!container || !targetFile || !asset) throw new Error('Trigger image and AR content are required')

  onStatus?.('Compiling trigger image…')
  const compiled = await compileOrbitTarget(targetFile, onProgress)
  onStatus?.('Starting camera…')

  const mindarThree = new MindARThree({
    container,
    imageTargetSrc: compiled.url,
    uiLoading: 'no',
    uiScanning: 'no',
    uiError: 'no',
    // Let MindAR react faster during motion; Orbit's settled lock handles stillness.
    filterMinCF: 0.005,
    filterBeta: 1700,
    missTolerance: 8,
    warmupTolerance: 6,
  })

  const { renderer, scene, camera } = mindarThree
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.toneMapping = THREE.NoToneMapping
  if ('outputEncoding' in renderer && THREE.sRGBEncoding) renderer.outputEncoding = THREE.sRGBEncoding
  if ('outputColorSpace' in renderer && THREE.SRGBColorSpace) renderer.outputColorSpace = THREE.SRGBColorSpace

  const anchor = mindarThree.addAnchor(0)
  const stabilizeAnchor = createAnchorMatrixStabilizer(transform.tilt ?? 0)

  let media = null
  if (asset.kind === 'video') media = (await addVideo(anchor.group, asset, transform)).media
  else if (asset.kind === 'model') await addModel(anchor.group, asset, transform)
  else await addImage(anchor.group, asset, transform)

  let lostTimer = null

  anchor.onTargetFound = () => {
    stabilizeAnchor.reset()
    if (lostTimer) {
      clearTimeout(lostTimer)
      lostTimer = null
    }
    onStatus?.('Target found')
    if (media) media.play().catch(() => {})
    onFound?.()
  }

  anchor.onTargetLost = () => {
    stabilizeAnchor.reset()
    onStatus?.('Searching for trigger…')
    if (lostTimer) clearTimeout(lostTimer)
    lostTimer = setTimeout(() => {
      if (media) media.pause()
      onLost?.()
    }, 140)
  }

  // MindAR calls this immediately after writing its final target-space matrix.
  // Smooth that exact matrix in place so the asset never leaves the native anchor space.
  anchor.onTargetUpdate = () => {
    if (anchor.visible) stabilizeAnchor.update(anchor.group)
  }

  const ambient = new THREE.AmbientLight(0xffffff, 1.0)
  scene.add(ambient)

  await mindarThree.start()
  onStatus?.('Searching for trigger…')
  renderer.setAnimationLoop(() => renderer.render(scene, camera))

  return async () => {
    try { if (lostTimer) clearTimeout(lostTimer) } catch {}
    try { media?.pause() } catch {}
    try { renderer.setAnimationLoop(null) } catch {}
    try { mindarThree.stop() } catch {}
    try { URL.revokeObjectURL(compiled.url) } catch {}
    try { container.innerHTML = '' } catch {}
  }
}
