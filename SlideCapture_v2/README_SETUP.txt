BUILD THE APK (no Android Studio)
1. github.com -> sign up (free) -> New repository (e.g. SlideCapture) -> Create.
2. "uploading an existing file": drag in everything from this unzipped folder
   (app, build.gradle.kts, settings.gradle.kts, gradle.properties, stitch_scan.ijm). Commit.
3. IMPORTANT: add the workflow separately: Add file > Create new file, name it exactly
   .github/workflows/build.yml   and paste the contents of that file. Commit.
4. Actions tab -> wait for the green check (3-6 min) -> open the run -> Artifacts -> SlideCapture-apk.
   If it fails (red X), open the run and send me the error text.
5. Unzip the artifact, copy app-debug.apk to the Pixel 8, tap it, allow "install unknown apps".

USE
- Plug the USB camera in (OTG), open the app, allow camera, tap Refresh, pick the USB camera.
- Calibrate each objective once: put the ruler on the stage, read how many mm span the preview width, type it in.
- Tiles save to Pictures/SlideScans/Scan_..._R<rows>xC<cols>/tile_0001.jpg ... in snake order.
- On PC: Fiji > run stitch_scan.ijm, pick the scan folder, enter columns/rows from the folder name.
