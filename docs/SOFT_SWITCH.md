# Cambio "suave" de celular: análisis del firmware y plan futuro

Hoy cambiar de celular **reinicia el dongle** (~30 s). Este documento explica qué hace el firmware por dentro, por qué existe un camino mucho más corto (segundos) y qué haría falta para usarlo. **No está implementado ni probado**: es investigación para una mejora futura.

> Fuente: scripts de texto plano del firmware público **2024.10.31** de la comunidad ([ludwig-v](https://github.com/ludwig-v/wireless-carplay-dongle-reverse-engineering/tree/master/Firmware/U2AC/_AUTOKIT/2024.10.31.1827), carpeta `Common_2024.07.19.1729.hwfs_Extracted/script/`). El dongle de este proyecto tiene la `2025.12.29.1746`, **más nueva**: los detalles pueden haber cambiado. Los binarios (`ARMAndroidAuto`, `fakeAADevice`, `server.cgi`…) vienen empaquetados y **no se pueden leer**; todo lo siguiente sale de los scripts.

## 1. `phone_link_deamon.sh`, línea por línea

Uso: `phone_link_deamon.sh <CarPlay|AndroidAuto|HiCar|ICCOA> <start|stop>`

| Parte | Qué hace |
|---|---|
| `CheckPhoneLinkFile` | Sale si falta el binario de esa tecnología (`/usr/sbin/ARMAndroidAuto`, `AppleCarPlay`, `ARMHiCar`, `iccoa`) |
| `CopyPhoneLinkFile` | Copia los binarios a `/tmp/bin` y las librerías a `/tmp/lib` (el sistema de archivos del dongle es de solo lectura/flash; se ejecuta desde RAM). Para Android Auto: `ARMAndroidAuto`, `hfpd`, `libssl*` |
| `RunLinkProcess` | Android Auto: si `hfpd` no corre, lo lanza en segundo plano con `hfpd -y -E -f &`; después ejecuta **`ARMAndroidAuto` en primer plano** (bloquea hasta que termine) |
| `EndLinkProcess` | Android Auto: `killall hfpd` y `killall ARMAndroidAuto` |
| `StartLinkDeamon` | Crea `/tmp/.AndroidAuto_daemon_started` (si ya existía, sale) y entra en un **bucle**: mientras exista ese archivo, cada segundo llama a `RunLinkProcess`. Espera a que existan `wlan0` y `hci0`; sale si hay una actualización en curso (`/tmp/update_status` ≠ 4) |
| `StopLinkDeamon` | Si existe el archivo-candado: lo borra (el bucle termina) y ejecuta `EndLinkProcess` |

**Idea clave: es un supervisor.** Si `ARMAndroidAuto` muere por cualquier motivo, `RunLinkProcess` termina, el bucle duerme 1 s y lo **vuelve a lanzar**. Por tanto, matar `ARMAndroidAuto` y `hfpd` **sin borrar el candado** reinicia el enlace con el celular en ~1–2 s, sin reiniciar el dongle. Ese es el "cambio suave".

## 2. Quién es quién

| Proceso | Lado | Función |
|---|---|---|
| `ARMAndroidAuto` | Celular | Habla Android Auto con el celular por WiFi (empaquetado) |
| `hfpd` | Celular | Perfil manos libres Bluetooth (HFP); Android Auto inalámbrico lo exige |
| `hcid`, `hciconfig`, `hcitool`, `bluetoothDaemon` | Celular | Pila Bluetooth (BlueZ clásico) |
| `hostapd`, `udhcpd` | Celular | Punto de acceso WiFi y DHCP (`192.168.50.x`) |
| `fakeAADevice` | Carro | Se hace pasar por un teléfono Android ante el carro (AOA) |
| `AAaoaForword` | Carro | Reenvía el flujo AOA entre carro y celular |
| `ARMadb-driver` | Servicio | Servicio principal (arranca otros procesos) |
| `boa` + `server.cgi` | API web | Atiende `http://192.168.50.2` |
| `riddleBoxCfg` | Config | Almacén de ajustes (`WifiPassword`, `WiFiChannel`, `CustomBoxName`, `USB*`…) |

## 3. Los modos USB y el misterio de los "acuses de 12 bytes"

`script/start_mtp.sh` (modo de espera, sin carro):

```
idVendor  = 12d1        idProduct = 0001
iProduct / iManufacturer = CustomBoxName   (el nombre del dispositivo: "AutoKit" / "VISION")
functions = mtp,adb | mtp | adb   (según riddleBoxCfg USBConnectedMode: 0, 1, 2)
adbd y mtp-server se lanzan
```

Eso es exactamente el `12D1:0001` que vimos desde la Mac. Cuando un carro (o el DHU) hace el saludo **AOA**, `script/start_aoa.sh` hace `killall adbd; killall mtp-server` y cambia el dispositivo a `18d1:2d00` (modo *accessory*); allí entra en juego `fakeAADevice`.

**Resolución del misterio.** Las respuestas `0c000000 03000520 <n>` que el dongle devolvía a cualquier mensaje eran **respuestas MTP**: longitud 12, tipo de contenedor 3 (*Response*), código **`0x2005` = Operation Not Supported**, y el último campo es el *transaction id*, que `mtp-server` tomaba de los bytes que le mandábamos. No era un protocolo propio de Carlinkit: era el servidor MTP rechazando cada petición. (Ver [RESEARCH.md](RESEARCH.md).)

Consecuencias:
- **`adb` por USB**: `adbd` se lanza en el modo de espera si `USBConnectedMode` es 0 o 2. En este dongle `adb devices` no lista nada, así que el valor es 1 (solo MTP) o `adbd` está desactivado en el firmware de producción. En modo carro (AOA) `adbd` se mata siempre, así que **no habría shell dentro del carro** por esa vía.
- `riddleBoxCfg` guarda ajustes de USB (`USBVID`, `USBPID`, `USBProduct`, `USBSerial`, `USBConnectedMode`, `USBTransMode`…) que **la interfaz web no expone**.
- Existe un modo de fabricación (`/sys/module/libcomposite/parameters/mfgmode`): con valor 2 ejecuta `flash_erase /dev/mtd0 0 1 && reboot` (**borra el arranque**). No tocar.

## 4. Cómo se vería un cambio suave

Secuencia candidata, ejecutada **dentro del dongle** cuando el celular B pide el carro y A acepta:

```sh
# 1. soltar a A sin reiniciar el dongle
hcitool dc <MAC de A>          # corta el enlace Bluetooth de A (hcitool de BlueZ)
killall hfpd ARMAndroidAuto    # el supervisor los relanza en ~1 s
# 2. (opcional) evitar que A reconecte primero: ignorarlo unos segundos
# 3. B conecta (Bluetooth -> HFP -> WiFi -> Android Auto)
```

Lo que **no sabemos** y habría que comprobar en un dongle de pruebas:
1. Si al matar `ARMAndroidAuto` el carro (`fakeAADevice` / `AAaoaForword`) pierde la sesión y vuelve a pedirla, o si queda colgado.
2. Qué decide **a qué celular** se conecta `ARMAndroidAuto` tras relanzarse (el binario está empaquetado): orden de la lista, último usado, o quien conecta primero.
3. Si A reconectaría antes que B; haría falta bloquearlo (por ejemplo `hciconfig`/lista negra temporal).
4. Cuánto tarda en total (estimación: 5–10 s, como el cambio entre dos celulares en un carro con Android Auto nativo).

## 5. Cómo ejecutar eso desde la app

La API web **no** tiene estos comandos (y `server.cgi` está empaquetado, así que no se pueden leer comandos ocultos). Opciones, de menor a mayor riesgo:

| Vía | Qué requiere | Riesgo |
|---|---|---|
| **A.** Probar si `set` acepta claves internas de `riddleBoxCfg` (por ejemplo `USBConnectedMode`) | Una petición `set` con una clave no documentada | Bajo–medio: puede ignorarse o cambiar un ajuste interno. Sin sentido en el carro (adbd muere en AOA); solo sirve para un shell en el banco de pruebas |
| **B.** Firmware modificado con `/script/custom_init.sh` que arranque un pequeño servicio (por ejemplo `busybox httpd`/`nc`) con un endpoint `/switch` que ejecute la secuencia del punto 4 | Volcado de la flash SPI, desempaquetar el sistema de archivos, saltar/replicar la verificación (`hwSecret`, `hwfsTools`), reflashear | **Alto**: bloqueo de activación (`/etc/uuid_sign`), firmware empaquetado que cambia en cada versión; recuperable con el programador porque la flash es externa |
| **C.** Esperar a que el fabricante o la comunidad exponga el comando | — | Ninguno |

## 6. Plan por fases con puntos de decisión

1. **Banco de pruebas.** Comprar un **segundo dongle** (idealmente el mismo modelo) y un programador **CH341A + clip SOIC8** (unos 10–15 USD). *Nunca* empezar con el dongle que se usa en el carro.
2. **Respaldo.** Leer la flash dos veces y comparar hashes; guardar las imágenes fuera del repositorio. Decisión: ¿coinciden? Si no, no seguir.
3. **Desempaquetar** el rootfs (el trabajo de la comunidad muestra un JFFS2 en offset `0x380000` en otras versiones) y comprobar si se puede modificar y reflashear sin romper la verificación. Decisión: ¿arranca el dongle de pruebas?
4. **Añadir `custom_init.sh`** con un servicio mínimo y registrar el comportamiento de la secuencia del punto 4, **solo en el dongle de pruebas**.
5. Medir tiempos y fallos; si va bien, añadir en la app un modo "cambio suave" que use el endpoint y **mantenga el reinicio como respaldo** cuando no responda.

## 7. Mientras tanto

El cambio sigue usando el reinicio (~30 s). La app muestra una **cuenta atrás con barra de progreso** (en el celular que cede y en el que pide) para que el corte se entienda y no parezca una falla.
