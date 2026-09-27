# WhatsApp Bot — How It Works

A member sends a receipt (photo or PDF) to the Fuggs WhatsApp number. It ends
up as a `Document` in `fuggs-app`, runs through the normal analysis pipeline,
and the member gets an LLM-written reply on WhatsApp confirming what
happened.

For the full design rationale and history, see
[`docs/plan-whatsapp-bot.md`](../../docs/plan-whatsapp-bot.md) at the repo
root. This file is the short version.

## Flow

```
Meta Cloud API
     │ POST (webhook push)
     ▼
WhatsAppWebhookResource          (this service, fuggs-bot)
 ├─ verify X-Hub-Signature-256
 ├─ parse payload → find attachment
 ├─ dedupe by WhatsApp message id
 ├─ resolve media URL, download bytes   (WhatsAppClient / WhatsAppMediaDownloader)
 └─ submit(channel, phone, bytes, ...)  (ReceiptIntakeService)
          │ HTTP + shared secret
          ▼
BotDocumentResource               (fuggs-app)
 ├─ resolve Member by phone (per-channel case)
 ├─ create Document, store file, trigger analysis
 └─ BotNotificationService generates reply text (LLM)
          │ HTTP response
          ▼
ReceiptIntakeService polls status → IntakeOutcome
          │
          ▼
WhatsAppClient.sendMessage()  →  reply back to the member on WhatsApp
```

Two services, one job each:

- **`fuggs-bot`** (this service) is transport only: receive, verify,
  download, send. It has no business logic and doesn't know what a
  `Document` is.
- **`fuggs-app`** owns everything else: who the sender is, creating the
  document, running analysis, wording every reply via an LLM (per issue #94,
  no static canned strings).

`ReceiptIntakeService` is the seam between them — channel-agnostic, so a
second channel (e.g. Telegram, on its own branch) plugs in as another
webhook/poller adapter calling the same service, without touching
`fuggs-app` at all.

## Message structure

Meta wraps every event (messages, delivery statuses, account updates) in the
same nested envelope, regardless of channel. This bot only cares about one
path through it — the `image`/`document` on an inbound message:

```
WhatsAppWebhookPayload
└─ entry: [ WhatsAppEntry ]
    └─ changes: [ WhatsAppChange ]          field="messages" is the only one we act on
        └─ value: WhatsAppValue
            └─ messages: [ WhatsAppMessage ]
                ├─ from        sender's E.164 phone, no leading '+' — also the reply address
                ├─ type        "image" | "document" | "text" | ... (only the first two matter)
                ├─ image:    WhatsAppMedia ┐
                └─ document: WhatsAppMedia ┘  attachment() picks document over image (uncompressed)
                                                  ├─ id          media id → resolve download URL
                                                  ├─ mimeType
                                                  └─ filename    present for documents, absent for images
```

Everything else Meta might send (`statuses`, `contacts`, text-only messages,
reactions) deserializes into the same records and is simply ignored —
`@JsonIgnoreProperties(ignoreUnknown = true)` on every level means unknown
fields don't break parsing.

Downloading a file is a two-hop dance the API forces on every caller:
`media.id()` → `GET /{media-id}` → `WhatsAppMediaUrlResponse.url()` (a
short-lived, bearer-token-protected URL) → download the bytes.

Sending a reply is the mirror shape: `WhatsAppSendMessageRequest` /
`WhatsAppTextBody` model the free-form text message body for
`POST /{phone-number-id}/messages`.

## Why this shape

- **One record per JSON shape, not fewer.** The 4-level envelope
  (`payload → entry → change → value → message`) isn't a design choice, it's
  what Meta's API dictates — collapsing levels would mean hand-parsing JSON
  instead of letting Jackson do it, for no benefit.
- **Ack fast, process async.** The webhook returns `200` as soon as the
  signature checks out; the actual download + analysis + reply runs on a
  background virtual thread. Analysis takes seconds — Meta redelivers on a
  slow or non-2xx response, so blocking would cause duplicate work.
- **Dedupe by message id.** A bounded (500-entry) LRU set absorbs Meta's
  redeliveries so a slow analysis doesn't create two documents for one
  receipt.
- **Fail closed on signature verification.** No app secret configured means
  every payload is rejected, not silently accepted — this endpoint is public
  and triggers S3 writes plus paid LLM calls.
- **Transport/business split.** Keeping all Fuggs domain logic in
  `fuggs-app` and only transport in `fuggs-bot` means the bot gateway can
  gain more channels without fuggs-app changing, and fuggs-app's document
  pipeline doesn't need to know WhatsApp exists.
- **LLM-generated replies, with a static fallback only for infra failures.**
  Per issue #94, member-facing text must come from the LLM; the two purely
  technical outcomes (`StillProcessing`, `SubmissionFailed`) are the only
  ones with hardcoded German text, since there's no business fact for an LLM
  to word in those cases.
