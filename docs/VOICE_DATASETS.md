# Voice Dataset Prep

Jarvis includes utilities for preparing local voice datasets. Only use recordings that you own or have permission to use.

## Prepare Source Audio

Place source files in:

```text
data/raw/
```

Optional transcript file:

```csv
file,text
recording.wav,This is the exact text spoken in the file.
```

## Build LJSpeech-Style Output

```powershell
.\.venv\Scripts\python.exe .\scripts\prepare_dataset.py --input .\data\raw --output .\datasets\ljspeech --transcripts .\data\transcripts.csv
```

Without transcripts, use faster-whisper:

```powershell
.\.venv\Scripts\python.exe .\scripts\prepare_dataset.py --input .\data\raw --output .\datasets\ljspeech --transcribe --whisper-model medium
```

Review machine transcripts before training.

## Validate

```powershell
.\.venv\Scripts\python.exe .\scripts\check_dataset.py .\datasets\ljspeech
```

First practical targets:

- 30 to 90 minutes for a rough first voice
- 2 to 6 hours for a stronger single-speaker voice
- one speaker
- clean speech
- clips around 2 to 12 seconds
