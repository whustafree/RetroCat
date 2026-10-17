# LOG DE SESIÓN — RetroCat (fork de Lemuroid)

Última actualización: 2026-10-01 (pre-reinicio)
Proyecto: `C:\Users\whustaf\AppData\Local\Temp\opencode\lemuroid`
APK entregada: `C:\Users\whustaf\AppData\Local\Temp\opencode\RetroCat-1.18.0.apk`

> Este log no contiene secretos. El `Turnos-SESION-LOG.md` de la misma carpeta es de OTRO
> proyecto (TurnosApp) e incluye claves: no mezclarlos.

---

## 1. DÓNDE QUEDAMOS

**ESTADO ACTUAL: log guardado. El usuario va a reiniciar el PC para activar el hypervisor.**

> **Si estás leyendo esto después de un reinicio, este es el siguiente paso exacto:**
> 1. `HypervisorPresent` debe ser `True`. Verificar:
>    `(Get-CimInstance Win32_ComputerSystem).HypervisorPresent`
> 2. Arrancar el emulador y esperar a que adb lo vea:
>    `& "$env:ANDROID_HOME\emulator\emulator.exe" -avd RetroCat -no-snapshot`
> 3. `adb wait-for-device` y luego `adb install -r RetroCat-1.18.0.apk`
> 4. Capturas: `adb exec-out screencap -p > captura.png`
> 5. Probar D-pad: `adb shell input keyevent 20` (abajo) y `19` (arriba), `21`/`22` izquierda/derecha
>
> Si el hypervisor sigue en False, el plan B es conectar el teléfono por USB: mejor resultado
> porque se ve el mando real. Ver sección 1, "Bloqueo actual".

El modo horizontal/Big Picture está implementado y compilando. Lo último que se hizo fue
corregir el carrusel: los 2 primeros juegos salían corridos y se veía arte detrás.

**Lo que falta de verdad:** nada está verificado en pantalla. Todos los cambios visuales y de
interacción están sin confirmar contra hardware real. Ese es el único bloqueo real.

### Bloqueo actual: falta hypervisor

La máquina **sí** soporta virtualización (`VirtualizationFirmwareEnabled: True`, SLAT `True`,
Windows 11 Pro). Lo que falta es el hypervisor: `HypervisorPresent: False`, sin servicios
`vmcompute` ni `vmms`.

El emulador muere sin él:
- Sin aceleración: `x86_64 emulation currently requires hardware acceleration!`
- Con `-accel off` (emulación por software): llega a imprimir el kernel pero el proceso muere
  a los ~15s con **exit code `-1073741819` (0xC0000005, access violation)**. No es lentitud,
  el qemu de 64 bits no puede con un guest de 64 bits sin aceleración.

**El usuario está ejecutando esto en PowerShell como Administrador:**
```
Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V -All
```
Va en `[Running]`. Cuando termine tiene que **reiniciar** (`Restart Needed: True`, obligatorio:
el hypervisor no carga sin reiniciar).

Ojo con el nombre: `dism /featurename:Microsoft-Hyper-V-All` **NO existe** en Windows 11
modern, da `0x800f080c`. El nombre válido es `Microsoft-Hyper-V` vía PowerShell, o el más
liviano `HypervisorPlatform`. Los paquetes de Hyper-V sí están en CBS, así que es instalable.

**Plan B, más rápido:** conectar el teléfono por USB con depuración activada y hacer
`adb install -r`. Da mejor resultado que el emulador porque se ve el D-pad con un mando real.

Tras el reinicio, verificar antes de intentar nada más:
```
HypervisorPresent: True
```

### Pendientes de código

- [ ] **Probar en el teléfono** y decir qué se ve mal. Prioridad: carrusel, hero, D-pad,
      shelf de ContinuePlaying, notch/inset en landscape.
- [ ] **D-pad sin verificar.** Añadí `.focusable()` porque `requestFocus()` fallaba en
      silencio, pero que funcione con un mando real no está comprobado.
- [ ] **"Resume" es solo una etiqueta.** Marca `catalog_resume` cuando `Game.lastPlayedAt`
      no es null. NO carga una partida guardada. Falta la lógica real de savestate.
