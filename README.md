# [JBang](https://www.jbang.dev/) Extension for [b<span style="color:orange">l</span>d](https://rife2.com/bld)

[![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![bld](https://img.shields.io/badge/2.3.0-FA9052?label=bld&labelColor=2392FF)](https://rife2.com/bld)
[![Release](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/releases/com/uwyn/rife2/bld-jbang/maven-metadata.xml?color=blue)](https://repo.rife2.com/#/releases/com/uwyn/rife2/bld-jbang)
[![Snapshot](https://flat.badgen.net/maven/v/metadata-url/repo.rife2.com/snapshots/com/uwyn/rife2/bld-jbang/maven-metadata.xml?label=snapshot)](https://repo.rife2.com/#/snapshots/com/uwyn/rife2/bld-jbang)
[![GitHub CI](https://github.com/rife2/bld-jbang/actions/workflows/bld.yml/badge.svg)](https://github.com/rife2/bld-jbang/actions/workflows/bld.yml)

To install the latest version, add the following to the `lib/bld/bld-wrapper.properties` file:

```properties
bld.extension-jbang=com.uwyn.rife2:bld-jbang
```

For more information, please refer to the [extensions](https://github.com/rife2/bld/wiki/Extensions) documentation.

## Execute a JBang command

For example, to execute a JBang script, add the following to your build file:

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

Then run the following command:

```
./bld jbang
```

The script will be executed using the currently installed instance of JBang.
To manually specify the location of the JBang home use the one of the
[jBangHome()](https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#jBangHome(java.lang.String))
methods.

- [View Example Project](https://github.com/rife2/bld-jbang/tree/main/example)

To set `trusts` before running a script, you could do something like:

```java
@BuildCommand(summary = "Runs JBang script")
public void jbang() throws Exception {
    var trusts = List.of("https://github.com/", "https://jbang.dev/");
    var op = new JBangOperation().fromProject(this);
    op.jBangArgs("trust", "add").jBangArgs(trusts).execute();
    op.reset();
    op.script("https://github.com/jbangdev/jbang-examples/blob/main/examples/helloworld.javall").execute();
}


```

Please check the [documentation](https://rife2.github.io/bld-jbang/rife/bld/extension/JBangOperation.html#method-summary-table)
for all available configuration options.
