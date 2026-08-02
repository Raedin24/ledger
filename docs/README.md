# docs

`icon.svg` is a transcription of the adaptive launcher icon
(`app/src/main/res/drawable/ic_launcher_foreground.xml` over
`@color/ic_launcher_background`), cropped to the 72dp region a launcher
actually shows. `icon.png` is that file rendered at 512×512.

The SVG is kept alongside the raster so the PNG can be regenerated at any size
rather than edited by hand:

```sh
npx @resvg/resvg-js-cli docs/icon.svg docs/icon.png
```

Changing the app icon means changing the vector drawable in `app/src/main/res`;
these two are downstream of it, not the source.
