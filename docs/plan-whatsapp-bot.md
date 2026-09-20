# Plan — Issue #94: WhatsApp Bot

> **Status: implemented**, dev-tested end-to-end (webhook verified, real
> receipt sent from a WhatsApp test number, document created in Fuggs,
> LLM-generated reply received). This document originally described a plan
> where the bot logic lived inside `fuggs-app`'s `DocumentResource`; the
> actual implementation instead extended a **bot-gateway architecture**
> (`app.fuggs.fuggs-bot`) that was first built for a Telegram channel on a
> separate branch. Everything below reflects what was actually built for
> WhatsApp, not the original plan.
>
> **Telegram lives on its own branch** (`feature/telegram-bot`), not here -
> this repository state is WhatsApp-only, per the scope of issue #94. The bot
> gateway is still structured so re-adding Telegram (or any other channel) is
> "one more `case`" in the classes below, not a rewrite.
>
> **For the Meta/operational setup** (creating the app, the four env vars,
> the webhook tunnel, and the gotchas we hit getting it running) see
> [`app.fuggs.fuggs-bot/README.md`](../app.fuggs.fuggs-bot/README.md). This
> document is about the design.

Goal: a member sends a receipt photo/PDF into a WhatsApp chat with the Fuggs
bot; it lands in Fuggs as a `Document`, runs through the existing analysis
flow, and the member gets LLM-written confirmations.

---

## 1. Architecture: a channel added to a reusable bot gateway

A Telegram implementation built earlier (`feature/telegram-bot` branch)
already established a shape that turned out to need almost no changes to add
a second channel:

- **`app.fuggs.fuggs-bot`** is a separate microservice (its own Quarkus app,
  port 8104), not code inside `fuggs-app`. It owns transport only - receiving
  updates, downloading attachments, sending replies - for each channel.
- **`app.fuggs.document.api.BotDocumentResource`** on `fuggs-app` is the one
  machine-to-machine endpoint every channel submits through
  (`POST /api/bot/documents`, guarded by a shared secret, not
  `@Authenticated` since the caller has no user session). It takes a generic
  `channel` + `senderIdentifier` pair and resolves the member per channel -
  WhatsApp by phone number today - via one more `case` in `resolveMember`.
- **`app.fuggs.bot.document.ReceiptIntakeService`** (in fuggs-bot) is
  channel-agnostic: submit bytes, poll fuggs-app for the analysis outcome,
  return a sealed `IntakeOutcome` (`Success`, `AnalysisFailed`,
  `UnknownSender`, `StillProcessing`, `SubmissionFailed`). Neither this nor
  `BotDocumentResource` needed a single change for WhatsApp.
- **`app.fuggs.messaging.BotMessageService`** (fuggs-app, `@RegisterAiService`
  over DeepSeek) generates every member-facing sentence, per issue #94's
  "keine statischen vorgefertigten Strings" requirement. Channel-agnostic,
  unchanged for WhatsApp.

**What WhatsApp actually added**, all under `app.fuggs.bot.whatsapp` in
fuggs-bot:

| Piece | Purpose |
|---|---|
| `WhatsAppConfig` | access token, phone number id, app secret, verify token, `isUsable()` |
| `WhatsAppClient` (`@RegisterRestClient`) | Graph API: get phone number info, resolve a media URL, send a text message |
| `WhatsAppMediaDownloader` | plain `HttpClient` two-hop media download (bearer-token-protected URL, not a fixed path template) |
| `WhatsAppWebhookResource` | `/api/whatsapp/webhook` - verify handshake (`GET`), receive + verify + dispatch (`POST`) |
| `WhatsAppConnectivityService` + dev-only `WhatsAppPingResource` | startup/on-demand credential check against the Graph API |
| `model/*` | records for the webhook payload, media, outbound message |

Plus one `case` each in `BotDocumentResource.resolveMember` and
`NotificationResource`/`BotNotificationService`'s channel dispatch.

## 2. Member identification: WhatsApp phone reuses `Member.phone`

The original plan proposed a new `Member.phoneE164` column, separate from the
existing free-text `phone` field. **That turned out to be unnecessary** - a
WhatsApp number and a member's general contact phone number are the same
real-world number in the overwhelming majority of cases, so there's no reason
to make a Bommelwart type it twice.

What actually shipped:

- `Member.setPhone(String)` derives `whatsappPhoneE164` (E.164, no leading
  `+`, matching how WhatsApp identifies senders) as a side effect, using
  **libphonenumber** (`com.googlecode.libphonenumber:libphonenumber`) with
  region `DE` as the default for numbers entered without a country code.
- `whatsappPhoneE164` is a separate, indexed, unique **column** (not shown in
  any form) purely so lookups don't need to normalize the whole table on
  every request - `phone` stays the single field a Bommelwart sees and edits,
  with its helper text now noting it's also used for the WhatsApp bot.
- `MemberRepository.findByWhatsAppPhoneE164` is deliberately unscoped, same
  documented precedent as `findByEmail` / `findByKeycloakUserId`.
