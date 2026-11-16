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

By default, the extension uses an existing JBang installation when available,
or downloads the latest release into the sandbox when none is found. Downloading
requires a project.

To use a specific local installation:

```java
new JBangOperation()
        .fromProject(this)
        .jBangHome("/path/to/.jbang")
        .script("path/to/script.java")
        .execute();
```

To always use a downloaded JBang, ignoring any local installation:

```java
new JBangOperation()
        .fromProject(this)
        .useDownloadedJBang()
        .script("path/to/script.java")
        .execute();
```

See [`jBangHome(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#jBangHome(java.io.File)>)
and
[`useDownloadedJBang()`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#useDownloadedJBang()>)
for details.

## Pinning the JBang version

To download and use a specific JBang version via
[`version(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#version(java.lang.String)>),
regardless of any local installation:

```java
new JBangOperation()
        .fromProject(this)
        .version("0.143.0")
        .script("path/to/script.java")
        .execute();
```

## Setting JBang options

Common JBang options have dedicated setters such as:
[`verbose(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#verbose(boolean)>),
[`quiet(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#quiet(boolean)>),
[`offline(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#offline(boolean)>),
[`fresh(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#fresh(boolean)>),
[`insecure(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#insecure(boolean)>),
[`stacktrace(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#stacktrace(boolean)>),
[`config(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#config(java.lang.String)>),
[`netrc(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#netrc(java.lang.String)>),
etc.

```java
new JBangOperation()
        .fromProject(this)
        .offline(true)
        .fresh(true)
        .config("custom-jbang.properties")
        .script("path/to/script.java")
        .execute();
```

Anything else can be passed through [`jBangArgs(...)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#jBangArgs(java.util.Collection)>):

```java
new JBangOperation()
        .fromProject(this)
        .jBangArgs("--debug")
        .script("path/to/script.java")
        .execute();
```

## Controlling execution

The child JBang process can be tuned without changing the JBang arguments:

```java
new JBangOperation()
        .fromProject(this)
        .env("JAVA_HOME", "/path/to/jdk")
        .timeout(120)
        .inheritIO(false)
        .outputConsumer(System.out::println)
        .exitOnFailure(false)
        .script("path/to/script.java")
        .execute();
```

- **[`env(name, value)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#env(java.lang.String,java.lang.String)>)** — set environment variables for the JBang child.
- **[`timeout(seconds)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#timeout(long)>)** — execution timeout, in seconds. Default is `600`; use a negative
  value for no timeout. Zero is rejected.
- **[`inheritIO(boolean)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#inheritIO(boolean)>)** — let the child inherit the current JVM's I/O.
  Default is `true`.
- **[`outputConsumer(Consumer<String>)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#outputConsumer(java.util.function.Consumer)>)** — receive captured output
  when `inheritIO(false)`.
- **[`exitOnFailure(boolean)`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#exitOnFailure(boolean)>)** — throw on a non-zero JBang exit status.
  Default is `true`.

## Reusing an operation

Call [`reset()`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#reset()>)
to clear per-script settings while keeping the launcher, version pin, `timeout` and I/O configuration:

```java
var op = new JBangOperation()
        .fromProject(this)
        .jBangHome("/path/to/.jbang")
        .timeout(120);

op.script("scripts/first.java")
        .args("input.txt")
        .execute();

op.reset();

op.script("scripts/second.java")
        .args("other.txt")
        .execute();
```

Or just create a fresh operation per script — [`reset()`](<https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#reset()>)
is only a convenience.

## API Reference

Please check the [documentation](https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#method-summary-table)
for all available configuration options.