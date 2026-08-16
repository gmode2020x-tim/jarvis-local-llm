# Home Assistant Entity Audit

Jarvis can ground Home Assistant answers when your LLM service has a Home Assistant token and exposes audit endpoints. This repo provides the documentation pattern, not a bundled private Home Assistant configuration.

## Recommended Audit Shape

Expose or record:

- total entity count
- domain counts
- unavailable or unknown entities
- stale entities
- prompt/reply review rows for state-specific questions
- pass/review/error scoring for entity-state answers
- friendly-name and object-ID natural-language coverage

## Example Endpoints

If your LLM service implements a dashboard API, useful endpoints are:

```text
GET http://YOUR-LLM-HOST:8787/api/home-assistant/audit
GET http://YOUR-LLM-HOST:8787/api/prompt-review?limit=50
GET http://YOUR-LLM-HOST:8787/api/integrations
```

## Accuracy Pattern

For voice prompts:

1. Resolve explicit entity IDs such as `sensor.time`.
2. Fall back to friendly-name matching.
3. Fetch live entity state.
4. Send the exact state to the model as authoritative context.
5. Record prompt, reply, matched entity, expected state, route, model, timing, and score.
6. Retry with narrower state context if the first answer misses the expected value.

Favor accuracy over speed until you have a deterministic state-summary route.

The bundled VM service also audits every entity with generated friendly-name and object-ID questions. Explicit-ID coverage alone is not sufficient: it proves that an API can retrieve `sensor.office_temperature`, but not that a person can ask `How warm is the office?` and reach the correct device.
