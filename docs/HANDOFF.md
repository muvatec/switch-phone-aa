# Handoff: Infisical (Heimdall) en Coolify

Sin secretos en este archivo. Rama de trabajo: `ccr-e76d98cb-jmhgna`.

## Estado (2026-10-25)
- Coolify `https://master.muvatec.com` (v4.5.0, un solo servidor `localhost`, arm64, IP `129.153.222.97`).
- Proyecto nuevo **Heimdall** (`ezqba5epfewm31pfp1vkerq8`), env `production`. No tocar SPIDER ni blaze.
- Servicio `infisical` (`4ie6oert6gu7j6y79imcrc3t`), app `backend` (`fyvytitkqr1ftpgmnibr3vsb`).
  Compose propio en `deploy/infisical/docker-compose.yml` (Postgres 14, Redis 7, Infisical v0.154.6).
  Limites: Infisical 1536m (heap 1024), Postgres 256m, Redis 96m. Con heap 384 daba OOM y 502.
- Dominio `https://heimdall.muvatec.com` (registro A -> 129.153.222.97), `SITE_URL` igual. `/api/status` responde 200.
- Primer administrador creado por el usuario. Registro solo por invitacion.
- Machine Identity `claude-mcp` (Universal Auth) creada. Login del MCP daba `401 Invalid credentials`:
  el secret `Claude` mostraba 0 usos. Lockout activo (3 intentos / 5 min). Plan: Reset All Lockouts,
  crear client secret nuevo, guardarlo sin espacios y abrir sesion nueva.

## Pendiente
1. HECHO: proxy reiniciado, `heimdall.muvatec.com` con cert Let's Encrypt valido.
2. HECHO (2026-10-25): `mcp__infisical__list-projects` funciona. Proyecto `Switch phone` (slug `switch-phone-y1-nt`,
   id `78f4774b-6e94-4c36-add6-9441ecb6f8d4`, envs dev/staging/prod). Usar ese slug en el workflow, no `switch-phone`.
3. PARCIAL: keystore PKCS12 (alias `switchphone`) generado y los 4 secretos ya estan en Infisical `prod`.
   Falta: Machine Identity `github-actions` (solo lectura) y en GitHub secrets `INFISICAL_CLIENT_ID/SECRET` + var `INFISICAL_URL`.
   (original) secretos de firma (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`), Machine Identity solo lectura para GitHub Actions
   (`INFISICAL_CLIENT_ID/SECRET` en secrets del repo; `INFISICAL_URL` en vars). Ver `docs/INFISICAL.md`.
4. HECHO en la rama (no en main): `release.yml` usa `Infisical/secrets-action` y exige keystore. Al mergear a main sale la release firmada.
5. Cerrar: revocar el token de Coolify y guardar offline la `ENCRYPTION_KEY` de Infisical.

## Notas tecnicas
- `.mcp.json` define `coolify` e `infisical` leyendo variables de entorno (COOLIFY_*, INFISICAL_*).
- Conector personalizado "Coolify" en claude.ai apunta a `https://master.muvatec.com/mcp` (OAuth); da las
  herramientas `mcp__Coolify__*`. El proxy de sesion inyecta una conexion "Coolify" que daba 401 con la
  API directa (`X-Proxy-Error: upstream auth failed`), no usar curl contra la API.
- Nombres de variables del MCP: `INFISICAL_UNIVERSAL_AUTH_CLIENT_ID/_SECRET`, `INFISICAL_HOST_URL`.
