# [JBang](https://www.jbang.dev/) Extension for [b<span style="color:orange">l</span>d](https://rife2.com/bld)

[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![bld](https://img.shields.io/badge/3.0.1-FA9052?label=bld&labelColor=2392FF)](https://rife2.com/bld)
[![Release](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo.rife2.com%2Freleases%2Fcom%2Fuwyn%2Frife2%2Fbld-jbang%2Fmaven-metadata.xml&color=blue)](https://repo.rife2.com/#/releases/com/uwyn/rife2/bld-jbang)
[![Snapshot](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo.rife2.com%2Fsnapshots%2Fcom%2Fuwyn%2Frife2%2Fbld-jbang%2Fmaven-metadata.xml&label=snapshot)](https://repo.rife2.com/#/snapshots/com/uwyn/rife2/bld-jbang)
[![GitHub CI](https://github.com/rife2/bld-jbang/actions/workflows/bld.yml/badge.svg)](https://github.com/rife2/bld-jbang/actions/workflows/bld.yml)

To install the latest version, add the following to the `lib/bld/bld-wrapper.properties` file:

```properties
bld.extension-jbang=com.uwyn.rife2:bld-jbang
```

For more information, please refer to the [extensions](https://github.com/rife2/bld/wiki/Extensions) documentation.

## Execute a JBang script

```java
@BuildCommand(summary = "Runs JBang script")
public void jbang() throws Exception {
    new JBangOperation()
            .fromProject(this)
            .jBangArgs("--quiet")
            .script("path/to/script.java")
            .args("foo", "bar")
            .execute();
}
```

```
./bld jbang
```

- [View Example Project](https://github.com/rife2/bld-jbang/tree/main/example)

To set trusts before running a script:

```java
@BuildCommand(summary = "Runs JBang script")
public void jbang() throws Exception {
    var trusts = List.of("https://github.com/", "https://jbang.dev/");
    var op = new JBangOperation().fromProject(this);

    op.jBangArgs("trust", "add")
            .jBangArgs(trusts)
            .execute();

    op.reset();

    op.script("https://github.com/jbangdev/jbang-examples/blob/main/examples/helloworld.java")
            .execute();
}
```

## Choosing the JBang launcher

The extension resolves the JBang executable in this order:

1. A specific `version(...)`, when set — that version is downloaded into the
   sandbox and used, even when a local JBang is available.
2. An explicit `jBangHome(...)`, or the `JBANG_HOME` environment variable.
3. A downloaded distribution in the sandbox, when a project is available and
   neither of the above is set.
4. The `jbang` executable on the `PATH`.

Without `fromProject(...)`, steps 1 and 3 have nowhere to download into, so
the launcher always resolves via `jBangHome(...)`/`JBANG_HOME` or the `PATH`.

To use a specific installation:

```java
new JBangOperation()
        .fromProject(this)
        .jBangHome("/path/to/.jbang")
        .script("path/to/script.java")
        .execute();
```

To force the sandbox-downloaded JBang even when `JBANG_HOME` is set (useful
for reproducible builds):

```java
new JBangOperation()
        .fromProject(this)
        .useDownloadedJBang()
        .script("path/to/script.java")
        .execute();
```

`useDownloadedJBang()` and `jBangHome(...)` override each other — whichever is
called last wins, so the call order relative to `fromProject(...)` doesn't
matter.

## Pinning the JBang version

To download and use a specific JBang version, regardless of any local
installation:

```java
new JBangOperation()
        .fromProject(this)
        .version("0.143.0")
        .script("path/to/script.java")
        .execute();
```

When `version(...)` is not set, the latest release is fetched from the JBang
release server the first time the sandbox is needed. The distribution is
cached and reused on subsequent builds.

Note that `version(...)` requires a project to download into; without
`fromProject(...)`, the explicit version is silently ignored and the
`jBangHome`/`PATH` fallback is used instead.

## Setting JBang options

Common JBang options have dedicated setters: `verbose(...)`, `quiet(...)`,
`offline(...)`, `fresh(...)`, `insecure(...)`, `stacktrace(...)`,
`config(...)`, `netrc(...)`, `noNetrc(...)`.

```java
new JBangOperation()
        .fromProject(this)
        .offline(true)
        .fresh(true)
        .config("custom-jbang.properties")
        .script("path/to/script.java")
        .execute();
```

Anything else can be passed through `jBangArgs(...)`:

```java
new JBangOperation()
        .fromProject(this)
        .jBangArgs("--debug")
        .script("path/to/script.java")
        .execute();
```

## Reusing an operation

`reset()` clears the per-script settings — `script`, `args`, `jBangArgs`,
`env`, and `exitOnFailure` — while keeping the project configuration
(`workDir`, `jBangHome`, `useDownloadedJBang`, `timeout`, `inheritIO`,
`outputConsumer`).

```java
var op = new JBangOperation()
        .fromProject(this)
        .jBangHome("/path/to/.jbang")
        .timeout(120);

// First invocation
op.script("scripts/first.java")
        .args("input.txt")
        .execute();

// Clear script-specific state, keep workDir, jBangHome, timeout, ...
op.reset();

// Second invocation reuses the configuration
op.script("scripts/second.java")
        .args("other.txt")
        .execute();
```

If you'd rather create a fresh operation for each script, that works too —
`reset()` is only a convenience for avoiding repeated configuration.

## API Reference

Please check the [documentation](https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#method-summary-table)
for all available configuration options.