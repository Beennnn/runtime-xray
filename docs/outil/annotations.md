# Annotating the runs — alone, on your own machine, or together

> A measurement without a name cannot be found again. This document describes what can be
> added to a run after measuring it, where it is written, and the three ways of keeping those
> annotations alive — from the browser alone to a shared server.
>
> **In the page, all of this is called a run's "card"**: that is the readable word for what
> this document calls its annotations. The card is what comes from a human; all the rest of
> the page comes from the measurement, and the two never mix.

## The problem

Three runs in a report is already three timestamped directories whose names say nothing. A
week later, nobody remembers which one carried the reduced data set, which one was used for
the demonstration, or why the middle one had been re-run.

What the measurement cannot produce — **what one was after, what one understood, what is left
to check** — must therefore be writable afterwards, and stay attached to the run. That is the
whole subject of what follows.

## What can be added

| | What it is | What it is for |
|---|---|---|
| **Name** | The label shown everywhere the run appears | Finding *that* one in a selector showing five |
| **Description** | Free text | Saying what one was after and what one saw — the report will never guess it |
| **Labels** | Keys, each with an **optional** text | Tying to a ticket, an environment, a version; marking "to replay" with no further comment |
| **Pruning** | Branches cut, a root brought back | Showing only the branch that matters — see below |

All of it is typed into the page, on the **identity band** placed under the run's name, or
written by hand into a file. Both paths lead to the same place.

## Three identities, and why three are needed

| | What it is | Changes? |
|---|---|---|
| **Identifier** | Generated at launch, written into `run-context.json` | **never** — it is the stable key, the one that survives every renaming |
| **Name at launch** | The `--name` given to the measurement | never — it says what one **thought** one was measuring, and allows going back |
| **Name in the tool** | Put on afterwards | yes, as many times as one likes — it says what one **understood** |

The name shown follows this order: **the name put on in the tool**, failing that **the
launch's**, failing that **the shortened identifier**. Nothing is invented on the way: a run
launched without `--name` shows under its identifier, never under a convenience label — "run
of 21/08 at 00:41" would pass itself off as an intention of the operator when it is not one.

## The three modes

A page opened as a file can write nothing to disk: that is a browser rule, and it is also
what makes the page passable on as it is. Hence three ways of keeping the annotations alive,
from the simplest to the most shared. **None excludes the others**: it is the same format,
and one moves from one to the next without converting anything.

| | What one does | What it gives | What it supposes |
|---|---|---|---|
| **1. Each in their own browser** | Open the page, annotate | Immediate. The annotations stay in that browser from one visit to the next. **Export** hands over a file, **import** takes in a colleague's | Nothing |
| **2. On your own machine** | `--serve`, then annotate | The page writes beside the runs and the report is regenerated: the annotation is secured, including for whoever reopens the file with no server | Running the tool |
| **3. A shared server** | `--serve --serve-host 0.0.0.0` on a machine where the results are dropped | Everyone reaches it through a browser and **annotates in parallel**, without overwriting each other | A machine, and either filtered access or a shared secret |

```bash
# 2. on your own machine — http://localhost:8787
java -jar runtime-xray.jar --report-only --out runtime-xray-out --serve

# 3. shared server — run directories are dropped there, everyone reads and annotates
java -jar runtime-xray.jar --report-only --out /srv/runtime-xray --serve 8080 --serve-host 0.0.0.0

# 3 bis. the same, guarded by a shared secret (drawn at random and shown once)
java -jar runtime-xray.jar --report-only --out /srv/runtime-xray --serve 8080 \
  --serve-host 0.0.0.0 --serve-token
```

The page recognises by itself which mode it is in: served by the tool, it offers **save** in
its top bar, under **card**; opened as a file or from static hosting, it sticks to **export**
and **import**, and the band then announces *kept in this browser*.

Where to find what in the page: the **card** band, under the breadcrumb in the overview,
carries what is typed in — name, description, labels; the **card** group in the top bar
carries what is done with it — save, export, import, view the JSON. Nothing editable lives in
the body of the page: a report is read, it is not filled in.

### Annotating together

That is the only real difficulty of mode 3, and it is dealt with where it arises:

- **A write bears on one run**, never on the whole file. Two people annotating two runs
  therefore never meet.
- On the **same** run, each sends the fingerprint of what they had in front of them. If
  somebody went past in the meantime, the second one gets **"changed in the meantime"** and
  the recorded version, and chooses: keep theirs, or *take the recorded version*. Nobody
  overwrites anybody in silence.
- The page **re-reads the server every fifteen seconds**: names put on by others arrive on
  their own, without reloading.
- As long as an entry has not been sent, the band announces it — *unsaved* — and the **save**
  button is marked with a dot.

### Dropping results while it runs

That is mode 3's very gesture: one copies a run directory into `runs/`, and everyone must see
it. The server **polls the directory every ten seconds** and reassembles the page as soon as
a run appears or disappears — there is nothing to restart.

Open pages do not reload by themselves: somebody may be reading a method or writing a
description. They announce it, at the bottom right — *"The report changed on the server — 4
runs now"* — and let one reload when the moment is right.

