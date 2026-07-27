# Jarvis Wake Word

Target phrase: `hey jarvis`

Home Assistant can use a public openWakeWord model or a custom model that you train.

## Public Model

Check the openWakeWord catalog from Home Assistant first. If a `hey_jarvis` model is available, no custom model is required.

## Optional Custom Model

Train a replacement only if you want a private or locally tuned wake word:

```text
https://openwakeword.com/train
```

Recommended settings:

- Wake word: `hey jarvis`
- Keep private: enabled if offered
- Download the generated `.tflite` output

## Install A Replacement Model

Run this from the Jarvis workspace after downloading a custom `.tflite` model:

```powershell
.\.venv\Scripts\python.exe .\scripts\install_jarvis_wake_word.py C:\Path\To\hey_jarvis.tflite --model-dir \\homeassistant\share\openwakeword --pipeline-file \\homeassistant\config\.storage\assist_pipeline.pipelines --wake-word-id hey_jarvis
```

Then restart or reload the openWakeWord add-on and the Home Assistant voice pipeline. If Home Assistant exposes the custom wake word with a different id, rerun the script with the exact id.
