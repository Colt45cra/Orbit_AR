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
  let initialized = false

  return {
    reset() {
      initialized = false
    },
    update(group) {
      group.matrix.decompose(rawPosition, rawQuaternion, rawScale)

      if (!initialized) {
        smoothPosition.copy(rawPosition)
        smoothQuaternion.copy(rawQuaternion)
        smoothScale.copy(rawScale)
        initialized = true
      } else {
        const positionDelta = smoothPosition.distanceTo(rawPosition)
        const rotationDelta = smoothQuaternion.angleTo(rawQuaternion)
        const scaleDelta = smoothScale.distanceTo(rawScale)

        // Small pose changes are mostly feature/camera noise. Larger changes
        // are followed progressively faster so deliberate phone motion stays responsive.
        const positionDeadZone = THREE.MathUtils.lerp(0.0025, 0.0032, upright)
        const rotationDeadZone = THREE.MathUtils.degToRad(THREE.MathUtils.lerp(0.30, 0.48, upright))
        const scaleDeadZone = THREE.MathUtils.lerp(0.0025, 0.0030, upright)

        if (positionDelta > positionDeadZone) {
          const smallAlpha = THREE.MathUtils.lerp(0.075, 0.055, upright)
          const alpha = positionDelta > 0.07 ? 0.50 : positionDelta > 0.025 ? 0.28 : smallAlpha
          smoothPosition.lerp(rawPosition, alpha)
        }

        if (rotationDelta > rotationDeadZone) {
          // Upright planes visually amplify tiny angular noise at their top edge,
          // so damp only the micro-rotation band more strongly as tilt approaches 90°.
          const smallAlpha = THREE.MathUtils.lerp(0.07, 0.04, upright)
          const mediumAlpha = THREE.MathUtils.lerp(0.25, 0.20, upright)
          const alpha = rotationDelta > 0.14 ? 0.48 : rotationDelta > 0.045 ? mediumAlpha : smallAlpha
          smoothQuaternion.slerp(rawQuaternion, alpha)
        }

        if (scaleDelta > scaleDeadZone) {
          const smallAlpha = THREE.MathUtils.lerp(0.08, 0.06, upright)
          const alpha = scaleDelta > 0.045 ? 0.35 : smallAlpha
          smoothScale.lerp(rawScale, alpha)
        }
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
    filterMinCF: 0.001,
    filterBeta: 1000,
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
