# Investigación: cómo funciona el dongle por dentro

Notas de la ingeniería inversa hecha para este proyecto sobre un **Carlinkit CPC200-2Air** (5.0, producto `U2AC_AUTOKIT`). Sirven para quien quiera ir más lejos (modificar firmware, hacer un emulador de carro propio, etc.).

## Hardware y firmware

- Familia "CPC200-2Air / Carlinkit 5.0 / U2AC". WiFi/Bluetooth Broadcom BCM4358 (módulo `bcmdhd`), flash SPI de 16 MB, Linux embebido (kernel 3.14, ARMv7) según el trabajo previo de la comunidad ([ludwig-v/wireless-carplay-dongle-reverse-engineering](https://github.com/ludwig-v/wireless-carplay-dongle-reverse-engineering)).
- Módulos reportados por `infos` (`ModuleInfo`): `Boot128`, `USBDrv`, `Common`, `Service`, `Upg`, `HUCP` (head unit CarPlay), `HUAA` (head unit Android Auto), `CarPlay`, `AndroidAuto`, `BCM4358`, `WebAUTOKIT`, `Cus_U2AC_AUTOKIT`.
- Servidor web Boa 0.94 con interfaz Vue; CGI en `/cgi-bin/server.cgi`.
- El firmware se actualiza solo (`2024.03.21.1830` → `2025.12.29.1746` durante el desarrollo). Cambió el nombre USB de `AutoKit` a `VISION`, ocultó ajustes y cambió el `code` de `BoxInfo`, pero mantuvo la API.
- La activación del dispositivo la controla el fabricante (`/etc/uuid_sign` en el sistema de archivos del dongle). Un firmware modificado puede quedar bloqueado en modo activación.

## Cómo se ve por USB

El dongle se presenta al carro como un **teléfono Android en modo accesorio**:

1. Al conectarlo a un host enumera con `VID:PID 12D1:0001`, una interfaz de clase de fabricante (`255/255/0`), endpoints bulk IN/OUT y un interrupt IN.
2. Responde a **Android Open Accessory (AOA) protocolo 2**: la petición de control `51` (`GET_PROTOCOL`) devuelve `2`.
3. Tras el arranque AOA pasa a `18D1:2D00` (accesorio) y habla **Android Auto 1.7** con TLS 1.2 (`ECDHE-RSA-AES128-GCM-SHA256`).

Eso permite usar el **Desktop Head Unit (DHU)** de Google como carro de pruebas con `--usb=<serial del dongle>`. Ver [DEVELOPMENT.md](DEVELOPMENT.md#probar-sin-carro-con-el-dhu).

> **Corrección posterior.** Los "acuses de 12 bytes" descritos abajo eran **MTP**, no un protocolo del dongle: el modo de espera `12D1:0001` es un dispositivo MTP (`mtp-server`) y `0x2005` es *Operation Not Supported*. Explicación completa en [SOFT_SWITCH.md](SOFT_SWITCH.md#3-los-modos-usb-y-el-misterio-de-los-acuses-de-12-bytes).

### Lo que NO sirve: el protocolo "Carlinkit" en este modo

Las apps tipo `pi-carplay` / `FastCarPlay` hablan un protocolo propio de Carlinkit (cabecera `0x55AA55AA`, longitud, tipo, `~tipo`). Contra este dongle en modo `12D1:0001`:

- Acepta los paquetes y responde **solo un acuse de 12 bytes** por mensaje: `0c000000 03000520 <n>`. (Era un contenedor MTP *Response* con código `0x2005`; `<n>` es el *transaction id*.)
- Nunca envía `Open`, `Plugged`, `Phase` ni vídeo; sin tráfico a los ~2–4 s se reinicia el USB.
- El cifrado USB opcional de firmware nuevo (cabecera `0x55BB55BB`, AES-128-CFB) tampoco cambió el comportamiento.
- No se encontró el VID:PID `12D1:0001` en ninguno de los proyectos revisados (que esperan `1314:1520/1521`).

Conclusión: en este firmware el puerto USB hacia el carro habla **AOA + Android Auto**, no el protocolo Carlinkit de cabecera propia. Para pruebas de carro usa AOA (DHU).

## WiFi y red

- AP del dongle: SSID `AutoKit_xx`, 5 GHz canal 36 por defecto, IP del dongle `192.168.50.2`.
- Acepta varios clientes a la vez (por ejemplo un computador y un celular).
- Sin aislamiento de clientes observado. Puertos abiertos vistos: 80.
- La MAC del dongle tiene el prefijo `00:E0:4C` (Realtek) en el adaptador USB; el WiFi es Broadcom según el firmware.

## Android Auto inalámbrico

- El celular y el dongle se emparejan por Bluetooth; por Bluetooth el dongle entrega al celular los datos del WiFi.
- El celular se une al AP, abre la conexión de Android Auto y el dongle la reenvía al carro por USB (AOA).
- Un celular conecta si está guardado en el dongle y su Android Auto está en modo automático. El dongle guarda varios celulares pero solo mantiene **uno** enlazado.

## Cómo conecta el dongle a un celular por dentro (firmware público 2024.10)

Leído de los scripts de texto plano de la comunidad (los binarios vienen empaquetados y no se pueden leer con `strings`, incluido `server.cgi`, 42 KB):

- `script/phone_link_deamon.sh <CarPlay|AndroidAuto|HiCar|ICCOA> <start|stop>` arranca y para el proceso de enlace de cada tecnología. Para Android Auto: `hfpd` (manos libres Bluetooth) y `ARMAndroidAuto`.
- `script/attach_bluetooth.sh` y `start_bluetooth_wifi.sh` inicializan el chip BCM4358 (`hciattach`, `hciconfig`) y levantan `hostapd` en `wlan0` con el canal de `riddleBoxCfg`.
- `script/start_main_service.sh` ejecuta `/script/custom_init.sh` si existe: hay un gancho para código propio al arrancar.
- Hay `hcitool`, `hciconfig`, `hcid`, `adbd`, `wpa_supplicant`, `udhcpd` en el firmware.

Implicación: **existe un camino interno más liviano que reiniciar todo el dongle** (parar y arrancar solo `hfpd`/`ARMAndroidAuto`, o desconectar el celular con `hcitool`), pero **la API web no lo expone** y desde fuera no hay shell. Usarlo exigiría firmware modificado (ver más abajo).

## Ideas no exploradas

- Volcar la flash con un clip SPI y buscar un comando interno para elegir celular (¿mensajes entre `Service` y `HUAA`?).
- Subir firmware modificado con `upload.cgi` (riesgo de brick y de activación).
- Reemplazar el dongle por una Raspberry Pi con [WirelessAndroidAutoDongle](https://github.com/nisargjhaveri/WirelessAndroidAutoDongle), que sí permite control total.

## Referencias

- [ludwig-v/wireless-carplay-dongle-reverse-engineering](https://github.com/ludwig-v/wireless-carplay-dongle-reverse-engineering)
- [lvalen91/CPC200-CCPA_resources](https://github.com/lvalen91/CPC200-CCPA_resources)
- [niellun/FastCarPlay](https://github.com/niellun/FastCarPlay)
- [rhysmorgan134/node-CarPlay](https://github.com/rhysmorgan134/node-CarPlay), [RGreeneRI/pi-carplay](https://github.com/RGreeneRI/pi-carplay)
- [Android: probar con el Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu)
