# Content acquisition and normalization

Contract: `docs/integration/CONTENT-IMPORT-SPEC.md`. This adapter fetches only observed read-only routes of the fixed Shkolkovo HTTPS origin. It does not accept arbitrary destinations, inherit proxies/cookies, follow redirects, or bypass challenges. Strict certificate/hostname verification and a pinned DNS snapshot use the existing provider VPN route; this is not a claim that DNS addresses are public.

Raw responses, source captures, assets, reviews and answer-containing packages belong under ignored `runtime/content-import/`, directories 0700 / files 0600. No source statements or reference solutions are checked into these fixtures.

- `acquire.py`: JSONL `{url,kind,body?}` observed request manifest; SHA cache and resumable private ledger. Budgets: 250 rows / 1 MiB manifest, 16 MiB response, 64 MiB job, 300 s job, 30 s request, minimum 0.5 s spacing. Failure/challenge stops the batch; an oversized response can read one detection byte beyond the remaining budget. Failed-transfer byte count is unknown and never represented as successful stored bytes.
- `svg.py` validates the inert vector subset before `render_svg.py` invokes existing pinned Sharp in an offline, bounded container. Renderer output is checked against the declared hash filenames and PNG dimensions before the host reads it. Unknown SVG/XML/path constructs are rejected.
- `html_parser.py` preserves paragraph/text/formula/figure order. Formula alt is not exact TeX; it never enters math, answer strings or grading text. U+FFFC is an explicit private object placeholder. Source math typography is normalized from observed 24 px/middle alignment; figures retain their aspect.
- `classification.py` requires a capture-hash-bound editorial review and a current same-position topic. Source memberships provide candidates, never a blanket automatic mapping. The current 141 topics preserve legacy version memberships separately.
- `normalize.py` / `build_package.py` produce a private **candidate** package from manual whitespace-normalized DOM captures, source assets and explicit classification. This is not a raw API export. Extended answers have separate typed blocks; short answers require an exact source literal. Publisher validation and root content approval remain required.
- `preview.py` is an ephemeral loopback-only review page using escaped typed text and package PNGs. It never renders upstream HTML or serves arbitrary files.

Run the offline checks:

```sh
python3 -m unittest discover -s scripts/content_import -p 'test_*.py' -v
```

Current acquisition limit: the observed public question-list endpoint returned an uninformative 500 for the two authorized research payloads. No further payload guessing or bulk crawl is implemented. Root independently observed 74 allowed sections, 4257 overlapping occurrences / 249 pages; private manifest contains exact IDs/counts. Three pilot records retain 41 image occurrences with 32 scoped PNG assets, pending publisher gates. Source criteria 408166 were not captured and no grading rubric is fabricated.

## Exact resume checkpoint

Root whole-card content comparison approved the unchanged private package:
`runtime/content-import/pilot-candidate/tasks.jsonl`, SHA256
`95a806cf386bb7de1a591bf5e5eb9e9cc4f8f3e291f52d9df5985bc3b7cfc1cc`.
`quality-review.json` binds this hash; `completeness.json` lists each record and limitations.
The renderer/source ledgers are in `runtime/content-import/rendered-v2/pilot-*`
and `runtime/content-import/raw/{manual-captures,pilot-*}`.

1. Before bulk work, obtain the actual authorized public-list payload or a lossless official export. Preserve it privately; update only the documented query schema/routes with source evidence. Do not infer another wrapper or retry a challenge.
2. Enumerate the private 74-section manifest with bounded pages. Verify response section/page/IDs against each observed total; record unique provider IDs separately from section occurrences. Reconcile multi-source memberships and excluded-source conflicts.
3. Parse all statement, answer and solution sessions with their exact assets. Unsupported HTML/SVG, absent fields, unknown short-answer notation and uncertain method mapping go to quarantine, with explicit counts. Preserve raw source criteria separately until a reviewed adapter supports them.
4. Rasterize only validated vectors using the existing pinned image, isolated offline. A renderer failure never becomes a successful package.
5. Bind classification reviews to capture hashes. Build a candidate, compare complete cards, then pass the exact package hash to the existing publisher preflight. Publisher validation/publication and replay/history/media-access checks are separate gates; local/stage apply sequentially.

The current loopback review process serves `http://127.0.0.1:18094` from typed blocks.
It is a temporary engineering preview, not a product route or final responsive UX gate.
The full bank remains pending the upstream payload/export; the three-row pilot does not establish complete acquisition.
