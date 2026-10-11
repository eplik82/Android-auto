# Pargitud video

Android Auto rakendus, millega kaasreisija saab vaadata YouTube'i videot või
sirvida veebi auto ekraanilt, **kuid ainult siis, kui auto on pargitud**.

## Kuidas see töötab

- Telefonis valitakse video: kleebi link rakendusse „Pargitud video“ või
  jaga see YouTube'ist (Jaga → Pargitud video).
- Auto ekraanil ilmuvad nupud „Video“ ja „Brauser“ alles siis, kui auto on
  seadetes määratud aja (vaikimisi **30 s**, lubatud 30–600 s) katkematult
  paigal seisnud. Lühike peatus, näiteks foori taga, neid ei ava.
- Brauser avab seadetes määratud avalehe. Lehel saab puudutada linke ja
  kerida, nupp „Tagasi“ liigub eelmisele lehele. Teksti saab sisestada ainult
  telefonis (avalehe aadress), sest auto ekraanil klaviatuuri pole.
- Kui auto hakkab liikuma (üle ~1 km/h), suletakse video või brauser kohe
  koos heliga. Uuesti pargituna tuleb need jälle käsitsi avada.
- Kui auto kiiruseandmeid pole või luba puudub, midagi ei näidata.

## Seaded (telefonis)

| Seade | Selgitus |
| --- | --- |
| YouTube'i video | Link või video ID; saab ka YouTube'ist jagada |
| Brauseri avaleht | Nt `www.google.com` |
| Paigalseismise aeg | 30–600 sekundit enne video/brauseri lubamist |
| Päeva/öö režiim | Automaatne (auto järgi, nt esitulede sisselülitamisel), Päev või Öö. Mõjutab auto ekraani teateid, veebilehti (tume teema toega lehed) ja telefoni seadete vaadet |

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
| `ParkedScreen.kt` | Auto ekraan, nupud ja pargitud oleku reeglid |
| `ParkingMonitor.kt` | Kiiruse jälgimine → `MOVING` / `STOPPED` / `PARKED` |
| `SurfaceRenderer.kt` | Auto ekraani pind → virtuaalne ekraan, päeva/öö teema |
| `SurfacePresentation.kt` | Teade, YouTube'i mängija või veebileht (WebView) |
| `AppSettings.kt`, `SettingsInput.kt` | Seaded ja nende kontroll |
| `MainActivity.kt` | Telefoni seadete vaade |