The polling is deliberate, rather than watching the file system: results often arrive over a
network share, where change notifications are irregular at best. Ten seconds is enough — one
does not drop a run ten times a minute.

### Closing the door: `--serve-token`

Modes 1 and 2 have nothing to guard — the local loopback lets in only the machine itself.
Mode 3 changes the question: anyone who reaches the port reads the reports and annotates.
Hence a shared secret, **optional**:

```bash
--serve-token "chosen phrase"    # this one, and no other
--serve-token                    # with no value: drawn at random and shown once
XRAY_SERVE_TOKEN=... --serve     # the same effect, without exposing it in "ps"
```

What it gives:

- a visitor lands on an entry page, types the secret, and **the session lasts twelve hours** —
  they do not retype it on every page;
- the session is an `HttpOnly`, `SameSite=Strict` cookie, which **does not contain the
  secret**;
- a script or a `curl` goes through `Authorization: Bearer <secret>`, with no form;
- after five failed attempts from the same address, answering stops for about thirty seconds;
- if the session expires while annotating, **nothing is lost**: the page says so, what was not
  yet saved stays in the browser, and one only has to log in again.

Without the option, nothing changes: the server stays open, and says so at start-up.

```
⚠️ Listening beyond the local loopback, WITHOUT authentication:
   anyone who reaches this port can read the reports and annotate.
   To be placed behind something that already filters access, or
   guarded by --serve-token.
```

### What that secret is worth, and what it is not

A shared secret is not accounts. **The tool does not know who annotates** — it never did, and
the annotation files carry no author. Claiming otherwise would be lying about what they hold.

Three caveats, to read before deploying:

- **Over plain HTTP, the secret travels in the clear.** For it to really protect, TLS is
  needed in front, terminated by a proxy. On a trusted internal network it stops a passer-by;
  it does not stop someone listening to the network.
- **A secret on the command line is readable in `ps`** by the machine's other accounts.
  `XRAY_SERVE_TOKEN` exists for that.
- **It must fit in printable ASCII.** An accent crosses neither the `Authorization` header nor
  certain environment variables: the tool refuses it at start-up, with its reason, rather than
  leaving an inexplicable 401 once deployed.

In other words it **complements** network filtering, it does not replace it. It is a
diagnostic tool, not a service: the only write it accepts is a run's annotation, in a file
whose name it chooses itself, and the file serving refuses any path that would leave the
served directory.

### Deploying it, so nobody has to remember the options

Repeating `--serve-host` on every launch is how one ends up not repeating it. Two things
carry it instead — and neither of them ever *starts* a server, which is the point:

**In the project's configuration**, because which interface a machine exposes is a property
of that machine:

```conf
SERVE_HOST="0.0.0.0"
```

> ⚠️ **This key widens what is readable, and it is off by default.** Past the loopback, the
> report — with the argument values captured from a real application — becomes readable by
> whoever reaches the port, and the annotation writes become theirs too. The key sets the
> interface and nothing else: `--serve` remains the only gesture that puts the tool into
> listening, so a configuration file copied into a repository, a ticket or another machine
> cannot open a port by travelling.

**In a service**, for a machine that serves results permanently. The secret goes through the
environment rather than the command line, where `ps` would show it:

