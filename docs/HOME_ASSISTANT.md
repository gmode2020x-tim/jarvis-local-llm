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
