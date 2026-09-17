import React, { useEffect, useMemo, useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Box, Camera, Image as ImageIcon, Layers3, Play, RotateCcw, Save, Sparkles, Upload, Video, X } from 'lucide-react'
import '@google/model-viewer'
import './styles.css'

const emptyTransform = { x: 50, y: 50, scale: 1, rotation: 0, opacity: 1 }

function App() {
  const [projectName, setProjectName] = useState('Untitled Orbit')
  const [trigger, setTrigger] = useState(null)
  const [asset, setAsset] = useState(null)
  const [transform, setTransform] = useState(emptyTransform)
  const [saved, setSaved] = useState(false)
  const triggerInput = useRef(null)
  const assetInput = useRef(null)

  useEffect(() => {
    const raw = localStorage.getItem('orbit-ar-project')
    if (!raw) return
    try {
      const parsed = JSON.parse(raw)
      if (parsed.projectName) setProjectName(parsed.projectName)
      if (parsed.transform) setTransform(parsed.transform)
    } catch {}
  }, [])

  const assetKind = useMemo(() => {
    if (!asset) return null
    if (asset.type.startsWith('video/')) return 'video'
    if (asset.type === 'model/gltf-binary' || asset.name?.toLowerCase().endsWith('.glb')) return 'model'
    return 'image'
  }, [asset])

  function readFile(file, setter) {
    if (!file) return
    const url = URL.createObjectURL(file)
    setter({ name: file.name, type: file.type || 'application/octet-stream', url })
    setSaved(false)
  }

  function saveProject() {
    localStorage.setItem('orbit-ar-project', JSON.stringify({ projectName, transform }))
    setSaved(true)
    setTimeout(() => setSaved(false), 1800)
  }

  function resetTransform() {
    setTransform(emptyTransform)
    setSaved(false)
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand-lockup">
          <div className="orbit-mark"><Sparkles size={18} /></div>
          <div>
            <div className="brand-name">ORBIT <span>AR</span></div>
            <div className="brand-sub">Image-triggered experiences</div>
          </div>
        </div>
        <button className="ghost-btn" onClick={saveProject}><Save size={16} /> {saved ? 'Saved' : 'Save'}</button>
      </header>

      <main className="workspace">
        <section className="hero-panel">
          <div className="eyebrow">ORBIT STUDIO</div>
          <h1>Turn any image into an <span>AR trigger.</span></h1>
          <p>Upload the image people will point their camera at, attach a PNG, video, or 3D model, then position the experience exactly where you want it.</p>
        </section>

        <section className="studio-grid">
          <aside className="panel controls-panel">
            <label className="field-label">Project name</label>
            <input className="text-input" value={projectName} onChange={e => { setProjectName(e.target.value); setSaved(false) }} />

            <div className="section-title"><ImageIcon size={17}/> Trigger image</div>
            <button className="upload-card" onClick={() => triggerInput.current?.click()}>
              <Upload size={21} />
              <strong>{trigger ? trigger.name : 'Upload trigger image'}</strong>
              <span>JPG, PNG, WEBP</span>
            </button>
            <input ref={triggerInput} hidden type="file" accept="image/*" onChange={e => readFile(e.target.files?.[0], setTrigger)} />

            <div className="section-title"><Layers3 size={17}/> AR content</div>
            <button className="upload-card" onClick={() => assetInput.current?.click()}>
              <Upload size={21} />
              <strong>{asset ? asset.name : 'Upload media or model'}</strong>
              <span>PNG, JPG, MP4, WEBM, GLB</span>
            </button>
            <input ref={assetInput} hidden type="file" accept="image/*,video/*,.glb,model/gltf-binary" onChange={e => readFile(e.target.files?.[0], setAsset)} />

            <div className="transform-head">
              <div className="section-title no-margin"><Box size={17}/> Placement</div>
              <button className="icon-btn" onClick={resetTransform} title="Reset"><RotateCcw size={15}/></button>
            </div>

            <Slider label="Horizontal" min="0" max="100" value={transform.x} onChange={v => setTransform(t => ({...t, x:v}))} suffix="%" />
            <Slider label="Vertical" min="0" max="100" value={transform.y} onChange={v => setTransform(t => ({...t, y:v}))} suffix="%" />
            <Slider label="Scale" min="0.2" max="3" step="0.05" value={transform.scale} onChange={v => setTransform(t => ({...t, scale:v}))} suffix="×" />
            <Slider label="Rotation" min="-180" max="180" value={transform.rotation} onChange={v => setTransform(t => ({...t, rotation:v}))} suffix="°" />
            <Slider label="Opacity" min="0.1" max="1" step="0.05" value={transform.opacity} onChange={v => setTransform(t => ({...t, opacity:v}))} />
          </aside>

          <section className="panel preview-panel">
            <div className="preview-toolbar">
              <div>
                <div className="preview-title">Trigger preview</div>
                <div className="preview-sub">What appears when the image is recognized</div>
              </div>
              <div className="preview-badge">LIVE PREVIEW</div>
            </div>

            <div className="stage-wrap">
              <div className="stage">
                {trigger ? <img className="trigger-img" src={trigger.url} alt="Trigger"/> : <EmptyTrigger />}
                {asset && <AssetLayer asset={asset} kind={assetKind} transform={transform} />}
              </div>
            </div>

            <div className="status-row">
              <Status icon={<ImageIcon size={16}/>} label="Trigger" value={trigger ? 'Ready' : 'Missing'} active={!!trigger}/>
              <Status icon={assetKind === 'video' ? <Video size={16}/> : assetKind === 'model' ? <Box size={16}/> : <ImageIcon size={16}/>} label="Content" value={asset ? assetKind : 'Missing'} active={!!asset}/>
              <Status icon={<Camera size={16}/>} label="Tracking" value="Next phase" active={false}/>
            </div>
          </section>
        </section>

        <section className="panel tracking-panel">
          <div className="tracking-icon"><Camera size={24}/></div>
          <div className="tracking-copy">
            <div className="section-title no-margin">Camera tracking pipeline</div>
            <p>The editor is ready for a real image-target tracker. The next integration will compile the uploaded trigger into tracking data, recognize it through the phone camera, estimate pose, then anchor this same configured content to the physical image.</p>
          </div>
          <button className="primary-btn" disabled><Play size={16}/> AR test coming next</button>
        </section>
      </main>
    </div>
  )
}

