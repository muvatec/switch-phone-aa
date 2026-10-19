# Arquitectura

## Piezas

```
 Celular A (activo)                     Dongle (CPC200-2Air)                Carro
 ┌────────────────────┐   WiFi 5 GHz   ┌──────────────────┐   USB (AOA)   ┌───────────┐
 │ Android Auto       │◄──────────────►│ AutoKit firmware │◄─────────────►│ Head unit │
 │ Switch Phone       │   HTTP :80     │  web API /cgi-bin│               └───────────┘
 └─────────▲──────────┘                └──────────────────┘
           │ BLE (presencia + solicitud)
 ┌─────────▼──────────┐
 │ Celular B          │   (sin conexión al dongle: solo BLE)
 │ Switch Phone       │
 └────────────────────┘
```

| Clase | Rol |
|---|---|
| `DongleClient` | Habla con la API web del dongle (firma MD5, `infos`, `set`, `restart`). Enlaza las peticiones a la red WiFi del dongle aunque el celular use datos móviles |
| `Presence` | Beacon y escáner BLE. Anuncia "yo soy X y estoy/no estoy proyectando" y "pido el control". Mantiene qué celulares se ven |
| `PresenceService` | Servicio en primer plano: mantiene el beacon, crea las notificaciones y ejecuta el cambio cuando se acepta una solicitud |
| `RequestInbox` | Buzón en memoria de la solicitud pendiente; lo leen la notificación y la pantalla del carro |
| `DongleCarAppService` / `SurfaceScreen` | App de Android Auto (Car App Library, categoría navegación). Dibuja las tarjetas sobre la superficie |
| `DongleTemplateCarAppService`, `DevicesScreen`, `ConfirmScreen` | Segunda entrada (categoría IoT) con plantillas de Car App Library (grid). Respaldo por si Android Auto no lista la versión de navegación |
| `CarLog` | Registro persistente (200 KB) de lo que hace Android Auto con la app: servicio creado, host, pantalla, tamaño, toques. Se comparte desde Herramientas |
| `MainActivity` + `Tabs` | UI del celular (Compose): menú inferior y pantalla *Celulares* |
| `SettingsTab`, `MonitorTab`, `ToolsTab` | Ajustes dinámicos (solo claves que reporta el firmware), monitor en vivo y herramientas (logo, carInfo, logs, reset) |
| `SwitchTileService`, `SwitchHereActivity` | Botón del panel rápido y atajo del ícono |

## Estados de un celular

| Estado | Cómo se decide |
|---|---|
| **ACTIVO** | El propio celular lo sabe (`CarConnection` = proyección). Los demás lo ven en la bandera `projecting` del beacon |
| **DISPONIBLE** | Está guardado en el dongle **y** su beacon se oyó en los últimos 45 s |
| **GUARDADO** | Está guardado en el dongle y no se oye |

El dongle no sirve para saber quién está activo: reporta `MDLinkType=AndroidAuto` pero deja `MDModel` vacío, y el campo `index` de `DevList` no es fiable. Por eso la verdad la dan los propios celulares.

## Beacon BLE

- UUID de servicio `0000a7c1-0000-1000-8000-00805f9b34fb` en el paquete de anuncio.
- Datos de servicio en la respuesta de escaneo (9 bytes): `hash(4)` + `flags(1)` + `destino(4)`.
  - `hash` = primeros 4 bytes de `MD5(nombre Bluetooth en minúsculas)`. Se compara con los nombres que el dongle tiene guardados.
  - `flags`: bit 0 = proyectando Android Auto; bit 1 = pide el control; bit 2 = **rechazado**; bit 3 = **aceptado**.
  - `destino` = hash del celular al que va dirigido el mensaje (el que pide → el activo; la respuesta → el que pidió). `00000000` = sin destino.
- Sin conexión BLE (`setConnectable(false)`), anuncio balanceado, escaneo balanceado con filtro por UUID.

## Solicitud de conexión

1. El celular B ve que A está proyectando. Muestra un aviso "A está usando el carro" con **Cambiar a este celular**, o el usuario pulsa **Solicitar conexión** en la app, el tile o el atajo.
2. B activa el bit "pide el control" durante 45 s.
3. A (que está en el WiFi del dongle) lo oye, lo guarda en `RequestInbox` y avisa por tres vías:
   - **Aviso heads-up en Android Auto** (`CarAppExtender`, importancia alta) con **Aceptar / Rechazar**, visible aunque el conductor esté en Waze o Spotify. Al tocarlo abre Switch Phone en el carro.
   - Ventana en la pantalla de Switch Phone si está abierta.
   - Notificación normal en el celular.
4. Al aceptar, A pide a la API `restart`. (Opcional, "cambio agresivo": antes olvida a A en el dongle.)
5. El dongle reinicia (~20 s). B es el único que está buscando y conecta. B empieza a proyectar y el estado ACTIVO cambia solo.

### Respuesta y reintento

A contesta por el mismo canal BLE durante 30 s: bit **aceptado** o **rechazado**, dirigido al hash de B. B pasa por estos estados (`Presence.ReqPhase`):

| Estado | Cuándo | Qué ve el usuario |
|---|---|---|
| `WAITING` | Pidió y espera (hasta 45 s) | Barra de progreso y **Cancelar** |
| `REJECTED` | A rechazó | "X rechazó la solicitud" + **Reintentar** (app y notificación) |
| `TIMEOUT` | A no respondió en 45 s | "X no respondió" + **Reintentar** |
| `ACCEPTED` | A aceptó | "X aceptó. Conectando…" |
| `IDLE` | Nada pendiente o ya está proyectando | Botón **Solicitar conexión** |

Al rechazar, A olvida el antiguo bloqueo anti-repetición de 60 s para el solicitante, de modo que B puede reintentar de inmediato.

Con "Aceptar solicitudes automáticamente" activado, A salta el paso de aprobación.

## UI en el carro

Android Auto no deja personalizar fondo ni tarjetas de las plantillas. Para tener tarjetas redondeadas, tema claro/oscuro y estados con color, la app es de **categoría navegación**: recibe una superficie (`SurfaceCallback`) y dibuja ahí con `Canvas`. Los toques llegan por `SurfaceCallback.onClick`. El tema sale de `CarContext.isDarkMode`.

Consecuencias:
- Requiere los permisos `androidx.car.app.NAVIGATION_TEMPLATES` y `androidx.car.app.ACCESS_SURFACE`.
- Para publicar en Google Play habría que cumplir los requisitos de apps de navegación; instalada por APK funciona con "Fuentes desconocidas".

## Seguridad y privacidad

- La app solo habla con `192.168.50.2` por HTTP en claro (el dongle no soporta HTTPS); `network_security_config` lo permite únicamente para esa IP.
- El beacon BLE solo contiene un hash de 4 bytes del nombre Bluetooth. No hay datos personales.
- Cualquiera que imite el hash de un celular guardado podría **pedir** un cambio; el celular activo debe aceptarlo salvo que se active el auto-aceptar. Por eso el auto-aceptar viene apagado.
- La sal de firma de la API es una constante del JavaScript que el propio dongle sirve a cualquier cliente; no es un secreto.
