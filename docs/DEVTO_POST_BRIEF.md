# Brief para el agente que escribirá el post de dev.to

Pega este documento completo como instrucción al agente/IA que redactará el post. Tiene todo el contexto: qué pasó, qué se construyó, referencias y el tono esperado.

## Objetivo del post

Contar la historia completa de **Switch Phone**: un proyecto que arrancó como "¿qué le puedo mejorar a mi dongle de Android Auto inalámbrico?" y terminó siendo una app Android completa (con pantalla propia dentro de Android Auto) para gestionar varios celulares compartiendo un mismo dongle Carlinkit — con investigación real de ingeniería inversa documentada en el camino. Cierra invitando a la comunidad a usar, reportar y contribuir al repo, que ya es público.

**Repo:** https://github.com/muvatec/switch-phone-aa (público, MIT, con CI que publica releases automáticas)

## Tono

- Técnico pero narrativo — es un post de "cómo llegamos hasta acá", no un README reformulado.
- Primera persona del plural ("empezamos a investigar", "nos dimos cuenta") — fue un trabajo de exploración real, con callejones sin salida incluidos. No ocultar los fracasos; son parte de lo interesante (el intento fallido de hablar el protocolo Carlinkit por USB, el cifrado de hardware que bloqueó la decompilación, etc.).
- Dirigido a developers Android / gente de IoT / entusiastas de car-hacking en dev.to. Puede asumir conocimiento técnico medio-alto.
- Nada de marketing vacío. Mostrar código, capturas de terminal, hallazgos concretos.

## Estructura sugerida

1. **El problema real**: dos personas, un carro, un dongle Carlinkit CPC200-2Air (Android Auto inalámbrico) — y cada vez que cambiaban de celular, un dolor de cabeza manual.
2. **Primeros pasos de investigación**: cómo se abordó el dongle por USB, el callejón sin salida del protocolo Carlinkit (acks de 12 bytes que resultaron ser respuestas MTP), el hallazgo de que el dongle habla AOA y se puede probar con el Desktop Head Unit de Google sin tener el carro delante.
3. **El hallazgo clave**: la API web no documentada del dongle (`192.168.50.2/cgi-bin/server.cgi`, firma MD5) — de ahí salió todo lo demás.
4. **Construir la app**: por qué terminó siendo una app con UI dibujada a mano sobre la superficie de Android Auto (las plantillas estándar no daban suficiente control visual), el sistema de presencia por BLE para saber qué celular está "activo" / "disponible" / "guardado" sin que el dongle lo sepa, el protocolo de solicitud-aceptación entre celulares.
5. **El límite real**: el intento de decompilar `server.cgi` para encontrar un modo de cambiar de celular sin reiniciar el dongle, el hallazgo de que está protegido con una capa de cifrado por hardware (`/dev/hwaes`), y por qué ahí se detuvo la investigación (límite ético/técnico, no solo de tiempo).
6. **Estado actual**: lo que la app hace hoy (lista abajo), capturas si el agente puede generarlas o pedir al usuario que las agregue.
7. **Invitación a contribuir**: repo público, MIT, con `CONTRIBUTING.md`, templates de issues/PR, CI que compila y publica releases automáticas en cada push a main.

## Qué construimos (resumen funcional, para la sección de features)

- **Multi-celular**: cualquier número de celulares pueden compartir el mismo dongle. La app muestra el estado de cada uno: **ACTIVO** (usando el carro ahora), **DISPONIBLE** (cerca, visto por Bluetooth Low Energy), **GUARDADO** (conocido por el dongle pero no visible ahora).
- **Cambio de celular con un toque**: desde la app, desde la pantalla de Android Auto (UI propia dibujada, no las plantillas estándar), o desde un atajo/tile del panel rápido de Android.
- **Protocolo de solicitud**: un celular pide el control; el celular activo recibe un aviso (heads-up en Android Auto + notificación) con Aceptar/Rechazar. Si rechaza o no responde, se puede reintentar.
- **Cuenta atrás visible**: el cambio reinicia el dongle (~30s); la app muestra una barra de progreso con fases ("Soltando el carro…", "Reiniciando…", "Conectando…") en vez de dejar al usuario viendo una pantalla en blanco.
- **Ajustes del dongle construidos dinámicamente**: lee las claves reales que expone el firmware vía su API web y arma la pantalla de ajustes con las etiquetas y textos de ayuda exactos de la interfaz oficial del fabricante (nombres, conexión, audio, video, WiFi, idioma).
- **Monitor en vivo**: temperatura, CPU, memoria y tráfico WiFi del dongle, con gráficas tipo donut y aviso si se sobrecalienta.
- **Monitor de memoria del propio celular**: se descubrió que la causa real de que Android Auto "se sintiera lento" en un celular no era el dongle ni el WiFi — era el celular quedándose sin RAM/swap. La app ahora mide y avisa.
- **Herramientas**: logo personalizado del carro, datos del vehículo, descarga de logs, reinicio/restauración del dongle.
- **Guía para agregar un celular nuevo**: paso a paso con detección automática de cuándo el celular nuevo quedó registrado.
- **Emulación del carro sin tener el carro**: usando el Desktop Head Unit de Google en modo AOA directo contra el dongle por USB — se puede desarrollar y probar sin subirse al auto.

