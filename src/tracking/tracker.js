import { createTargetCache, startupError } from './target-cache.js'
import { createCardTracker } from './card-tracker.js'
import { installOrbitCamera } from './camera.js'
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
      reject(new Error('Trigger image could not be decoded. Upload it again as JPG or PNG.'))
    }
    img.src = url
  })
}

export const compileOrbitTarget = createTargetCache(async (file, onProgress) => {
  const image = await fileToImage(file)
  const compiler = new Compiler()
  await compiler.compileImageTargets([image], progress => onProgress(Math.round(progress)))
  const buffer = await compiler.exportData()
  return {
    blob: new Blob([buffer], { type: 'application/octet-stream' }),
    imageWidth: image.naturalWidth,
    imageHeight: image.naturalHeight,
  }
})

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
  video.crossOrigin = 'anonymous'
  video.loop = true
  video.muted = true
  video.playsInline = true
  video.preload = 'auto'
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => done(new Error('Video could not load. Try an MP4 encoded with H.264.')), 20000)
    function done(error) {
      clearTimeout(timer)
      video.onloadeddata = null
      video.onerror = null
      if (error) { video.removeAttribute('src'); video.load(); reject(error) }
      else resolve()
    }
    video.onloadeddata = () => done()
    video.onerror = () => done(new Error('This video cannot be decoded by your browser. Try an H.264 MP4.'))
    video.src = asset.url
    video.load()
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
  const chroma = asset.chroma || {}
  const chromaMaterial = chroma.enabled ? new THREE.ShaderMaterial({
    uniforms: {
      map: { value: texture },
      keyColor: { value: new THREE.Color(chroma.keyColor || '#00ff00') },
      similarity: { value: Number(chroma.similarity ?? 0.34) },
      smoothness: { value: Number(chroma.smoothness ?? 0.14) },
      spill: { value: Number(chroma.spill ?? 0.32) },
      opacity: { value: p.opacity },
    },
    vertexShader: `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      precision mediump float;
      varying vec2 vUv;
      uniform sampler2D map;
      uniform vec3 keyColor;
      uniform float similarity;
      uniform float smoothness;
      uniform float spill;
      uniform float opacity;
      vec2 chroma(vec3 c) {
        return vec2(dot(c, vec3(-0.168736, -0.331264, 0.5)),
                    dot(c, vec3(0.5, -0.418688, -0.081312)));
      }
      void main() {
        vec4 source = texture2D(map, vUv);
        float distanceFromKey = distance(chroma(source.rgb), chroma(keyColor));
        float alpha = smoothstep(similarity, similarity + max(smoothness, 0.001), distanceFromKey);
        // Reduce green spill at transparent edges.
        float edge = 1.0 - smoothstep(0.0, 0.42, distanceFromKey);
        float spillAmount = spill * edge;
        source.g = mix(source.g, min(source.g, max(source.r, source.b)), spillAmount);
        gl_FragColor = vec4(source.rgb, source.a * alpha * opacity);
      }
    `,
    transparent: true,
    side: THREE.DoubleSide,
    depthWrite: false,
    toneMapped: false,
  }) : makeUnlitMaterial(texture, p.opacity)
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, height), chromaMaterial)
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

