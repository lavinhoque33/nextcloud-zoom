# Nextcloud Zoom

A personal build of the [Nextcloud Android app](https://github.com/nextcloud/android) whose full-screen
image viewer keeps full resolution and can zoom far enough to read very tall images (comic strips,
long screenshots). It installs next to the official app as **Nextcloud Zoom**
(`com.nextcloud.client.zoom`) with its own account type and provider authorities, so the store app is
untouched and keeps updating.

## What changes

Upstream's viewer loses detail in three places; for a 540×21242 strip the result is unreadable at any zoom.

| Where | Upstream | Nextcloud Zoom |
|---|---|---|
| Image not downloaded | requests a preview at half the screen size | requests a preview twice the screen's short side; strips come back at full width |
| Image downloaded | decodes to fit the screen (540×21242 → 67×2655) | decodes at full resolution up to 16 MP |
| Zoom levels | fixed 1× / 1.75× / 3× | double tap fills the screen along the short side; maximum is 3× past that and past 1:1 pixels |

While zoomed in, swipe to pan the current image. Zoom back out until the whole image fits to swipe to another file.

Gallery thumbnails and the preview cache are unchanged.

The `zoom` flavor is otherwise the F-Droid (`generic`) build, so it also has no Google Play
restriction on opening `.apk` files.

Files: `app/src/zoom/`, the `zoom` flavor in `app/build.gradle.kts`, `DetailZoomPhotoView.kt`,
`BitmapUtils.decodeFileForViewer`/`decodeBytesForViewer`, `ResizedImageGenerator.getFullDetail`,
`getFullDetailPreviewEndpoint`, and the viewer changes in `PreviewMediaFragment` and
`ResizedImageGenerationJob`.

## Reading PDFs

Tap a PDF to open it in Nextcloud Zoom. Remote PDFs download before opening; downloaded PDFs can be read offline.
Scroll to read. Tapping a page leaves you in the document at the same position. Each page fits the reader width
independently, even when a document mixes page sizes. To use another reader, choose **Open with** on the downloaded file.
The app's bottom navigation stays hidden while reading and returns when you go back to the file list.

## Build and sign

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleZoomRelease
cd app/build/outputs/apk/zoom/release
zipalign -f -p 4 app-zoom-release-unsigned.apk aligned.apk
apksigner sign --ks ~/.android/nextcloud-zoom-release.jks \
  --ks-pass file:$HOME/.android/nextcloud-zoom-release.pass --ks-key-alias nczoom \
  --out Nextcloud-Zoom.apk aligned.apk
```

Always sign with the same key (`~/.android/nextcloud-zoom-release.jks`, not in this repository):
Android only installs an update over an app signed with the same key. A different key means
uninstalling Nextcloud Zoom first and logging in again.

## Update to a new upstream release

```sh
git fetch upstream --tags
git rebase --onto <new-tag> <old-tag> zoom
```

Then build, sign and install as above. The change is small and confined to the image viewer, so
conflicts are rare; when they happen they are in `PreviewMediaFragment.kt` or `ResizedImageGenerator.kt`.
