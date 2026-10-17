# CrossPoint Pal Android Manifesto

This repository contains the Android side of CrossPoint Pal. The firmware and
the Android application are separate products with a shared companion contract:
the phone does the work that belongs on a phone, and the e-ink device does the
work that becomes better on a small persistent display.

The firmware project remains the authority for device-side behavior and product
direction. This repository owns the Android application and its Android-specific
lifecycle, permissions, persistence, and user experience.

## Purpose

CrossPoint Pal Android exists to make a phone and a CrossPoint Pal device more
useful together.

The phone is good at networking, notification access, rich configuration,
authoring, storage, and computation. The e-ink device is good at remaining
visible, calm, readable, persistent, and low-power. The application should join
those strengths instead of trying to make either device imitate the other.

The application should make useful companion workflows simple:

- pair and connect to a CrossPoint Pal device;
- send small, bounded pieces of content to the e-ink display;
- receive physical-button events and other explicit device responses;
- mirror selected phone notifications when the user enables that capability;
- later support cards, references, configuration, or synchronization only when
  a demonstrated workflow benefits from the e-ink device.

The application is not a cloud service and does not require one to be useful.

## The phone and the e-ink device are partners

A feature belongs on Android when the phone is the natural owner of the work.

Examples include:

- accessing Android notifications;
- choosing and preparing content;
- network-heavy operations;
- rich configuration and editing;
- maintaining phone-side state;
- deciding when phone-owned information should be offered to the device.

A feature belongs in firmware when it is naturally device-local.

Examples include:

- rendering to the physical panel;
- power and sleep behavior;
- hardware buttons and device input;
- device-local persistence;
- hardware limits and board policy;
- radio and hardware lifecycle that only the device can enforce.

Do not duplicate ownership merely because both sides can technically implement
the same rule. The wire protocol should connect owners, not create two competing
state machines.

## Build companion workflows, not a general platform

CrossPoint Pal Android is not intended to become:

- a generic BLE toolkit;
- a universal e-reader manager;
- a general automation platform;
- a cloud account system;
- a remote administration framework;
- a cross-platform abstraction built in advance for hypothetical iOS or desktop
  clients;
- a second implementation of firmware policy.

Future clients are possible, but they are not requirements until a real use
case establishes them.

A useful test for every proposed feature is:

> Does this make the CrossPoint Pal companion workflow materially better?

If not, it probably does not belong here.

## Local-first and private by default

Phone notifications, message previews, identifiers, and other companion data can
be sensitive.

The default architecture should therefore be local-first:

- no cloud dependency for core companion behavior;
- no telemetry or analytics merely because the platform makes them easy;
- no notification titles, bodies, credentials, pairing material, or other
  sensitive values in normal logs;
- no broad data retention when transient state is enough;
- no silent export of user content;
- no permission that lacks a demonstrated workflow.

Android Notification Access is especially sensitive. It must be explicit,
optional, user-controlled, and isolated from unrelated app functionality.

## Permissions are product surface

Every Android permission and special access request increases the app's
authority and the user's trust burden.

Add permissions only when a current workflow requires them. Keep optional
capabilities optional. A future Wi-Fi transfer feature is not a reason to ask
for Wi-Fi authority today; a future notification feature is not a reason for a
minimal BLE transport build to require Notification Access.

When Android offers a narrower system mechanism, prefer it over broader ambient
authority.

## Background work must respect Android

Android lifecycle restrictions are part of the platform contract, not obstacles
to work around indiscriminately.

Use background execution, foreground services, scans, wakeups, and reconnect
mechanisms only when the demonstrated workflow needs them. Prefer platform
callbacks and bounded work over polling and permanent activity.

A foreground service must have a user-visible reason to exist. Background
reconnection must degrade safely when Android delays or refuses it. The app
must remain understandable when the operating system suspends, recreates, or
kills its process.

## The companion protocol is a product contract

The BLE protocol is not an implementation detail of either repository.

Protocol messages, identities, size limits, state transitions, acknowledgements,
and failure behavior must be explicit and versioned. Unknown or malformed input
must fail closed. Remote-controlled lengths and counts must be bounded before
allocation or persistence.

Neither side may silently reinterpret an existing protocol message. A protocol
change must identify:

