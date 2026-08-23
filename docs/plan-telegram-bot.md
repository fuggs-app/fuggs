# Plan — Issue #94 via Telegram (first channel)

Same user story as [`plan-whatsapp-bot.md`](./plan-whatsapp-bot.md): a member
sends a receipt into a bot chat, it lands in Fuggs as a `Document`, runs the
existing analysis flow, and the member gets LLM-written confirmations.

Telegram first because it is dramatically cheaper to set up **and** it is the
only one of the two that can satisfy all three acceptance criteria as written.

Mapping is by **Telegram username**, stored in the member's Stammdaten — not by
phone number. See §3 for what that buys and what it costs.

---

## 1. Why Telegram is the better first target

| | WhatsApp Cloud API | Telegram Bot API |
|---|---|---|
| Cost | free tier, paid outside 24h window | **free, no tier** |
| Setup | Business app, System User token, App Secret | **`/newbot` in BotFather → one token** |
| Test audience | max 5 verified numbers | **anyone who opens the chat** |
| Local dev | needs public HTTPS tunnel | **long polling — no tunnel at all** |
| Proactive message days later | pre-approved paid template | **plain `sendMessage`, any time** |
| AC #3 ("bearbeitet … vorgestern") | ⚠️ paid template, conflicts with "no static strings" | ✅ **works natively, fully LLM-generated** |
| Business Verification | realistically required | none |

The decisive row is the second-to-last. WhatsApp forbids free-form text more
than 24h after the user's last message, which is exactly when AC #3 fires — so
on WhatsApp it needs a pre-approved template, i.e. the "statische
vorgefertigte Strings" the issue explicitly rules out. **Telegram has no such
window.** AC #3 becomes an ordinary `sendMessage` with LLM-written German text,
free, days later. No config flag, no compromise.

## 2. What you need to do to enable me

**Two minutes, one secret, no account verification, no tunnel.**

1. Open Telegram, message **@BotFather** → `/newbot`.
2. Give it a display name (e.g. `Fuggs Belege`) and a username ending in `bot`
   (e.g. `fuggs_belege_bot`).
3. BotFather replies with a token like `8123456789:AAF…`. **That's the only
   credential.**
4. Optional polish, also in BotFather: `/setdescription`, `/setuserpic`, and
   `/setprivacy` — leave privacy *enabled*, we only handle 1:1 chats.

Hand me:

```bash
FUGGS_TELEGRAM_BOT_TOKEN=8123456789:AAF…
```

**Also tell me your Telegram @username** so I can seed it onto a `DataSeeder`
member and you can test end-to-end immediately. If you don't have one set:
Telegram → Settings → Username. (That requirement is itself a design
constraint — see §3.)

That's it. No cloudflared, no Meta app, no payment method, no recipient
allow-list. In dev the bot uses **long polling** (`getUpdates`), so it works
from `./mvnw quarkus:dev` on your laptop with no inbound connectivity.

## 3. Mapping by username — trade-offs

**What it buys.** Compared to the phone-number route this removes a lot:
no libphonenumber dependency, no E.164 normalization, no `phone_e164` column,
no "Telefonnummer teilen" reply keyboard, and no forwarded-contact spoofing
check. Onboarding friction drops to zero — a member just sends a receipt and it
works, no button-tapping first. The Bommelwart types `@hugo` into Hugo's
Stammdaten and that's the whole setup.

**Four things it costs, all manageable:**

1. **Usernames are optional in Telegram.** `message.from.username` is absent for
   users who never set one, and plenty haven't. Those members are unidentifiable
   — the bot must reply with an LLM-written "set a Telegram username, or ask
   your Bommelwart" message. This is a **new edge case** the phone route didn't
   have, and it needs its own branch.
2. **Usernames are mutable.** A member can change theirs any time, silently
   breaking the Stammdaten mapping. Mitigated by persisting
   `Member.telegramChatId` on first successful match and preferring it
   afterwards — so a rename only breaks members who never used the bot.
3. **Usernames are case-insensitive** in Telegram (`@Hugo` == `@hugo`). Store
   and compare lowercased, or the mapping fails on capitalisation.
