# End-to-end tests

Cypress end-to-end specs for the spectator and admin web surfaces. These are not wired into
the default CI job (see `docs/architecture.md`); run them against a seeded environment:

```bash
cd apps/web
pnpm add -D cypress        # one-off; not yet a committed dependency
pnpm exec cypress open
```

Set `CYPRESS_BASE_URL` to the running web app. Specs live in `specs/`.
