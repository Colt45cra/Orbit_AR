export const defaultTransform = { x: 50, y: 50, scale: 1, rotation: 0, tilt: 0, opacity: 1 }
export function safeURL(value, base, data = false) {
  const url = new URL(value, base)
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost','127.0.0.1'].includes(url.hostname)) && !(data && url.protocol === 'data:')) throw new Error('Experience files must use HTTPS.')
  return url.href
}
export function parseExperience(raw, base) {
  if (raw?.version !== 1 || !raw.target?.compiled || !raw.asset?.url || !['image','video','model'].includes(raw.asset.kind)) throw new Error('This is not a published Orbit AR experience.')
  const transform = { ...defaultTransform }
  for (const key of Object.keys(transform)) {
    const value = raw.transform?.[key]
    if (value !== undefined && (!Number.isFinite(value) || Math.abs(value) > 1000)) throw new Error('Invalid placement settings.')
    if (value !== undefined) transform[key] = value
  }
  return { title: String(raw.title || 'AllTheMoney experience').slice(0,120), target: { compiled: safeURL(raw.target.compiled, base, true), image: raw.target.image ? safeURL(raw.target.image, base, true) : null }, asset: { kind: raw.asset.kind, url: safeURL(raw.asset.url, base, true), chroma: raw.chroma || { enabled: false } }, transform, chroma: raw.chroma || { enabled: false } }
}
export function viewerLink(manifest, origin = window.location.origin) {
  const url = new URL('/ar', origin)
  url.searchParams.set('experience', safeURL(manifest, origin))
  return url.href
}
export function blobData(blob) {
  return new Promise((resolve,reject) => { const r = new FileReader(); r.onload = () => resolve(r.result); r.onerror = reject; r.readAsDataURL(blob) })
}
