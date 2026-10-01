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
