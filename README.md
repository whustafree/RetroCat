## Qué es

RetroCat es un fork de [Lemuroid](https://github.com/Swordfish90/Lemuroid) con el catálogo
horizontal rehecho como un modo **Big Picture** de consola: carrusel inmersivo, carátulas
grandes, navegación con D-pad y una pantalla de juego a pantalla completa.

El emulador, el escaneo de ROMs, los cores, el mapeo de mandos y la base de datos son los
de Lemuroid. Lo que cambia es la capa de presentación.

## Qué hace el modo horizontal

- **Carrusel anclado al inicio.** Un toque selecciona, un doble toque lanza el juego y un
  long press abre los detalles. La selección, el scroll, el D-pad y el swipe son la misma
  decisión: lo que está más a la izquierda en pantalla es lo que está enfocado.
- **Carátulas 3:4 ajustadas al alto disponible**, no un número fijo de columnas, así que no
  se cortan al rotar.
- **Fondo opaco.** Sin la copia desenfocada del artwork detrás: la fila se lee como una sola
  capa, no como dos peleándose.
- **Hero** con el título, el sistema, el desarrollador, el favorito y el botón de jugar del
  juego enfocado. El botón dice *Resume* si ese juego tiene partida reciente.
- **ContinuePlaying** en una banda propia arriba, que no se monta sobre las carátulas.
- **Chrome auto-ocultable** a los 5 segundos, con un aviso de los controles al entrar.
- **Panel de búsqueda y filtros** con sistema, orden, densidad y agrupación de letras, que
  incluye un control para cerrar el panel desde dentro.
- **Edge-to-edge** con recortes de notch calculados según la orientación. La versión TV queda
  fuera a propósito.

## Navegación con mando

Flechas y cruceta mueven la selección una carátula a la vez, sin tirar la fila. La tecla de
aceptar usa exactamente el mismo camino que un toque, porque los callbacks de
`combinedClickable` son también los de teclado y foco.

## Otros cambios

- Tema pixel art fijo, sin Material You. Paleta índigo, menta, ámbar y magenta, formas duras
  y tipografía monoespaciada.
- Iconos adaptativos propios con capa monochrome.
- Búsqueda FTS saneada: sanea comillas y prefijos para que una búsqueda con apóstrofe o a
  media palabra no rompa la consulta.
- Migración de base de datos 10 → 11 sin borrado destructivo.

## Compilar

Requiere JDK 21 y el SDK de Android.

```powershell
$env:JAVA_HOME="<jdk-21>"
$env:ANDROID_HOME="<android-sdk>"
.\gradlew.bat ktlintFormat
.\gradlew.bat ktlintCheck :retrograde-app-shared:testDebugUnitTest `
  :lemuroid-app:testFreeDynamicDebugUnitTest :lemuroid-app:assembleFreeDynamicRelease
```

> `ktlintFormat` y `ktlintCheck` van en **dos comandos separados**. En la misma invocación el
> check revisa los archivos ya reformateados y falla con un error que no existe.

- Variante que funciona: `freeDynamic`. `freeBundle` no compila en Windows.
- La APK sale en `lemuroid-app/build/outputs/apk/freeDynamic/release/`.
- 50 tests JVM, sin Robolectric ni Compose test.

### Cores

Los binarios de los cores son el submódulo `lemuroid-cores`, que apunta al
[Swordfish90/LemuroidCores](https://github.com/Swordfish90/LemuroidCores). No está incluido en
este repositorio:

```powershell
git submodule update --init --recursive
```

## Descargar la app

Las APKs se publican como releases, no en el árbol de código. Baja la más reciente desde la
página de releases, o el tag `apk-latest`.

## Estado

Compila, pasa los tests y la APK está firmada. **La parte visual todavía no se ha verificado
en un dispositivo real**: ni el carrusel, ni el hero, ni el D-pad con un mando de verdad. La
aritmética del layout está cubierta por tests, pero el resultado en pantalla no.

El registro de trabajo está en [RETROCAT-SESION-LOG.md](RETROCAT-SESION-LOG.md).

## Licencia

Sustituye a Lemuroid. Consulta los archivos de licencia del proyecto original.
