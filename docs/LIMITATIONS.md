# Limitaciones conocidas

| Limitación | Por qué | Cómo se maneja |
|---|---|---|
| No hay un comando "conectar al celular X" | La API del dongle solo guarda y borra celulares y reinicia | El cambio reinicia el dongle y deja que el celular que pidió conecte primero |
| El celular anterior puede reconectarse solo | Su Android Auto está en automático | "Cambio agresivo" lo olvida en el dongle (hay que re-emparejar) o desactiva su conexión automática |
| El dongle no dice quién está conectado | `MDModel` llega vacío con Android Auto y `index` no es fiable | Cada celular informa de sí mismo (`CarConnection`) por BLE |
| "Disponible" depende de la app | Solo se ve a los celulares que tienen Switch Phone activo con Bluetooth y permisos | Instalar la app en todos los celulares |
| Cambiar ajustes desde fuera del carro requiere el WiFi del dongle | La API solo está en `192.168.50.2` | En el carro el celular ya está en ese WiFi |
| Estilo de tarjetas propio en el carro | Las plantillas de Android Auto no dejan cambiar fondo ni tarjetas | La app es de categoría navegación y dibuja en la superficie |
| Publicación en Google Play | Las apps de navegación tienen requisitos y revisión | Se distribuye por APK + "Fuentes desconocidas" |
| Distancia BLE | Unos 10–30 m | Suficiente dentro del carro |
| Firmware | Cada actualización del dongle puede ocultar ajustes o cambiar nombres | La app usa solo los campos presentes |
| Android 12+ | Permisos `BLUETOOTH_SCAN/ADVERTISE/CONNECT` y notificaciones | Se piden al abrir la app |
| Hotspot / internet para pasajeros | Android no deja que una app normal encienda el hotspot ni cambie su nombre o clave (hacen falta `TETHER_PRIVILEGED` / `NETWORK_SETTINGS`, que sí tiene el usuario `shell` de `adb`). **Se probó una tarjeta con QR y atajo y se retiró por inútil** (la configuración seguía siendo manual) | Usar el hotspot del celular a mano, un router 4G, o a futuro Shizuku |
| Internet por el WiFi del dongle | El celular es cliente del dongle; Android no reenvía tráfico a otros clientes de esa red sin hotspot | Hotspot del celular, router 4G, o un firmware modificado que haga de puente (sin NAT confirmado en el firmware) |
| Cambiar de celular sin reiniciar el dongle | La API web solo ofrece guardar/olvidar celulares y reiniciar. `server.cgi` está empaquetado: no se pueden leer comandos ocultos. Internamente el firmware sí puede parar y arrancar solo el enlace (`phone_link_deamon.sh`), pero no está expuesto | El cambio reinicia el dongle (~20–30 s). Un cambio de unos segundos exigiría firmware modificado con shell o un comando propio vía `custom_init.sh` |
