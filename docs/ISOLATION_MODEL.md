# Isolation Model

## MVP model
Use module/package boundaries and typed interfaces inside one app process. Do not introduce dynamic code loading, plugin downloads, arbitrary shell execution, or a remote agent runtime for the MVP.

## Trust zones
1. **UI / user input:** untrusted strings and user-selected configuration.
2. **Local meeting data:** private content, accessible only through repository APIs.
3. **Inference engines:** native or model runtimes processing bounded inputs; treat outputs as untrusted.
4. **Optional remote provider:** separate explicit-consent boundary; no access to raw audio unless a future feature explicitly asks and documents it.
5. **Export/share boundary:** user-initiated and previewable.

## Rules
- Validate all data crossing module boundaries.
- Use app-private storage and least privilege.
- Keep API keys out of source control and local preferences that are not protected.
- Do not expose database paths or arbitrary file paths to model output.
- Do not let a transcript or generated text issue tool commands or trigger side effects.
- Cancel and release native inference resources when work ends.
- Separate audio capture from transcript/model processing so capture can stop even if inference hangs.

## Future hard isolation
A separate process is considered only if a concrete security or stability requirement justifies it; it has costs in IPC, memory and lifecycle complexity and is not a Phase 1 default.
