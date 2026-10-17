# CrossPoint Pal Android agent guide

This repository contains the Android companion application for CrossPoint Pal.

Read [MANIFESTO.md](MANIFESTO.md) before making architectural or product
decisions. It defines the stable product boundary and engineering principles.
This file defines how agents should work inside that boundary.

The repository is intentionally early-stage. Do not infer architecture from
empty directories, planned features, reference implementations, or previous
experiments. Establish the current repository state first.

## Start here

At the beginning of a work session:

1. inspect the current branch and working tree;
2. inspect the remotes and current upstream/fork relationships, if any;
3. read the relevant current documentation before changing its contract;
4. inspect the code that currently owns the responsibility being changed;
5. distinguish current production behavior from plans, experiments, and
   external reference implementations.

Typical first commands in a local checkout are:

```sh
uname -s
git branch --show-current
git remote -v
git status --short
```

Do not assume a file, Gradle task, test suite, Android API level, dependency, or
product capability exists until the repository proves it.

If the task explicitly asks only to analyze, inspect, review, or plan, do not
modify files or create commits.

## Related projects

The Android application is one half of a two-repository product.

- Firmware:
  `kivarun/crosspoint-pal`
- Upstream firmware:
  `crosspoint-reader/crosspoint-reader`

The firmware repository owns device-side behavior: rendering, device power,
hardware input, board policy, and radio lifecycle that only firmware can
enforce.

This repository owns Android-specific behavior: permissions, Android lifecycle,
phone-side data, phone-side persistence, and user interaction.

The BLE protocol is a shared product contract. Neither repository may silently
change its meaning independently.

External implementations such as `Terramoto/Crosspoint-Reader` or
`DarkStarDS9/xteink-companion-ble` are reference material. They may contain
valuable evidence and tested mechanisms, but they are not architectural
authority for CrossPoint Pal.

## Development approach

### 1. Work incrementally

Inspect the current behavior before changing it.

For non-trivial work:

- trace the current production path;
- identify the demonstrated problem or requirement;
- identify the current owner;
- identify behavior that must remain unchanged;
- choose the smallest coherent change;
- verify the result at the strongest available layer.

A large rewrite requires stronger evidence than an extension of an existing
owner.

### 2. Presume non-existence until evidence proves otherwise

For product and implementation decisions, a capability, state, owner,
permission, compatibility requirement, retry path, abstraction, background
worker, database, or dependency does not exist until evidence establishes it.

Evidence may be:

- an accepted product or protocol contract;
- reachable production behavior;
- authoritative Android or dependency documentation;
- a reproducible observation or regression;
- an explicit current human requirement.

Names, comments, TODOs, issue discussions, old branches, AI suggestions,
reference apps, and plausible platform behavior are leads to investigate, not
proof.

Do not add frameworks, adapters, migration systems, caches, background workers,
or configuration switches for possible future needs.

Future extensibility is a constraint, not a requirement to implement the future
today.

### 3. Keep inspection broad and mutation narrow

The requested task limits what may be changed, not what may be inspected.

Before modifying one responsibility, inspect enough surrounding code to find:

- every production entry point;
- sibling paths with the same semantics;
- persistence and lifecycle state;
- protocol and user-visible contracts;
- tests and diagnostics;
- existing owners that the new code could accidentally duplicate.

Change only what is needed to keep the affected responsibility coherent.

Do not opportunistically fix unrelated code. Report additional findings
separately unless they block the requested change.

### 4. One concept, one canonical owner

Before adding a new service, manager, repository, parser, state machine,
scheduler, store, adapter, or lifecycle helper, find the existing owner of that
responsibility.

Do not create a parallel implementation merely because extending the current
owner is inconvenient.

Examples:

- one BLE connection owner;
- one protocol codec/validator;
- one session state machine;
- one permission-state owner;
- one notification snapshot owner;
- one background connection lifecycle;
- one persistence owner for one kind of durable state.

If semantics are the same, extend or refactor the owner. If an implementation
is being replaced, migrate callers and remove the obsolete path unless a real
compatibility requirement requires both.

Passing tests do not justify duplicate ownership.

### 5. Keep domain vocabulary stable

One domain concept should have one canonical term throughout:

- Kotlin types;
- protocol messages;
- persistence;
- UI;
- logs;
- documentation;
- tests.