function Slider({ label, value, onChange, suffix='', ...props }) {
  return <div className="slider-row">
    <div className="slider-meta"><span>{label}</span><strong>{Number(value).toFixed(props.step && Number(props.step) < 1 ? 2 : 0)}{suffix}</strong></div>
    <input type="range" value={value} onChange={e => { onChange(Number(e.target.value)); }} {...props} />
  </div>
}

function EmptyTrigger() {
  return <div className="empty-trigger">
    <div className="corner tl"/><div className="corner tr"/><div className="corner bl"/><div className="corner br"/>
    <ImageIcon size={34}/>
    <strong>Add a trigger image</strong>
    <span>Your target image will appear here</span>
  </div>
}

function AssetLayer({ asset, kind, transform }) {
  const style = {
    left: `${transform.x}%`, top: `${transform.y}%`,
    transform: `translate(-50%, -50%) scale(${transform.scale}) rotate(${transform.rotation}deg)`,
    opacity: transform.opacity,
  }
  if (kind === 'video') return <video className="asset-layer" style={style} src={asset.url} autoPlay muted loop playsInline controls={false}/>
  if (kind === 'model') return <model-viewer class="asset-layer model-layer" style={style} src={asset.url} camera-controls auto-rotate shadow-intensity="1" exposure="1" />
  return <img className="asset-layer" style={style} src={asset.url} alt="AR content"/>
}

function Status({ icon, label, value, active }) {
  return <div className={`status-pill ${active ? 'active' : ''}`}>{icon}<span>{label}</span><strong>{value}</strong></div>
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>)
