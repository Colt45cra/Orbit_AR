# Orbit AR

Orbit AR is a mobile-first studio for creating image-triggered augmented reality experiences.

## Current foundation

- Upload a trigger image.
- Attach PNG/JPG artwork, video, or a GLB 3D model.
- Position, scale, rotate, and adjust opacity over the trigger.
- Preview the experience before camera tracking is connected.
- Save editor state locally in the browser.
- Tracking implementation is isolated behind `src/tracking/tracker.js`.

## Next build phase

1. Compile uploaded images into image-target tracking data.
2. Open the phone camera securely over HTTPS.
3. Detect the trigger image and estimate its pose.
4. Anchor the configured PNG, video, or 3D model to the physical target.
5. Pause/hide content when tracking is lost and resume when reacquired.
6. Add Supabase-backed projects, media storage, publishing, and shareable AR URLs.

## Local development

```bash
npm install
npm run dev
```

## Production build

```bash
npm run build
```

The project is Vite + React and is ready to deploy on Vercel.
