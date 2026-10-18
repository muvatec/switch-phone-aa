# Firmware: versiones y hallazgos

Registro del firmware del dongle **Carlinkit CPC200-2Air (5.0, `U2AC_AUTOKIT`)** observado durante el desarrollo, para comparar contra versiones futuras. Muestras de `infos` sin datos personales en [`firmware/`](firmware/). Para guardar una nueva: `tools/snapshot_infos.sh` (conectado al WiFi del dongle).

## Versiones observadas en este dongle

| | `2024.03.21.1830` | `2025.12.29.1746` |
|---|---|---|
| Hardware (`hardwareVer`) | `YMYE-WB58-0104` | `YMA0-WB58-0104` |
| `cgiVer` (compilación del CGI) | Feb 19 2024 14:29:29 | Dec 26 2025 18:14:16 |
| `mfd` | `20240321` | — |
| Nombre USB (`USB Product`) | `AutoKit` | `VISION` |
| `boxName` por defecto | `AutoKit` | `VISION` |
| Interfaz web (`WebAUTOKIT`) | `2024.03.21.1830`, cliente `20240220173059-8610` | `2025.12.29.1433`, cliente `20250808174401-10796` |
| `platformType` / `customVer` | `M6` / `2` | `M6` / `2` |
| Campo `upTime` en `BoxInfo` | no | sí |
| Ajuste nuevo | — | `squareCorner`, `naviVolume` (UI) |

Módulos (`ModuleInfo`):

| Módulo | 2024.03.21.1830 | 2025.12.29.1746 |
|---|---|---|
| Boot128 | 2024.03.21.1830 | 2025.12.29.1746 |
| USBDrv | 2024.03.21.1830 | **2024.12.23.1345** |
| Common | 2024.03.21.1830 | 2025.12.29.1745 |
| Service | 2024.03.21.1830 | 2025.12.29.1744 |
| Upg | 0000.00.00.0000 | 0000.00.00.0000 |
| HUCP | 2024.03.21.1830 | 2025.12.29.1743 |
| HUAA | 2024.03.21.1830 | 2025.12.29.1744 |
| CarPlay | 2024.03.21.1830 | 2025.12.29.1745 |
| AndroidAuto | 2024.03.21.1830 | 2025.12.29.1746 |
| BCM4358 | 2024.03.21.1830 | 2025.12.29.1746 |
| WebAUTOKIT | 2024.03.21.1830 | 2025.12.29.1433 |
| Cus_U2AC_AUTOKIT | 2024.03.21.1830 | 2025.12.29.1511 |

### Qué cambió en `Settings` al pasar a 2025.12.29

Claves que **dejaron de aparecer** en `infos` (la interfaz del dongle solo muestra las que existen): `echoDelay`, `btCall`, `micGain`, `displaySize`, `autoPlay`, `mouseMode`, `Udisk`, `ScreenDPI`, `KnobMode`, `NaviAudio`, `RepeatKeyFrame`, `BtAudio`, `MicMode`, `SpsPpsMode`, `MediaPacketLen`, `TtsPacketLen`, `VrPacketLen`, `TtsVolumGain`, `VrVolumGain`, `heartBeat`, `emptyFrame`, `micType`, `connectedMode`, `returnMode`, `backRecording`, `autoUpdate`.

Clave que **apareció**: `squareCorner`.

Los ajustes que habíamos cambiado (`fastConnect`, `mediaDelay`, `resolution*`) **se conservaron** tras la actualización. El `code` de activación y el nombre USB sí cambiaron. La API (`/cgi-bin/server.cgi`, firma MD5 con la misma sal) **no cambió**.

## Imágenes de firmware conocidas (comunidad)

Según [ludwig-v/wireless-carplay-dongle-reverse-engineering](https://github.com/ludwig-v/wireless-carplay-dongle-reverse-engineering/tree/master/Firmware/U2AC), carpeta U2AC (V5, tipo AUTOKIT):

| Versión | Archivo |
|---|---|
| 2024.10.31 18:27 | `U2AC_AUTOKIT_Update.hwfs` |
| 2024.03.21 18:30 | `U2AC_AUTOKIT_Update.img` |
| 2024.01.18 10:53 | `U2AC_AUTOKIT_Update.img` |
| 2023.10.14 17:11 | `U2AC_AUTOKIT_Update.img` |
| 2023.07.12 17:54 | `U2AC_AUTOKIT_Update.img` |

La `2025.12.29.1746` de este dongle es **más nueva que cualquier imagen pública** listada. Las versiones recientes cambiaron a la extensión `.hwfs` y el empaquetado (el trabajo previo indica que el formato cambió varias veces y que el fabricante lo endurece).

Este repositorio **no incluye imágenes de firmware** (son del fabricante).

## Cómo se actualiza (vías oficiales)

1. **Desde el propio dongle:** el celular se conecta al WiFi del dongle, abre `http://192.168.50.2` y, si hay versión nueva, la interfaz la ofrece (descarga desde la nube del fabricante y sube la imagen a `upload.cgi`).
2. **App oficial de Carlinkit/AutoKit**, igual que la anterior.
3. **Memoria USB (sin nube):** copiar `U2AC_AUTOKIT_Update.img` (o `.hwfs`) a la raíz de una memoria FAT32, alimentar el dongle por una fuente externa, insertar la memoria cuando el LED rojo esté fijo y esperar a que los LEDs dejen de parpadear.

Riesgos: una imagen incorrecta o una interrupción de energía puede dejar el dongle inservible; **bajar de versión** puede dejarlo bloqueado en modo activación. Por eso Switch Phone **no** actualiza firmware.

## Cómo comparar una versión nueva

```bash
tools/snapshot_infos.sh                       # guarda docs/firmware/infos-<versión>.sample.json
diff <(jq -S .Settings docs/firmware/infos-2025.12.29.1746.sample.json) \
     <(jq -S .Settings docs/firmware/infos-<nueva>.sample.json)
```

Revisa sobre todo: claves de `Settings` que aparecen o desaparecen, cambios de `productType`/`platformType`, cambios en `ModuleInfo`, y si `/cgi-bin/server.cgi` sigue respondiendo con la misma firma (si falla con `400`, la sal o el formato cambiaron: busca `signFormData` en el JavaScript de la interfaz nueva).