4. **The binding is asserted, not proved.** The incoming `from.username` is
   authentic — Telegram guarantees the message really came from that account.
   What's unverified is whether `@hugo` in the Stammdaten is *the* Hugo. A typo
   or a squatted handle would let a stranger upload receipts into the Verein.
   By contrast the contact-share route had Telegram itself attest the phone
   number. For a Verein where the Bommelwart knows the members personally this
   is the same trust level as typing in an email address — fine for the MVP,
   worth knowing it isn't cryptographic.

**One constraint that mapping choice does *not* remove:** the Bot API **cannot
initiate a chat with a private user by username.** `sendMessage` needs a numeric
`chat_id`, and you only learn it when the user writes to the bot first. So:

- `Member.telegramChatId` is **required**, not a nice-to-have — AC #3 is
  impossible without it.
- A member who has never messaged the bot will never receive an AC #3
  notification. That's inherent to Telegram, not a gap in the design, and it's
  arguably correct behaviour.

**Robustness upgrade for later (not the MVP):** a deep link
`https://t.me/fuggs_belege_bot?start=<one-time-token>` with a "Telegram
verbinden" button per member in the Fuggs UI. That makes the binding *proved*
rather than typed, and sidesteps all four costs above. It needs
`MemberResource` UI work plus a token table, so it's the natural follow-up once
the basic loop works.

## 4. Implementation steps

### Step 1 — Extract document intake from the controller *(no token needed)*

**Identical to the WhatsApp plan — shared work, not throwaway.**

`DocumentResource.createAndPersistDocument` (`DocumentResource.java:272`) reads
`organizationContext.getCurrentOrganization()` and
`securityIdentity.getPrincipal().getName()` straight from the request, and
takes a `FileUpload`. A bot has bytes and a `Member`, not a session.

New `app.fuggs.document.service.DocumentIntakeService`:

```java
@Transactional(TxType.REQUIRES_NEW)
public Long intake(Organization org, String uploadedBy,
                   byte[] content, String fileName, String contentType);
```

Same body as today — `Document`, total `ZERO`, `EUR`, `PENDING`/`UPLOADED`,
`StorageService` upload, persist, `analysisService.triggerAnalysis(...)` — but
org and uploader are arguments. `DocumentResource` becomes a thin adapter that
resolves both from the session and delegates. Existing `DocumentResourceTest`
staying green is the regression signal.

**Why this is unavoidable:** `OrganizationContext.getCurrentOrganization()`
opens with `if (securityIdentity.isAnonymous()) throw new
IllegalStateException(...)`, and *every* org-scoped repository injects it. A
bot update is unauthenticated, so nothing routed through `findByIdScoped` is
reachable on the bot path. The org has to come from the matched `Member`.

### Step 2 — Two columns on `Member` *(no token needed)*

```java
@Column(name = "telegram_username", unique = true, length = 32)
private String telegramUsername;      // stored lowercase, no leading '@'

@Column(name = "telegram_chat_id", unique = true)
private Long telegramChatId;          // captured on first successful match
```

- **Setter normalizes:** strip a leading `@` (Bommelwarts will paste it),
  `trim()`, lowercase. Empty → `null`, so the unique constraint doesn't trip
  over multiple blanks.
- **Validate on input:** Telegram usernames are 5–32 characters, letters,
  digits and underscores, starting with a letter. A `@Pattern` plus a clear
  German validation message in the member form beats a silently non-matching
  bot.
- `MemberRepository.findByTelegramUsername(String)` and
  `findByTelegramChatId(Long)` — both deliberately **unscoped**, following the
  documented precedent of the existing `findByEmail` / `findByKeycloakUserId`.
- **Lookup order in the handler:** `chat_id` first (fast, survives renames),
  falling back to `username` and persisting the `chat_id` on match.
- Multi-org collision: issue #94 puts "Personen, die in mehreren Vereinen sind"
  out of scope. The unique constraints make this unreachable; if a lookup
  somehow returns >1, log `WARN` and treat as *not found* rather than silently
  picking an org.
