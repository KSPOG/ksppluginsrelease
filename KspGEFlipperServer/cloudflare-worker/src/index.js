/**
 * Authenticated Cloudflare Worker relay for the KSP GE Flipper Java API.
 *
 * CLIENT_API_KEY is accepted from the plugin. ORIGIN_API_KEY is injected only
 * on the request from this Worker to the Java server.
 */

const ALLOWED_ROUTES = [
  { pattern: /^\/health$/, methods: ["GET"] },
  { pattern: /^\/v1\/account$/, methods: ["POST"] },
  { pattern: /^\/v1\/recommendation$/, methods: ["POST"] },
  { pattern: /^\/v1\/recommendations\/[0-9a-f-]{36}$/i, methods: ["GET"] },
  { pattern: /^\/v1\/transactions$/, methods: ["GET", "POST"] },
  { pattern: /^\/v1\/outcomes$/, methods: ["GET", "POST"] },
  { pattern: /^\/v1\/offers$/, methods: ["POST"] },
  { pattern: /^\/v1\/prices\/\d+$/, methods: ["GET"] },
  { pattern: /^\/v1\/dumps$/, methods: ["GET"] },
  { pattern: /^\/v1\/events\/dumps$/, methods: ["GET"] },
  { pattern: /^\/v1\/portfolio$/, methods: ["GET"] },
  { pattern: /^\/v1\/metrics$/, methods: ["GET"] },
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (!isAllowed(url.pathname, request.method)) {
      return json({ error: "not_found" }, 404);
    }

    if (!env.ORIGIN_URL || !env.CLIENT_API_KEY || !env.ORIGIN_API_KEY) {
      return json({ error: "relay_not_configured" }, 503);
    }

    if (!constantTimeEqual(request.headers.get("X-KSP-API-Key"), env.CLIENT_API_KEY)) {
      return json({ error: "unauthorized" }, 401);
    }

    let origin;
    try {
      origin = new URL(env.ORIGIN_URL);
    } catch {
      return json({ error: "relay_not_configured" }, 503);
    }

    if (origin.protocol !== "https:") {
      return json({ error: "origin_must_use_https" }, 503);
    }

    origin.pathname = joinPath(origin.pathname, url.pathname);
    origin.search = url.search;

    const headers = new Headers(request.headers);
    headers.set("X-KSP-API-Key", env.ORIGIN_API_KEY);
    headers.set("X-Forwarded-Proto", "https");
    headers.delete("Host");
    headers.delete("CF-Connecting-IP");

    try {
      const response = await fetch(origin.toString(), {
        method: request.method,
        headers,
        body: request.method === "GET" ? undefined : request.body,
        redirect: "manual",
      });
      const responseHeaders = new Headers(response.headers);
      responseHeaders.set("Cache-Control", "no-store");
      responseHeaders.set("X-Content-Type-Options", "nosniff");
      return new Response(response.body, { status: response.status, headers: responseHeaders });
    } catch {
      return json({ error: "origin_unavailable" }, 502);
    }
  },
};

function isAllowed(pathname, method) {
  return ALLOWED_ROUTES.some((route) => route.pattern.test(pathname) && route.methods.includes(method));
}

function joinPath(basePath, requestPath) {
  return `${basePath.replace(/\/$/, "")}${requestPath}`.replace(/^([^/])/, "/$1");
}

function constantTimeEqual(actual, expected) {
  if (typeof actual !== "string" || actual.length !== expected.length) return false;
  let mismatch = 0;
  for (let i = 0; i < actual.length; i += 1) mismatch |= actual.charCodeAt(i) ^ expected.charCodeAt(i);
  return mismatch === 0;
}

function json(value, status) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
