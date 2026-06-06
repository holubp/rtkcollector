# Contributing

RtkCollector uses a specification-first workflow.

Before implementing non-trivial behaviour:

1. Update the relevant document under `docs/`.
2. Keep receiver-driver API contracts and session-format documentation aligned
   with code.
3. Add focused tests before production code for reusable Kotlin interfaces,
   data models, parsers and protocol helpers.
4. Avoid maps, GIS editing, shapefile handling, RTKLIB integration, Android PPP
   solving and full NTRIP caster implementation in bootstrap work.
5. Preserve the rule that raw capture is authoritative and parser failures must
   not block recording.

Pull requests should explain whether they affect byte-exact capture,
background recording, receiver commands, session file compatibility or
correction routing.