- **UI:** add the field to `MemberResource` `create.html` and `detail.html`
  next to `phone`, wired through the existing `@RestForm` parameters in
  `MemberResource:95` and `:144`. A short helper text ("Telegram-Benutzername
  ohne @, z. B. hugo_mueller") saves support questions.
- `DataSeeder` gets your username so dev mode works out of the box.

Note this step no longer touches `Member.phone` at all — that stays as it is.
See §6 for what that means for the WhatsApp plan.

### Step 3 — Receiving updates *(needs the token)*

New package `app.fuggs.telegram`.

Two transports, one handler — `TelegramUpdateHandler` holds all logic:

- **Dev: long polling.** A `@Scheduled` (or startup-launched) loop calling
  `getUpdates` with `offset` + `timeout=30`. No public URL, works on your
  laptop. Enabled via `%dev.fuggs.telegram.mode=polling`.
- **Prod: webhook.** `TelegramWebhookResource` at `/api/telegram/webhook`,
  **without** `@Authenticated` (class-level `@Authenticated` is the pattern in
  every existing controller, and `application.properties` has no global
  lockdown — so omitting it makes the path public). Registered via `setWebhook`
  with a **`secret_token`**, which Telegram then echoes in the
  `X-Telegram-Bot-Api-Secret-Token` header — verify it on every request and
  reject mismatches. This endpoint triggers S3 writes and paid LLM calls, so
  that check is not optional.

Handler behaviour:

- **Ack fast, process async.** Telegram redelivers on non-2xx; the ZugFerd →
  Azure chain takes seconds. Return `200` immediately and push work onto the
  async path, same shape as `DocumentFlowService`.
- **Dedupe on `update_id`** so redeliveries don't create duplicate documents.
- **Identify the sender** per the lookup order in step 2. Three outcomes:
  known member → proceed; username present but unknown → AC #1 message;
  **username absent → "please set a Telegram username" message** (the new edge
  case from §3).
- **Photos:** `message.photo` is an array of sizes — take the **last/largest**
  `file_id`. Telegram *recompresses* photos, which costs OCR accuracy, so the
  ack should gently suggest sending PDFs "als Datei" instead.
- **Documents:** `message.document` gives `file_name`, `mime_type`,
  `file_size` — the good path for ZugFerd PDFs, since it's uncompressed.
- **Download is two hops:** `getFile?file_id=…` returns a `file_path`, then
  `GET https://api.telegram.org/file/bot<token>/<file_path>` for the bytes.
- **Size guard:** bots can only download files up to **20 MB**, and
  `az-document-ai` is configured with `quarkus.http.limits.max-body-size=4M`.
  Reject above that with an LLM-written explanation rather than a stack trace.
- Ignore anything that isn't a photo or document for the MVP — stickers, plain
  text, edits, group messages, `my_chat_member` events. (With username mapping
  there are no `contact` messages to handle at all.)

### Step 4 — Sending messages *(needs the token)*

- `TelegramClient` — `@RegisterRestClient(configKey = "telegram")` against
  `https://api.telegram.org`, methods `sendMessage`, `getFile`,
  `sendChatAction`. This mirrors `ZugFerdClient` / `DocumentAiClient` exactly
  and adds **no new dependency**. (I checked Maven Central: there is no
  official Quarkiverse Telegram extension — only Camel Quarkus, far heavier
  than the ~30 lines this needs, and one small third-party lib.)
- Add `quarkus-langchain4j-openai` to `app.fuggs.fuggs-app/pom.xml`. The app
  module currently has **no** LLM dependency — only the two extraction services
  do. Reuse their `gpt-4o-mini` for cost.
- `TelegramMessageService` with a `@RegisterAiService` interface generating the
  German text, same shape as the existing `DocumentTagService`. The issue
  requires *"Alle Nachrichten werden vom LLM generiert, keine statischen
  vorgefertigten Strings"*, so the prompt receives facts (member first name,
  vendor, total, tags, Bommelwart name) and writes the sentence.
- Minimal static fallback if OpenAI is unavailable, logged at `WARN` — dropping
  a member's receipt silently is worse than one off-spec sentence.
