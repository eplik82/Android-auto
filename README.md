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

## Kiiruse allikas

Liikumist ja seismist hinnatakse kiiruse järgi. Allika saab seadetes valida:

| Allikas | Kuidas töötab | „Seisab“ |
| --- | --- | --- |
| **Auto** (vaikimisi) | Auto enda kiirusandur Android Auto kaudu (`CarInfo` speed, luba `CAR_SPEED`) | kuni ~1 km/h |
| **Telefoni GPS** | Telefoni GPS-i kiirus (`LocationManager`, täpse asukoha luba) | kuni ~4 km/h, sest GPS kõigub |

Auto ekraanil on rida „Kiirus (auto/GPS): … km/h“. Kui seal on „andmed
puuduvad“, siis valitud allikas kiirust ei anna: auto ei edasta kiirust
või GPS-signaali pole (tunnel, parkimismaja). Siis jääb video lukku.
Kui GPS-ilt pole 5 sekundit uut lugemit tulnud, loetakse kiirus teadmata.

## Auto info (diagnostika)

Auto ekraani nupp **Info** avab loendi kõigest, mida auto Android Auto kaudu
edastab: mark/mudel/aasta, kütuse liik ja laadimispistik, tegelik ja näidiku
kiirus, kütuse ja aku tase, sõiduulatus, läbisõit, teemaksukaart, kompass,
kiirendusandur, güroskoop, auto GPS, öörežiim ja Car API tase.

Iga väärtuse asemel võib olla „pole saadaval“ (auto ei saada), „auto ei
toeta“, „luba puudub“ või „ootan…“ (andmeid pole veel tulnud). Käiku,
seisupidurit, uksi jms Android Auto rakendustele ei anna. Sõidu ajal võib
Android Auto loendit lühendada.

## Seaded (telefonis)

| Seade | Selgitus |
| --- | --- |
| YouTube'i video | Link või video ID; saab ka YouTube'ist jagada |
| Brauseri avaleht | Nt `www.google.com` |
| Kiiruse allikas | Auto või telefoni GPS (vt eespool) |
| Paigalseismise aeg | 30–600 sekundit enne video/brauseri lubamist |
| Päeva/öö režiim | Automaatne (auto järgi, nt esitulede sisselülitamisel), Päev või Öö. Mõjutab auto ekraani teateid, veebilehti (tume teema toega lehed) ja telefoni seadete vaadet |

Android Auto ei anna rakendustele käigu- ega seisupiduri infot, seega
otsustatakse pargitud olek ainult kiiruse järgi. Kasuta rakendust ainult
päriselt pargitud autos.

## Testimine

Telefoni seadete all on nupud **„Telefon liigub“**, **„Telefon seisab“** ja
**„Lõpeta test“**. Need rakenduvad kohe ja auto ekraanil on testi ajal näha
märge `[Testrežiim …]`.

- „Telefon liigub“ kehtib alati: video või brauser sulgub kohe.
- „Telefon seisab“ kehtib ainult siis, kui kiiruse luba on antud, aga auto
  päris kiirust ei anna (nt arvutis Android Auto emulaatoriga DHU). Päris
  autos kehtib alati tegelik kiirus, seega testnupuga ei saa sõidu ajal
  videot avada. Ka testis tuleb oodata määratud paigalseismise aeg.

## Paigaldamine

Google Play sellist rakendust ei luba (video on Android Autos keelatud ja
rakendus kasutab kuvamiseks navigatsioonirakenduse pinda), seega paigaldatakse
see isiklikuks kasutuseks käsitsi:

1. Laadi APK alla GitHubi **Releases** lehelt (`pargitud-video-vX.Y.Z.apk`).
   Arendusversiooni leiad viimase „Build APK“ käivituse alt (artefakt
   `pargitud-video-debug-apk`).
2. Paigalda APK telefoni.
3. Ava telefonis Android Auto seaded, puuduta 10 korda versiooninumbrit ja
   lülita arendaja seadetes sisse **Unknown sources**.
4. Ühenda telefon autoga. „Pargitud video“ ilmub rakenduste loendisse.
   Esimesel käivitamisel küsitakse telefonis luba lugeda auto kiirust.

## Uue versiooni väljaandmine

GitHubis: **Actions → Release APK → Run workflow**, sisesta versioon (nt
`1.2.3`). Töövoog loob sildi `v1.2.3` ise. Teine võimalus on lükata silt ise üles:

```sh
git tag v1.2.3
git push origin v1.2.3
```

Töövoog „Release APK“ ehitab release-APK ja lisab selle GitHubi releasele.

### Püsiv allkirjastamisvõti (soovitatav)

Ilma võtmeta allkirjastatakse APK debug-võtmega, mis on igal ehitusel erinev.
Siis ei saa uut versiooni vana peale paigaldada, vaid vana tuleb enne eemaldada.
Püsiva võtme seadistamiseks:

```sh
keytool -genkeypair -v -keystore release.jks -alias pargitud-video \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # kopeeri väljund
```

Lisa GitHubis **Settings → Secrets and variables → Actions** alla:
`RELEASE_KEYSTORE_BASE64` (base64 väljund), `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS` (`pargitud-video`) ja `RELEASE_KEY_PASSWORD`.
Hoia `release.jks` ja paroolid alles, aga ära lisa neid repositooriumi.

## Struktuur

| Fail | Ülesanne |
| --- | --- |
| `ParkedVideoService.kt` | Android Auto teenus ja sessioon |
| `ParkedScreen.kt` | Auto ekraan, nupud ja pargitud oleku reeglid |
| `ParkingMonitor.kt` | Kiiruse jälgimine → `MOVING` / `STOPPED` / `PARKED` |
| `CarInfoScreen.kt` | Diagnostikavaade „Auto info“ |
| `SpeedSources.kt` | Kiiruse allikad: auto andur ja telefoni GPS |
| `SurfaceRenderer.kt` | Auto ekraani pind → virtuaalne ekraan, päeva/öö teema |
| `SurfacePresentation.kt` | Teade, YouTube'i mängija või veebileht (WebView) |
| `AppSettings.kt`, `SettingsInput.kt` | Seaded ja nende kontroll |
| `MainActivity.kt` | Telefoni seadete vaade |
