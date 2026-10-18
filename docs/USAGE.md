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

## Ajustes del dongle

Se ven cuando el celular está en el WiFi del dongle (durante una sesión de Android Auto lo está).

| Ajuste | Efecto |
|---|---|
| Conexión rápida | Reconecta más rápido |
| Conectar automáticamente | El dongle busca celular al encender |
| Retardo de audio (300–2000 ms) | Menos = más reactivo, más = más estable |
| Nombres: dispositivo, Bluetooth, WiFi | 1–31 caracteres: letras, números, `_` y `-`. **Reinicia el dongle para aplicar.** Cambiar Bluetooth o WiFi puede obligar a re-emparejar los celulares |
| Resolución 800x480 / 1280x720 / 1920x1080 | Reinicia el dongle |
| Canal WiFi 5 GHz | Reinicia el dongle |
| Aceptar solicitudes automáticamente | El celular activo cede sin preguntar. **Apagado por defecto** |
| Cambio agresivo | Al cambiar, olvida el celular actual en el dongle; hay que volver a emparejarlo |

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| "Sin conexión con el dongle" en la app | El celular no está en el WiFi del dongle. Fuera del carro, conéctate a `AutoKit_xx` |
| Un celular no sale DISPONIBLE | Su app no está activa, Bluetooth apagado o falta el permiso. Ábrela una vez |
| Tras cambiar, vuelve el celular anterior | Su Android Auto sigue en automático. Usa "Cambio agresivo" o desactiva la conexión automática en ese celular |
| La app no aparece en el carro | Falta **Fuentes desconocidas** en Android Auto, o reconecta Android Auto |
