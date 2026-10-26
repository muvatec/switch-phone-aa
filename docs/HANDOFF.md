# Handoff: release firmada del APK (Infisical + GitHub Actions)

Sin secretos en este archivo. Rama de trabajo: `claude/handoff-docs-continuation-jpue30`.
Objetivo final: una release en GitHub Releases con el APK firmado, lista para descargar.
La sesion nueva debe EJECUTAR todo lo de "Plan", no solo describirlo.

## Estado hecho (2026-10-25)
- Infisical self-hosted en `https://heimdall.muvatec.com` (Coolify `https://master.muvatec.com`, servidor `localhost`
  uuid `nqdr9o4jwdhzrp3knzi7h4p2`). Proxy reiniciado; cert Let's Encrypt valido. No tocar SPIDER ni blaze.
- Proyecto Infisical `Switch phone`: slug `switch-phone-y1-nt`, id `78f4774b-6e94-4c36-add6-9441ecb6f8d4`,
  entornos dev/staging/prod. Org id `17017284-7f1d-4d32-923f-9c4e0329c8be`.
- Keystore PKCS12 (alias `switchphone`, RSA 4096) generado. Guardado en Infisical `prod`:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. No esta en el repo.
- `.github/workflows/release.yml` (en la rama, NO en main): usa `Infisical/secrets-action@v1.0.9`
  (`project-slug: switch-phone-y1-nt`, `env-slug: prod`) y falla si falta `KEYSTORE_BASE64`.
  `app/build.gradle.kts` lee `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` del entorno.
- Identidad de CI en Infisical: `github-actions`, de proyecto, rol viewer, Universal Auth.
  id `a7b7060c-76a3-45d2-a9be-0f2d3a96241e`, Client ID `0a983d6f-f2f7-47bb-9693-e964382f3743`.
  Verificado: lee los 4 secretos de prod. Su client secret NO se conserva: hay que crear uno nuevo.
- Existe otra identidad `github-actions` (id `dd80d0ad-3e4d-4b09-b989-abb51508e057`, rol member) que no
  creo Claude. No es de solo lectura; preguntar al usuario si se borra.
- El entorno trae `INFISICAL_UNIVERSAL_AUTH_CLIENT_ID/_SECRET` con rol admin de organizacion (puede crear
  identidades via API REST; el MCP de Infisical solo cubre proyectos/entornos/carpetas/secretos) y `GITHUB_TOKEN`
  del usuario con permisos amplios. `COOLIFY_TOKEN` tambien presente.

## Plan (ejecutar en orden)
1. Comprobar que se puede escribir en GitHub Actions: `gh secret list -R muvatec/switch-phone-aa`.
   Si da `403 ... not permitted through this proxy`, esa sesion tiene el proxy bloqueado: avisar al usuario de que
   necesita una sesion local/otra sin esa restriccion o que pegue los valores a mano (paso 3b), y seguir con el resto.
2. Crear un client secret nuevo:
   login `POST https://heimdall.muvatec.com/api/v1/auth/universal-auth/login` con las vars `INFISICAL_UNIVERSAL_AUTH_*`
   -> `accessToken`; luego `POST /api/v1/auth/universal-auth/identities/a7b7060c-76a3-45d2-a9be-0f2d3a96241e/client-secrets`
   con `{"description":"github-actions","ttl":0,"numUsesLimit":0}`. Guardar el valor solo en un fichero temporal
   y pasarlo por stdin a `gh secret set`; no imprimirlo.
3. Poner en el repo `muvatec/switch-phone-aa`:
   `gh secret set INFISICAL_CLIENT_ID -b 0a983d6f-f2f7-47bb-9693-e964382f3743`,
   `gh secret set INFISICAL_CLIENT_SECRET < fichero`, `gh variable set INFISICAL_URL -b https://heimdall.muvatec.com`.
   3b (si el proxy bloquea): indicar al usuario esos tres valores y que el secret esta en el fichero temporal.
4. Abrir PR de la rama a `main` (buscar plantilla de PR; usar `mcp__github__create_pull_request`) y mergear solo con
   permiso del usuario. El merge dispara el workflow. Seguirlo con `mcp__github__actions_*` y leer logs si falla.
5. Verificar la release `v0.1.N`: debe traer `switch-phone-0.1.N.apk` firmado con la llave release
   (`apksigner verify --print-certs`, no la debug). Dar al usuario el enlace de descarga.
6. Cierre: pedir al usuario revocar el token de Coolify, guardar offline la `ENCRYPTION_KEY` de Infisical y un
   respaldo del keystore. Sugerir rotar la llave de firma: su password y base64 se vieron en una conversacion.

## Notas
- Nunca imprimir valores de secretos; el sistema bloquea `cat` de credenciales. Usar ficheros temporales y stdin.
- Crear identidades de organizacion con el MCP no es posible; usar API REST con el token de arriba.
- Un conector "Coolify" inyectado por el proxy daba 401 con la API directa; usar las herramientas `mcp__Coolify__*`.
- Si hay que crear otro proyecto en Infisical, el token admin de organizacion lo permite.

## Actualizacion (2026-10-26): auth por OIDC
- El workflow ya no usa client secret. Identidad Infisical `github-ci-switch-phone` (id `d7e0a661-5368-46c8-a9e7-de08c85207c4`),
  OIDC: issuer `https://token.actions.githubusercontent.com`, audience `https://heimdall.muvatec.com`,
  subject `repo:muvatec/switch-phone-*:ref:refs/heads/main`, Viewer en el proyecto `Switch phone`.
- Variables de repo en `switch-phone-aa`: `INFISICAL_IDENTITY_ID`, `INFISICAL_URL` (las variables de org no sirven en repos privados del plan Free).
- Ya no hacen falta `INFISICAL_CLIENT_ID/SECRET`; los pasos 2-3 del Plan quedan obsoletos.
- Pendiente: quitar Universal Auth de la identidad (solo OIDC); seguir con los pasos 4-6.
