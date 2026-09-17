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

async function addImage(group, asset, transform) {
  const texture = await new THREE.TextureLoader().loadAsync(asset.url)
  texture.colorSpace = THREE.SRGBColorSpace
  const img = texture.image
  const aspect = img?.width && img?.height ? img.width / img.height : 1
  const p = placement(transform)
  const width = 0.72 * p.scale
  const height = width / aspect
  const material = new THREE.MeshBasicMaterial({ map: texture, transparent: true, opacity: p.opacity, side: THREE.DoubleSide })
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), material)
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
  texture.colorSpace = THREE.SRGBColorSpace
  const p = placement(transform)
  const width = 0.72 * p.scale
  const height = width / aspect
  const material = new THREE.MeshBasicMaterial({ map: texture, transparent: true, opacity: p.opacity, side: THREE.DoubleSide })
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), material)
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
    }
  })
  group.add(root)
  return { media: null }
}

function createPoseStabilizer() {
  const targetPosition = new THREE.Vector3()
  const targetQuaternion = new THREE.Quaternion()
  const targetScale = new THREE.Vector3(1, 1, 1)
  const currentPosition = new THREE.Vector3()
  const currentQuaternion = new THREE.Quaternion()
  const currentScale = new THREE.Vector3(1, 1, 1)
  let initialized = false

  return (matrix, outputGroup) => {
    matrix.decompose(targetPosition, targetQuaternion, targetScale)

    if (!initialized) {
      currentPosition.copy(targetPosition)
      currentQuaternion.copy(targetQuaternion)
      currentScale.copy(targetScale)
      initialized = true
    } else {
      const positionDelta = currentPosition.distanceTo(targetPosition)
      const scaleDelta = currentScale.distanceTo(targetScale)
      const rotationDelta = currentQuaternion.angleTo(targetQuaternion)

      // Ignore tiny pose changes caused by camera / feature noise.
      // Increase responsiveness automatically when the user actually moves.
      const positionAlpha = positionDelta < 0.0025 ? 0 : positionDelta > 0.035 ? 0.38 : 0.16
      const rotationAlpha = rotationDelta < 0.004 ? 0 : rotationDelta > 0.08 ? 0.34 : 0.13
      const scaleAlpha = scaleDelta < 0.002 ? 0 : scaleDelta > 0.03 ? 0.30 : 0.11

      if (positionAlpha) currentPosition.lerp(targetPosition, positionAlpha)
      if (rotationAlpha) currentQuaternion.slerp(targetQuaternion, rotationAlpha)
      if (scaleAlpha) currentScale.lerp(targetScale, scaleAlpha)
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
    // Stronger One Euro filtering for image targets. Lower cutoff smooths
    // stationary jitter; beta keeps real camera movement responsive.
    filterMinCF: 0.0005,
    filterBeta: 700,
    // Persist briefly through single-frame tracking misses to avoid flicker.
    missTolerance: 10,
    warmupTolerance: 6,
  })

  const { renderer, scene, camera } = mindarThree
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  const anchor = mindarThree.addAnchor(0)

  // Render content on a separate group. We read MindAR's raw/filtered anchor
  // pose each frame and apply an additional adaptive stabilization pass.
  const stabilizedGroup = new THREE.Group()
  stabilizedGroup.visible = false
  scene.add(stabilizedGroup)
  const stabilizePose = createPoseStabilizer()

  let media = null
  if (asset.kind === 'video') media = (await addVideo(stabilizedGroup, asset, transform)).media
  else if (asset.kind === 'model') await addModel(stabilizedGroup, asset, transform)
  else await addImage(stabilizedGroup, asset, transform)

  let targetVisible = false
  anchor.onTargetFound = () => {
    targetVisible = true
    stabilizedGroup.visible = true
    onStatus?.('Target found')
    if (media) media.play().catch(() => {})
    onFound?.()
  }
  anchor.onTargetLost = () => {
    targetVisible = false
    stabilizedGroup.visible = false
    onStatus?.('Searching for trigger…')
    if (media) media.pause()
    onLost?.()
  }

  const ambient = new THREE.AmbientLight(0xffffff, 2.1)
  scene.add(ambient)

  await mindarThree.start()
  onStatus?.('Searching for trigger…')
  renderer.setAnimationLoop(() => {
    if (targetVisible) stabilizePose(anchor.group.matrix, stabilizedGroup)
    renderer.render(scene, camera)
  })

  return async () => {
    try { media?.pause() } catch {}
    try { renderer.setAnimationLoop(null) } catch {}
    try { mindarThree.stop() } catch {}
    try { URL.revokeObjectURL(compiled.url) } catch {}
    try { container.innerHTML = '' } catch {}
  }
}
