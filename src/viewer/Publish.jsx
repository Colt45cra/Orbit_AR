import React, { useState } from 'react'
import QRCode from 'qrcode'
import { blobData, viewerLink } from './experience.js'

export default function Publish({trigger, asset, transform, title}) {
  const [busy,setBusy] = useState(false), [message,setMessage] = useState(''), [url,setURL] = useState(''), [qr,setQR] = useState(''), [link,setLink] = useState('')
  async function exportExperience() {
    setBusy(true); setMessage('Preparing card tracking…')
    let compiled
    try {
      const { compileOrbitTarget } = await import('../tracking/tracker.js')
      compiled = await compileOrbitTarget(trigger.file, n => setMessage(`Preparing card tracking: ${n}%`))
      const target = await fetch(compiled.url).then(r=>r.blob())
      const experience = { version: 1, title, target: { compiled: await blobData(target), image: await blobData(trigger.file) }, asset: { kind: asset.kind, url: await blobData(asset.file) }, transform, chroma: asset.chroma || { enabled: false } }
      const downloadURL = URL.createObjectURL(new Blob([JSON.stringify(experience)], {type:'application/json'}))
      const a = document.createElement('a'); a.href = downloadURL; a.download = 'experience.json'; a.click(); setTimeout(()=>URL.revokeObjectURL(downloadURL),1000)
      setMessage('Experience exported. Host experience.json at a public HTTPS URL, then create its QR below.')
    } catch(e) { setMessage(e.message || 'Export failed. Please try again.') }
    finally { if(compiled) URL.revokeObjectURL(compiled.url); setBusy(false) }
  }
  async function makeQR() {
    try { const next = viewerLink(url); setQR(await QRCode.toDataURL(next,{width:1000,margin:4,errorCorrectionLevel:'M'})); setLink(next); setMessage('QR ready. Test it on the printed card before handing it out.') } catch(e) {setMessage(e.message)}
  }
  return <section className="panel" style={{padding:24,marginBottom:20}}><div className="eyebrow">ALLTHEMONEY AR • SHARE</div><h2>From your studio to their camera.</h2><p>Export your image, video or model with precompiled card tracking. The viewer opens directly into AR, with no account or installation.</p><button className="primary-btn" disabled={!trigger || !asset || busy} onClick={exportExperience}>{busy ? 'Preparing…' : 'Export viewer experience'}</button><p><label className="field-label" htmlFor="experience-url">Hosted experience JSON URL</label><input id="experience-url" className="text-input" type="url" placeholder="https://your-site.com/experiences/swell.json" value={url} onChange={e=>{setURL(e.target.value);setQR('');setLink('')}}/></p><button className="primary-btn" disabled={!url || busy} onClick={makeQR}>Create card QR</button><p role="status">{message}</p>{qr && <div><img src={qr} alt="QR code opening this AllTheMoney AR experience" style={{width:200,background:'white'}}/><p><a href={qr} download="AllTheMoney-AR-QR.png">Download QR PNG</a> · <a href={link} target="_blank" rel="noreferrer">Open viewer</a></p><input className="text-input" aria-label="Viewer link" readOnly value={link}/></div>}</section>
}
