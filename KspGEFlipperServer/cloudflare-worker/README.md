# KSP GE Flipper Cloudflare Worker relay

This Worker is an authenticated relay between `KspGEFlipper` and a deployed Java `KspGeServer`. It does **not** run the Java server. Host the Java server (and PostgreSQL, if used) on a VM/container platform, then expose it through an HTTPS origin such as a Cloudflare Tunnel or a reverse proxy.

The Worker uses two different secrets:

- `CLIENT_API_KEY`: configured in the plugin; validates requests at the Worker.
- `ORIGIN_API_KEY`: the Java server's `KSP_API_KEY`; injected by the Worker and never placed in the plugin.

`ORIGIN_URL` must be the HTTPS base URL of the Java server. The Worker only exposes the documented API routes and never caches requests or responses.

## Deploy

From this directory, install dependencies and authenticate with Cloudflare:

```powershell
npm install
npx wrangler login
```

Generate two distinct random values, set the Java server's `KSP_API_KEY` to the origin value, and add all three Worker secrets. Each `secret put` command prompts for its value:

```powershell
npx wrangler secret put ORIGIN_URL
npx wrangler secret put CLIENT_API_KEY
npx wrangler secret put ORIGIN_API_KEY
npm run deploy
```

Set the plugin's **Backend URL** to the `https://<worker>.<subdomain>.workers.dev` URL printed by deployment and its **Backend API key** to `CLIENT_API_KEY`. Do not use the Java server URL or `ORIGIN_API_KEY` in the plugin.

For local Worker testing, copy `.dev.vars.example` to `.dev.vars`, supply real values, and run `npm run dev`. Keep `.dev.vars` out of source control.

## Verify

```powershell
curl.exe -i -H "X-KSP-API-Key: <CLIENT_API_KEY>" https://<worker-url>/health
```

Expect HTTP 200 with `"marketReady": true` once the Java server has fetched market data. A 502 means the Worker cannot reach its configured origin; a 401 means the plugin key is incorrect.

## Security notes

- Keep the Java server reachable only through its HTTPS origin path; do not expose port 8181 publicly without independent network protection.
- Rotate `CLIENT_API_KEY` if a plugin configuration is shared and rotate `ORIGIN_API_KEY` if the server credential is exposed.
- The current plugin uses one shared static key. If individual user identities or revocable per-client keys are required, add an authentication service before distributing the plugin.
