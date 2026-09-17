// Orbit AR tracking adapter boundary.
// The editor can remain independent from the image-tracking implementation.
// A concrete tracker should implement this interface and emit pose updates.

export class OrbitTracker {
  async compileTarget(_imageFile) {
    throw new Error('Image target compiler not connected yet')
  }

  async start({ videoElement, targetData, onFound, onUpdate, onLost }) {
    void videoElement
    void targetData
    void onFound
    void onUpdate
    void onLost
    throw new Error('Camera tracker not connected yet')
  }

  async stop() {}
}