export async function startOrbitTracking({ container, targetFile, targetSrc, targetAspect = 7 / 4, asset, transform, onProgress, onFound, onLost, onStatus, deviceId, signal, onCamera }) {
  if (!container || (!targetFile && !targetSrc) || !asset) throw new Error('Trigger image and AR content are required')

  if (!targetSrc) onStatus?.('Compiling trigger image…')
  let compiled
  try { compiled = targetSrc ? { url: targetSrc, imageWidth: targetAspect, imageHeight: 1 } : await compileOrbitTarget(targetFile, onProgress) }
  catch (error) { throw startupError(error, 'Preparing trigger') }
  if (signal?.aborted) { if (!targetSrc) URL.revokeObjectURL(compiled.url); signal.throwIfAborted() }
  onStatus?.('Preparing content…')

  const mindarThree = new MindARThree({
    container,
    imageTargetSrc: compiled.url,
    uiLoading: 'no',
    uiScanning: 'no',
    uiError: 'no',
    // Recognition seeds the card-edge search; do not retain poses after loss.
    filterMinCF: 0.01,
    filterBeta: 1000,
    missTolerance: 0,
    warmupTolerance: 2,
  })

  installOrbitCamera(mindarThree, { deviceId, signal, onCamera })
  const { renderer, scene, camera } = mindarThree
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.toneMapping = THREE.NoToneMapping
  if ('outputEncoding' in renderer && THREE.sRGBEncoding) renderer.outputEncoding = THREE.sRGBEncoding
  if ('outputColorSpace' in renderer && THREE.SRGBColorSpace) renderer.outputColorSpace = THREE.SRGBColorSpace

  const anchor = mindarThree.addAnchor(0)
  const content = new THREE.Group()
  content.matrixAutoUpdate = false
  content.visible = false
  scene.add(content)
  let cardTracker = null

  let stage = 'Loading AR content'
  let cleaned = false
  let media = null
  let lostTimer = null
  const cleanup = async () => {
    if (cleaned) return
    cleaned = true
    if (mindarThree.controller) mindarThree.controller.onUpdate = null
    if (lostTimer) clearTimeout(lostTimer)
    cardTracker?.dispose()
    media?.pause()
    if (media) { media.removeAttribute('src'); media.load() }
    renderer.setAnimationLoop(null)
    try { if (mindarThree.controller) mindarThree.stop() } catch {}
    mindarThree.video?.srcObject?.getTracks().forEach(track => track.stop())
    scene.traverse(node => {
      node.geometry?.dispose()
      const materials = Array.isArray(node.material) ? node.material : [node.material]
      materials.forEach(material => { material?.map?.dispose(); material?.dispose() })
    })
    mindarThree.controller?.worker?.terminate()
    renderer.dispose()
    renderer.forceContextLoss()
    if (!targetSrc) URL.revokeObjectURL(compiled.url)
    container.innerHTML = ''
  }
  try {
    if (asset.kind === 'video') media = (await addVideo(content, asset, transform)).media
    else if (asset.kind === 'model') await addModel(content, asset, transform)
    else await addImage(content, asset, transform)

    anchor.onTargetFound = () => {

      if (lostTimer) {
        clearTimeout(lostTimer)
        lostTimer = null
      }
      onStatus?.('Checking card corners…')
      if (media) media.play().catch(() => {})
      onFound?.()
    }

    anchor.onTargetLost = () => {

      cardTracker?.lost()
      content.visible = false
      onStatus?.('Searching for card…')
      if (lostTimer) clearTimeout(lostTimer)
      lostTimer = setTimeout(() => {
        if (media) media.pause()
        onLost?.()
      }, 140)
    }

    // MindAR calls this immediately after writing its final target-space matrix.
    // Use the recognized target only to seed the measured card-edge search.
    anchor.onTargetUpdate = () => {
      if (anchor.visible) cardTracker?.observe()
    }

    const ambient = new THREE.AmbientLight(0xffffff, 1.0)
    scene.add(ambient)

    signal?.throwIfAborted()
    stage = 'Starting selected camera'
    onStatus?.('Starting camera…')
    await mindarThree.start()
    signal?.throwIfAborted()
    stage = 'Preparing card tracking'
    cardTracker = createCardTracker({ mindar: mindarThree, anchor, content, aspect: compiled.imageHeight / compiled.imageWidth, onStatus })
    if (anchor.visible) cardTracker.observe()
    onStatus?.('Searching for card…')
    renderer.setAnimationLoop(() => { cardTracker.update(); renderer.render(scene, camera) })

    const stop = cleanup
    stop.setMuted = value => { if (media) media.muted = value }
    stop.play = () => media?.play().catch(() => {})
    return stop
  } catch (error) {
    await cleanup()
    throw startupError(error, stage)
  }
}
