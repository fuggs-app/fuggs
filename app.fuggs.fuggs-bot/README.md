# fuggs-bot

Chat bot gateway (WhatsApp) for uploading receipts into Fuggs. A member sends
a receipt photo/PDF into a chat, this service forwards it to `fuggs-app`'s
internal document intake API, and relays the (LLM-generated) reply back to
the member.

This service is transport-only: it knows how to receive/send messages on each
channel, but all business logic (member lookup, document creation, analysis,
message wording) lives in `fuggs-app` and is reached over HTTP with a shared
secret (`fuggs.bot.shared-secret` / `FUGGS_BOT_SHARED_SECRET`, blank on both
sides by default so local dev works without setting it).

## Running in dev mode

```bash
./mvnw quarkus:dev
```

Runs on **port 8104**. `fuggs-app` must also be running (default port 8080) -
this service calls it for every document submission and status check.

> **_NOTE:_** Dev UI is available at <http://localhost:8104/q/dev/>.

This gateway is deliberately structured so a second channel is "one more
`case`" rather than a rewrite - see the architecture note in
`docs/plan-whatsapp-bot.md` §1 if you're adding one back.

---

## WhatsApp setup

WhatsApp is more involved: it requires a Meta developer app, a webhook (no
polling transport exists), and therefore a public HTTPS tunnel in dev.

### 1. Create the Meta app

1. <https://developers.facebook.com/apps> → **Create App** → type **Business**
   → add the **WhatsApp** product.
