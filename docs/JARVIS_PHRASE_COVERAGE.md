# Jarvis Home Assistant Phrase Coverage

Jarvis accepts ordinary Home Assistant questions without requiring people to know entity IDs. Every response follows the same character contract: concise, precise, conversational, confidently sarcastic, addressed naturally by the configured user name, and grounded in current data. Jarvis says when data is missing, ambiguous, conflicting, or outside the authorized read-only route.

## Common Question Families

| Family | Example phrases | Expected behavior |
| --- | --- | --- |
| Named entity state | `Is the right garage door open?`, `What is the office temperature?` | Resolve the best domain-compatible entity and report its live state without a forced personality suffix. |
| Room conditions | `How warm is the living room?`, `What is the basement humidity?` | Prefer temperature, humidity, air-quality, or other matching sensor classes in that room. |
| Lights | `Which lights are on?`, `Are any lights still on?` | Summarize matching lights across the home. |
| Doors, locks, and covers | `Is the front door locked?`, `Are any doors open?` | Report state; call out unavailable or conflicting entities instead of guessing. |
| Batteries | `Are any batteries low?`, `How much charge does the garage sensor have?` | Find battery-class sensors and summarize low or named-device charge. |
| Updates | `Are any updates available?`, `What needs updating?` | Summarize update entities currently reporting an available update. |
| Cameras and media | `How many cameras do I have?`, `What is the family room TV playing?` | Count consolidated camera devices or report a named media entity without treating a whole-home count as an ambiguous device request. |
| Climate | `What is the thermostat set to?`, `Is the heat running?` | Report HVAC mode, current temperature, or target temperature when available. |
| Presence | `Is anyone home?`, `Where is my phone?` | Summarize authorized person or device-tracker state without inventing location detail. |
| Weather | `What is the weather at home?`, `Will it rain today?` | Use the current Home Assistant weather entity and available forecast attributes. |
| Calendar | `What is next on my calendar?`, `Do I have anything today?` | Use current calendar state and event attributes. |
| History | `What happened in the last 48 hours?`, `Was the garage opened today?` | Use Home Assistant history for the requested entity and time range. |
| General assistance | `Jarvis`, `Who are you?`, `What time is it?`, `Thank you`, `Good night`, `How are you?` | Use rotated deterministic Jarvis replies for speed, direct name use, and consistent personality. |
| Control request | `Turn on the kitchen lights.` | State that control is not enabled unless a separate authorized control workflow exists. |

Natural variants such as `forty eight hours`, `48 hours`, and `48 hrs` should resolve to the same history request. Polite prefixes, contractions, and common question forms are normalized before matching.

## Quick Response Rotation

Quick replies are grouped by intent so a greeting cannot accidentally produce a calendar or status answer. Each delivered reply records only its category key, variant key, and timestamp. For the next seven days, that key is excluded while unused replies remain in the category; if a category is exhausted, Jarvis reuses the least-recently heard variant. The history survives an LLM UI restart and contains no transcript, Home Assistant state, or personal message content.

The configured user name is rendered at delivery time. Response templates stay portable, while the active household assistant can address its user naturally without falling back to `sir`.

## Resolution Order

1. Handle deterministic common-assistance questions.
2. Detect whole-home summaries such as lights, low batteries, and updates.
3. Resolve an explicit `domain.entity_id` when present.
4. Rank friendly-name and object-ID aliases while respecting device domain, device class, room, and directional words.
5. Ask a short clarification when plausible matches remain tied.
6. Use the model only when no deterministic Home Assistant answer applies.

This separation matters. A 100% explicit-ID audit proves that every entity can be fetched by its technical ID; friendly-name and object-ID audits prove that natural phrases can reach those same entities.

## Response Rules

- Live Home Assistant state is authoritative.
- Conflicting duplicate entities are listed rather than silently selecting one.
- `unknown` and `unavailable` are reported plainly.
- Read-only Assist routes never imply that a requested action succeeded.
- Model-backed replies use the same Jarvis persona as deterministic replies.
- Short voice answers lead with the result and avoid generic assistant filler.
- Direct address uses `JARVIS_USER_NAME`; formal honorifics are not the default.
- The configured name is occasional direct address, not a compulsory ending on every response.
- Sarcasm is conversational but optional. It is never cruel, personal, repeated as a canned suffix, or allowed to obscure a safety warning or factual state.
- Travel, steps, counts, temperatures, and other routine facts normally receive a natural one-sentence answer.

## Dashboard

Open the VM dashboard and select **Home Assistant**. The dashboard reports:

- connection, entity, and domain counts;
- explicit entity-ID coverage;
- friendly-name and domain-qualified object-ID coverage;
- natural-language sample misses;
- unknown or unavailable state counts;
- recent answer scoring and surfaced conflicts;
- supported phrase families and the active Jarvis response contract.

The full entity audit reads the current Home Assistant inventory and can take several seconds. The rest of the dashboard loads independently; **Refresh audit** shows an in-progress state until the audit finishes.

## Verification

Run the offline language suite:

```bash
cd vm/llm-ui
npm run verify:language
```

On a configured VM, inspect the live audit:

```text
GET http://YOUR-LLM-HOST:8787/api/home-assistant/audit
```

Check `resolverCoverage`, `languageCoverage.friendlyName`, `languageCoverage.objectId`, and `languageCoverage.sampleMisses`. Replay important spoken phrases through the actual Home Assistant Assist path; an audit is strong coverage evidence, but it is not a substitute for end-to-end voice verification.