- [ ] Ajustar constantes de apariencia si se ven mal (ver sección 5, "AJUSTES PENDIENTES").
- [ ] `heroPanelWidth()` existe y tiene tests, pero NO se usa en el layout. Está pendiente
      decidir si el hero va a ser panel lateral o franja inferior.
- [ ] `CatalogScreen.kt` conserva un `CatalogGameTile` privado que duplica
      `RetroCatGameTile.kt`. No unificado.
- [ ] `catalog_sort_favorites` como filtro: existe el ORDEN `FAVORITES` en el ViewModel y
      las queries SQL, pero no un interruptor dedicado "solo favoritos".

---

## 2. CÓMO COMPILAR

```powershell
$env:JAVA_HOME="C:\Users\whustaf\AppData\Local\Temp\opencode\jdk21\jdk-21.0.12.1+1"
$env:ANDROID_HOME="C:\Users\whustaf\AppData\Local\Temp\opencode\android-sdk"
cd C:\Users\whustaf\AppData\Local\Temp\opencode\lemuroid

# OJO: ktlintFormat y ktlintCheck en la MISMA invocación falla con un error falso.
# El check corre contra los archivos ya reformateados. Son dos comandos separados.
.\gradlew.bat ktlintFormat
.\gradlew.bat ktlintCheck :retrograde-app-shared:testDebugUnitTest `
  :lemuroid-app:testFreeDynamicDebugUnitTest :lemuroid-app:assembleFreeDynamicRelease
