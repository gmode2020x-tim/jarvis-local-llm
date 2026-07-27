# Public Release Checklist

Run this before pushing to a public repository:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check_public_release.ps1
git status --short
```

Do not publish:

- `.env` files
- Home Assistant tokens
- SSH keys
- private IP-specific deployment notes
- voice recordings
- generated datasets
- model weights
- runtime memory or notes
- `vm/llm-ui/.env`
- Home Assistant webhook secrets
- temporary screenshots or diagnostics

Safe public files include:

- source code
- empty `.gitkeep` placeholders
- `.env.example`
- documentation with placeholder hostnames
- examples that use placeholder tokens

After the check passes, publish with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\publish_github.ps1 -RepoName jarvis-local-llm -Visibility public
```
