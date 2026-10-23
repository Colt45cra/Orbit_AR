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

## AllTheMoney AR companion viewer

`/ar?experience=https%3A%2F%2Fyour-host%2Fexperiences%2Fswell.json`
opens a public experience and requests camera access. No sign-in or installation
is required. The full printed card is the image target; the QR opens the URL.
Video is muted by default and has an explicit sound button. Hide/pause on loss of
tracking; close the camera when finished.

The Studio's **Export viewer experience** compiles the target once and downloads
`experience.json` with the target image, compiled target and media embedded.
Serve it over HTTPS (for example in `public/experiences/swell.json` in this repo),
then enter that public URL in **Create card QR**. Download the QR PNG for print.
The QR links to the manifest, so replacing its media at the same URL preserves
printed cards. Remote manifests/media must allow CORS from the viewer origin.
A version 1 manifest can alternatively reference separate HTTPS media and .mind
files, avoiding base64 overhead for larger videos.

This is a web companion, not an App Store release. Browser camera permission is
required; sound needs a user gesture. Test real iPhone Safari and Android Chrome
against the final printed artwork before distributing cards. The final Swell
card artwork and video are still required; no campaign assets are supplied here.