```

- Variante: `freeDynamic`. **`freeBundle` NO sirve en Windows** (stubs/symlinks).
- Tests: **50** en total, todos JVM puros, sin Robolectric ni Compose test.
  | Archivo | Tests |
  |---|---|
  | `catalog/CatalogLandscapeLayoutTest.kt` | 16 |
  | `library/ScreenshotUrlsTest.kt` | 6 |
  | `db/GameCatalogDaoTest.kt` | 16 |
  | `db/GameSearchDaoTest.kt` | 7 |
  | `db/MigrationsTest.kt` | 5 |
- Se intentó Robolectric y se abandonó:
  `Unable to resolve activity for Intent { ... cmp=org.robolectric.default/androidx.activity.ComponentActivity }`
- APK: copiar de `lemuroid-app\build\outputs\apk\freeDynamic\release\*.apk`
- Verificar firma (requiere `JAVA_HOME` en la MISMA sesión, si no falla):
  ```
  & "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat" verify --print-certs <apk>
  ```
- Firmante: `CN=RetroCat`, cert SHA-256
  `426e20b14de3c38be7028d9d160a6383a677716471f93e34fe60b4fdcbaf2f09`

### APK actual
- Tamaño: `12,570,659` bytes
- SHA-256: `66D8EF5F7416359B7DCF90D0EA969E64463BB16A6C22E45D5173859860BD7300`

---

## 3. POR QUÉ NO HAY VERIFICACIÓN VISUAL

- No hay dispositivo Android conectado.
- El emulador NO arranca:
  `x86_64 emulation currently requires hardware acceleration!` /
  `CPU acceleration status: Android Emulator hypervisor driver is not installed on this machine`
- La máquina no tiene virtualización anidada. Habilitar WHPX requiere elevación + reinicio.
  No se puede hacer desde aquí.

---

## 4. ÚLTIMOS CAMBIOS (carrusel)

Son los que hizo el usuario en el último turno. Ojo con las constantes de apariencia.

### Anclaje al inicio (arregla "2 juegos corridos")
La fila se centraba: `focusGameAt` y el tap usaban
`animateScrollToItem(index, scrollOffset = -(viewportEndOffset / 2))` y `focusedIndex` se
derivaba de "lo más cercano al centro del viewport". Eso obligaba a retroceder sobre el
inicio de la lista y empujaba las primeras carátulas fuera de cuadro.

Ahora los TRES puntos coinciden en anclaje al inicio:
- `focusGameAt` → `scrollOffset = 0`
- el tap delega en `focusGameAt` (antes tenía su propia copia del scroll centrado)
- `focusedIndex` = primera carátula **completamente** dentro del viewport

### "Nada detrás" (3 capas eliminadas)
1. Fondo con artwork desenfocado a `alpha(0.5f)` + `blur(28.dp)` + scrim → quitado.
   `CarouselHeroBackground` ahora es un `Box` con `background(colorScheme.surface)` opaco.
2. Carátulas no enfocadas a `alpha(0.4f)` → **subido a 0.78**. A 0.4 se transparentaba el
   fondo y parecía un fallo de render, no profundidad.
3. Bloque de color `coverWidth + 8.dp` desplazado 6.dp que hacía de sombra "estilo sprite":
   no estaba alineado a ninguna carátula, flotaba suelto → **eliminado**.

### Shelf de ContinuePlaying
Estaba en overlay `align(TopCenter)` y se dibujaba ENCIMA de la primera fila de carátulas.
Ahora reserva banda: `CONTINUE_PLAYING_BAND_HEIGHT = 140.dp` (card 108dp + título), que entra
como `top` del `contentPadding` y se resta antes de calcular el tamaño de la carátula.
Solo reserva altura si `continuePlaying.isNotEmpty()`.

### Padding
`contentPadding` horizontal simétrico 48.dp → `start = 20.dp`, `end = 24.dp`.
El simétrico gastaba media pantalla en el lado final (hueco muerto visible).

### Focus del D-pad (bug real)
`FocusRequester` estaba sobre un contenedor SIN `focusable()`, así que `requestFocus()` lanzaba
excepción y el `runCatching` la silenciaba: el mando quedaba muerto sin pista. Se añadió
`.focusable()`. Import correcto: `androidx.compose.foundation.focusable` (NO `ui.focus`).

### Dos suspicions del resumen anterior que resultaron FALSAS
- `onGamePlay = onGameClick` **no** está mal. `onGameClick` ES `gameInteractor.onGamePlay(game)`;
  el diálogo de detalles tiene su propio callback aparte.
- `remember(games)` NO captura un `focusedIndex` obsoleto: al ser propiedad local delegada por
  `by`, la lambda captura el delegado y la lectura es viva.

---

## 5. AJUSTES PENDIENTES (valores de apariencia, sin verificar)

En `CarouselCover`:
```kotlin
scale: if (focused) 1.08f else 0.9f     // antes 1.12 / 0.84
alpha: if (focused) 1f    else 0.78f   // antes 1f / 0.4
```
En `GridDensity.kt`:
```kotlin
CAROUSEL_START_PADDING = 20.dp          // antes contentPadding 48.dp simétrico
CONTINUE_PLAYING_BAND_HEIGHT = 140.dp
```
Si las carátulas no enfocadas se ven apagadas, subir alpha. Si el shelf roba mucho alto,
bajar la banda (pero ≥108dp o el card se corta; hay un test que lo fija).

---

## 6. ARQUITECTURA DEL CARRUSEL

`CatalogScreen` (horizontal) → `CatalogCarousel`:

```
BoxWithConstraints  (onPreviewKeyEvent + focusable + focusRequester)
└── Column
    ├── Box (weight 1f)
    │   ├── CarouselHeroBackground      surface opaco
    │   ├── ContinuePlayingRow          align(TopCenter), dentro de la banda reservada
    │   └── LazyRow                     start-anchored, verticalAlignment = CenterVertically
    │       ├── items → CarouselCover   aspectRatio 3:4, combinedClickable
    │       └── item("append_trigger")  centinela de paging
    └── CarouselHero                    64.dp, título + sistema/dev + favorito + info + play