Do not introduce synonyms for the same state or overload one term for different
states.

Translate platform-native or firmware-native vocabulary at the boundary rather
than letting it silently rename the product concept.

### 6. Do not silently change contracts

Treat these as contracts unless the task explicitly changes them:

- BLE service and characteristic identities;
- protocol version and message shapes;
- message size/count bounds;
- pairing and peer identity semantics;
- acknowledgement and retry behavior;
- permission requirements;
- persisted state formats;
- user-visible enable/disable semantics;
- logging and privacy guarantees.

When implementation and documentation disagree, establish which one is
authoritative before changing either.

If a required change alters the protocol or security boundary, stop treating it
as an implementation detail. Describe the old contract, proposed contract,
compatibility impact, and migration requirement explicitly.

### 7. Prefer deletion and platform behavior over reinvention

Before adding code, ask whether the correct change is:

- deleting dead state;
- consolidating duplicate validation;
- using an Android platform mechanism directly;
- extending an existing owner;
- documenting an intentional limitation.

Do not reproduce behavior Android already owns unless CrossPoint Pal has a
specific product reason to add policy on top.

Examples include lifecycle dispatch, permission state, BLE stack behavior, and
system notification access.

Use wrappers when they establish a real product boundary or materially improve
testability, not merely to hide an API.

## Android product boundaries

### Permissions

Every permission must map to a current user workflow.

Do not request permissions early for planned features.

In particular:

- BLE transport does not justify Notification Access;
- Notification Access does not justify Wi-Fi permissions;
- a future file-transfer feature does not justify current network authority;
- background execution does not justify a permanent foreground service by
  default.

Optional capabilities must remain optional where the workflow permits it.

Permission denial is normal product state and must have a clear, non-crashing
path.

### Background execution

Do not fight Android lifecycle rules with unbounded loops, aggressive polling,
or restart tricks.

Prefer:

- platform callbacks;
- bounded work;
- explicit connection windows;
- system-supported reconnect mechanisms;
- foreground services only when the active workflow genuinely requires one.

Background behavior must remain correct when Android suspends, recreates, or
kills the process.

Vendor-specific behavior must be reported as such until reproduced elsewhere.

### Privacy

Treat notification content and companion payloads as sensitive user data.

Never write the following to ordinary logs, analytics, crash diagnostics, test
fixtures, or CI artifacts:

- notification titles or bodies;
- message contents;
- credentials or tokens;
- BLE pairing material;
- private identifiers not required for diagnosis.

Use synthetic data in tests.

Do not add telemetry, analytics, cloud synchronization, or remote crash upload
without an explicit product decision.

## BLE and protocol rules

### One radio/session owner

The app must have one authoritative BLE connection/session lifecycle.

Do not allow UI screens, notification code, background services, and feature
modules to each create their own independent GATT lifecycle.

Feature code should express intent to the connection owner. It should not
silently create a second connection path.

### Protocol first

Before implementing a protocol message:

1. locate the canonical protocol contract;
2. verify the firmware version that implements it;
3. identify message direction and owner;
4. define bounds;
5. define acknowledgement/failure behavior;
6. define compatibility behavior.

If no canonical contract exists, stop and establish one rather than inferring a
wire protocol from one side's code.

Unknown protocol versions, malformed frames, invalid lengths, impossible state
transitions, and unexpected peer identity must fail closed.

Remote-controlled lengths and counts must be bounded before allocation,
decoding, or persistence.

### State authority

Phone-owned facts remain phone-owned. Device-owned facts remain device-owned.

The app must not infer device success merely because a write call returned
success. Use the protocol's explicit acknowledgement or observable contract.

Likewise, the firmware must not be mirrored into a second Android-side state
authority merely to simplify UI code.

Cached presentation state is not authoritative device state unless the protocol
explicitly defines it that way.

### Pairing and identity

Pairing, peer replacement, reconnect, and reset behavior are security and
product contracts.

Do not silently downgrade an authenticated/bonded workflow to an unauthenticated
one.

Do not add multi-device or multi-phone identity models until a demonstrated
workflow requires them.

## Dependencies and build reproducibility

Prefer Android and Kotlin platform capabilities before adding another library.

Every dependency must have a concrete owner and use case.

Avoid:

- floating versions such as `+`;
- duplicated libraries for the same responsibility;
- large frameworks added for one trivial helper;
- dependencies introduced only because a reference implementation uses them.

Pin build inputs where practical. Preserve Gradle wrapper and plugin-version
reproducibility once they exist.

Generated files are outputs of their generators. Do not hand-edit generated
sources, Room schemas, or build artifacts unless the repository explicitly
documents them as source-controlled inputs.

Do not commit local Android Studio state, signing secrets, SDK paths, build
outputs, or machine-specific configuration.

## Testing and evidence

Use the strongest test that matches the claim.

Prefer pure host/unit tests for:

- protocol encoding and decoding;
- bounds validation;
- session state transitions;
- content identity/idempotency;
- persistence transformations;
- policy decisions.

Use Android tests when the claim depends on Android framework behavior.

Use real-device UAT when the claim depends on:

- BLE pairing;
- reconnect behavior;
- background scanning;
- process recreation;
- NotificationListener behavior;
- foreground-service behavior;
- a specific Android vendor;
- interaction with the physical CrossPoint Pal device.

An emulator or mocked Bluetooth stack does not prove real BLE behavior.

Firmware/e-ink/power behavior is verified in the firmware project and on real
hardware; this app must not claim to have proved those effects indirectly.

When a required tool, emulator, phone, or reader is unavailable, report the
check as **not run**, never as passed.

Once the Android project defines canonical verification commands, keep them
documented here and in CI. Until then, inspect the actual Gradle tasks rather
than inventing commands.

## Failure handling

Expected permission, protocol, connection, and lifecycle failures are product
states, not generic exceptions to hide.

Prefer typed Android/API results and explicit domain errors over matching
human-readable error text.

Do not retry indefinitely.

Retries require:

- a demonstrated transient failure;
- an owner;
- a bound;
- an idempotency story;
- a visible terminal outcome.

A reconnect mechanism is not permission to replay every operation blindly.

## Reference implementations and copied code

Research can be wide; copied implementation must be narrow and attributable.

Before copying or adapting substantial code:

1. identify the source repository and exact revision;
2. inspect the source license;
3. verify compatibility with this repository's license;
4. preserve required attribution and notices;
5. record what was copied or adapted and why;
6. still review the code against this project's architecture and contracts.

Do not import an external architecture wholesale merely because it already
works.

This repository is licensed under the GNU Affero General Public License v3 as
currently recorded in `LICENSE`. External permissive code may still require
copyright and license notices.

## Documentation

Repository-facing documentation and comments use plain English and standard
technical terminology.

Comments should explain durable constraints, ownership, platform quirks, or
non-obvious reasons. Do not narrate obvious code.

Keep current behavior, plans, and experiments distinguishable.

A design document does not make an unimplemented feature real. An old issue or
roadmap item is not a compatibility contract.

For externally observed behavior, record versions, revisions, and hardware when
they matter to reproduction.

## Human ownership and agent authority

A change is a long-term maintenance commitment. Working code is not enough:
prefer the simplest design that meets the demonstrated requirement and can be
understood by its human owner.

Agents must expose:

- evidence;
- assumptions;
- unresolved questions;
- architectural choices;
- verification performed;
- verification not performed;
- remaining risks.

Do not hide a product, security, permission, or protocol decision inside an
implementation patch.

Creating or amending commits requires explicit human approval.

Push only on explicit human instruction to push. Approval to edit or commit does
not imply approval to push.

Never open, close, or merge a pull request unless the human explicitly asks for
that exact action.

Never publish an APK, release, store build, or signing artifact without explicit
human approval.

Never alter signing keys, keystores, package ownership, store credentials, or
production distribution settings as an incidental part of feature work.

## Handoff

Before declaring a logical change ready, provide a compact handoff containing:

- starting and final revision;
- files changed;
- product behavior changed;
- contracts changed or explicitly unchanged;
- tests and static checks run;
- Android builds run;
- hardware/device tests run;
- checks not run and why;
- known limitations and residual risks;
- external code/provenance, if any.

For a protocol change, include the firmware-side compatibility status.

For a change involving permissions, background execution, Notification Access,
pairing, or sensitive data, explicitly state the resulting authority and privacy
surface.

Do not call work complete while a required real-device behavior remains
untested; call the software work complete and the hardware/UAT status
separately.
