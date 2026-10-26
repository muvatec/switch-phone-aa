# Contribuir a Switch Phone

Gracias por el interés. Reglas cortas para que las contribuciones sean fáciles de revisar y de mantener.

## Antes de escribir código

- **Revisa [docs/CONTEXT.md](docs/CONTEXT.md)** antes que nada: tiene la historia del proyecto, lo que ya se intentó y no funcionó, y las decisiones de diseño con su razón. Evita repetir caminos cerrados.
- **Abre un issue primero** si el cambio es grande (nueva función, cambio de arquitectura) o toca el protocolo BLE de presencia/solicitud. Para errores chicos o docs, un PR directo está bien.
- Si tu dongle es un modelo o firmware distinto al documentado, dilo en el issue: las claves de `Settings` y los textos de ayuda cambian entre firmwares (ver [docs/SETTINGS_REFERENCE.md](docs/SETTINGS_REFERENCE.md) y [docs/FIRMWARE.md](docs/FIRMWARE.md)).

## Qué sí y qué no

**Sí:**
- Correcciones de bugs, con los pasos para reproducir.
- Soporte para otros modelos de dongle Carlinkit/AutoKit (nueva fila en `docs/SETTINGS_REFERENCE.md`, ajuste en `DongleClient`).
- Mejoras de UI que mantengan el estilo (Compose, Material 3, ver `MainActivity.kt`).
- Documentación: capturas, traducciones, correcciones.
- Nuevos ajustes del dongle, siempre con la etiqueta y ayuda **tal como las muestra la interfaz web del propio dongle** (no inventadas), citando de dónde salen.

**No:**
- Código que intente descifrar, decompilar o extraer material criptográfico de los binarios del firmware (`server.cgi` y similares vienen protegidos por una capa de cifrado de hardware; ver [docs/SERVER_CGI.md](docs/SERVER_CGI.md)). Las contribuciones de análisis **estático de scripts y binarios sin cifrar** sí son bienvenidas.
- Binarios del fabricante (imágenes de firmware, `.hwfs`, `.img`) dentro de este repositorio. Enlázalos a su fuente pública en vez de subirlos.
- Cambios que activen por defecto algo que ceda el control del carro sin confirmación del usuario (ver el porqué de "Aceptar solicitudes automáticamente" apagado por defecto en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)).

## Flujo

1. Haz un fork y crea una rama descriptiva (`fix-wifi-channel-24ghz`, no `patch-1`).
2. Si tocas la app: compila y, si puedes, pruébalo en un dongle real o en el emulador DHU (ver [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#probar-sin-carro-con-el-dhu)).
3. Actualiza la documentación relevante en el mismo PR — un cambio de comportamiento sin su doc no se acepta.
4. Describe en el PR: qué cambia, por qué, y cómo lo probaste (dongle/firmware usado si aplica).
5. Un mantenedor revisa y puede pedir cambios; los PR quedan abiertos a discusión.

## Reportar un problema

Issues con:
- Modelo de dongle y versión de firmware (`Herramientas → Firmware` en la app, o `infos.BoxInfo.ver`).
- Pasos para reproducir.
- Registro si aplica (`Herramientas → Registro del carro` o `adb logcat -s SwitchPhone`).

## Seguridad

Si encuentras un problema de seguridad (por ejemplo algo explotable en el beacon BLE o en la API del dongle), abre un issue normal — no hay datos sensibles de usuarios en este proyecto y el impacto se limita al propio dongle del usuario.

## Licencia

Al contribuir aceptas que tu aporte se publique bajo la licencia MIT de este repositorio (ver [LICENSE](LICENSE)).