```

Gestos en `CarouselCover`: 1 toque = seleccionar, doble toque = jugar, long press = detalles.
Los `onClick` de `combinedClickable` son también los de teclado/D-pad, así que no hay dos
rutas de activación.

Chrome: `MainActivity` maneja `showChrome`/`chromeVisible`. Auto-oculta a los 5s
(`LANDSCAPE_CHROME_TIMEOUT_MS`). El chrome solo se oculta en horizontal + ruta `CATALOG`.

---

## 7. OTRO TRABAJO YA TERMINADO (no perder)

- **Tema pixel art** fijo, sin Material You (`USE_DYNAMIC_COLOR = false`). Paleta
  índigo/menta/ámbar/magenta, formas duras, tipografía monoespaciada.
- **Iconos adaptativos** RetroCat con monochrome.
- **Edge-to-edge** en `BaseGameActivity` / `MobileGameScreen`, con recortes de notch según
  orientación. TV excluida a propósito.
- **FTS** sanitizado (`FtsQuery.kt`, `toFtsPhrase(prefixLastToken = true)`); deep links
  protegidos; índices Room redundantes eliminados con `VERSION_10_11` (esquemas 10/11 nuevos).
- **Catálogo responsive**: columnas derivadas del ancho real (`catalogColumnCount`), nunca
  un número fijo. Carátulas 3:4 calculadas desde el alto (`carouselCoverSize`).
- **Validación de URLs de captura** (`ScreenshotValidator`, `ScreenshotUrlsTest`).
- Filtros por sistema, orden (nombre/reciente/favoritos), densidad y agrupación de letras.

---

## 8. ARCHIVOS QUE MÁS SE TOCAN

```
lemuroid-app/src/main/java/com/swordfish/lemuroid/app/mobile/feature/catalog/
    CatalogScreen.kt        <- el grueso: carrusel, hero, chrome, gestos, foco
    GridDensity.kt          <- constantes y aritmética de layout (testeable)
    CatalogViewModel.kt     <- filtros, paging, continuePlaying
    CatalogPreferences.kt
    GameDetailDialog.kt / GameDetailContent.kt
lemuroid-app/src/main/java/.../feature/main/MainActivity.kt
    <- showChrome, chromeVisible, auto-hide, botones abrir/cerrar, callbacks de juego
