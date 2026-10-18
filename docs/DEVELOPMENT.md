# Desarrollo

## Requisitos

- JDK 21 (Temurin/Zulu).
- Android SDK con `platforms;android-35` y `build-tools;35.0.0` (`sdkmanager`).
- Gradle: usa el wrapper (`./gradlew`), Gradle 8.7.

```bash
export JAVA_HOME=<jdk 21>
export ANDROID_SDK_ROOT=$HOME/Library/Android/sdk     # o tu ruta
./gradlew assembleDebug                               # APK en app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Stack: Kotlin 1.9, Jetpack Compose (Material 3), AndroidX Car App Library 1.4, minSdk 26.

## Estructura de código

Ver [ARCHITECTURE.md](ARCHITECTURE.md). Paquete: `com.muvatec.switchphone`.

## Probar sin carro con el DHU

El **Desktop Head Unit** de Google emula el carro en tu computador. Como el dongle habla AOA (ver [RESEARCH.md](RESEARCH.md)), el DHU puede conectarse **al dongle** y mostrar lo que muestra un carro real.

1. Instala el DHU: `sdkmanager "extras;google;auto"` (queda en `$ANDROID_SDK_ROOT/extras/google/auto`).
2. En cada celular, Android Auto → modo desarrollador → **Fuentes desconocidas**.
3. Conecta el dongle al computador por USB (el puerto de datos del dongle).
4. Obtén el serial USB del dongle (macOS): `ioreg -p IOUSB -l | grep -i "USB Serial Number"`.
5. Lanza:

   ```bash
   DONGLE_SERIAL=<serial> tools/run-dhu.sh
   ```

   `--usb` sin serial intentaría tomar **todos** los dispositivos USB compatibles (por ejemplo el celular por cable); con el serial solo toma el dongle. El script reintenta si el dongle se desconecta.
6. Conecta el celular al dongle por Bluetooth y WiFi como en un carro real. La ventana del DHU muestra Android Auto y el lanzador con **Switch Phone**.
7. Tema claro/oscuro del DHU: escribe `day` o `night` en la consola donde corre el DHU.

Límites: el DHU 2.0 no refleja todas las capacidades de un carro real (por ejemplo los toques sobre la superficie dependen del nivel de API que anuncie el DHU).

### Depurar

```bash
adb logcat -s SwitchPhone                       # presencia BLE, estados, solicitudes
adb shell dumpsys activity services com.muvatec.switchphone
```

Mensajes útiles: `BLE saw <hash> projecting=… requesting=…`, `NAMES box=… bt=… wifi=…`, `BoxInfo MD*…`.

## Script de API por terminal

`tools/dongle_api.sh` repite la firma de la app para consultar o ajustar el dongle desde un computador conectado a su WiFi:

```bash
tools/dongle_api.sh infos
tools/dongle_api.sh set mediaDelay 500
```

## CI y releases

`.github/workflows/release.yml` corre en cada push a `main`:

1. Instala JDK 21 y el SDK de Android.
2. Compila `assembleRelease` con `versionName = 0.1.<run_number>` y `versionCode = <run_number>`.
3. Publica una release `v0.1.<run_number>` con el APK `switch-phone-0.1.<n>.apk` y notas generadas.

### Firma del APK

Sin secretos, el APK se firma con la **llave de debug** del runner (instalable, pero la firma cambia entre ejecuciones, así que Android no deja actualizar encima: hay que desinstalar). Para tener una firma estable genera una llave y guárdala como secretos del repositorio:

```bash
keytool -genkeypair -v -keystore switch-phone.jks -alias switchphone \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -i switch-phone.jks | pbcopy        # macOS: copia el contenido
```

Secretos (Settings → Secrets and variables → Actions):

| Secreto | Valor |
|---|---|
| `KEYSTORE_BASE64` | Archivo `.jks` en base64 |
| `KEYSTORE_PASSWORD` | Contraseña del keystore |
| `KEY_ALIAS` | `switchphone` |
| `KEY_PASSWORD` | Contraseña de la llave |

Guarda el `.jks` fuera del repositorio (`*.jks` está en `.gitignore`).

## Commits firmados

El historial de este repositorio está firmado con SSH. Para que GitHub muestre **Verified**, añade la llave pública en GitHub → Settings → SSH and GPG keys → **New SSH key** → tipo **Signing Key**.