- `sendChatAction("upload_document")` while the flow runs gives a free
  "typing…" indicator, covering the multi-second analysis latency.
- Rate limits are ~1 message/sec per chat, ~30/sec overall. Irrelevant at our
  volume, but worth not looping.

**The acceptance criteria, all free and all LLM-generated:**

| AC | Trigger | Hook |
|---|---|---|
| 1 — unknown sender | username not matched to a `Member` | `TelegramUpdateHandler` |
| 1b — no username set | `from.username` absent (new, see §3) | `TelegramUpdateHandler` |
| 2 — upload ack with content | analysis flow reaches `ANALYZED` | after `DocumentAnalysisActivitiesService.completeAnalysis(...)`, where `extractionSource` and the extracted fields land |
| 3 — transaction created | receipt booked by the Bommelwart | `DocumentResource.createTransactionFromDocument`, after `transactionRepository.persist` (line ~821) |

AC #2 must wait for the flow to finish so it can name the vendor ("Kaufland")
and the item — that's the whole charm of the issue's example.

For AC #3, `Document.uploadedBy` needs to carry enough to find the uploading
member back. Today the web path stores the principal name; the bot path should
store the member's `userName` so the same lookup works for both. If that member
has no `telegramChatId`, skip silently — see §3.

### Step 5 — Tests *(no token needed)*

`quarkus-wiremock` and `quarkus-wiremock-test` are already dependencies, so the
whole loop is testable with zero Telegram involvement.

- `DocumentIntakeServiceTest` — org-explicit intake with no session.
- `MemberRepositoryTest` — username normalization matrix: `@Hugo`, `hugo`,
  `  Hugo  `, `""` → null, invalid pattern rejected, unique collision.
- `TelegramUpdateHandlerTest` — captured update fixtures: photo, PDF document,
  unknown username → AC #1, **missing username → AC #1b**, duplicate
  `update_id` → no second document, oversized file → friendly error,
  `chat_id` persisted on first match, renamed username still resolved via
  stored `chat_id`.
- `TelegramWebhookResourceTest` — RESTAssured, including **wrong
  `X-Telegram-Bot-Api-Secret-Token` → 401**.
- WireMock stubs for `getFile`, the file download host, and `sendMessage`.

## 5. Sequencing

Steps 1, 2 and 5 need **nothing from you**. Step 1 carries most of the risk —
it touches the tenancy path every repository depends on. I'd start there while
you run `/newbot`. Steps 3 and 4 light up as soon as the token lands.

## 6. Relationship to the WhatsApp plan

**Step 1 is shared verbatim** — `DocumentIntakeService` is channel-agnostic and
is the single largest piece of work in either plan.

**Step 2 is now Telegram-specific.** Switching to username mapping means this
plan no longer builds the `phone_e164` normalization that the WhatsApp plan
needs, since WhatsApp *does* identify senders by phone number. That's a
deliberate trade: a simpler, faster Telegram MVP in exchange for not
pre-building WhatsApp's member lookup. If WhatsApp happens later it picks up
§4 step 2 of its own plan as fresh work — maybe half a day.

I'd deliberately **not** build a channel abstraction yet. With one channel live
an interface is speculation; once a second transport exists the seam will be
obvious, and by then the intake service and the LLM message service — the parts
actually worth sharing — are already separate beans.

## 7. Decisions

1. **Mapping by Telegram username** in the member's Stammdaten, plus a
   captured `telegram_chat_id`. Costs and mitigations in §3. Deep-link
   verification is the noted follow-up.
2. **LLM fallback:** minimal static German text on OpenAI failure, `WARN`
   logged. Say the word if you'd rather keep the AC pure and send nothing.
3. **Schema:** assuming pre-alpha with no prod data to preserve, so
   `quarkus.hibernate-orm.database.generation=drop-and-create` covers dev/test
   and I write no migration script.
4. **Out of scope**, per the issue: Auslagenmanagement, Stories, members in
   multiple Vereine. Additionally out of scope for the MVP: group chats,
   editing an already-booked receipt from chat, and any conversational
   back-and-forth beyond the acknowledgements.
