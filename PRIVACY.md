# Privacy

Mise does not operate a hosted service. The person running a Mise server controls that installation and its data.

## Data stored by Mise

The server stores recipes, collections, favorites, and grocery items in `DATA_DIR/state.json`. Images fetched during imports are kept under `DATA_DIR/images`. The web client keeps a fallback copy of the recipe box and its server URL in browser local storage.

The Android client stores recipes and imported cover images in the app's private storage. It stores the selected server URL and theme in Android DataStore. When a server is configured, the app sends the recipe-box state to that server over the URL you supplied. It does not upload local Android cover-image files.

## Requests to other services

Importing a URL causes the Mise server, or the disconnected Android fallback, to contact that site. The destination can see the request's IP address and ordinary HTTP metadata. Opening a saved source link contacts the source site in the user's browser.

The server tries to cache imported cover images. If a cache attempt fails, a recipe may retain the original image URL. Displaying that cover then contacts the image host from the browser or Android device, which exposes that device's IP address and ordinary HTTP metadata to the host.

If `OLLAMA_URL` is configured, the server may send selected recipe-card image crops to that endpoint to clarify ambiguous fractions. The project code does not send those crops anywhere else.

## Android text recognition

The Android app uses Google ML Kit text recognition. Google states that input images and recognized output remain on-device. The ML Kit SDK may still contact Google for fixes, model or accelerator updates, and may send app, device, performance, and usage metrics. Consult the [ML Kit terms and privacy documentation](https://developers.google.com/ml-kit/terms) before distributing the Android app, and complete any app-store data disclosures that apply to your build.

## Project telemetry

The Mise source code does not add analytics, advertising, crash reporting, or a developer-operated telemetry endpoint. This statement does not cover the software around a deployment, such as its reverse proxy, hosting provider, browser, operating system, Google ML Kit, or a fork that adds its own services.

## Deleting data

Recipes and grocery items can be removed in the clients. To delete a server installation completely, stop it and remove its configured `DATA_DIR` or Docker volume. Uninstalling the Android app removes its private local data according to Android's normal backup and uninstall behavior.
