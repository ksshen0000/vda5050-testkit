# vda5050-testkit

Conformance checks for [VDA 5050](https://github.com/VDA5050/VDA5050), the MQTT interface between
fleet control and AGVs / mobile robots. Point it at a broker (or a recorded trace) and it tells you
which side breaks the specification, where, and which section says so.

[中文说明](README.zh-CN.md)

```
$ java -jar vda5050-testkit.jar demo --with-mistakes
VDA 5050 conformance report
  trace:    132 messages, 2026-01-01T08:00:00Z to 2026-01-01T08:00:50Z (50.0 s)
  vehicles: TESTKIT-SIM/AGV-001, TESTKIT-SIM/AGV-002
  versions: 2.1.0

  PASS  SCHEMA           Valid against the official JSON schema        [publisher, 132 checked, spec 2.x: 6.5 / 3.0: 4.3]
  PASS  HEADER-ID        headerId increases by one per topic           [publisher, 120 checked, spec 2.x: 6.4 / 3.0: 7.2]
  ...
  FAIL  ORDER-UPDATE     Order updates stitch onto the decision point  [fleet control, 2 checked, spec 2.x: 6.6.2, 6.6.4.3 / 3.0: 6.1.2, 6.1.4.4, 6.1.4.5]
        ERROR   TESTKIT-SIM/AGV-001  order o-1/1 starts at node A (sequenceId 0) but the decision point of order o-1/0 is B (sequenceId 2) (#9, #17)
  PASS  ORDER-ACK        Vehicle accepts or rejects every order        [vehicle, 4 checked, spec 2.x: 6.6.2 (figure 8), 6.6.4, 6.10 / 3.0: 6.1.2, 6.1.4, 6.6]
  PASS  CANCEL-ORDER     cancelOrder is carried out as specified       [vehicle, 2 checked, spec 2.x: 6.6.3 / 3.0: 6.1.3, 6.6.7]

Result: FAIL - 1 failed, 0 with warnings, 13 passed, 0 inconclusive, 1 not applicable
```

Here fleet control sent an update that restarts at node A instead of continuing from the decision
point B. The vehicle rightly rejected it, so `ORDER-ACK` passes and the failure is put on fleet
control. `#9, #17` are the positions of the two messages in the trace.

## Why

Most VDA 5050 integration problems are not malformed JSON. They are behaviour: an order update that
does not start at the decision point, a vehicle that never answers an order, `nodeStates` that skip a
node, a `cancelOrder` that reports FINISHED while the route is still there, a connection message that
is not retained. Schema validators do not see any of that, and each integrator ends up checking it by
hand in MQTT Explorer.

Even the official schemas need care: the 3.0.0 schemas as released contain trailing commas that make
three of them invalid JSON, and the 2.x state schema leaves out an action status the text allows.
The corrections are listed in [docs/schema-patches.md](docs/schema-patches.md).

## What it checks

15 rules. Each finding names the party responsible, so a report can go straight to the vehicle
vendor or the fleet control team.

| Rule | Checks | Who | Spec 2.x / 3.0 |
| --- | --- | --- | --- |
| `TOPIC` | `interfaceName/majorVersion/manufacturer/serialNumber/topic`, allowed characters, known topics | publisher | 6.3 / 4.2 |
| `PAYLOAD` | every payload is a JSON object | publisher | 4 / 4 |
| `SCHEMA` | the official schema of the version in the header (2.0.0, 2.1.0, 3.0.0) | publisher | 6.5 / 4.3 |
| `HEADER-MATCH` | manufacturer, serialNumber and major version agree with the topic | publisher | 6.3, 6.4 / 4.2, 7.2 |
| `HEADER-ID` | headerId goes up by one per topic (gaps are warnings: QoS 0 may lose messages) | publisher | 6.4 / 7.2 |
| `TIMESTAMP` | ISO 8601 UTC, not running backwards, clock in sync with the observer | publisher | 6.4 / 7.2 |
| `RETAIN` | connection and factsheet retained; orders and instant actions not | publisher | 6.14, 6.15 / 6.5, 6.10 |
| `CONNECTION` | ONLINE announced before reporting state, also after reconnects | vehicle | 6.14 / 6.5 |
| `STATE-INTERVAL` | state at least every 30 s while online | vehicle | 6.10 / 6.6 |
| `ORDER-STRUCTURE` | sequence ids, edges connecting their nodes, base before horizon, unique action ids | fleet control | 6.6.1, 6.6.2 / 6.1.1, 6.1.2 |
| `ORDER-UPDATE` | updates start at the decision point, unchanged; resends are identical | fleet control | 6.6.2, 6.6.4.3 / 6.1.2, 6.1.4.4 |
| `ORDER-ACK` | every order is either taken over or rejected with an error; outdated updates are rejected | vehicle | 6.6.2, 6.6.4 / 6.1.2, 6.1.4 |
| `ORDER-PROGRESS` | nodeStates / edgeStates hold exactly what is still ahead; lastNode only moves forward | vehicle | 6.10.1, 6.10.2 / 6.6.1, 6.6.2 |
| `ACTION-STATES` | every order and instant action is reported; status only moves forward | vehicle | 6.9, 6.11 / 6.2.1, 6.6.9 |
| `CANCEL-ORDER` | with an order: remaining actions FAILED, route cleared, orderId kept, then FINISHED; without: FAILED plus `noOrderToCancel` | vehicle | 6.6.3 / 6.1.3, 6.6.7 |

A rule reports PASS, WARN (only warnings), FAIL, `?` (the trace ended before it could decide) or `-`
(nothing in the trace it applies to). `vda5050-testkit rules` prints the list.

## Quick start

Needs Java 21 or later. Build the jar (or take it from a CI run) and try the demo; it simulates two
vehicles and a fleet control in memory, no broker needed:

```
./mvnw package -DskipTests
java -jar vda5050-testkit-cli/target/vda5050-testkit.jar demo
java -jar vda5050-testkit-cli/target/vda5050-testkit.jar demo --with-mistakes   # fleet control sends a broken update
```

Or with Docker:

```
docker build -t vda5050-testkit .
docker run --rm vda5050-testkit demo
```

## Checking a real system

Record everything on the broker and check it when you stop (Ctrl+C or `--duration`):

```
vda5050-testkit record -b tcp://broker:1883 --check            # writes vda5050-<time>.jsonl
vda5050-testkit record -b ssl://broker:8883 --username fleet -i vda5050 -d 600 -o shift.jsonl --check
```

Check a trace later, or several merged by time:

```
vda5050-testkit check shift.jsonl
vda5050-testkit check --format json --report report.json shift.jsonl
vda5050-testkit check --disable TIMESTAMP,RETAIN --reaction-time 10 shift.jsonl
```

The exit code is 0 if no rule failed, 1 if one did, 2 on bad input, so it can gate a CI pipeline or an
acceptance test. Options: `--reaction-time` (how long a vehicle may take to show an order or action,
default 5 s), `--state-interval` (30 s), `--cancel-time` (60 s), `--max-clock-skew` (2 s).

`record` subscribes with MQTT 5 "retain as published", so it also sees the retain flag of live
messages. With `--mqtt3` (for MQTT 3.1.1-only brokers) that flag is unknown and `RETAIN` is skipped.

### Trace format

JSON Lines, one message per line, payload embedded as JSON (or as `payloadText` if it is not JSON):

```json
{"receivedAt":"2026-10-25T08:00:01.250Z","topic":"uagv/v2/ACME/AGV-001/state","retained":false,"payload":{"headerId":12,...}}
```

Easy to produce from other tools, to diff, and to attach to a bug report.

## Simulated vehicles

`sim` starts VDA 5050 2.1 vehicles on a broker, for testing a fleet control without hardware:

```
vda5050-testkit sim -b tcp://localhost:1883 -n 5 --speed 1.2 --time-scale 2
```

They drive orders (nodes need `nodePosition`, mapId `SIM` by default), run actions such as `pick` and
`drop` with HARD/SOFT/NONE blocking, and handle `startPause`, `stopPause`, `cancelOrder`,
`initPosition`, `stateRequest`, `factsheetRequest` and charging. They publish state, visualization,
retained connection (with last will) and factsheet. Vehicle i starts at x=0, y=i*5.

## Libraries

The command line tool is built from three libraries without framework dependencies, usable on their own:

- `vda5050-conformance`: the checker. Feed `TraceEntry`s to a `ConformanceChecker`, get a `Report`.
  Depends only on Jackson and a JSON schema validator.
- `vda5050-protocol`: typed VDA 5050 2.1 messages, JSON codec, topics, MQTT clients for the vehicle
  side (`AgvClient`) and the fleet side (`MasterClient`), with HiveMQ and in-memory transports.
- `vda5050-simulator`: the simulated vehicle (`VirtualVehicle`, `SimulatedAgv`, `SimulationEngine`).

They are not on Maven Central yet; `./mvnw install` puts them in your local repository
(`io.github.ksshen0000:vda5050-conformance:0.1.0-SNAPSHOT`).

## Limits

- The checker sees what the broker delivers to it. A message lost with QoS 0 looks like a message never
  sent, which is why headerId gaps are only warnings.
- Behaviour rules follow orders sent while recording. Start recording before fleet control sends the
  orders you want checked.
- Schema checks cover 2.0, 2.1 and 3.0. Behaviour rules were developed against 2.1 and know the 3.0
  differences in field names, error types and `instantActionStates`; 3.0 zones, corridors and the
  request/response mechanism are not checked yet.
- Passing does not mean certified. VDA 5050 has no official certification; this project is not
  affiliated with the VDA or VDMA.

## Building

```
./mvnw verify        # unit tests; the broker integration tests also run if Docker is available
```

## License

Apache-2.0. The bundled VDA 5050 schemas are MIT-licensed by the VDA; see [NOTICE](NOTICE).
