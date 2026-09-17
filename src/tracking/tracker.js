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

async function addImage(anchor, asset, transform) {
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
  anchor.group.add(mesh)
  return { media: null }
}

async function addVideo(anchor, asset, transform) {
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
  anchor.group.add(mesh)
  return { media: video }
}

async function addModel(anchor, asset, transform) {
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
  anchor.group.add(root)
  return { media: null }
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
    filterMinCF: 0.001,
    filterBeta: 0.01,
    missTolerance: 5,
    warmupTolerance: 5,
  })

  const { renderer, scene, camera } = mindarThree
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  const anchor = mindarThree.addAnchor(0)

  let media = null
  if (asset.kind === 'video') media = (await addVideo(anchor, asset, transform)).media
  else if (asset.kind === 'model') await addModel(anchor, asset, transform)
  else await addImage(anchor, asset, transform)

  anchor.onTargetFound = () => {
    onStatus?.('Target found')
    if (media) media.play().catch(() => {})
    onFound?.()
  }
  anchor.onTargetLost = () => {
    onStatus?.('Searching for trigger…')
    if (media) media.pause()
    onLost?.()
  }

  const ambient = new THREE.AmbientLight(0xffffff, 2.1)
  scene.add(ambient)

  await mindarThree.start()
  onStatus?.('Searching for trigger…')
  renderer.setAnimationLoop(() => renderer.render(scene, camera))

  return async () => {
    try { media?.pause() } catch {}
    try { renderer.setAnimationLoop(null) } catch {}
    try { mindarThree.stop() } catch {}
    try { URL.revokeObjectURL(compiled.url) } catch {}
    try { container.innerHTML = '' } catch {}
  }
}