lemuroid-app/src/test/java/.../feature/catalog/CatalogLandscapeLayoutTest.kt
retrograde-app-shared/src/main/java/.../lib/library/db/dao/FtsQuery.kt
retrograde-app-shared/src/main/java/.../lib/library/db/dao/Migrations.kt
```

Estado de git: 88 archivos modificados/nuevos, sin commit. Todo el trabajo está **sin
commitear** en el working tree. `lemuroid-cores` aparece como submódulo modificado (` m`).

---

## 9. CÓMO REANUDAR

### Repos

| | |
|---|---|
| GitHub | https://github.com/whustafree/RetroCat (público, rama `main`) |
| Release APK | https://github.com/whustafree/RetroCat/releases/tag/v1.18.0 |
| Alias fijo | https://github.com/whustafree/RetroCat/releases/tag/apk-latest |
| Carpeta publicada | `C:\Users\whustaf\AppData\Local\Temp\opencode\RetroCat` (copia limpia con git) |
| Carpeta de trabajo | `C:\Users\whustaf\AppData\Local\Temp\opencode\lemuroid` (sin commitear) |

El remoto `origin` de `lemuroid` es `Swordfish90/Lemuroid`, donde la cuenta **no tiene push**
(`push: false`). Por eso se creó el repo nuevo. Para seguir trabajando de ahora en más, usar
`opencode\RetroCat`.

`lemuroid-cores` (632 MB de binarios) se dejó como puntero de submódulo, no se copió.
Para compilar desde el repo nuevo:
```
git submodule update --init --recursive
```

### Pasos

1. `cd C:\Users\whustaf\AppData\Local\Temp\opencode\RetroCat`
2. Montar `JAVA_HOME` (ver sección 2) y compilar con los dos comandos ktlint separados.
3. Copiar la APK de `lemuroid-app\build\outputs\apk\freeDynamic\release\`
4. `adb install -r` en el teléfono, reportar qué se ve mal.
5. Al terminar bien: commit + `git push`. NO hacer `reset` ni descartar cambios a la ligera.

> **OJO:** las secciones 1–9 son del trabajo anterior (UI/carrusel) y su "estado de git" quedó
> obsoleto: aquel trabajo YA está commiteado y pusheado. El estado real está en la sección 10.

---

## 10. LA TIENDA (STORE) — estado actual

Idea: un catálogo remoto (publicado en Drive, GitHub o cualquier host estático) que RetroCat
lee para listar juegos, enseñar una vista previa y descargar el elegido a la biblioteca.

### Estado de git (real)

Rama `main` de https://github.com/whustafree/RetroCat, todo pusheado:

| Commit | Qué |
|---|---|
| `bf9948d` | UI: unificar la rejilla con `RetroCatGameTile` y borrar duplicados muertos |
| `8f14da3` | Store: modelo del manifiesto, parser y repositorio HTTP |
| `69b09ba` | Store: descarga con progreso, verificación sha256 y carpeta destino |
| `98a1861` | Store: pantalla con baldas, vista previa y acceso desde la barra |
| `844c4bd` | Store: cortar la descarga usa `Job.ensureActive` |
| `1fc81b8` | Store: traducción al español en un fichero UTF-8 aparte |

Carpeta de trabajo: `C:\Users\whustaf\AppData\Local\Temp\opencode\RetroCat-review`
(la carpeta `opencode\RetroCat` de las secciones anteriores quedó dañada; usar `-review`).

### Paquete nuevo

`lemuroid-app/src/main/java/com/swordfish/lemuroid/app/mobile/feature/store/`

- `StoreManifest.kt` — modelo `@Serializable` + validación + agrupado por sistema.
- `StoreRepository.kt` — descarga el JSON con OkHttp y lo parsea.
- `StorePreferences.kt` — guarda la URL del manifiesto (SharedPreferences).
- `StoreDownloader.kt` — baja el ROM a un `.part`, verifica sha256 y solo entonces renombra.
- `StoreLibrary.kt` | `StoreViewModel.kt` | `StoreFormat.kt` | `StoreScreen.kt`
- Tests JVM: `StoreManifestTest.kt`, `StoreFormatTest.kt`.
- Español: `res/values-es-rES/store_strings.xml` (fichero UTF-8 aparte, ver nota de encoding).

### Formato del manifiesto

JSON plano. Solo son obligatorios `id`, `title`, `system` y `url`. `system` es el `dbname` de
`SystemID` (`snes`, `psx`, `gba`, `nes`, `md`, …). Poner SIEMPRE `fileName` con la extensión
correcta, porque el escáner decide el sistema por la extensión.

```json
{
  "version": 1,
  "name": "Mi catálogo",
  "games": [
    {
      "id": "snes-supermarioworld",
      "title": "Super Mario World",
      "system": "snes",
      "url": "https://…/Super%20Mario%20World.sfc",
      "fileName": "Super Mario World.sfc",
      "sizeBytes": 524288,
      "sha256": "…64 hex…",
      "coverUrl": "https://…/cover.png",
      "screenshots": ["https://…/shot1.png"],
      "description": "Texto libre."
    }
  ]
}
```

Claves desconocidas se ignoran, así el formato puede crecer sin romper clientes viejos.
`validate()` marca avisos (id duplicado, sistema desconocido, sha256 mal formado…) y la pantalla
los enseña. En Drive usar el enlace directo `https://drive.google.com/uc?export=download&id=…`;
ese enlace **no** da un nombre de fichero usable, por eso `fileName` es imprescindible.

### Cómo funciona la descarga

`StoreLibrary` resuelve la carpeta destino igual que el proveedor local: la carpeta externa
heredada (`pref_key_legacy_external_folder`) si el usuario configuró una, y si no la carpeta
interna de roms. Bajo ella, `retrocat-store/<dbname-sistema>/<fileName>`. Al terminar:
`LibraryIndexScheduler.scheduleLibrarySync(appContext)`, así el juego aparece solo.

`StoreViewModel` vive en la Activity (no en la pantalla) para que una descarga siga viva al
cambiar de pestaña. La descarga baja a `.part`, comprueba sha256 si el manifiesto lo trae, y solo
entonces renombra: una descarga cortada nunca deja un fichero a medias que el escáner indexe.

Entrada: pestaña **Tienda** en la barra inferior (ruta `MainRoute.STORE`). La primera vez pide la
URL del manifiesto; luego se puede cambiar con "Cambiar la URL del manifiesto".

### Dos trampas encontradas (no repetir)

1. **`values-es-rES/strings.xml` está en Windows-1252**, no UTF-8. Editarlo con herramienta UTF-8
   lo corrompe. Solución: las cadenas nuevas van en `values-es-rES/store_strings.xml` (UTF-8).
   Android fusiona todos los XML de una carpeta `values-*`.