## La parte de ingeniería inversa (para la sección técnica/investigación)

Contar esto con honestidad sobre lo que sí se logró y lo que no:

- **Lo que funcionó**: análisis estático de los scripts de shell del firmware (texto plano, sin cifrar) encontrados en un repositorio comunitario de ingeniería inversa de estos dongles. De ahí se entendió cómo arranca el enlace Android Auto (`phone_link_deamon.sh`, un supervisor que relanza los procesos si mueren), el mapa de procesos internos, y los modos USB (MTP en espera, AOA cuando detecta un carro).
- **El misterio resuelto**: los primeros intentos de hablar el protocolo propio de Carlinkit por USB fallaban con "acks" de 12 bytes sin sentido. Resultó ser el dongle respondiendo como dispositivo MTP estándar ("operación no soportada") — no un protocolo propio, sino el modo de espera por defecto.
- **El límite real**: `server.cgi` (el binario que atiende la API web) resultó ser un ejecutable ARM auto-extraíble con una capa de cifrado por hardware (dispositivo de kernel `/dev/hwaes`) antes de descomprimir con LZMA. Sin la llave del fabricante (que vive en el propio chip, no en el software), no se puede leer el contenido. Se investigó si el controlador de ese cifrado estaba accesible en otros módulos del firmware público — no lo estaba.
- **Dónde se trazó la línea**: ahí se detuvo deliberadamente la investigación — extraer claves de cifrado de hardware no es terreno donde seguir, independientemente de que sea el propio dispositivo del usuario. El post puede mencionar esto como una decisión consciente, no una limitación técnica lamentada.
- Todo este trabajo de investigación está documentado en el repo en `docs/RESEARCH.md`, `docs/SOFT_SWITCH.md`, `docs/SERVER_CGI.md` y `docs/CONTEXT.md` (este último es literalmente la bitácora completa del proyecto, con los callejones sin salida incluidos — vale la pena linkearlo directamente como "si quieres la historia completa, está toda acá").

## Stack técnico (para que el post sea preciso)

- Kotlin + Jetpack Compose (Material 3) para la UI del celular.
- Android for Cars App Library — pero con una pantalla de **categoría navegación** dibujando directamente sobre el `Surface` que entrega Android Auto, en vez de usar las plantillas estándar (que no permiten el control visual que se quería).
- Bluetooth Low Energy como mecanismo de "presencia" entre celulares — el dongle no tiene forma de decir "qué celular está usando el carro ahora mismo", así que cada instancia de la app anuncia su propio estado por BLE y escucha a las demás.
- Comunicación con el dongle vía su API web HTTP interna (no documentada oficialmente, descubierta inspeccionando el JavaScript de su interfaz de configuración).
- CI en GitHub Actions: cada push a `main` compila y publica un APK como release automática.

## Referencias a enlazar en el post

- Repo del proyecto: https://github.com/muvatec/switch-phone-aa
- `docs/CONTEXT.md` del repo — la bitácora completa, para quien quiera la historia sin filtrar.
- `docs/ARCHITECTURE.md` — arquitectura técnica de la app.
- `docs/RESEARCH.md`, `docs/SOFT_SWITCH.md`, `docs/SERVER_CGI.md` — la parte de ingeniería inversa.
- Repositorio comunitario de ingeniería inversa de dongles Carlinkit que sirvió de base para varios hallazgos (buscar y confirmar el nombre/URL exacto al escribir: es un repo de GitHub de reverse engineering de wireless CarPlay/Android Auto dongles — el agente debe verificarlo en el propio repo del proyecto, está citado en `docs/RESEARCH.md`).
- Desktop Head Unit de Google (herramienta oficial de Android para probar Android Auto sin un carro real).

## Pie de página / llamado a la acción

Cerrar invitando explícitamente a:
1. Probar la app si alguien tiene un dongle Carlinkit/AutoKit compatible.
2. Reportar su modelo/firmware si es distinto al documentado (los ajustes varían entre firmwares, y el proyecto está armado para adaptarse a eso).
3. Contribuir código — el repo tiene `CONTRIBUTING.md` con reglas claras de qué se acepta y qué no (en particular: nada de intentos de descifrar binarios del firmware, eso quedó fuera de línea a propósito).

## Restricciones para el agente que escribe

- No inventar números de performance, cantidad de usuarios, ni fechas que no estén en este brief o en el repo.
- No afirmar que el proyecto "rompió" la seguridad del dongle — al contrario, fue deliberadamente respetuoso de ese límite, y eso es parte de la historia a contar.
- Verificar en el repo real (`docs/`) cualquier detalle técnico específico antes de afirmarlo con precisión (nombres de archivos, comandos, versiones).
- El autor/mantenedor del repo es Duvan Jamid Vargas (`duvanjamid`); usar su nombre si el post lo requiere, sin inventar otros colaboradores.
