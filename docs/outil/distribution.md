# Distributing the tool

> Starting question: *how does one put this tool into someone else's hands?*

## What the tool is today

**A single jar**, `runtime-xray.jar`, with no dependency outside the JDK:
`java -jar runtime-xray.jar --config my-project.conf`. No bash, no Python.

It was not always so — and it is that question that set off the rewrite. The original
version was a shell script plus a Python script: two more prerequisites on a machine where
**Java is necessarily present**, and no Windows portability at all. The argument "if it is
distributed as one file, it may as well be a jar" held.

The same jar also serves the report: `--serve` turns it into a small HTTP server, with
nothing else to install. Distributing the tool and **hosting the results for a team**
therefore take a single file — see [Annotating the runs](annotations.md) and [Publishing the
report](../resultat/publication.md), which compares it with the other channels.

## The three routes, compared

| Route | Effort for whoever uses it | When to choose it |
|---|---|---|
| **Jar attached to the code repository** *(chosen)* | one `curl`, nothing to install, no clone | By default |
| **Maven repository, public or internal** | the same, served by the pipe that already exists | A closed network, where the Maven mirror is often the only open channel |
| **Building from source** | clone, install Maven, compile | Only to modify the tool |

⚠️ The point often missed: **on a closed network, a public repository is useless.** Putting
the jar on an internal mirror gives exactly the same benefit, with no account, no signature,
no publication process.

The interest of a Maven repository, whichever it is, is not "being a library" — nobody will
put this tool in a `<dependency>`. It is **going through the pipe that already exists**:
behind a firewall, the Maven mirror is often the only open channel, and it is already how the
tool fetches its analysis components.

## What a public Maven repository demands

The process was carried through to the end once, on version `1.0.0`, to find out what it
really costs:

- a **namespace** one proves ownership of, which supposes proving an account or a domain;
- artefacts **signed with GPG**, the key published on a public server, together with the
  `sources` and `javadoc` jars;
- publication through a portal, with strict validation rules and a bundle to review before
  confirming;
- **immutable** versions: nothing is corrected afterwards, only one more version.

Conclusion: the price is real, and the benefit — **nothing to do** for whoever tries the tool
— is obtained just as well by attaching the jar to the code repository. That is the route
chosen: no clone, no JDK to line up, no build to get right, no account to create, one `curl`
and one `java -jar`. A Maven repository keeps one advantage of its own, but only one: on a
closed network, it is sometimes the only open channel.

## What the rewrite in Java brought

- **one single file** to carry, instead of three plus two interpreters;
- **no dependency** outside the JDK — checkable: the module has only JUnit, in test scope;
- **Windows portability**, for free, which was missing;
- incidentally, **real test coverage** on the assembly logic: reading the coverage XML,
  folding frames away, unbroken links in the produced page. A shell script is not tested like
  that.