2. **No hay ningún JDK en la máquina**, así que aquí no se puede compilar ni pasar ktlint ni
   tests. Todo el código está revisado a mano contra la API real (se comprobó `SystemID` es enum,
   `GameSystem.findById`, `SharedPreferencesHelper.getLegacySharedPreferences`,
   `DirectoriesManager.getInternalRomsDirectory`, `LibraryIndexScheduler.scheduleLibrarySync`,
   `MutableStateFlow.update` estable en coroutines 1.6.4, etc.). **Falta compilar en la máquina
   del usuario.**

### Pendiente (por orden sugerido)

1. **Compilar y probar** en la máquina del usuario; reportar errores de compilación o qué se ve mal.
2. Autorar un manifiesto real y publicarlo (Drive/GitHub) para probar el flujo entero.
3. Descarga con `WorkManager` (sobrevive a que muera el proceso).
4. Acción "jugar" tras instalar: buscar el `Game` por uri `file://` y llamar a `onGamePlay`.
5. Los 6 ítems de UI acordados al principio (Big Picture en Inicio, filtro solo favoritos, etc.).

---

## 16-10-2026 — Tienda desde una carpeta de Google Drive (partes 1-3)

Objetivo nuevo: que la tienda funcione **solo pegando el enlace** de una carpeta de Drive, sin
escribir el manifiesto a mano, y que un botón de reescanear recoja los juegos añadidos después.

### Cómo funciona (sin login ni API key)

`https://drive.google.com/embeddedfolderview?id=<CARPETA>#list` devuelve el listado **entero** de
una carpeta pública, incluso con miles de ficheros, como HTML plano. Con esa respuesta se genera el
catálogo en memoria:

- `DriveFolderListing` — parser del HTML (id, nombre, es carpeta) y de las entidades HTML.
- `DriveCatalogLoader` — descarga la carpeta y **recorre subcarpetas** (profundidad máx 3, sin
  revisitar ninguna) y junta los ficheros con su ruta.
- `DriveManifestBuilder` — decide qué es un juego: la extensión tiene que estar en la tabla de la
  propia app (`GameSystem.getSupportedExtensions()`), así que la tienda nunca ofrece algo que el
  emulador no abra. El sistema sale de la extensión y, si es ambigua (`iso`), del nombre de la
  carpeta. Las carátulas se emparejan por nombre normalizado (sin extensión, sin el `- ` inicial).
- URLs: `uc?export=download&id=<FICHERO>` para el ROM y `thumbnail?id=<FICHERO>&sz=w512` para la
  portada.

### Descarga

En ficheros grandes Google responde con una página HTML de confirmación en vez del fichero.
`DriveDownloadPage` (lógica pura, con tests) detecta esa página por `Content-Type` y saca de ella
la URL real, que el descargador sigue.

### Verificado contra datos reales

- Tu carpeta: **1036 ROMs y 1089 carátulas**, con **1036/1036 ROMs emparejadas** con su portada.
- `thumbnail` devuelve 200 `image/png` (374 KB) y `uc?export=download` devuelve 200
  `application/octet-stream` (8 MB, cabecera GBA válida). Sin página de aviso para ROMs de GBA.
- 31 tests de la tienda en verde (`testFreeDynamicDebugUnitTest`).

### Estado en git

`cef6f54`, `2821abd`, `eca0e85` en `main` (subidos). APK: pendiente de publicar la versión
`1.19.1` tras subir el tag.

### Pendiente

- Los 6 ítems de UI acordados al principio (siguen aplazados).
- Google Sign-In (OAuth, `drive.readonly`) si algún día hace falta acceso a carpetas **privadas**;
  recordar que usuario+contraseña no es posible.
- Descarga con `WorkManager`.

---

## 16-10-2026 (2) — Vista en lista para la tienda

La tienda se veía como una estantería horizontal por sistema. Con 1036 juegos eso obliga a deslizar
muchísimo y no deja ver "cuántos hay". Se añade un selector de vista:

- **Lista (por defecto)**: `LazyColumn` con una fila por juego (carátula pequeña 56x74, título y
  estado de la descarga) y una cabecera por sistema con su nombre y el **número de juegos**
  (`pluralStringResource` con `R.plurals.store_system_games`).
- **Estanterías**: el carrusel horizontal de antes.