2. In the app dashboard, open **"Connect on WhatsApp"** (may show under a
   different label depending on Meta's current UI) → **"Step 1. Try it out"**.
   This page gives you, for free, a **test phone number** you can message and
   send from, with no business verification and no payment method required.

### 2. Where to find each of the four required values

| Env var | Where to find it |
|---|---|
| `FUGGS_WHATSAPP_TOKEN` | Same "Step 1. Try it out" page → **Access token** → **Generate token**. ⚠️ This temporary token **expires after ~24h**. For a token that doesn't expire, create a System User instead: **Business Settings → Users → System Users → Add** → grant it `whatsapp_business_messaging` + `whatsapp_business_management` on this app → **Generate token** → expiry **Never**. |
| `FUGGS_WHATSAPP_PHONE_NUMBER_ID` | Same page, shown next to the test number (a numeric id). Doesn't expire. |
| `FUGGS_WHATSAPP_APP_SECRET` | App dashboard → **App settings → Basic** → **App secret** → click "Show". Doesn't expire. |
| `FUGGS_WHATSAPP_VERIFY_TOKEN` | Not from Meta - **you invent this string yourself** (any value works, e.g. a random word or UUID). You'll type the exact same value into Meta's webhook config in step 6. |

Also note your **WhatsApp Business Account ID (WABA ID)** from the same
page - you'll need it in step 7, but it isn't an env var.

### 3. Export the four vars and start the service

```bash
export FUGGS_WHATSAPP_TOKEN=<from step 2>
export FUGGS_WHATSAPP_PHONE_NUMBER_ID=<from step 2>
export FUGGS_WHATSAPP_APP_SECRET=<from step 2>
export FUGGS_WHATSAPP_VERIFY_TOKEN=<a string you invent>

cd app.fuggs.fuggs-bot && ./mvnw quarkus:dev
```

**Important:** `export` only persists in the terminal session it was run in.
If you close that terminal, restart the dev session, or the temporary token
expires, you have to re-export and restart - a silent `503` / "not
configured" response on the endpoints below is the usual symptom.

### 4. Make your phone number a Fuggs member

Either add it manually via `fuggs-app`'s `/mitglieder` UI (any phone number
format works, it's normalized automatically), or, for local dev, export this
on **`fuggs-app`** before starting it, so `DataSeeder` seeds it onto the demo
member automatically every restart:

```bash
export FUGGS_DEV_WHATSAPP_PHONE="+4917012345678"   # your test number
```

Note: on WhatsApp's test tier, a phone can only be reached at all if it's also
registered as one of the up-to-5 verified recipients on Meta's "Try it out"
page (each confirmed via an SMS/WhatsApp code) - do that first if you haven't.

### 5. Sanity check - no tunnel needed yet

```bash
curl http://localhost:8104/api/whatsapp/ping
```

Should return `{"connected": true, ...}` with your test number's info. This
proves the token + phone number id are valid before you touch anything else.

### 6. Expose the webhook publicly

Meta's Cloud API only ever pushes to a public HTTPS URL - there is no polling
transport, so a tunnel is required even in dev:

```bash
brew install cloudflared   # once
cloudflared tunnel --url http://localhost:8104
```

Copy the printed `https://xxxx.trycloudflare.com` URL. **This URL changes
every time you restart `cloudflared`** (the free tier issues a random
hostname) - you'll need to redo steps 7-9 whenever that happens. For a stable
hostname across restarts, set up a free Cloudflare account and a *named*
tunnel instead.

### 7. Allow the tunnel through Quarkus dev mode's own protections

Quarkus dev mode rejects any request whose `Host` header isn't `localhost`,
as DNS-rebinding protection - which blocks Meta's webhook calls through the
tunnel. `application.properties` already disables the app-wide check for dev
(safe, since the webhook itself independently verifies Meta's HMAC signature
on every POST regardless of Host header):

```properties
%dev.quarkus.http.host-validation.require-localhost=false
```

Dev UI has its own, separate localhost-only filter that still needs the
current tunnel hostname explicitly. Export it (no scheme, no path) and
restart whenever the tunnel URL changes:

```bash
export FUGGS_DEV_TUNNEL_HOST=xxxx.trycloudflare.com
```

Verify both requests get through before touching Meta:

```bash
curl https://xxxx.trycloudflare.com/api/whatsapp/ping
```

### 8. Configure the webhook in Meta

App dashboard → **"Connect on WhatsApp"** → **"Step 2. Production setup"** →
**Configure Webhooks**:

- **Callback URL:** `https://xxxx.trycloudflare.com/api/whatsapp/webhook`
- **Verify token:** exactly your `FUGGS_WHATSAPP_VERIFY_TOKEN` value
- Click **Verify and save**
- Subscribe to the **`messages`** field (a separate toggle/checkbox from
  verifying - easy to miss, and required for anything to arrive at all)

### 9. Subscribe your app to the WhatsApp Business Account

**This step is easy to miss and Meta's dashboard does not always do it for
you.** Verifying the webhook and subscribing to `messages` only wires your
*app* up; the WABA also needs to be told to actually push events to that app.
Symptom if this is missing: webhook verification succeeds, Meta's dashboard
"test" button works, but real inbound WhatsApp messages never reach your
webhook at all - silently.

```bash
curl -X POST "https://graph.facebook.com/v21.0/<WABA_ID>/subscribed_apps" \
  -H "Authorization: Bearer $FUGGS_WHATSAPP_TOKEN"
```

Expect `{"success": true}`. (A follow-up `GET` on the same URL to double-check
may fail with `"(#200) Provide valid app ID"` using this token - that's a
separate, unrelated permission quirk on the read endpoint; it doesn't mean the
subscribe failed.)

### 10. Send a test receipt

From the phone number that is both a verified Meta test recipient (step 4)
and a Fuggs member (step 4), open WhatsApp, message the test number, and
attach a receipt photo or PDF.

Watch the fuggs-bot terminal for:

```
WhatsApp attachment received: id=..., from=..., mediaId=...
```

You should get an LLM-generated reply within a few seconds, and the document
should appear at `http://localhost:8080/belege`.

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| `curl .../api/whatsapp/ping` → `503`, `"No WhatsApp phone number id configured"` | Env vars not exported in the terminal that's running this service, or it wasn't restarted after exporting |
| `curl` through the tunnel → `Couldn't resolve host` | Tunnel died/restarted - get the new URL and redo steps 7-9 |
| Dev UI errors: `LocalHostOnlyFilter ... unexpected host` | `FUGGS_DEV_TUNNEL_HOST` not exported, stale (tunnel restarted), or the service wasn't restarted after exporting it |
| Meta "Verify and save" fails | Verify token mismatch (whitespace, wrong field), or Callback URL missing the `/api/whatsapp/webhook` path |
| Verification succeeds, dashboard test message works, but real messages never trigger anything | Step 9 (subscribe app to WABA) was skipped |
| Reply is a generic fallback ("Beleg erhalten"/"Danke, dein Beleg wurde hochgeladen") instead of a proper LLM sentence | `DEEPSEEK_API_KEY` not set on `fuggs-app` - soft failure, the receipt is still processed |
| Access token suddenly stops working after ~24h | Temporary token expired - regenerate, or switch to a permanent System User token (see step 2) |

See `docs/plan-whatsapp-bot.md` for the full design rationale, including why
templates/paid messaging outside the 24h window are intentionally
unimplemented.
