# Infisical (self-hosted on Coolify)

Secrets manager for **CI only** (APK signing). The Android app is a client: anything
bundled inside the APK is public, so the app itself never reads from Infisical.

Files: `deploy/infisical/docker-compose.yml` and `deploy/infisical/.env.example`.
No real secrets are stored in this repo.

## 1. Deploy in Coolify
1. Project > New resource > **Docker Compose Empty**, paste `docker-compose.yml`.
2. Environment Variables: fill `ENCRYPTION_KEY`, `AUTH_SECRET`, `POSTGRES_PASSWORD`,
   `SITE_URL` (generate with the commands in `.env.example`).
3. Assign a domain to the `infisical` service, port `8080`, HTTPS (Let's Encrypt), and
   set `SITE_URL` to that same `https://` URL.
4. Deploy. Back up `ENCRYPTION_KEY` offline: without it the database is unreadable.
5. Open the URL and create the first admin account (the first sign-up becomes admin).
   Then disable open sign-ups in Admin settings.

## 2. Project, key and secret
1. Create a project `switch-phone`, environment `prod`.
2. Add the signing secrets (same names the workflow uses): `KEYSTORE_BASE64`,
   `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
   Keystore to base64: `base64 -w0 release.jks`.
3. Organization > Access Control > Machine Identities > create `github-actions`
   with **Universal Auth**. Add it to the project with read-only access.
   The **Client ID** and **Client Secret** are the key and secret for CI.

## 3. Connect GitHub Actions
Repo secrets: `INFISICAL_CLIENT_ID`, `INFISICAL_CLIENT_SECRET` (the only two stored in GitHub).
Repo variables: `INFISICAL_URL` (`https://...`), `INFISICAL_PROJECT_ID`.

```yaml
- uses: Infisical/secrets-action@v1.0.9
  with:
    method: universal
    client-id: ${{ secrets.INFISICAL_CLIENT_ID }}
    client-secret: ${{ secrets.INFISICAL_CLIENT_SECRET }}
    domain: ${{ vars.INFISICAL_URL }}
    project-slug: switch-phone
    env-slug: prod
```

The action exports the secrets as environment variables; `release.yml` currently reads
the same names from GitHub secrets, so migrating is replacing that source.

## Rotation
Revoke or recreate the Client Secret in Infisical and update the GitHub secret.
Coolify API tokens used during setup should be revoked afterwards.
