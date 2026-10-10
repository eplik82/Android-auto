# Pargitud video

Android Auto rakendus, millega kaasreisija saab vaadata YouTube'i videot auto
ekraanilt, **kuid ainult siis, kui auto on pargitud**.

## Kuidas see töötab

- Telefonis valitakse video: kleebi link rakendusse „Pargitud video“ või
  jaga see YouTube'ist (Jaga → Pargitud video).
- Auto ekraanil saab nupu „Esita“ vajutada alles siis, kui auto on
  vähemalt **30 sekundit** katkematult paigal seisnud. Lühike peatus, näiteks
  foori taga, ei avata videot.
- Kui auto hakkab liikuma (üle ~1 km/h), suletakse mängija kohe koos heliga.
  Uuesti pargituna tuleb video jälle käsitsi käivitada.
- Kui auto kiiruseandmeid pole või luba puudub, videot ei näidata.

Android Auto ei anna rakendustele käigu- ega seisupiduri infot, seega
otsustatakse pargitud olek ainult kiiruse järgi. Kasuta rakendust ainult
päriselt pargitud autos.

## Paigaldamine

Google Play sellist rakendust ei luba (video on Android Autos keelatud ja
rakendus kasutab kuvamiseks navigatsioonirakenduse pinda), seega paigaldatakse
see isiklikuks kasutuseks käsitsi:

1. Laadi APK alla GitHub Actionsi viimase „Build APK“ käivituse alt
   (artefakt `pargitud-video-debug-apk`) või ehita ise:
   `./gradlew assembleDebug`.
2. Paigalda APK telefoni.
3. Ava telefonis Android Auto seaded, puuduta 10 korda versiooninumbrit ja
   lülita arendaja seadetes sisse **Unknown sources**.
4. Ühenda telefon autoga. „Pargitud video“ ilmub rakenduste loendisse.
   Esimesel käivitamisel küsitakse telefonis luba lugeda auto kiirust.

## Struktuur

| Fail | Ülesanne |
| --- | --- |
| `ParkedVideoService.kt` | Android Auto teenus ja sessioon |
| `VideoScreen.kt` | Auto ekraan, nupud ja pargitud oleku reeglid |
| `ParkingMonitor.kt` | Kiiruse jälgimine → `MOVING` / `STOPPED` / `PARKED` |
| `VideoSurfaceRenderer.kt` | Auto ekraani pind → virtuaalne ekraan |
| `VideoPresentation.kt` | Teade või YouTube'i mängija (WebView) |
| `MainActivity.kt` | Telefoni vaade video valimiseks |
