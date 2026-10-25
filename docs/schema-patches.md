# Corrections to the official schemas

The checker validates against the JSON schemas published in the
[VDA5050/VDA5050](https://github.com/VDA5050/VDA5050) repository, tags `2.0.0`, `2.1.0` and `3.0.0`
(MIT license, see `LICENSE.txt` next to them). The specification says the PDF wins where text and
schemas disagree. A few schema defects would make valid messages fail, or make the schema unreadable,
so the checker corrects them. Every correction is listed here.

The files in `vda5050-conformance/src/main/resources/.../schemas/` are the upstream files except for
item 1; items 2 to 4 are applied in code when a schema is loaded (`SchemaCatalog.patch`).

| # | Version | File | Defect | Correction |
| --- | --- | --- | --- | --- |
| 1 | 3.0.0 | `factsheet.schema`, `order.schema`, `visualization.schema` | Trailing commas (4, 1 and 2 of them) make the files invalid JSON, so a standard JSON parser rejects them | Commas removed in the bundled files; nothing else changed |
| 2 | 3.0.0 | `zoneSet.schema` | `$schema` is `http://json-schema.org/draft/2020-12/schema`; the meta-schema is identified with `https` | Rewritten to `https` |
| 3 | 2.0.0, 2.1.0 | `factsheet.schema` | `protocolFeatures.agvActions[].blockingTypes` is declared `"type": "array"` with the string `enum` on the array itself, so no array value can be valid | `enum` moved into `items` |
| 4 | 2.0.0, 2.1.0 | `state.schema` | `actionStatus` leaves out `PAUSED`, which the text lists as a valid value (2.1 table 2, section 6.11) | `PAUSED` added to the enum |

If you find another one, please open an issue with the message that fails and the section of the
specification it should pass under.
