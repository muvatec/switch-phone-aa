# Análisis de `server.cgi` (la API web del dongle)

Estado: **análisis estático parcial. La primera capa (cargador) está entendida; el contenido sigue cifrado por hardware y no se pudo leer.** No se ejecutó nada. Este repositorio no contiene el binario (es del fabricante); sí contiene el script que reproduce el análisis: [`tools/re/analyze_packed_elf.py`](../tools/re/analyze_packed_elf.py).

- Muestra: `etc/boa/cgi-bin/server.cgi` del módulo `WebAUTOKIT_2024.06.14.1807` de la compilación pública **2024.10.31** de [la comunidad](https://github.com/ludwig-v/wireless-carplay-dongle-reverse-engineering/tree/master/Firmware/U2AC/_AUTOKIT/2024.10.31.1827). 42 304 bytes, SHA-256 `a8b37ff2…ddb6b7`.
- El dongle de este proyecto trae la `2025.12.29.1746`; su `server.cgi` es otra compilación (la API web que usamos es la misma).
- Propósito: **interoperabilidad con un dispositivo propio** (buscar si existe un comando para cambiar de celular sin reiniciar). No se busca copiar ni redistribuir el software del fabricante.

## 1. Qué tipo de archivo es

| Dato | Valor |
|---|---|
| Formato | ELF de 32 bits, ARM EABI5, ejecutable estático, `OSABI=3` (Linux) |
| Secciones | **ninguna** (`shnum = 0`): sin tabla de símbolos ni nombres |
| Segmentos | `PT_LOAD` R+X de 0xa2e7 bytes en `0x10000` (todo el archivo); `PT_LOAD` RW de 0x12c5c bytes en `0x20000` (sin datos); `GNU_STACK` |
| Punto de entrada | `0x18eac` (offset `0x8eac`), cerca del final del archivo |
| Entropía | ~7.9 bits/byte en casi todo (cifrado o comprimido); baja (5.9) solo en la zona del cargador |
| Bytes tras el segmento | 601 |

Lo normal en un ELF no empaquetado sería ver cientos de cadenas legibles (`cmd`, `infos`, `restart`…). Aquí `strings` devuelve 478 líneas, todas ruido: el programa real **no está en claro en el archivo**.

## 2. Cómo funciona el cargador (de 725 instrucciones ARM)

Es un **autoextraíble en memoria con la forma de UPX** (UPX es un compresor de ejecutables), con **LZMA** y una **capa extra de cifrado por hardware**:

1. `0x18eac` → `bl 0x19b1c`: abre **`/proc/self/exe`** (se lee a sí mismo) y calcula dónde empieza la carga útil leyendo un pequeño trailer al final del código.
2. Reserva memoria con `mmap2` (syscall `0xc0`): un búfer anónimo y un mapeo fijo del propio archivo.
3. **Descifrado**: si el tamaño es ≥ `0x2000`, el cargador abre el nodo **`/dev/hwaes`** (la cadena se arma byte a byte: `/`,`d`,`e`,`v`,`/`,`h`,`w`,`a`,`e`,`s`), y llama a `ioctl(fd, 0xC00C6206, &{origen, destino, longitud})`. El código `0xC00C6206` es `_IOWR('b', 6, 12 bytes)`: la petición **la atiende un controlador del kernel del dongle (`hwaes`)**, no código del archivo. `hw` es del fabricante (HeWei). La llave nunca está en el binario.
4. **Descompresión LZMA** (reconocible por el cálculo del tamaño de las probabilidades `0x600 << (lc+lp)` + `0xe80`, y por los límites `0x1000000` del rango).
5. `cacheflush` (syscall privado ARM `0xf0002`) + `mprotect` (`0x7d`, permisos R+X) sobre la memoria descomprimida y **salto** al programa real.
6. Si algo falla escribe un mensaje de 14 bytes en `stderr` y sale con código 127.

Cadenas legibles en el cargador: `/proc/self/exe`, `/dev/hwaes` (armada con `mov`), y dos rutas poco comunes, `/etc/bluetooth/temp/addressme` y `/etc/bluetooth/temp/addressit` (probable archivo temporal/zona de trabajo del cargador o de la carga útil; no confirmado).

## 3. Por qué se detiene el análisis

Sin el controlador `hwaes` (o la llave que usa) **no se puede descifrar la carga útil**, y sin eso no hay descompilación posible. Las opciones, de menor a mayor esfuerzo:

| Opción | Qué hace falta | Estado |
|---|---|---|
| **A.** Leer el controlador `hwaes` | El módulo de kernel está en `script/ko.tar.gz` (108 751 bytes) del módulo `USBDrv` de la misma compilación pública. Ver si la llave está dentro (constante) o la toma del hardware (OTP/CAAM del i.MX6, no recuperable por software) | **Pendiente**: requiere descargar y leer ese archivo |
| **B.** Emular el cargador con una llave conocida | La llave de la opción A | Bloqueado por A |
| **C.** Volcar `server.cgi` ya descifrado desde un dongle vivo | Shell en el dongle (no hay: `adbd` se mata en modo carro) o firmware modificado | Ver [SOFT_SWITCH.md](SOFT_SWITCH.md) |
| **D.** Dejar de buscar comandos ocultos | Observar el comportamiento externo (probar comandos desconocidos contra la API) | No se hizo: un comando desconocido puede reiniciar o romper el dongle |

El resto de binarios del dongle que importan (`ARMAndroidAuto`, `hfpd`, `fakeAADevice`, `boxNetworkService`, `ARMadb-driver`) siguen el mismo esquema de protección, así que la vía A también serviría para ellos.

## 4. Lo que sí se aprendió

- La API web **no se puede leer** con herramientas estáticas simples; la documentación de [DONGLE_API.md](DONGLE_API.md) viene de la interfaz web (JavaScript), no del binario.
- El firmware protege sus binarios con cifrado ligado a un dispositivo del kernel (`/dev/hwaes`). Eso explica que las herramientas de la comunidad extraigan los módulos `.hwfs` pero no abran sus programas.
- Los scripts de texto plano sí son legibles y dan el mapa de procesos y modos USB ([SOFT_SWITCH.md](SOFT_SWITCH.md)).

## 5. Reproducir

```bash
python3 -m venv venv && venv/bin/pip install capstone
venv/bin/python tools/re/analyze_packed_elf.py server.cgi
```

Imprime cabeceras, entropía por bloque, syscalls del cargador (`mmap2`, `open`, `ioctl`, `close`, `munmap`, `cacheflush`) y las cadenas. (Nota: la cadena `/dev/hwaes` aparece como `/dvhwas` en la última línea porque el script solo cuenta los `mov` inmediatos; el código hace `add r0, r0, #1` para `d`→`e`.)
