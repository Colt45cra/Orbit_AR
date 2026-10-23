import React, { useEffect, useRef, useState } from 'react'
import { parseExperience, safeURL } from './experience.js'
import './viewer.css'

export default function Viewer() {
  const stage = useRef(null), controller = useRef(null)
  const [experience, setExperience] = useState(null), [error, setError] = useState('')
  const [status, setStatus] = useState('Loading your experience…'), [active, setActive] = useState(false)
  const [found, setFound] = useState(false), [sound, setSound] = useState(false), [retry, setRetry] = useState(0)
  useEffect(() => {
    const abort = new AbortController()
    const manifest = new URLSearchParams(location.search).get('experience')
    if (!manifest) { setStatus('Bring your card to life.'); return }
    ;(async () => {
      try {
        const url = safeURL(manifest, location.origin)
        const response = await fetch(url, { signal: abort.signal, credentials: 'omit' })
        if (!response.ok) throw new Error('This experience could not be found. Please check the QR link.')
        const exp = parseExperience(await response.json(), response.url)
        if (abort.signal.aborted) return
        setExperience(exp); setActive(true)
      } catch (e) { if (e.name !== 'AbortError') setError(e.message) }
    })()
    return () => abort.abort()
  }, [])
  useEffect(() => {
    if (!active || !experience) return
    let cancelled = false, stop
    setError(''); setFound(false); setSound(false)
    ;(async () => {
      try {
        if (!window.isSecureContext || !navigator.mediaDevices) throw new Error('Open this link in Safari or Chrome over HTTPS to use your camera.')
        const { startOrbitTracking } = await import('../tracking/tracker.js')
        if (cancelled) return
        stop = await startOrbitTracking({ container: stage.current, targetSrc: experience.target.compiled, asset: experience.asset, transform: experience.transform,
          onStatus: s => !cancelled && setStatus(s), onFound: () => !cancelled && setFound(true), onLost: () => !cancelled && setFound(false) })
        if (cancelled) await stop(); else controller.current = stop
      } catch(e) { if (!cancelled) setError(e.name === 'NotAllowedError' ? 'Camera access is needed. Allow it in your browser settings, then try again.' : e.message || 'Could not start AR.') }
    })()
    return () => { cancelled = true; controller.current = null; stop?.() }
  }, [active, experience, retry])
  return <main className="atm-viewer">
    <div className="atm-camera" ref={stage}/>
    <header className="atm-header"><a href="/ar" aria-label="AllTheMoney AR home" className="atm-logo">ATM<span>ALLTHEMONEY <b>AR</b></span></a><span className="atm-live">{found ? '● CARD LOCKED' : 'A NEW DIMENSION'}</span></header>
    {active && !error ? <>
      {!found && <div className="atm-scan"><div className="atm-reticle"><i/><i/><i/><i/></div><h1>Aim at your card.</h1><p>Keep the full card in view, in good light.</p>{experience.target.image && <img src={experience.target.image} alt="The card to scan"/>}</div>}
      <footer className="atm-bottom"><span role="status">{found ? 'Your experience is live' : status}</span><div>{experience.asset.kind === 'video' && <button onClick={() => { const enabled = !sound; controller.current?.setMuted(!enabled); controller.current?.play(); setSound(enabled) }}>{sound ? 'Mute sound' : 'Enable sound'}</button>}<button onClick={() => setActive(false)}>Close camera</button></div><small>POWERED BY ORBIT AR</small></footer>
    </> : <section className="atm-welcome"><div className="atm-orb">AR</div><p className="atm-kicker">ALLTHEMONEY • AUGMENTED REALITY</p><h1>Your card.<br/><em>Another dimension.</em></h1><p>{error || (experience ? 'Point your camera at the card to watch it come to life.' : 'Scan the QR on an ATM card with your phone camera to open its experience.')}</p>{experience && <button className="atm-start" onClick={() => { setActive(true); setRetry(n=>n+1) }}>{error ? 'Try camera again' : 'Open camera'} →</button>}<p className="atm-privacy">No account needed. Camera images stay on your device.</p></section>}
  </main>
}
