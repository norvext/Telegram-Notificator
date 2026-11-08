# Local Markdown issue tracker

Use one directory per feature under `.scratch/<feature-slug>/`.

- Publish the feature specification as `spec.md`.
- Publish each implementation ticket as a separate `issues/<NN>-<slug>.md` file. Number tickets from `01`.
- Record triage state in a `Status:` line near the top. Use the [status mapping](triage-labels.md).
- Append discussion under `## Comments`.
- Fetch a ticket by reading its path or finding its number within the relevant feature.

## Wayfinding

- Keep the map in `.scratch/<effort>/map.md` and child tickets in `issues/<NN>-<slug>.md`.
- Record ticket type as `Type: research`, `prototype`, `grilling`, or `task`.
- Record dependencies as `Blocked by: NN, NN`. Choose the first open, unclaimed ticket by number whose dependencies are resolved.
- Set `Status: claimed` before working. To resolve, append `## Answer`, set `Status: resolved`, and link the answer from the map's decisions.