- A real normalization gotcha hit during implementation: libphonenumber's
  "default region" only applies when a number has **no** leading `+` - it
  never guesses that such a number might *already* carry a country code.
  WhatsApp's own sender ids are exactly that shape (E.164 digits without `+`,
  e.g. `4917012345678`), so naively parsing that against region `DE` silently
  double-prepends the country code (`4917012345678` → `494917012345678`,
  syntactically "valid" per libphonenumber's lenient length check, but wrong).
  Fixed by trying the international (`+`-prefixed) interpretation first,
  falling back to local-format parsing only if that fails.

Multi-org collision (a member in more than one organization) is out of scope
per issue #94, and the unique constraint on `whatsapp_phone_e164` makes a
duplicate match unreachable in practice.

## 3. The webhook

A polling-based channel needs no public endpoint at all. **Meta's Cloud API
has no polling transport, though** - it only ever pushes to a
subscribed, publicly reachable HTTPS URL, which is why WhatsApp needs a
webhook and, in dev, a tunnel (`cloudflared`).

`WhatsAppWebhookResource`:

- **`GET`** - Meta's subscription handshake: echoes `hub.challenge` when
  `hub.verify_token` matches the configured value; rejects (403) otherwise.
- **`POST`** - verifies `X-Hub-Signature-256` (HMAC-SHA256 of the *raw* body
  keyed with the app secret, constant-time compared) before parsing anything.
  An unconfigured secret **fails closed** - every payload is rejected rather
  than verification being skipped, since this endpoint is public and triggers
  S3 writes plus paid LLM calls.
- Acks with `200` immediately, then processes each attachment on a background
  virtual thread - the ZugFerd/AI analysis chain takes seconds, and Meta
  redelivers on a slow or non-2xx response.
- **Dedupes on the WhatsApp message id** via a bounded (500-entry) LRU set,
  so redeliveries don't create duplicate documents.
- Picks a `document` attachment over an `image` when both would be present
  (documents arrive uncompressed); ignores text, reactions, `statuses`
  (delivery receipt) events, and anything without an attachment.
- **Size guard at 4 MB** - not a WhatsApp limit (their documents allow up to
  100 MB) but `az-document-ai`'s own `quarkus.http.limits.max-body-size=4M`.
  Rejected with a friendly German message rather than a stack trace.

One operational subtlety worth documenting here since it cost real debugging
time: **verifying the webhook URL and subscribing to the `messages` field is
not sufficient**. The WhatsApp Business Account also has to be explicitly
subscribed to the app via `POST /{waba-id}/subscribed_apps` - a step Meta's
dashboard does not reliably do automatically. Symptom when it's missing:
verification succeeds, Meta's dashboard "send test message" button works, but
real inbound messages never reach the webhook at all, silently. See the
README's troubleshooting table.

## 4. The three acceptance criteria

`BotNotificationService` (fuggs-app) generates all three via
`BotMessageService`, with a static German fallback logged at `WARN` if the
LLM call fails (a member losing their confirmation is worse than one
off-brand sentence):

| AC | Trigger |
|---|---|
| 1 - unknown sender | phone not found via `findByWhatsAppPhoneE164` |
| 2 - upload ack incl. content | `DocumentAnalysisActivitiesService.completeAnalysis(...)` reached, surfaced via the status-poll response |
| 3 - transaction created | `DocumentResource.createTransactionFromDocument`, after `transactionRepository.persist`, via `BotNotificationService.resolveNotificationTarget` |

WhatsApp needs no separate "push address" captured from the inbound message -
the phone number that identifies the sender **is** the address used to
message them back. `IntakeRequest.pushAddress` stays a reserved, unused field
for the `"whatsapp"` channel; a channel that can't be reached by its inbound
identifier alone (e.g. one that needs a numeric chat id) would populate it.

## 5. The one thing that still isn't free - unchanged from the original plan

WhatsApp's 24h free-form-messaging window is a hard platform rule, not
something this implementation works around: **AC #3 fires exactly when it's
usually violated** (a Bommelwart booking a receipt days later). This part of
the original plan's analysis held up and nothing was built to route around
it - see the original decision below.

## 6. Decisions

1. **Bot logic lives in `app.fuggs.fuggs-bot`, not `fuggs-app`.** Supersedes
   the original plan's `DocumentIntakeService`-in-`DocumentResource`
   approach - superseded because Telegram had already built and proven the
   gateway-microservice shape first; extending it turned out to need far
   less new code than the originally-planned direct integration.
2. **WhatsApp identity reuses `Member.phone`**, derived automatically, no
   separate form field. See §2 - a simplification made after initially
   building (and then removing) a dedicated `whatsappPhone` field, on the
   reasoning that the two are the same number for virtually every member.
3. **AC #3 stays unimplemented for the paid, outside-24h, template-message
   case.** Building the free-form path (works when a receipt is booked
   within 24h of upload) was in scope and shipped; the template variant -
   which conflicts with "keine statischen vorgefertigten Strings" and needs a
   payment method plus realistically Business Verification - stays
   documented but unbuilt, exactly as originally decided. No config flag was
   added since there was nothing to flag off.
4. **LLM fallback:** minimal static German text on OpenAI/DeepSeek failure,
   logged at `WARN` - shipped as originally decided in `BotNotificationService`.
5. **Schema:** no migration was written -
   `quarkus.hibernate-orm.database.generation=drop-and-create` covers dev/test
   as assumed. Still true: say the word if a database needs to survive a
   deploy before this ships anywhere with persistent data.