- the current owner of the changed semantics;
- the new contract;
- compatibility requirements, if any;
- the firmware and Android versions affected;
- how failure behaves when the peer does not support the change.

Compatibility is justified by real supported peers and stored state, not by a
desire to preserve every experiment forever.

## Secure the relationship, not just the transport

The application should request only the authority needed for the selected
workflow and should preserve the distinction between identity, authentication,
transport, and product state.

When a workflow relies on a bonded or encrypted BLE relationship, do not replace
that boundary with an informal application convention without an explicit
security review.

Pairing reset, peer replacement, and reconnect behavior must be explicit.
Failure to authenticate or establish the expected peer must not silently become
an unauthenticated fallback.

## Evidence before assumptions

Repository state, Android documentation, logs, protocol traces, tests, and
reproducible measurements outrank plausible stories.

Distinguish:

- confirmed facts;
- reasonable hypotheses;
- unknowns.

A class name, TODO, old branch, reference implementation, AI suggestion, or
apparently similar Android API is evidence to investigate, not proof of current
product behavior.

## Presumption of non-existence

For product and design decisions, anything not established by evidence does not
exist.

A capability, lifecycle state, compatibility requirement, background service,
database, abstraction, adapter, retry path, permission, or future client needs
an accepted contract, reachable production behavior, authoritative platform
guarantee, reproducible observation, or explicit current use case.

Do not build frameworks, migrations, compatibility layers, background systems,
or configuration switches for hypothetical futures.

## Broad analysis, narrow change

Inspect as widely as necessary to understand the real owner, callers, state,
platform behavior, and failure modes.

Then change only what is necessary to solve the demonstrated problem and keep
its contracts coherent.

The task bounds mutation, not investigation. A focused feature must not become
an excuse for unrelated cleanup.

## One concept, one canonical owner

Equivalent semantics should have one canonical term, one source of truth, and
one production owner.

Do not create parallel BLE clients, connection managers, protocol parsers,
session state machines, persistence stores, permission models, or background
lifecycles for the same responsibility merely because the current owner is
awkward to extend.

If a new implementation replaces an old one, migrate callers and remove the
obsolete path unless a demonstrated compatibility requirement requires both.

## Entropy is a cost

Every noun, state, abstraction, flag, special case, alias, adapter, dependency,
permission, and production path increases the amount of system that must be
understood and maintained.

New structure must earn its existence.

Prefer:

- one canonical term for one domain concept;
- one owner for each responsibility;
- one production path for equivalent behavior;
- direct platform APIs over unnecessary wrappers;
- deletion and consolidation when distinctions no longer serve a real need.

## Explicit contracts, fail closed

External input, authority, persistence, lifecycle transitions, and cross-process
communication must be explicit.

Missing, malformed, contradictory, or stale state must not silently become
permission or success. Preserve useful evidence, report the failure, and recover
only through an accepted contract.

Success must be reported only after the operation's real success point.

## Reproducibility and provenance matter

It should be possible to identify which source revision, dependency set,
configuration, Android version, device, and firmware peer produced an observed
result.

Pin dependencies and tool versions where the build permits it. Do not use
floating dependency versions for production behavior.

When code or design is derived from another project, record the source
repository, source revision, and applicable license. Reference implementations
are evidence and prior art, not authority.

## Real-device truth wins

Host tests and emulators are necessary, but they do not prove:

- real BLE pairing and reconnect behavior;
- background scan behavior across Android vendors;
- NotificationListener behavior under real system policy;
- radio coexistence with the firmware;
- e-ink rendering;
- device power behavior;
- physical-button latency.

Claims that depend on the real phone or reader require hardware UAT and must be
reported as unverified until that evidence exists.

## Human ownership

AI agents are development tools, not product owners.

Architecture, user-visible behavior, security boundaries, permissions, and
long-term maintenance remain human decisions. Working code is not sufficient if
its owner cannot understand and maintain it.

Agents should expose uncertainty, evidence, trade-offs, and remaining risks.
They must not hide architectural choices inside implementation work.

## Principles are tools, not commandments

These principles exist to improve the product, reduce complexity, and protect
users. They are not axioms to defend against demonstrated needs.

When a principle conflicts with an observed workflow, identify the goal it
protects and choose the smallest explicit exception that preserves that goal.
If repeated evidence shows that a principle produces worse outcomes, change the
principle rather than forcing the product to conform to it.