El botón está en la cabecera de la tienda (icono de rejilla ⇄ lista). La elección se guarda en
`StorePreferences.viewMode` (clave `store_view_mode`), así que sobrevive a cerrar la app.

Ficheros tocados: `StorePreferences.kt` (enum `StoreViewMode` + propiedad), `StoreViewModel.kt`
(`viewMode` en el estado y `toggleViewMode()`), `StoreScreen.kt` (`StoreGameList`,
`StoreSystemHeader`, `StoreGameRow`), y las cadenas nuevas en `values/strings.xml` y
`values-es-rES/store_strings.xml`.

Build + tests: `BUILD SUCCESSFUL` y 47 tests, 0 fallos (31 de la tienda).

---

## 17-10-2026 — Tienda: buscar, filtrar, ordenar, índice A-Z y caché del catálogo

La tienda tenía 1036 juegos y ninguna herramienta para navegarlos. Esta etapa le da las que ya
tenía la biblioteca y dos que no tenía:

- **Buscador**: coincide con **todas** las palabras de la consulta, ignorando mayúsculas y
  acentos ("zelda minish" encuentra *The Minish Cap*).
- **Filtros por estado**: todos / nuevos / sin instalar / instalados.
- **Orden**: nombre, más recientes, tamaño.
- **Cabeceras de letra y tira A-Z** a la derecha de la lista: se salta a una letra de un toque.
- **Insignia NUEVO**: los juegos que la última consulta trajo y la anterior no. La primera consulta
  de todas no marca nada, para que la insignia signifique algo desde la segunda.
- **Caché del catálogo en disco** (`files/store-catalog/<sha256(origen)>.json`): la tienda abre con
  lo último que se vio y sigue funcionando sin conexión. Si la actualización falla habiendo catálogo
  en pantalla, sale un aviso arriba en vez de dejar la lista vacía.

Ficheros nuevos: `StoreBrowsing.kt` (lógica pura: filtros, órdenes, filas y letras) y
`StoreCatalogCache.kt`. `StoreBrowsingTest.kt` la cubre con 16 casos de JUnit a pelo (JVM, sin
Android). Commit `f0762e5`.

Build + tests: `BUILD SUCCESSFUL` y **63 tests, 0 fallos**.

---

## 17-10-2026 (2) — Tienda: descargas en segundo plano y descarga por lotes

Antes la descarga vivía dentro de la pantalla: salir de la pestaña Tienda o que Android matara el
proceso la cortaba. Ahora cada ROM es un **`StoreDownloadWork` de WorkManager**:

- **Sobrevive a todo**: WorkManager conserva el trabajo, lo reintenta con espera exponencial (hasta
  3 intentos) y reanuda lo que quede pendiente cuando vuelve la conexión (restricción
  `NetworkType.CONNECTED`).
- **Notificación de progreso** en primer plano, una por juego (id propio por juego, así dos
  descargas simultáneas no se pisan), con barra determinada si Drive anuncia el tamaño.
- **Reintento solo si tiene sentido**: un `IOException` o un fallo de Drive se reintenta; un
  **checksum que no cuadra no**, porque volvería a fallar igual.
- **Cancelar** desde el detalle del juego: borra el `.part`, así que no queda basura.
- **Descarga por lotes**: mantén pulsado un juego para empezar una selección, toca los que quieras
  (o "Seleccionar todo"), y "Descargar" encola todos los que falten. La barra de selección
  sustituye al buscador mientras se eligen. La lista es obligatoria en modo selección: en una
  estantería de carátulas no cabe una casilla.
- La pantalla recibe el estado de WorkManager (`getWorkInfosByTagLiveData`, convertido a `Flow`) y
  lo pinta: *En cola*, *Descargando… 42 % · 1.5 MB / 3.0 MB*, instalado o error con botón
  **Reintentar**. Un juego puede tener varios trabajos registrados (un intento fallido y otro
  nuevo), así que gana el estado más avanzado, no el último de la lista.

Ficheros nuevos: `StoreDownloadWork.kt` y `StoreDownloadScheduler.kt`. `StoreViewModel` ya no
descarga: encola y refleja. La notificación se apoya en `NotificationsManager` (canal ya existente).

Build + tests: `BUILD SUCCESSFUL` y **63 tests, 0 fallos**.

