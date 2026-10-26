# Switch Phone

[![Build and release APK](https://github.com/muvatec/switch-phone-aa/actions/workflows/release.yml/badge.svg)](https://github.com/muvatec/switch-phone-aa/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/muvatec/switch-phone-aa)](../../releases)

Cambia entre varios celulares Android en un dongle inalámbrico de Android Auto **Carlinkit CPC200-2Air (5.0 / U2AC, firmware AutoKit)**, desde el propio celular y desde la pantalla del carro.

- Ve **qué celular está en uso** (ACTIVO), **cuáles están a la vista** (DISPONIBLE) y **cuáles solo están guardados** en el dongle (GUARDADO).
- **Cambia de celular** con un toque, en la pantalla de Android Auto o en la app.
- El que quiere conectarse **abre la app y pide la conexión**; el celular activo **aprueba o rechaza** (aviso en el celular y ventana en la pantalla del carro).
- Notificación permanente en el celular activo con **Desconectar** y **Abrir app**.
- Botón en el **panel rápido** y **atajo** en el ícono: "Usar este celular".
- Menú inferior con **Celulares · Ajustes · Monitor · Herramientas**.
- **Ajustes del dongle** desde el celular según lo que reporte tu firmware: nombres (dispositivo, Bluetooth, WiFi), conexión, audio y llamadas, video, canal WiFi, idioma.
- **Monitor** en vivo (temperatura, CPU, memoria, WiFi) con aviso si el dongle se calienta.
- **Herramientas:** logo propio, datos del carro, descarga de logs y restauración.

> Proyecto independiente. No está afiliado a Carlinkit, Google ni Android Auto. Usa la API web que el propio dongle expone para su interfaz de configuración. Úsalo bajo tu responsabilidad: cambiar nombres o resolución reinicia el dongle y puede pedir volver a emparejar.

## Cómo funciona, en una frase

El dongle solo sabe qué celulares tiene **guardados**; no sabe cuál está cerca ni quién quiere el carro. Por eso cada celular con la app **anuncia por Bluetooth Low Energy** su nombre y si está proyectando Android Auto, y escucha a los demás. El cambio se hace pidiéndole al dongle que se reinicie para que el celular que pidió el control conecte primero.

Detalle en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Instalación

1. Descarga el APK de la última [release](../../releases) en **cada** celular e instálalo (permite "instalar apps desconocidas").
2. Abre la app y acepta los permisos de Bluetooth y notificaciones (necesarios para ver a los otros celulares).
3. Actívala en Android Auto (es una app de fuera de Play Store):
   - Ajustes → Dispositivos conectados → Preferencias de conexión → **Android Auto**.
   - Toca **Versión** 10 veces → acepta modo desarrollador.
   - Menú ⋮ → **Ajustes de desarrollador** → activa **Fuentes desconocidas**.
4. Conecta Android Auto al carro. En el lanzador del carro aparece **Switch Phone**.

Guía de uso completa: [docs/USAGE.md](docs/USAGE.md).

## Compilar

```bash
JAVA_HOME=<jdk 21> ANDROID_SDK_ROOT=<sdk> ./gradlew assembleDebug
```

Más en [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md), incluida la forma de **probar sin carro** con el emulador Desktop Head Unit de Google.

## Releases automáticas

Cada push a `main` ejecuta [`.github/workflows/release.yml`](.github/workflows/release.yml): compila el APK y publica una release `v0.1.<n>`. Por defecto firma con la llave de debug; para firmar con tu propia llave configura los secretos descritos en [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#firma-del-apk).

## Documentación

| Documento | Contenido |
|---|---|
| [docs/USAGE.md](docs/USAGE.md) | Uso diario, estados, solicitud de conexión, panel rápido |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Componentes, presencia BLE, protocolo de solicitud, decisiones |
| [docs/DONGLE_API.md](docs/DONGLE_API.md) | API web del dongle: firma, comandos, ajustes |
| [docs/RESEARCH.md](docs/RESEARCH.md) | Cómo funciona el dongle por dentro (USB, AOA, firmware) |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Compilar, probar con DHU, CI, firma |
| [docs/LIMITATIONS.md](docs/LIMITATIONS.md) | Qué no se puede hacer y por qué |
| [docs/SETTINGS_REFERENCE.md](docs/SETTINGS_REFERENCE.md) | Qué hace cada ajuste según el propio dongle |
| [docs/SERVER_CGI.md](docs/SERVER_CGI.md) | Análisis estático de `server.cgi`: cargador y cifrado por hardware (`/dev/hwaes`) |
| [docs/SOFT_SWITCH.md](docs/SOFT_SWITCH.md) | Cómo conecta el firmware a un celular y un plan para cambiar sin reiniciar |
| [docs/FIRMWARE.md](docs/FIRMWARE.md) | Versiones de firmware vistas, cambios entre ellas, cómo se actualiza |
| [docs/DEVTO_POST_BRIEF.md](docs/DEVTO_POST_BRIEF.md) | Brief para redactar un post de blog/dev.to sobre el proyecto |
| [docs/CONTEXT.md](docs/CONTEXT.md) | Historia del proyecto, decisiones, errores ya resueltos, pendientes |

## Estructura

```
app/            app Android (Kotlin, Compose, Car App Library)
docs/           documentación (y muestras sanitizadas de firmware en docs/firmware/)
branding/       logo (SVG maestro) y propuestas descartadas
tools/          scripts: API del dongle por terminal, emulador de carro (DHU), análisis de binarios (tools/re)
.github/        CI de releases, plantillas de issues y PR
```

## Contribuir

Issues y PR bienvenidos. Antes de empezar, lee [CONTRIBUTING.md](CONTRIBUTING.md) — tiene qué tipo de cambios se aceptan y qué no (en particular: nada que intente descifrar los binarios del firmware; ver [docs/SERVER_CGI.md](docs/SERVER_CGI.md) de por qué), y [docs/CONTEXT.md](docs/CONTEXT.md) para no repetir caminos ya cerrados.

Si tienes un modelo o firmware de dongle distinto al documentado, un issue contando qué encontraste ayuda mucho — las claves de ajustes cambian entre firmwares.

## Licencia

[MIT](LICENSE). Este proyecto es independiente, sin afiliación con Carlinkit, HeWei, Google ni Android Auto; no incluye binarios ni código del fabricante.
