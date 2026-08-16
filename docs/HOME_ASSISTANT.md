# Home Assistant Integration

Jarvis can be used beside Home Assistant in two ways:

- Home Assistant calls a Jarvis-compatible webhook or LLM service for Assist responses.
- Jarvis scripts inspect or push Home Assistant-adjacent data, such as wake-word models or camera/event summaries.

This repo does not include Home Assistant credentials. Keep tokens in Home Assistant secrets, `.env`, or your service manager.

## Wake Word

See [JARVIS_WAKE_WORD.md](JARVIS_WAKE_WORD.md).

## Assist Pipeline Pattern

Recommended shape:

1. Home Assistant handles wake word and speech-to-text.
2. A webhook sends recognized text to your LLM host.
3. The LLM host calls Jarvis or another local model route.
4. Home Assistant handles text-to-speech.

![Jarvis request flow](assets/request-flow.svg)

## Example REST Command

See `examples/home-assistant-rest-command.yaml`.

Never commit Home Assistant long-lived access tokens.

## Jarvis VM Webhook

The included VM service exposes:

```text
POST http://YOUR-LLM-HOST:8787/api/home-assistant/webhook/YOUR_SECRET
POST http://YOUR-LLM-HOST:8787/api/assist
```

The webhook form is preferred because the secret is part of the URL and can be rotated in `/opt/jarvis/vm/llm-ui/.env`. The `/api/assist` alias requires the same secret through `Authorization: Bearer YOUR_SECRET`, `X-Jarvis-Secret`, or a `secret` JSON field when `HOME_ASSISTANT_WEBHOOK_SECRET` is set.

Example payload:

```json
{
  "source": "assist",
  "text": "What is sensor.office_temperature?",
  "speak": true
}
```

To let Jarvis speak through Home Assistant, set:

```dotenv
HOME_ASSISTANT_URL=http://homeassistant.local:8123
HOME_ASSISTANT_TOKEN=YOUR_LONG_LIVED_ACCESS_TOKEN
HOME_ASSISTANT_WEBHOOK_SECRET=YOUR_LONG_RANDOM_SECRET
JARVIS_TTS_ENTITY=tts.piper
JARVIS_DEFAULT_SPEAKER=media_player.YOUR_SPEAKER
```

## Natural Phrase Coverage

Assist questions do not need to contain an entity ID. The VM service builds aliases from each entity's current friendly name, object ID, domain, and device class, then ranks only plausible matches. This supports phrases such as:

- `What is the living room humidity?`
- `How much charge does the garage sensor have?`
- `Is the left garage door open?`
- `What is the family room TV playing?`
- `When is the next automatic backup?`
- `Which lights are on?`
- `Are any batteries low?`
- `Are any updates available?`

Directional words and room names are weighted so similarly named devices do not silently replace one another. Ambiguous questions produce a short clarification, and unavailable entities are reported honestly. General questions that happen to share a word with an entity are not forced into the Home Assistant route.

The included route is read-only for natural-language device questions. A request such as `Turn on the kitchen lights` receives an honest capability response unless you deliberately add and authorize a control workflow.

All deterministic and model-backed voice answers use the same Jarvis persona: concise, precise, directly addressed, lightly dry, and grounded in live data.

Run the offline phrase suite from `vm/llm-ui`:

```bash
npm run verify:language
```
