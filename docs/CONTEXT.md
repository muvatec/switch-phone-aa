# Contexto del proyecto

Bitácora de cómo se llegó hasta aquí: qué se intentó, qué funcionó, qué no y por qué. Sirve para retomar el proyecto sin repetir caminos cerrados. (No es una transcripción: se quitaron identificadores personales como seriales, MAC, UUID y nombres de celulares.)

## Punto de partida

Dongle inalámbrico de Android Auto **Carlinkit CPC200-2Air (5.0, U2AC, firmware AutoKit)** compartido por dos personas, cada una con su celular Android. Objetivos que fueron apareciendo:

1. Entender cómo funciona el firmware y mejorarlo.
2. Ver en un emulador cómo se comporta en el carro.
3. Poder tener **varios celulares** y **cambiar** entre ellos fácilmente, incluso desde la pantalla del carro.

## Línea de tiempo

### 1. Primer contacto por USB (callejón sin salida)

- El dongle conectado al Mac enumera como **`12D1:0001`**, nombre `AutoKit` (después `VISION`), una interfaz de fabricante con endpoints bulk y un interrupt.
- Se probó con [`pi-carplay`](https://github.com/RGreeneRI/pi-carplay) parcheando el VID/PID (que esperan `1314:1520/1521`). El dongle **acusa recibo** de cada mensaje con 12 bytes (`0c000000 03000520 <tipo>`) pero nunca envía vídeo ni estado, y reinicia el USB a los 2–4 s.
- Se probó el cifrado USB de FastCarPlay (`0x55BB55BB`): sin cambio.
- **Conclusión:** en este firmware el puerto USB no habla el protocolo Carlinkit de cabecera propia. Ver [RESEARCH.md](RESEARCH.md).

### 2. La web del dongle (hallazgo clave)

- El dongle crea un WiFi `AutoKit_xx` y sirve su interfaz en `http://192.168.50.2` (Boa + Vue). Chrome en Android suele forzar HTTPS y no la carga.
- Se descargó el JavaScript de la interfaz y se extrajo la API: `POST /cgi-bin/server.cgi` con firma `md5(campos ordenados + sal)`. Ver [DONGLE_API.md](DONGLE_API.md).
- Un primer `400 Bad Request` se debía a usar `/server.cgi` en vez de `/cgi-bin/server.cgi`.
- Se leyó el firmware real (`infos`), los ajustes, y se cambiaron `fastConnect` y `mediaDelay`.

### 3. El emulador de carro (otro hallazgo clave)

- `GET_PROTOCOL` de AOA devuelve `2`: el dongle **es un accesorio Android**. El **Desktop Head Unit (DHU)** de Google se conecta con `--usb=<serial>` y habla Android Auto 1.7 con TLS.
- Sin serial, el DHU toma también el celular por cable (lo pone en modo accesorio y corta el anclaje USB a internet): **usar siempre el serial**.
- Para que el dongle transmita, un celular debe estar enlazado antes por Bluetooth y WiFi.
- Para no perder internet del Mac al unirlo al WiFi del dongle: **anclaje USB del celular** como ruta de internet.

### 4. Varios celulares: qué ofrece el dongle y qué no

- `DevList` guarda los celulares emparejados, pero **no hay comando "conectar a X"** ni dato fiable de quién está activo (`MDModel` vacío con Android Auto; `index` engaña).
- El cambio se resuelve con **reinicio del dongle**: el celular que está buscando conecta primero. Con las dos personas cerca funcionó sin "cambio agresivo" (~30 s).
- Un celular guardado puede venir **sin nombre**: se aprende el nombre al conectarse o se renombra a mano.
- Para saber quién está activo y quién está cerca se usa **BLE**: cada celular anuncia hash de su nombre + bandera "proyectando" (`CarConnection`). Después se añadió una **solicitud** (bit "pido el control") y una **respuesta** (aceptado/rechazado) para poder reintentar.

### 5. La app en el carro

1. Primero con plantillas de Car App Library (categoría IoT): lista y grid. Se veía feo y no se podía personalizar fondo ni tarjetas.
2. Después **UI dibujada** sobre la superficie de navegación (categoría navegación): tarjetas redondeadas, tema claro/oscuro según el carro, estados con color.
3. El aviso de solicitud se movió a un **heads-up del propio Android Auto** (`CarAppExtender`) para que llegue aunque el conductor esté en Waze/Spotify.

### 6. App del celular

Compose con menú inferior (Celulares · Ajustes · Monitor · Herramientas), ajustes construidos con las claves que reporta el firmware, monitor con donuts, logo propio, datos del carro, logs.

### 7. Repositorio y CI

- Commits firmados con SSH, releases automáticas en cada push a `main`.
- `android-actions/setup-android@v3` falló ("Failed to find package 'tools'"); se usa el SDK preinstalado del runner.

### 8. Internet para pasajeros (probado y descartado)

Se quiso compartir los datos del celular con todo el carro. Hallazgos: el Pixel soporta WiFi cliente + hotspot a la vez (`AP_STA`), pero una app normal **no puede encender el hotspot ni fijar su nombre/clave**. Se hizo una tarjeta con nombre, contraseña, QR y botón en la notificación; como la configuración seguía siendo manual no aportaba nada y **se eliminó**. Caminos que quedan abiertos: Shizuku (automático, requiere activarlo en cada arranque), router 4G con SIM, o un firmware modificado que convierta al dongle en puente (en el firmware público del dongle se ven `hostapd`, `wpa_supplicant` y `udhcpd`, pero no `iptables`).

### 9. Un celular nuevo que no conectaba y luego iba lento

- **No conectaba:** los registros de Android Auto mostraban `WIRELESS_SETUP_FAILED_TO_START_NO_HFP_FROM_HU_PRESENCE`: el Bluetooth con el dongle se cortaba a los ~2 s y Android Auto inalámbrico necesita el perfil manos libres (HFP) del dongle. El dongle atiende un solo Bluetooth a la vez; al apagar el Bluetooth de los otros celulares, olvidar el emparejamiento y volver a emparejar (con "Llamadas telefónicas" activas) conectó.
- **Iba lento:** con el celular proyectando, el enlace era sano (acuses de audio ~23 ms, WiFi 5 GHz a 270–300 Mbps), pero el celular tenía la RAM casi agotada y el **swap al 92 %**, `system_server` al 94 % de CPU y el limitador de cuadros de Android Auto alternando 60 ↔ 30 fps. Un Pixel 9 con 11 GB iba sobrado. Por eso la app ahora mide la memoria del celular.

## Errores que ya se pagaron (para no repetirlos)

| Error | Causa | Solución |
|---|---|---|
| App cerraba al abrir en Android Auto | `Row` sin título (el dongle devolvía un celular sin nombre) | `label` con respaldo |
| App cerraba al cambiar de celular | Una plantilla en estado `loading` no puede llevar icono ni acciones | Plantilla separada para "cargando" |
| `Template in a loading state can not have an icon` | Idem | Idem |
| "Activo" marcaba el celular equivocado | Se usaba `DevList.index` | Cada celular informa de sí mismo |
| El celular inactivo cedía sin aprobación | El primer diseño ejecutaba la petición sin preguntar | Buzón `RequestInbox` + aceptar/rechazar |
| No había forma de reintentar | El solicitante no sabía el resultado | Respuesta por BLE y estados `WAITING/REJECTED/TIMEOUT/ACCEPTED` |
| `set` con `val` vacío en `resetLogo` | La API exige el campo | Se envía `val=` vacío |
| En zsh, `for path in …` rompe `$PATH` | `path` está atado a `PATH` | Usar otro nombre de variable |
| `git commit` mostraba "No signature" | Falta `gpg.ssh.allowedSignersFile` para *verificar* localmente | La firma sí está en el commit (`gpgsig`); GitHub la verifica con la llave de firma registrada |

## Decisiones de diseño y por qué

- **HTTP en claro solo a `192.168.50.2`:** el dongle no soporta HTTPS.
- **Auto-aceptar solicitudes apagado por defecto:** el beacon BLE no está autenticado; cualquiera que imite un hash guardado podría *pedir* un cambio.
- **No actualizar firmware desde la app:** riesgo de dejar el dongle inservible. Ver [FIRMWARE.md](FIRMWARE.md).
- **Sin imágenes de firmware en el repo:** son del fabricante.
- **Categoría navegación:** única forma de dibujar libremente en la pantalla del carro; implica distribución por APK.

## Pendiente / ideas

- Perfiles por celular (resolución, retardo, volumen) aplicados al cambiar.
- Reglas automáticas ("si mi pareja está cerca, darle prioridad").
- Historial de conexiones.
- Probar con un carro real: todo se validó con el DHU y con el dongle en banco.
- Llave de firma estable del APK en los secretos del repositorio.
- Volcar la flash del dongle para buscar un comando interno de selección de celular.
