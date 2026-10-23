// Share compilation across retries/lens changes; every session owns its blob URL.
export function createTargetCache(compile) {
  const cache = new WeakMap()
  return async (file, onProgress = () => {}) => {
    let entry = cache.get(file)
    if (!entry) {
      entry = Promise.resolve().then(() => compile(file, onProgress))
      cache.set(file, entry)
      entry.catch(() => { if (cache.get(file) === entry) cache.delete(file) })
    }
    const result = await entry
    onProgress(100)
    return { ...result, url: URL.createObjectURL(result.blob) }
  }
}

export function startupError(error, stage) {
  const detail = error?.message || (typeof error === 'string' && error) || error?.type || 'Unknown browser error'
  return new Error(`${stage}: ${detail}`)
}
