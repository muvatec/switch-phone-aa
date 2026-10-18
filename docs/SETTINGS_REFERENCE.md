# Referencia de ajustes del dongle

Qué significa cada clave de `Settings` según **la propia interfaz del dongle** (archivo de idioma `lang/en-*.js` y sus textos de ayuda `*Popup`), firmware `2025.12.29`. Los textos entre comillas son del fabricante, traducidos.

| Clave | Etiqueta del dongle | Valores | Para qué sirve (según el dongle) |
|---|---|---|---|
| `autoConn` | Auto Connect | 0/1 | Activa o desactiva que el dongle conecte solo al celular |
| `fastConnect` | **High speed Mode** | 0/1 | Sin ayuda oficial |
| `carLinkType` | HULink Type | 30 Automático, 2 CarPlay, 4 Android Auto | Tipo de enlace con la unidad principal |
| `returnMode` | Return Mode | 0 Original Car, 1 Box | A qué pantalla vuelve el carro |
| `startDelay` | Start Delay | 0–120 | Retardo de arranque |
| `bgMode` | Background | 0/1 | "No muestra la interfaz de conexión del dongle; actívalo en carros que se ven borrosos tras conectar el celular" |
| `syncMode` | Sync Mode | 0/1 | "Sincronización del tablero de algunos carros antiguos; normalmente no hace falta" |
| `autoUpdate` | Automatic Update | 0/1 | Actualización automática de firmware |
| `mediaDelay` | Media Delay | 300–2000 ms | "Por defecto 1000 ms. Más retardo = menos cortes; menos retardo = música más sincronizada con la imagen. No se recomienda tocarlo si no hay problemas" |
| `CallQuality` | CallQuality | 0 Norm, 1 Clear, 2 HD | Calidad de llamada |
| `NaviAudio` | Nav Channel | 0 Nav, 1 Voice, 2 Media | Canal de audio de navegación |
| `naviVolume` | Call Navigation Volume | 0–100 | Volumen de navegación durante llamadas |
| `btCall` | Bluetooth PhoneCall | 0/1 | Llamadas por Bluetooth |
| `echoDelay` | Echo Delay | 20–2000 ms | Retardo de eco |
| `autoPlay` | AutoPlay | 0/1 | "Reproduce música automáticamente tras conectar la función **HiCar**" (Huawei) |
| `resolutionWidth/Height` | Video Resolution | 800x480, 1280x720, 1920x1080 o personalizada | "Resolución de la imagen del celular. Si se ve mal o estirada, igualar a la pantalla del carro; la mayoría de carros no necesita cambiarla" |
| `bitRate` | Video Bit Rate | 0–20 (0 = automático) | "Menor = más fluido; con trabas o retraso, por debajo de 8 Mbps. Mayor = calidad más estable; por encima de 8 Mbps" |
| `fps` | Frame | 0–60 | "Si la pantalla se ve con mucho retraso, poner 20" |
| `ScreenDPI` | DPI | 0–480 | Densidad de la interfaz |
| `displaySize` | Display Style | 0 Default, 1 S, 2 M, 3 L | Tamaño de la interfaz |
| `autoDisplay` | Auto Display | 0 Norm, 1 Automatic | Modo de pantalla |
| `transMode` | Transfer Mode | 0 Norm, 1 Compatible | Modo de transferencia |
| `gps` | GPS | 0/1 | "Con esto activo, el GPS del carro se sincroniza al celular" |
| `mouseMode` | MouseMode | 0/1 | "Aparece un cursor en carros con touchpad; tocar el touchpad tres veces alterna deslizar y arrastrar" |
| `KnobMode` | Knob Mode | 0/1 | Modo perilla |
| `autoRefresh` | Auto Refresh | 0/1 | Sin ayuda oficial |
| `backRecording` | Background Recording | 0/1 | Sin ayuda oficial |
| `wifiChannel`, `wifi5GSwitch` | Wi-Fi | canales de `WifiChannelList` | Banda y canal del WiFi del dongle. Aviso del fabricante: respeta la normativa local y elige la banda adecuada; una banda no permitida **impide conectar** |
| `mediaSound` | *(sin etiqueta)* | 0/1 | **Sin documentar** en la interfaz oficial (aparece como "Category" bajo "Media") |
| `squareCorner` | *(sin etiqueta)* | 0/1 | **Sin documentar**; apareció con el firmware `2025.12.29` |
| `lang` | Language | id de `LangList` | Idioma de la interfaz del dongle |

Acciones (no son claves de `Settings`): Rollback ("volver a la versión anterior"), Specified Ver, Check Update, Modify Wi-Fi Name, Modify Device Name, Car Logo / Restore Default, Reset (borra todos los datos).

Cómo se obtuvo: `curl http://192.168.50.2/` → `lang/en-<hash>.js` y se leyeron las claves `*Popup`.