```ini
# /etc/systemd/system/runtime-xray.service
[Unit]
Description=Runtime X-Ray — shared report
After=network-online.target

[Service]
User=xray
WorkingDirectory=/srv/xray
EnvironmentFile=/etc/runtime-xray.env      # XRAY_SERVE_TOKEN=…, chmod 600
ExecStart=/usr/bin/java -jar /opt/runtime-xray/runtime-xray.jar \
          --report-only --out /srv/xray/campaigns \
          --serve 8787 --serve-host 127.0.0.1
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Note the `127.0.0.1` in a unit meant to serve a whole team: **the TLS proxy is what listens
outside**, and the tool answers only to it. That way the plain HTTP never leaves the machine,
which is what the first caveat above asks for.

```nginx
server {
    listen 443 ssl;
    server_name xray.internal.example.com;
    ssl_certificate     /etc/ssl/certs/xray.pem;
    ssl_certificate_key /etc/ssl/private/xray.key;

    location / {
        proxy_pass http://127.0.0.1:8787;
        proxy_set_header Host $host;
        # The report is a lot of small files, and some of them are large enough to matter.
        proxy_buffering off;
    }
}
```

Two things to check before opening it, and they are the ones people skip:

- **The output directory is the perimeter.** Everything under `--out` is served, and a
  campaign carries the observed application's log and its captured values. Serve a directory
  that holds only what you meant to hand over.
- **`--serve-host 0.0.0.0` without a proxy is a decision**, not a shortcut. It works, the tool
  warns about it at start-up, and the warning is the whole of the protection you then have.

## Where the file is written

Seen as files, **a run is a directory**. Its annotation can live in three places, and the
order of priority is this one:

| Location | What it implies |
|---|---|
| `runs/<run>/config.json` | **Takes priority.** The annotation is *inside* the run: it follows it everywhere — a copy, an archive, sending it to a colleague |
| `runs/<run>-config.json` | Beside the directory, the same name suffixed. The run stays untouched: useful if it is read-only, signed, or produced by someone else |
| `noms.json` | A single file at the root, indexed by identifier. It is the **exchange format**: the one the page exports |

Two rules, and they are justified the same way:

- **the closest to the run wins** — whoever filed the annotation with the measurement
  expressed a more precise intention than whoever filled in the shared file;
- **the chosen source is taken whole** — mixing a name from one file with a description from
  another would give an annotation nobody wrote, and nobody could correct.

The server, for its part, writes **where the annotation already lives**, and into the run's
directory if it did not exist yet.

### The two shapes

A run's file carries no identifier — it is already in the right directory:

```json
{
  "nom": "Acceptance of 21/08 — night",
  "description": "24 M iterations. Check the weather branch.",
  "etiquettes": { "ticket": "RX-142", "to-replay": "" },
  "elagage": {
    "racine": "lab/sample/Main.main;lab/sample/RoutePlanner.travelTimeMinutes",
    "coupes": ["lab/sample/Main.main;lab/sample/Scenarios.at"]
  }
}
```

The shared file, for its part, is indexed by identifier, and accepts the name alone — that
is the shape that circulated before descriptions and labels, and it is still read as it is:

```json
{
  "8BF7DA2E-1C5A-4435-A3E3-4FE50CD41A2F": "Acceptance — reduced data set",

  "637985B7-4F91-46E6-BD08-8980D92653E8": {
    "nom": "Acceptance of 21/08 — night",
    "description": "24 M iterations. Check the weather branch.",
    "etiquettes": { "ticket": "RX-142" }
  }
}
```

Deleting an entry restores the original name. At the end of every measurement, the tool
recalls the run's identifier and the two possible shapes, ready to be pasted.

## Pruning the tree

A complete call tree shows **everything** that ran: the start-up, the plumbing, the branches
nobody cares about today. What one passes on to somebody is often **one** branch. Two
gestures, under the *prune* button at the end of each line of the **Runs** tab:

| Gesture | What it does |
|---|---|
| **Cut this branch** | Removes this node and everything leaving it — **on this path only**. The same method called from elsewhere stays visible |
| **Start again from here** | Hides the levels above: the tree begins at this node |

The paths are written as in the folded-stacks file — the frames from the root to the node,
separated by `;`. A path that has become unfindable, because the measurement changed, is
ignored rather than applied wrongly.

**What pruning does not do: correct the measurement.** The percentages stay relative to the
measured total, the original files and [the exports](exports.md) do not move, and a band is a
permanent reminder of what is cut, with what is needed to restore it all. The overview's time
graph follows the same framing — two places in the page must not tell two stories.

## From the page

The **identity band**, under the run's name in the overview, fits on one line: the shortened
identifier, the name — editable in place, it is the view's title —, the description, the
labels, and the state of the entry. What cannot be edited takes up no room: the launch name
and the full identifier live in the tooltip of the field they explain.

Nothing editable is placed in the body of the page: **a report is read, it is not filled in**.
The gestures join the other buttons in the top bar, under **card**:

| Button | What it does |
|---|---|
| **save** | Writes this run's annotation beside its results. Appears only if the page is served by the tool, and is marked with a dot as long as something is left to send |
| **export** | Downloads the annotation file for the whole report — to drop beside the runs, or to send |
| **import** | Takes in an existing file: a whole `noms.json`, or one run's `config.json` |
| **JSON** | Shows what would be written, over the page, to re-read or copy it |

The same bar carries, under **exports**, the four formats meant for other tools. Those that
were not produced stay **named but switched off** there: a capability that lives only in a
command-line option does not exist for whoever reads the page. Clicking one gives the command
that produces it — see [Taking the result into another tool](exports.md).

## Caveats

- **The browser forgets.** In mode 1, the annotations live in the browser's local storage:
  they disappear with it, do not follow from one machine to another, and are not visible to
  others. That is why export exists.
- **The annotations are not in the exports.** [What goes out to another tool](exports.md) is
  the measurement, not what was said about it — a `perf` profile has nowhere to lodge a
  description.
- **The shared secret identifies nobody.** It says who may enter, not who wrote what: two
  people behind the same secret are indistinguishable in the annotation files. That is enough
  for a diagnostic tool; it is not enough for a register of decisions.
- **The server regenerates the page after every write.** On a very large report that takes a
  few seconds; it is done in the background, and several writes close together trigger only
  one more assembly, at the end.
- **Neither lock nor history.** Two people on the same run are told apart at write time, but
  nothing keeps the replaced version: it is seen at the moment of refusal, not afterwards.

## What to read next

- [Manual](mode-emploi.md) — every setting, including `--serve` and `--serve-host`
- [Reading the report](lire-le-rapport.md) — what the page shows, what it folds away
- [Taking the result into another tool](exports.md) — perf, cpuprofile, LCOV
