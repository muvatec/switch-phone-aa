# Guía de uso

## Instalación en cada celular

1. Instala el APK de la [última release](../../../releases).
2. Abre **Switch Phone** y acepta **Bluetooth** y **notificaciones**. Sin Bluetooth la app no ve a los otros celulares.
3. En Android Auto activa el modo desarrollador y **Fuentes desconocidas** (ver [README](../README.md#instalación)).
4. Instálala en **todos** los celulares que usen el dongle: cada uno anuncia su presencia a los demás.

## Estados de cada celular

| Estado | Color | Significado |
|---|---|---|
| **ACTIVO** | verde | Es el celular conectado al carro ahora |
| **DISPONIBLE** | azul | Guardado en el dongle y a la vista por Bluetooth (su app está viva cerca) |
| **GUARDADO** | gris | Guardado en el dongle pero no visible |

Los nombres vienen del dongle. Si el dongle guarda uno sin nombre, la app aprende el nombre al conectarse; también puedes tocar un nombre y **Renombrar**.

## Cambiar de celular

### Desde el celular que quiere el carro (recomendado)
1. Abre la app. Si otro celular está en uso, verás **"X está usando el carro"**.
2. Toca **Solicitar conexión**.
3. En el carro aparece el aviso **"Y quiere conectarse"** con **Aceptar / Rechazar**; también llega a la notificación del celular activo.
4. Al aceptar, el dongle se reinicia (~20–30 s) y Android Auto pasa al celular que pidió.
5. Si el otro **rechaza** o **no responde** (45 s), tu app y una notificación lo dicen ("X rechazó la solicitud") y puedes pulsar **Reintentar**.

El celular inactivo también recibe un aviso automático **"Cambiar a este celular"** cuando ve a otro proyectando.

### Desde la pantalla del carro
Abre **Switch Phone** en el lanzador y toca la tarjeta del celular destino → **Cambiar**.

### Con un toque
- **Panel rápido:** en la app, **Añadir botón al panel rápido**. El tile "Switch Phone" pide el control.
- **Atajo del ícono:** mantén pulsado el ícono de la app → **Usar este celular**.

## Celular activo

Mientras proyecta Android Auto, el celular muestra una notificación permanente **"Conectado al carro"** con:
- **Desconectar:** reinicia el dongle para soltar este celular. Puede volver a conectarse solo si su Android Auto está en automático.
- **Abrir app.**

## Pestañas (menú inferior)

| Pestaña | Contenido |
|---|---|
| **Celulares** | Estado del dongle, solicitar conexión, tarjetas ACTIVO / DISPONIBLE / GUARDADO, cambiar, renombrar u olvidar |
| **Ajustes** | Nombres, conexión, audio, video, WiFi, idioma y opciones de esta app |
| **Monitor** | Temperatura, CPU, memoria y tráfico WiFi del dongle en vivo |
| **Herramientas** | Logo del carro, datos del carro, logs, firmware, reinicio y restauración |

Las pantallas de Ajustes, Monitor y Herramientas hablan con el dongle, así que necesitan que el celular esté en su WiFi (`AutoKit_xx`); durante una sesión de Android Auto ya lo está.

## Ajustes

Solo aparecen los ajustes que **tu firmware** reporta (como en la web del dongle). Los valores desconocidos se listan al final como solo lectura.

| Grupo | Ajuste | Efecto |
|---|---|---|
| Nombres | Dispositivo, Bluetooth, WiFi | 1–31 caracteres: letras, números, `_` y `-`. **Reinicia el dongle para aplicar.** Cambiar Bluetooth o WiFi puede obligar a re-emparejar |
| Conexión | Conexión rápida, conectar automáticamente | Reconexión más rápida / el dongle busca celular al encender |
| | Sincronizar hora, modo segundo plano | |
| | Al salir de Android Auto | Vuelve al carro original o al dongle |
| | Retardo de arranque (0–120 s) | |
| | Tipo de enlace | Automático / CarPlay / Android Auto |
| | Actualizar firmware automáticamente | El dongle puede cambiar de versión solo |
| Sin documentar | `mediaSound`, `squareCorner` | El dongle los reporta pero su interfaz oficial no los describe; no tienen efecto visible en Android Auto. Detalle de todos los ajustes en [SETTINGS_REFERENCE.md](SETTINGS_REFERENCE.md) |
| Audio | Retardo de audio (300–2000 ms) | Menos = más reactivo, más = más estable |
| | Sonido multimedia, calidad de llamada (Normal/Clara/HD) | |
| | Audio y volumen de navegación (0–100) | |
| | Llamadas por Bluetooth, retardo de eco, reproducción automática | |
| Video | Resolución 800x480 / 1280x720 / 1920x1080 | Reinicia el dongle |
| | Bitrate (0 = automático), FPS, DPI, tamaño de iconos | |
| | Modo de pantalla, modo de transferencia, esquinas cuadradas, modo ratón/perilla, GPS | |
| WiFi | Canal 5 GHz y 2.4 GHz | Reinicia el dongle |
| Esta app | Aceptar solicitudes automáticamente | El celular activo cede sin preguntar. **Apagado por defecto** |
| | Cambio agresivo | Al cambiar, olvida el celular actual; hay que volver a emparejarlo |

## Monitor

Lee `BoxMonitor` cada 2 s y lo muestra en **gráficas de torta (donut)**: CPU, memoria, temperatura (escala 0–100 °C) y WiFi (recibido vs enviado). Colores: verde < 60 °C, ámbar 60–75 °C, rojo ≥ 75 °C; CPU y memoria cambian a ámbar/rojo cuando se saturan. Con Android Auto activo, si el dongle llega a 75 °C el celular avisa con una notificación (máximo una cada 10 minutos).  El tráfico WiFi está en **Mbps** (igual que la web del dongle).

## Herramientas

- **Logo del carro:** elige una imagen; se recorta al centro, se reduce a 180×180 PNG (máx. 100 KB) y se sube a `upload.cgi`. Reinicia el dongle para verlo. *Volver al logo original* usa `resetLogo`.
- **Datos del carro:** marca, modelo y año (hasta 31 caracteres) que el dongle informa.
- **Logs:** descarga `logFile`, `appLogFile` o `sdkLogFile` y abre el menú de compartir.
- **Firmware:** versión, hardware y módulos. **No** actualiza el firmware: una imagen incorrecta puede dejar el dongle inservible; usa la app oficial.
- **Zona de peligro:** reiniciar, restablecer la app del dongle y restaurar de fábrica (pide escribir `RESTAURAR`; borra ajustes y celulares guardados).

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| "Sin conexión con el dongle" en la app | El celular no está en el WiFi del dongle. Fuera del carro, conéctate a `AutoKit_xx` |
| Un celular no sale DISPONIBLE | Su app no está activa, Bluetooth apagado o falta el permiso. Ábrela una vez |
| Tras cambiar, vuelve el celular anterior | Su Android Auto sigue en automático. Usa "Cambio agresivo" o desactiva la conexión automática en ese celular |
| La app no aparece en el carro | Falta **Fuentes desconocidas** en Android Auto, o reconecta Android Auto |
