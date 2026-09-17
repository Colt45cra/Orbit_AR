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
    transparent: opacity < 1 || true,
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
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), makeUnlitMaterial(texture, p.opacity))
  mesh.position.set(p.x, p.y, 0.01)
  mesh.rotation.z = p.rotation
  group.add(mesh)
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
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), makeUnlitMaterial(texture, p.opacity))
  mesh.position.set(p.x, p.y, 0.01)
  mesh.rotation.z = p.rotation
  group.add(mesh)
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
  root.position.set(p.x, p.y, 0.04)
  root.rotation.z = p.rotation
  root.traverse((node) => {
    if (node.material) {
      node.material.transparent = p.opacity < 1 || node.material.transparent
      node.material.opacity = p.opacity
      node.material.toneMapped = false
    }
  })
  group.add(root)
  return { media: null }
}

function median(values) {
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

function createPoseStabilizer() {
  const history = []
  const HISTORY_SIZE = 7

  const samplePosition = new THREE.Vector3()
  const sampleQuaternion = new THREE.Quaternion()
  const sampleScale = new THREE.Vector3(1, 1, 1)

  const currentPosition = new THREE.Vector3()
  const currentQuaternion = new THREE.Quaternion()
  const currentScale = new THREE.Vector3(1, 1, 1)
  let initialized = false

  return (matrix, outputGroup) => {
    matrix.decompose(samplePosition, sampleQuaternion, sampleScale)

    history.push({
      position: samplePosition.clone(),
      quaternion: sampleQuaternion.clone(),
      scale: sampleScale.clone(),
    })
    if (history.length > HISTORY_SIZE) history.shift()

    const targetPosition = new THREE.Vector3(
      median(history.map((h) => h.position.x)),
      median(history.map((h) => h.position.y)),
      median(history.map((h) => h.position.z)),
    )
    const targetScale = new THREE.Vector3(
      median(history.map((h) => h.scale.x)),
      median(history.map((h) => h.scale.y)),
      median(history.map((h) => h.scale.z)),
    )

    const targetQuaternion = history[Math.floor(history.length / 2)].quaternion.clone()

    if (!initialized) {
      currentPosition.copy(targetPosition)
      currentQuaternion.copy(targetQuaternion)
      currentScale.copy(targetScale)
      initialized = true
    } else {
      const posDelta = currentPosition.distanceTo(targetPosition)
      const rotDelta = currentQuaternion.angleTo(targetQuaternion)
      const scaleDelta = currentScale.distanceTo(targetScale)

      const posDeadZone = 0.004
      const rotDeadZone = THREE.MathUtils.degToRad(0.45)
      const scaleDeadZone = 0.0035

      const posAlpha = posDelta < posDeadZone ? 0 : posDelta > 0.08 ? 0.30 : posDelta > 0.03 ? 0.16 : 0.075
      const rotAlpha = rotDelta < rotDeadZone ? 0 : rotDelta > 0.16 ? 0.28 : rotDelta > 0.06 ? 0.14 : 0.06
      const scaleAlpha = scaleDelta < scaleDeadZone ? 0 : scaleDelta > 0.05 ? 0.20 : 0.07

      if (posAlpha) {
        const next = currentPosition.clone().lerp(targetPosition, posAlpha)
        const step = next.clone().sub(currentPosition)
        const maxStep = posDelta > 0.08 ? 0.03 : 0.012
        if (step.length() > maxStep) step.setLength(maxStep)
        currentPosition.add(step)
      }

      if (rotAlpha) {
        const allowed = rotDelta > 0.16 ? THREE.MathUtils.degToRad(4.0) : THREE.MathUtils.degToRad(1.5)
        const stepT = Math.min(1, allowed / Math.max(rotDelta, 1e-6), rotAlpha)
        currentQuaternion.slerp(targetQuaternion, stepT)
      }

      if (scaleAlpha) {
        const nextScale = currentScale.clone().lerp(targetScale, scaleAlpha)
        const scaleStep = nextScale.clone().sub(currentScale)
        const maxScaleStep = scaleDelta > 0.05 ? 0.025 : 0.01
        if (scaleStep.length() > maxScaleStep) scaleStep.setLength(maxScaleStep)
        currentScale.add(scaleStep)
      }
    }

    outputGroup.position.copy(currentPosition)
    outputGroup.quaternion.copy(currentQuaternion)
    outputGroup.scale.copy(currentScale)
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
    filterMinCF: 0.00035,
    filterBeta: 500,
    missTolerance: 14,
    warmupTolerance: 7,
  })

  const { renderer, scene, camera } = mindarThree
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.toneMapping = THREE.NoToneMapping
  if ('outputEncoding' in renderer && THREE.sRGBEncoding) renderer.outputEncoding = THREE.sRGBEncoding
  if ('outputColorSpace' in renderer && THREE.SRGBColorSpace) renderer.outputColorSpace = THREE.SRGBColorSpace

  const anchor = mindarThree.addAnchor(0)
  const stabilizedGroup = new THREE.Group()
  stabilizedGroup.visible = false
  scene.add(stabilizedGroup)
  const stabilizePose = createPoseStabilizer()

  let media = null
  if (asset.kind === 'video') media = (await addVideo(stabilizedGroup, asset, transform)).media
  else if (asset.kind === 'model') await addModel(stabilizedGroup, asset, transform)
  else await addImage(stabilizedGroup, asset, transform)

  let targetVisible = false
  let lostTimer = null

  anchor.onTargetFound = () => {
    if (lostTimer) {
      clearTimeout(lostTimer)
      lostTimer = null
    }
    targetVisible = true
    stabilizedGroup.visible = true
    onStatus?.('Target found')
    if (media) media.play().catch(() => {})
    onFound?.()
  }

  anchor.onTargetLost = () => {
    targetVisible = false
    onStatus?.('Searching for trigger…')
    if (lostTimer) clearTimeout(lostTimer)
    lostTimer = setTimeout(() => {
      stabilizedGroup.visible = false
      if (media) media.pause()
      onLost?.()
    }, 180)
  }

  const ambient = new THREE.AmbientLight(0xffffff, 1.0)
  scene.add(ambient)

  await mindarThree.start()
  onStatus?.('Searching for trigger…')
  renderer.setAnimationLoop(() => {
    if (targetVisible) stabilizePose(anchor.group.matrix, stabilizedGroup)
    renderer.render(scene, camera)
  })

  return async () => {
    try { if (lostTimer) clearTimeout(lostTimer) } catch {}
    try { media?.pause() } catch {}
    try { renderer.setAnimationLoop(null) } catch {}
    try { mindarThree.stop() } catch {}
    try { URL.revokeObjectURL(compiled.url) } catch {}
    try { container.innerHTML = '' } catch {}
  }
}
