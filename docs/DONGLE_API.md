# API web del dongle

El dongle (firmware AutoKit) sirve su interfaz de configuración en `http://192.168.50.2` (servidor Boa). Está disponible para cualquier cliente conectado a su WiFi (`AutoKit_xx`). La interfaz del navegador es una app Vue que llama a un único CGI. Esta página describe lo observado en el firmware `2024.03.21` y `2025.12.29`.

> HTTP sin cifrar. Chrome en Android suele forzar HTTPS y no carga la página; por eso existe la app nativa.

## Petición

```
POST http://192.168.50.2/cgi-bin/server.cgi
Content-Type: application/x-www-form-urlencoded

cmd=<comando>&[item=<clave>&val=<valor>&]ts=<ms epoch>&sign=<md5>
```

(`/server.cgi` sin `/cgi-bin/` devuelve 404 y es lo que usa la UI detrás de un proxy interno.)

### Firma

```
sign = md5( "k1=v1&k2=v2&...&kn=vn" + SALT )
```

- Los pares son todos los campos **excepto `sign`**, ordenados por clave y unidos con `&`.
- `ts` es `Date.now()` en milisegundos.
- `SALT` es la constante que el JavaScript de la propia interfaz del dongle (`signFormData`) añade a todas las peticiones. Ver `DongleClient.kt`.

Ejemplo de cadena a firmar para fijar un ajuste: `cmd=set&item=mediaDelay&ts=1760000000000&val=500` + SALT.

Una firma incorrecta o un formato multipart mal formado responde `400 Bad Request`.

## Comandos

| `cmd` | Parámetros | Respuesta |
|---|---|---|
| `infos` | — | JSON con `BoxInfo`, `Settings`, `DevList`, `WifiChannelList`, `ModuleInfo` |
| `set` | `item`, `val` | `{"err":0}` |
| `restart` | — | `{"err":0}` y el dongle reinicia (~20 s) |
| `reset` | — | Restaura valores de fábrica. **No usar a la ligera** |
| `resetApp` | — | Restaura la app |
| `BoxMonitor` | — | `{"BoxMonitor":{"CpuRate","CpuTemp","CpuFreq","MemRate","WifiRX","WifiTX"}}` (`WifiRX/TX` en **Mbps**, `CpuTemp` en °C) |
| `upgradeState` | — | `{"err":0,"progress":0,"failReason":0}` |
| `carInfo` | `brand`, `model`, `year`, `userid` | Datos del carro |
| `syncTime` | — | Sincroniza hora |
| `logFile`, `appLogFile`, `sdkLogFile` | — | Logs |
| *(subida)* `POST /upload.cgi` | multipart, campo `file` | Logo: PNG 180×180 ≤ 100 KB con nombre `carlogo.png`; luego `restart`. También recibe imágenes de firmware (la app **no** lo usa) |

## `infos`

`BoxInfo` (campos útiles):

| Campo | Ejemplo | Nota |
|---|---|---|
| `ver` | `2025.12.29.1746` | Versión del firmware |
| `productType` | `U2AC_AUTOKIT` | Modelo |
| `hardwareVer` | `YMA0-WB58-0104` | Revisión de hardware |
| `bt`, `wifi`, `boxName` | `AutoKit_210`, `AutoKit_0F`, `VISION` | Nombres editables |
| `MDLinkType` | `AndroidAuto` | Vacío si no hay enlace |
| `MDModel` | `""` | **Vacío con Android Auto**: no dice qué celular |

`DevList`: `[{ "id": "<MAC>", "type": "AndroidAuto", "name": "<nombre BT>", "index": "1" }]`. Son los celulares **guardados**. El `name` puede venir vacío. `index` no indica quién está activo.

## `set`: ajustes conocidos

| `item` | Valores | Reinicio |
|---|---|---|
| `boxName`, `btName`, `wifiName` | `^[0-9A-Za-z_-]{1,31}$` | sí |
| `delDev` | MAC del celular a olvidar | no |
| `autoConn`, `fastConnect`, `gps`, `bgMode`, `syncMode`, `autoPlay`, `btCall`, `autoRefresh`, `autoDisplay`, `autoUpdate`, `backRecording` | `0` / `1` | no |
| `mediaDelay` | 300–2000 | no |
| `echoDelay` | 20–2000 | no |
| `startDelay` | 0–120 | no |
| `bitRate` | 0–20 (0 = automático) | no |
| `fps` | 0–60 | no |
| `ScreenDPI` | 0–480 | no |
| `resolutionWidth`, `resolutionHeight` | 800x480, 1280x720, 1920x1080 (u otros) | sí |
| `wifiChannel` | 1–13, 36, 40, 44, 48, 149–165 | sí |
| `mediaSound`, `btCall`, `syncMode`, `squareCorner`, `mouseMode`, `KnobMode`, `returnMode` | `0` / `1` (returnMode: 0 carro original, 1 dongle) | no |
| `CallQuality` | 0 normal, 1 clara, 2 HD | no |
| `NaviAudio` | 0 navegación, 1 voz, 2 multimedia | no |
| `naviVolume` | 0–100 | no |
| `displaySize` | 0 predeterminado, 1 S, 2 M, 3 L | no |
| `autoDisplay`, `transMode` | 0 normal, 1 vehículo / compatible | no |
| `carLinkType` | 2 CarPlay, 4 Android Auto, 30 automático | probable |
| `lang` | id de `LangList` | no |
| `resetLogo` | — | no |

Qué ajustes aparecen en `Settings` depende del firmware: la versión `2025.12.29` oculta varios (`ScreenDPI`, `echoDelay`, `autoUpdate`, …), aunque la interfaz sigue enviándolos si existen.

## Lo que la API **no** tiene

No hay comando para "conectar al celular X" ni para saber cuál está conectado. Ver [LIMITATIONS.md](LIMITATIONS.md).