### Pendiente

- Los 6 ítems de UI acordados al principio (siguen aplazados).
- Google Sign-In (OAuth, `drive.readonly`) si algún día hace falta acceso a carpetas **privadas**.
- Firmar/limpiar la caché de carátulas, gestión de almacenamiento, auto-actualización, icono propio,
  español completo, CI con tests y ktlint, changelog automático.

---

## 17-10-2026 (3) — Tienda: borrar juegos y saber cuánto ocupan

Descargar mil juegos sin poder borrar ninguno no es una biblioteca, es una trampa. La ficha de un
juego instalado ahora tiene **"Eliminar de la biblioteca"**, con confirmación antes de borrar nada:

- `StoreLibrary.delete(game)` borra el ROM y, si quedara, el `.part` de una descarga interrumpida.
  El juego **sigue en el catálogo**, así que se vuelve a descargar cuando se quiera.
- Tras borrar se actualiza la lista de instalados y se lanza `LibraryIndexScheduler.scheduleLibrarySync`,
  así desaparece de la biblioteca en vez de quedarse como una entrada rota.
- Bajo el nombre del catálogo aparece **"1036 juegos · 2.1 GB instalados"**: el tamaño se calcula
  recorriendo solo la carpeta de la tienda (`retrocat-store`), que es lo único que puede afirmarse
  con honestidad; se recalcula al abrir y cuando termina o se borra una descarga, nunca en el hilo
  principal.

Build + tests: `BUILD SUCCESSFUL` y **63 tests, 0 fallos**.

### Pendiente

- Los 6 ítems de UI acordados al principio (siguen aplazados).
- Google Sign-In (OAuth, `drive.readonly`) si algún día hace falta acceso a carpetas **privadas**.
- Caché de carátulas, icono propio, español completo, CI con tests y ktlint, changelog automático.

---

## 17-10-2026 (4) — Auto-actualización desde GitHub

Cada versión nueva obligaba a buscar el APK a mano en GitHub. Ahora los ajustes tienen **"Buscar
actualizaciones"**, con la versión instalada debajo:

- `UpdateRepository` lee la lista de releases de GitHub (`GET /repos/whustafree/RetroCat/releases`) y
  se queda con la etiqueta más nueva que **sea una versión y traiga un APK**. Se lee la lista y no
  `releases/latest` a propósito: el flujo también mantiene la release móvil `apk-latest`, cuyo tag no
  es una versión, así que se ignora sola.
- `UpdateVersions` compara `v1.19.4` con `1.19.3-retrocat` partiendo el texto en números y rellenando
  con ceros lo que falte. Un tag que no es una versión nunca es "más nuevo": mejor decir que estás al
  día que ofrecer una instalación sin sentido. Cubierto por 13 casos de test.
- Al abrir los ajustes se comprueba **solo**, pero en silencio: la línea informa ("Disponible la
  versión 1.19.4") sin abrir nada. El diálogo —con **Descargar e instalar**— sale solo si el usuario
  toca la entrada.
- La descarga va a `cacheDir/update/` (limpiando APKs viejos) y se le entrega al **instalador del
  sistema** por `FileProvider`, que es lo único que puede reemplazar la app. El sistema pedirá
  permiso para instalar apps desconocidas, como con cualquier APK descargado.
- Un fallo al comprobar y un fallo al descargar se distinguen ("No se pudo comprobar la
  actualización" / "No se pudo descargar la actualización") en vez de decir que todo está al día.

Ficheros nuevos: `update/UpdateVersions.kt`, `update/UpdateRepository.kt`, `update/UpdateInstaller.kt`,
`res/xml/file_paths.xml` y `values-es-rES/update_strings.xml`. Se añade el permiso
`REQUEST_INSTALL_PACKAGES` y el `FileProvider`. `SettingsViewModel` gana el estado de actualización.

Build + tests: `BUILD SUCCESSFUL` y **76 tests, 0 fallos**.

### Pendiente

- Los 6 ítems de UI acordados al principio (siguen aplazados).
- Google Sign-In (OAuth, `drive.readonly`) si algún día hace falta acceso a carpetas **privadas**.
- Caché de carátulas, icono propio, español completo, CI con tests y ktlint, changelog automático.




