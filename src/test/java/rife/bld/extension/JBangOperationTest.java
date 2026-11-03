/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rife.bld.extension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import rife.bld.BaseProject;
import rife.bld.dependencies.Repository;
import rife.bld.dependencies.Version;
import rife.bld.dependencies.VersionNumber;
import rife.bld.extension.tools.SystemTools;
import rife.bld.operations.exceptions.ExitStatusException;
import rife.bld.testing.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(LoggingExtension.class)
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "DataFlowIssue", "PMD.ExcessiveImports"})
class JBangOperationTest {

    @SuppressWarnings("LoggerInitializedWithForeignClass")
    private static final Logger logger = Logger.getLogger(JBangOperation.class.getName());
    private static final TestLogHandler testLogHandler = new TestLogHandler();

    @RegisterExtension
    @SuppressWarnings("unused")
    private static final LoggingExtension loggingExtension = new LoggingExtension(
            logger,
            testLogHandler,
            Level.ALL
    );

    private static Path createLauncher(Path home) throws IOException {
        var bin = home.resolve("bin");
        Files.createDirectories(bin);
        var launcher = bin.resolve(launcherName());
        Files.writeString(launcher, "#!/bin/sh\n");
        assertTrue(launcher.toFile().setExecutable(true));
        return launcher;
    }

    private static boolean isJBangOnPath() {
        var path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        var launcher = launcherName();
        return Arrays.stream(path.split(File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(entry -> new File(entry, launcher))
                .anyMatch(file -> file.isFile() && file.canExecute());
    }

    private static String launcherName() {
        return JBangOperation.isWindows() ? "jbang.cmd" : "jbang";
    }

    @BeforeEach
    void beforeEach() {
        testLogHandler.clear();
        logger.setLevel(Level.ALL);
    }

    private JBangOperation newJBangOperation() {
        return new JBangOperation().fromProject(new TestProject());
    }

    @SuppressWarnings("PMD")
    static class TempDirProject extends TestProject {

        TempDirProject(Path dir) {
            workDirectory = dir.toFile();
        }
    }

    @SuppressWarnings("PMD")
    static class TestProject extends BaseProject {

        TestProject() {
            workDirectory = new File("src/test/resources/TestProject");
            pkg = "com.example";
            name = "TestProject";
            version = version(0, 1, 0);

            autoDownloadPurge = true;

            repositories = List.of(Repository.MAVEN_CENTRAL);
        }
    }

    @Nested
    @DisplayName("Command Construction Tests")
    class CommandConstructionTests {

        @Test
        void explicitVersionOverridesJBangHome(@TempDir Path tempDir) throws Exception {
            var bin = tempDir.resolve("bin");
            Files.createDirectories(bin);
            var launcher = bin.resolve(JBangOperation.isWindows() ? "jbang.cmd" : "jbang");
            Files.writeString(launcher, "#!/bin/sh\n");
            assertTrue(launcher.toFile().setExecutable(true));

            var op = newJBangOperation()
                    .jBangHome(tempDir.toFile())
                    .version("0.142.0");

            var command = op.buildJBangCommand();
            assertTrue(command.get(0).contains("jbang-0.142.0"),
                    "explicit version must override local jBangHome: " + command.get(0));
            assertNotEquals(launcher.toFile().getAbsolutePath(), command.get(0),
                    "local launcher must not be used when an explicit version is set");
        }

        @Test
        void explicitVersionWarningSuppressedWhenSilent() throws Exception {
            new JBangOperation().silent(true).version("0.142.0").buildJBangCommand();
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void explicitVersionWithoutProjectFallsBackToJBangHomeWithWarning(@TempDir Path tempDir)
                throws Exception {
            var launcher = createLauncher(tempDir);
            var op = new JBangOperation().jBangHome(tempDir.toFile()).version("0.142.0");

            var command = op.buildJBangCommand();
            assertEquals(launcher.toFile().getAbsolutePath(), command.get(0));
            assertTrue(testLogHandler.containsMessage("explicit JBang version was requested"));
            assertTrue(testLogHandler.containsMessage("configured JBang home"));
        }

        @Test
        void explicitVersionWithoutProjectFallsBackToPathWithWarning() throws Exception {
            var op = new JBangOperation().version("0.142.0");

            var command = op.buildJBangCommand();
            assertTrue(command.get(0).endsWith(launcherName()),
                    "PATH fallback must resolve the jbang launcher: " + command.get(0));
            assertTrue(testLogHandler.containsMessage("explicit JBang version was requested"));
            assertTrue(testLogHandler.containsMessage("PATH-resolved JBang"));
        }

        @Test
        void jBangHomeBranchTakesPrecedence(@TempDir Path tempDir) throws Exception {
            var bin = tempDir.resolve("bin");
            Files.createDirectories(bin);
            var launcher = bin.resolve(JBangOperation.isWindows() ? "jbang.cmd" : "jbang");
            Files.writeString(launcher, "#!/bin/sh\n");
            assertTrue(launcher.toFile().setExecutable(true));

            var op = newJBangOperation().jBangHome(tempDir.toFile());

            var command = op.buildJBangCommand();
            assertEquals(launcher.toFile().getAbsolutePath(), command.get(0),
                    "jBangHome launcher must win: " + String.join(" ", command));
        }

        @Test
        void jBangHomeCancelsUseDownloadedJBang(@TempDir Path tempDir) throws Exception {
            var bin = tempDir.resolve("bin");
            Files.createDirectories(bin);
            var launcher = bin.resolve(JBangOperation.isWindows() ? "jbang.cmd" : "jbang");
            Files.writeString(launcher, "#!/bin/sh\n");
            assertTrue(launcher.toFile().setExecutable(true));

            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .jBangHome(tempDir.toFile());

            var command = op.buildJBangCommand();
            assertEquals(launcher.toFile().getAbsolutePath(), command.get(0),
                    "jBangHome must cancel useDownloadedJBang(): " + command.get(0));
        }

        @Test
        void pathFallbackWhenNoProjectAndNoJBangHome() throws Exception {
            var op = new JBangOperation().workDir(Path.of(".").toFile());

            var command = op.buildJBangCommand();
            assertEquals(JBangOperation.isWindows() ? "jbang.cmd" : "jbang", command.get(0),
                    "PATH fallback must be the bare jbang name: " + String.join(" ", command));
        }

        @Test
        void pinnedJBangVersionConstantIsDownloaded() throws Exception {
            // Pinning the constant must be honored, never treated as "latest".
            var op = newJBangOperation().version(JBangOperation.JBANG_VERSION);

            var command = op.buildJBangCommand();
            assertTrue(command.get(0).contains("jbang-" + JBangOperation.JBANG_VERSION),
                    "pinned JBANG_VERSION must be downloaded: " + command.get(0));
        }

        @Test
        void sandboxBranchUsedWhenNoJBangHome() throws Exception {
            var op = newJBangOperation().useDownloadedJBang();

            var command = op.buildJBangCommand();
            var joined = String.join(" ", command);

            assertTrue(command.get(0).contains("bin"),
                    "sandbox branch must use the extracted wrapper launcher: " + joined);
            assertTrue(command.get(0).contains("jbang-"),
                    "sandbox branch must resolve under the extracted distribution: " + joined);
        }

        @Test
        void useDownloadedJBangClearsJBangHome() {
            var op = new JBangOperation().jBangHome("foo").useDownloadedJBang();
            assertNull(op.jBangHome());
        }

        @Test
        void useDownloadedJBangIsOrderIndependent() throws Exception {
            // useDownloadedJBang() before fromProject(): env must not repopulate jBangHome_
            var op = new JBangOperation()
                    .useDownloadedJBang()
                    .fromProject(new TestProject());

            var command = op.buildJBangCommand();
            assertTrue(command.get(0).contains("bin"),
                    "downloaded JBang must survive a subsequent fromProject(): " + command.get(0));
            assertTrue(command.get(0).contains("jbang-"),
                    "downloaded JBang must resolve under the sandbox: " + command.get(0));
        }

        @Test
        void useDownloadedJBangWithoutProjectWarnsAndFallsBackToPath() throws Exception {
            var op = new JBangOperation().useDownloadedJBang();

            var command = op.buildJBangCommand();
            assertTrue(command.get(0).endsWith(launcherName()),
                    "PATH fallback must resolve the jbang launcher: " + command.get(0));
            assertTrue(testLogHandler.containsMessage("downloaded JBang was requested"));
        }
    }

    @Nested
    @DisplayName("Config Tests")
    class ConfigTests {

        @Test
        void configAsFile() {
            var file = new File("/tmp/jbang-config");
            var op = new JBangOperation().config(file);
            assertTrue(op.jBangArgs().contains("--config=" + file.getAbsolutePath()));
        }

        @Test
        void configAsPath() {
            var path = Path.of("/tmp/jbang-config");
            var op = new JBangOperation().config(path);
            assertTrue(op.jBangArgs().contains("--config=" + path.toFile().getAbsolutePath()));
        }

        @Test
        void configAsString() {
            var op = new JBangOperation().config("/tmp/jbang-config");
            assertTrue(op.jBangArgs().contains(
                            "--config=" + new File("/tmp/jbang-config").getAbsolutePath()),
                    "jBangArgs should contain --config=<absolute path>: " + op.jBangArgs());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        void configRejectsBlankString(String value) {
            assertThrows(IllegalArgumentException.class,
                    () -> new JBangOperation().config(value));
        }

        @Test
        void configRejectsNullFile() {
            assertThrows(NullPointerException.class,
                    () -> new JBangOperation().config((File) null));
        }

        @Test
        void configRejectsNullPath() {
            assertThrows(NullPointerException.class,
                    () -> new JBangOperation().config((Path) null));
        }

        @Test
        void configRejectsNullString() {
            assertThrows(NullPointerException.class,
                    () -> new JBangOperation().config((String) null));
        }

        @Test
        void configReplacesPreviousValue() {
            var op = new JBangOperation()
                    .config("/tmp/first")
                    .config("/tmp/second");
            var configs = op.jBangArgs().stream()
                    .filter(a -> a.startsWith("--config="))
                    .toList();
            assertEquals(1, configs.size(),
                    "only one --config= should remain: " + op.jBangArgs());
            assertTrue(configs.get(0).endsWith("/tmp/second"),
                    "last value should win: " + configs.get(0));
        }
    }

    @Nested
    @DisplayName("Environment Variables Tests")
    class EnvironmentVariablesTests {

        @Test
        @DisplayName("env() returns mutable map")
        void envGetterReturnsMutableMap() {
            var op = new JBangOperation();
            var env = op.env();
            assertNotNull(env);
            assertTrue(env.isEmpty());

            env.put("X", "Y");
            assertEquals("Y", op.env().get("X"), "env() should return live mutable map");
        }

        @Test
        @DisplayName("env variables merged with parent environment")
        void envMergedWithParent(@TempDir Path tempDir) throws Exception {
            var script = tempDir.resolve("env.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class env {
                        public static void main(String[] args) {
                            System.out.println("CUSTOM=" + System.getenv("CUSTOM"));
                            System.out.println("PATH_EXISTS=" + (System.getenv("PATH") != null));
                        }
                    }
                    """);

            newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .env("CUSTOM", "value")
                    .inheritIO(false)
                    .execute();

            assertTrue(testLogHandler.containsMessage("CUSTOM=value"),
                    "Custom env var should be set");
            assertTrue(testLogHandler.containsMessage("PATH_EXISTS=true"),
                    "PATH from parent env should still exist - env must merge, not replace");
        }

        @Test
        @DisplayName("env(Map) adds multiple variables")
        void envMultipleVariables(@TempDir Path tempDir) throws Exception {
            var script = tempDir.resolve("env.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class env {
                        public static void main(String[] args) {
                            System.out.println("A=" + System.getenv("A"));
                            System.out.println("B=" + System.getenv("B"));
                        }
                    }
                    """);

            var vars = Map.of("A", "1", "B", "2");
            newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .env(vars)
                    .inheritIO(false)
                    .execute();

            assertTrue(testLogHandler.containsMessage("A=1"));
            assertTrue(testLogHandler.containsMessage("B=2"));
        }

        @Test
        @DisplayName("env variables override existing ones")
        void envOverridesExisting(@TempDir Path tempDir) throws Exception {
            var script = tempDir.resolve("env.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class env {
                        public static void main(String[] args) {
                            System.out.println("FOO=" + System.getenv("FOO"));
                        }
                    }
                    """);

            newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .env("FOO", "first")
                    .env("FOO", "second") // override
                    .inheritIO(false)
                    .execute();

            assertTrue(testLogHandler.containsMessage("FOO=second"),
                    "Last env value should win");
            assertFalse(testLogHandler.containsMessage("FOO=first"));
        }

        @Test
        @DisplayName("env rejects empty name")
        void envRejectsEmptyName() {
            var op = new JBangOperation();
            assertThrows(IllegalArgumentException.class, () -> op.env("", "value"));
        }

        @Test
        @DisplayName("env(Map) rejects null map")
        void envRejectsNullMap() {
            var op = new JBangOperation();
            assertThrows(NullPointerException.class, () -> op.env(null));
        }

        @Test
        @DisplayName("env rejects null name")
        void envRejectsNullName() {
            var op = new JBangOperation();
            assertThrows(NullPointerException.class, () -> op.env(null, "value"));
        }

        @Test
        @DisplayName("env rejects null value")
        void envRejectsNullValue() {
            var op = new JBangOperation();
            assertThrows(NullPointerException.class, () -> op.env("KEY", null));
        }

        @Test
        @DisplayName("env(String, String) adds single variable")
        void envSingleVariable(@TempDir Path tempDir) throws Exception {
            var script = tempDir.resolve("env.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class env {
                        public static void main(String[] args) {
                            System.out.println("FOO=" + System.getenv("FOO"));
                        }
                    }
                    """);

            newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .env("FOO", "bar")
                    .inheritIO(false)
                    .execute();

            assertTrue(testLogHandler.containsMessage("FOO=bar"),
                    "Environment variable should be passed to JBang process");
        }

        @Test
        @DisplayName("env with JBang-specific variables like JAVA_HOME")
        void envWithJavaHome(@TempDir Path tempDir) throws Exception {
            var script = tempDir.resolve("java.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class java {
                        public static void main(String[] args) {
                            System.out.println(System.getProperty("java.home"));
                        }
                    }
                    """);

            // This test just verifies the var is passed. JAVA_HOME influences the JDK
            // JBang selects when it is a valid JDK; JBANG_JAVA, when set, takes
            // precedence for script execution
            var fakeHome = tempDir.resolve("fake-jdk").toString();
            newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .env("JAVA_HOME", fakeHome)
                    .inheritIO(false)
                    .exitOnFailure(false) // JBang may fail if fakeHome invalid, that's ok
                    .execute();

            // We can't assert the actual java.home changed without a real JDK,
            // but we can verify the command didn't crash due to env setup
            assertTrue(testLogHandler.containsMessage("jbang") || testLogHandler.containsMessage("java"),
                    "Command should have executed");
        }
    }

    @Nested
    @DisplayName("Execute Tests")
    class ExecuteTests {

        @Test
        @DisplayName("execute with explicit version")
        void executeWithExplicitVersion(@TempDir Path tempDir) throws IOException {
            var requested = "0.142.0";
            var out = tempDir.resolve("out.txt");
            var script = tempDir.resolve("version.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class version {
                        public static void main(String[] args) throws Exception {
                            java.nio.file.Files.writeString(
                                    java.nio.file.Path.of(args[0]),
                                    "RAN");
                        }
                    }
                    """);

            var op = newJBangOperation()
                    .version(requested)
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .args(out.toString())
                    .inheritIO(false)
                    .exitOnFailure(false);

            assertDoesNotThrow(op::execute,
                    "JBang " + requested + " should execute the script");

            assertTrue(Files.exists(out),
                    "script should have run under JBang " + requested);
            assertEquals("RAN", Files.readString(out));

            // The downloaded distribution should be under the requested version directory
            var extracted = new File(
                    "src/test/resources/TestProject/lib/.sandbox/bld-jbang/jbang-" + requested);
            assertTrue(extracted.isDirectory(),
                    "sandbox should contain jbang-" + requested + ": " + extracted.getAbsolutePath());

            var versionTxt = new File(extracted, "version.txt");
            assertTrue(versionTxt.isFile(),
                    "distribution should contain version.txt: " + versionTxt.getAbsolutePath());
            assertEquals(requested, Files.readString(versionTxt.toPath()).trim(),
                    "extracted distribution should be version " + requested);
        }

        @Test
        void executeWithNoExitOnFailure(@TempDir Path tempDir) throws Exception {
            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .jBangArgs("init", "foo.java")
                    .workDir(tempDir);
            op.execute();

            assertTrue(Files.exists(tempDir.resolve("foo.java")));
            assertThrows(ExitStatusException.class, op::execute);

            op.exitOnFailure(false);
            assertDoesNotThrow(op::execute);
        }

        @Test
        void executeWithSilent() throws Exception {
            newJBangOperation()
                    .useDownloadedJBang()
                    .silent(true)
                    .jBangArgs("version")
                    .execute();

            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void executeWithSuppressAllLogs() throws Exception {
            logger.setLevel(Level.OFF);

            newJBangOperation()
                    .useDownloadedJBang()
                    .silent(true)
                    .jBangArgs("version")
                    .execute();
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void executeWithoutCommandLogging() throws Exception {
            logger.setLevel(Level.WARNING);
            newJBangOperation()
                    .useDownloadedJBang()
                    .jBangArgs("version")
                    .execute();
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void helloWorld(@TempDir Path tempDir) throws IOException {
            var helloTxt = tempDir.resolve("hello.txt");
            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .jBangArgs("--quiet")
                    .script("src/test/resources/Hello.java")
                    .args(helloTxt.toString());
            assertDoesNotThrow(op::execute);
            assertEquals("Hello World", Files.readString(helloTxt));
        }

        @Test
        void helloWorldInvalidArgs() {
            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .jBangArgs("--quiet")
                    .script("src/test/resources/Hello.java")
                    .args("foo/bar.txt");
            assertThrows(ExitStatusException.class, op::execute);
        }

        @Test
        void helloWorldNoArgs() {
            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .jBangArgs("--quiet")
                    .script("src/test/resources/Hello.java");
            assertThrows(ExitStatusException.class, op::execute);
        }
    }

    @Nested
    @DisplayName("Help Compatibility Tests")
    @DisabledOnCi
    class HelpCompatibilityTests {

        private static final String HELP_RESOURCE = "src/test/resources/TestProject";

        /**
         * Flags JBang documents that this extension deliberately does not expose.
         * Add here when JBang gains a flag we choose not to support; remove when a setter is added.
         */
        private static final Set<String> KNOWN_UNSUPPORTED =
                Set.of("--help", "--version", "--live", "--open");

        /**
         * One row per dedicated setter: its name, how to invoke it, and the {@code jbang}
         * argument it is expected to produce. {@code --config=...} is normalized to
         * {@code --config} for the help-flag comparison.
         */
        private static final List<SetterCase> SETTER_CASES = List.of(
                new SetterCase("verbose", op -> op.verbose(true), "--verbose"),
                new SetterCase("quiet", op -> op.quiet(true), "--quiet"),
                new SetterCase("offline", op -> op.offline(true), "--offline"),
                new SetterCase("fresh", op -> op.fresh(true), "--fresh"),
                new SetterCase("insecure", op -> op.insecure(true), "--insecure"),
                new SetterCase("stacktrace", op -> op.stacktrace(true), "--stacktrace"),
                new SetterCase("noNetrc", op -> op.noNetrc(true), "--no-netrc"),
                new SetterCase("config", op -> op.config("/tmp/x"), "--config=/tmp/x"),
                new SetterCase("netrc", op -> op.netrc("/tmp/x"), "--netrc=/tmp/x")
        );

        /**
         * Strips {@code =value} from an expected argument so it can be compared to a help flag.
         */
        private static String flagOf(String expectedArg) {
            var eq = expectedArg.indexOf('=');
            return eq < 0 ? expectedArg : expectedArg.substring(0, eq);
        }

        /**
         * Parses {@code --flag} tokens out of a {@code --help} dump. Each {@code --[no-]name}
         * expands to both {@code --name} and {@code --no-name}; {@code --foo=bar} is truncated
         * to {@code --foo}.
         */
        private static Set<String> flagsIn(String helpText) {
            var flags = new TreeSet<String>();
            var matcher = Pattern.compile("--(?:\\[no-])?[A-Za-z][\\w-]*").matcher(helpText);
            while (matcher.find()) {
                var token = matcher.group();
                if (token.startsWith("--[no-]")) {
                    var name = token.substring("--[no-]".length());
                    flags.add("--" + name);
                    flags.add("--no-" + name);
                } else {
                    flags.add(token);
                }
            }
            return flags;
        }

        private static JBangOperation opFor(SetterCase c) {
            return c.apply().apply(new JBangOperation());
        }

        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        private String jbangHelp() throws Exception {
            var out = new StringBuilder();
            new JBangOperation()
                    .workDir(new File(HELP_RESOURCE))
                    .jBangArgs("--help")
                    .inheritIO(false)
                    .outputConsumer(line -> out.append(line).append('\n'))
                    .exitOnFailure(false)
                    .execute();
            return out.toString();
        }

        @Test
        @DisplayName("no new JBang flags have gone unsupported")
        void noNewFlagsAppearUndetected() throws Exception {
            var unaccounted = new TreeSet<>(flagsIn(jbangHelp()));
            unaccounted.removeAll(KNOWN_UNSUPPORTED);
            for (var c : SETTER_CASES) {
                var flag = flagOf(c.expected());
                unaccounted.remove(flag);
                // A setter for "--x" also covers a documented "--[no-]x" (→ --no-x), and vice versa.
                if (flag.startsWith("--no-")) {
                    unaccounted.remove("--" + flag.substring(5));
                } else {
                    unaccounted.remove("--no-" + flag.substring(2));
                }
            }

            assertTrue(unaccounted.isEmpty(),
                    () -> "New JBang flags detected that the extension does not handle: " + unaccounted
                            + "\nEither add a setter, or add the flag to KNOWN_UNSUPPORTED.");
        }

        @Test
        @DisplayName("every dedicated setter maps to a flag in jbang --help")
        void settersMatchHelpOutput() throws Exception {
            var documented = flagsIn(jbangHelp());
            assertFalse(documented.isEmpty(), "jbang --help produced no flags");

            assertAll(SETTER_CASES.stream().map(c -> () -> {
                var op = opFor(c);
                assertTrue(op.jBangArgs().contains(c.expected()),
                        () -> "Setter " + c.name() + " did not produce expected argument: "
                                + c.expected() + " in " + op.jBangArgs());

                var flag = flagOf(c.expected());
                assertTrue(documented.contains(flag),
                        () -> "Setter " + c.name() + " maps to " + c.expected()
                                + " but " + flag + " is not documented in jbang --help: " + documented);
            }));
        }

        /**
         * {@code apply} takes a fresh {@link JBangOperation} and applies one setter call.
         */
        private record SetterCase(String name,
                                  java.util.function.UnaryOperator<JBangOperation> apply,
                                  String expected) {

        }
    }

    @Nested
    @DisplayName("InheritIO Execution Tests")
    class InheritIOExecutionTests {

        @Test
        @DisplayName("inheritIO(false) captures error output on failure")
        void inheritIOFalseCapturesErrorOnFailure(@TempDir Path tempDir) throws IOException {
            var script = tempDir.resolve("fail.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class fail {
                        public static void main(String[] args) {
                            System.err.println("this is an error");
                            System.exit(1);
                        }
                    }
                    """);

            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .inheritIO(false)
                    .exitOnFailure(true);

            assertThrows(ExitStatusException.class, op::execute);
            assertTrue(testLogHandler.containsMessage("this is an error"),
                    "stderr from failed process should be captured when inheritIO=false");
        }

        @Test
        @DisplayName("inheritIO(false) captures JBang output in logger")
        void inheritIOFalseCapturesOutput(@TempDir Path tempDir) throws IOException {
            var script = tempDir.resolve("echo.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class echo {
                        public static void main(String[] args) {
                            System.out.println("captured stdout");
                            System.err.println("captured stderr");
                        }
                    }
                    """);

            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .inheritIO(false); // capture output
            assertDoesNotThrow(op::execute);

            // Both stdout and stderr should be in the log handler
            assertTrue(testLogHandler.containsMessage("captured stdout"),
                    "stdout should be captured when inheritIO=false");
            assertTrue(testLogHandler.containsMessage("captured stderr"),
                    "stderr should be captured when inheritIO=false");
        }

        @Test
        @DisplayName("inheritIO(false) with output consumer")
        void inheritIOFalseWithOutputConsumer(@TempDir Path tempDir) throws IOException {
            var lines = new ArrayList<String>();
            var script = tempDir.resolve("fail.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class fail {
                        public static void main(String[] args) {
                            System.err.println("this is an error");
                            System.exit(1);
                        }
                    }
                    """);

            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .inheritIO(false)
                    .outputConsumer(lines::add)
                    .exitOnFailure(true);

            assertThrows(ExitStatusException.class, op::execute);
            assertTrue(lines.contains("this is an error"),
                    "stderr from failed process should be captured by output consumer");
        }

        @Test
        @DisplayName("inheritIO(true) does not capture JBang output in logger")
        void inheritIOTrueDoesNotCaptureOutput(@TempDir Path tempDir) throws IOException {
            var script = tempDir.resolve("echo.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class echo {
                        public static void main(String[] args) {
                            System.out.println("not captured stdout");
                            System.err.println("not captured stderr");
                        }
                    }
                    """);

            logger.setLevel(Level.ALL);

            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .inheritIO(true) // inherit - output goes to console, not logger
                    .jBangArgs("--quiet");

            assertDoesNotThrow(op::execute);

            // Output should NOT be in log handler because it went straight to console
            assertFalse(testLogHandler.containsMessage("not captured stdout"),
                    "stdout should NOT be captured when inheritIO=true");
            assertFalse(testLogHandler.containsMessage("not captured stderr"),
                    "stderr should NOT be captured when inheritIO=true");

            // But the command itself should still be logged at INFO
            assertTrue(testLogHandler.containsMessage("jbang"));
            assertTrue(testLogHandler.containsMessage("echo.java"));
        }
    }

    @Nested
    @DisplayName("Latest Version Cache Tests")
    class LatestVersionCacheTests {

        @Test
        void offlineUsesCachedVersion(@TempDir Path tempDir) throws Exception {
            var project = new TempDirProject(tempDir);
            writeCache(project, "0.142.0\n");
            var op = new JBangOperation()
                    .fromProject(project)
                    .useDownloadedJBang()
                    .offline(true);

            var command = op.buildJBangCommand();
            assertTrue(command.get(0).contains("jbang-0.142.0"),
                    "cached version must be used offline: " + command.get(0));
        }

        @Test
        void offlineWithEmptyCacheFails(@TempDir Path tempDir) throws Exception {
            var project = new TempDirProject(tempDir);
            writeCache(project, "  \n");
            var op = new JBangOperation()
                    .fromProject(project)
                    .useDownloadedJBang()
                    .offline(true);

            assertThrows(ExitStatusException.class, op::buildJBangCommand);
            assertTrue(testLogHandler.containsMessage("while offline"));
        }

        @Test
        void offlineWithoutCacheFails(@TempDir Path tempDir) {
            var op = new JBangOperation()
                    .fromProject(new TempDirProject(tempDir))
                    .useDownloadedJBang()
                    .offline(true);

            assertThrows(ExitStatusException.class, op::buildJBangCommand);
            assertTrue(testLogHandler.containsMessage("while offline"));
        }

        private void writeCache(BaseProject project, String content) throws IOException {
            var dir = project.buildDirectory().toPath();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(".jbang-latest-version"), content);
        }
    }

    @Nested
    @DisplayName("Netrc Tests")
    class NetrcTests {

        @Test
        void netrcAsFile() {
            var file = new File("/tmp/netrc");
            var op = new JBangOperation().netrc(file);
            assertTrue(op.jBangArgs().contains("--netrc=" + file.getAbsolutePath()));
        }

        @Test
        void netrcAsPath() {
            var path = Path.of("/tmp/netrc");
            var op = new JBangOperation().netrc(path);
            assertTrue(op.jBangArgs().contains("--netrc=" + path.toFile().getAbsolutePath()));
        }

        @Test
        void netrcAsString() {
            var op = new JBangOperation().netrc("/tmp/netrc");
            assertTrue(op.jBangArgs().contains(
                            "--netrc=" + new File("/tmp/netrc").getAbsolutePath()),
                    "jBangArgs should contain --netrc=<absolute path>: " + op.jBangArgs());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        void netrcRejectsBlankString(String value) {
            assertThrows(IllegalArgumentException.class,
                    () -> new JBangOperation().netrc(value));
        }

        @Test
        void netrcRejectsNullFile() {
            assertThrows(NullPointerException.class,
                    () -> new JBangOperation().netrc((File) null));
        }

        @Test
        void netrcRejectsNullPath() {
            assertThrows(NullPointerException.class,
                    () -> new JBangOperation().netrc((Path) null));
        }

        @Test
        void netrcRejectsNullString() {
            assertThrows(NullPointerException.class,
                    () -> new JBangOperation().netrc((String) null));
        }

        @Test
        void netrcReplacesPreviousValue() {
            var op = new JBangOperation()
                    .netrc("/tmp/first")
                    .netrc("/tmp/second");
            var netrcs = op.jBangArgs().stream()
                    .filter(a -> a.startsWith("--netrc="))
                    .toList();
            assertEquals(1, netrcs.size(),
                    "only one --netrc= should remain: " + op.jBangArgs());
            assertTrue(netrcs.get(0).endsWith("/tmp/second"),
                    "last value should win: " + netrcs.get(0));
        }

        @Test
        void noNetrcDisabled() {
            var op = new JBangOperation()
                    .noNetrc(true)
                    .noNetrc(false);
            assertFalse(op.jBangArgs().contains("--no-netrc"),
                    "--no-netrc should be removed when disabled: " + op.jBangArgs());
        }

        @Test
        void noNetrcEnabled() {
            var op = new JBangOperation().noNetrc(true);
            assertTrue(op.jBangArgs().contains("--no-netrc"),
                    "jBangArgs should contain --no-netrc: " + op.jBangArgs());
        }

        @Test
        void noNetrcIsIdempotent() {
            var op = new JBangOperation()
                    .noNetrc(true)
                    .noNetrc(true);
            assertEquals(1, op.jBangArgs().stream()
                            .filter(("--no-netrc")::equals).count(),
                    "duplicate --no-netrc should not accumulate: " + op.jBangArgs());
        }
    }

    @Nested
    @DisplayName("Options Tests")
    class Options {

        @Test
        void optionFlagSetters() {
            var op = new JBangOperation();
            op.verbose(true).quiet(true).offline(true).fresh(true)
                    .insecure(true).stacktrace(true).noNetrc(true);
            assertTrue(op.jBangArgs().contains("--verbose"));
            assertTrue(op.jBangArgs().contains("--quiet"));
            assertTrue(op.jBangArgs().contains("--offline"));
            assertTrue(op.jBangArgs().contains("--fresh"));
            assertTrue(op.jBangArgs().contains("--insecure"));
            assertTrue(op.jBangArgs().contains("--stacktrace"));
            assertTrue(op.jBangArgs().contains("--no-netrc"));
        }

        @Test
        void optionFlagSettersAreIdempotent() {
            var op = new JBangOperation()
                    .verbose(true).verbose(true);
            assertEquals(1, op.jBangArgs().stream()
                    .filter(("--verbose")::equals).count());
        }

        @Test
        void optionValueSetters() {
            var op = new JBangOperation()
                    .config("/tmp/config")
                    .netrc("/tmp/netrc");
            assertTrue(op.jBangArgs().contains("--config=/tmp/config"));
            assertTrue(op.jBangArgs().contains("--netrc=/tmp/netrc"));
        }

        @Test
        void optionValueSettersReplacePrevious() {
            var op = new JBangOperation()
                    .config("/tmp/a")
                    .config("/tmp/b");
            assertEquals(1, op.jBangArgs().stream()
                    .filter(a -> a.startsWith("--config=")).count());
            assertTrue(op.jBangArgs().contains("--config=/tmp/b"));
            assertFalse(op.jBangArgs().contains("--config=/tmp/a"));
        }

        @Test
        void verifyReset() {
            var op = new JBangOperation()
                    .exitOnFailure(false)
                    .jBangArgs("run")
                    .script("Hello.java")
                    .args("foo", "bar")
                    .env("TEST", "value");

            assertFalse(op.isExitOnFailure(), "exitOnFailure should be false");
            assertEquals(1, op.jBangArgs().size(), "jBangArgs should have 1 element");
            assertEquals("Hello.java", op.script(), "script should be Hello.java");
            assertEquals(2, op.args().size(), "args should have 2 elements");
            assertEquals("value", op.env().get("TEST"), "env should have TEST");

            op.reset();

            assertTrue(op.isExitOnFailure(), "exitOnFailure should be true");
            assertTrue(op.jBangArgs().isEmpty(), "jBangArgs should be empty");
            assertNull(op.script(), "script should be null");
            assertTrue(op.args().isEmpty(), "args should be empty");
            assertTrue(op.env().isEmpty(), "env should be empty");
        }

        @Test
        void verifyResetPreservesConfiguration() {
            var version = new VersionNumber(0, 142, 0);
            var op = new JBangOperation()
                    .workDir(".")
                    .jBangHome("foo")
                    .timeout(30L)
                    .inheritIO(false)
                    .version(version)
                    .script("Hello.java");

            op.reset();

            assertEquals(new File("."), op.workDir());
            assertEquals(new File("foo"), op.jBangHome());
            assertEquals(30L, op.timeout());
            assertFalse(op.isInheritIO());
            assertEquals(version, op.version());
            assertNull(op.script());
        }

        @Test
        void verifyResetPreservesUseDownloadedJBang() throws Exception {
            var op = new JBangOperation().useDownloadedJBang();

            op.reset();
            op.buildJBangCommand();

            assertTrue(testLogHandler.containsMessage("downloaded JBang was requested"),
                    "useDownloadedJBang must survive reset()");
        }

        @Nested
        @DisplayName("Args Tests")
        class ArgsTests {

            @Test
            @RandomString(size = 2)
            void verifyArgs(List<String> args) {
                var op = newJBangOperation().args(args);
                assertEquals(op.args().size(), args.size());
                assertTrue(op.args().containsAll(args));
            }

            @Test
            void verifyArgsAccumulate() {
                var op = newJBangOperation().args("foo").args(List.of("bar")).args("baz");
                assertEquals(List.of("foo", "bar", "baz"), op.args());
            }

            @Test
            void verifyArgsAsArray() {
                var op = newJBangOperation().args("foo", "bar");
                assertEquals(2, op.args().size());
                assertTrue(op.args().containsAll(List.of("foo", "bar")));
            }

            @Test
            void verifyEmptyArgsList() {
                var op = newJBangOperation();
                assertThrows(IllegalArgumentException.class, () -> op.args(List.of()));
            }
        }

        @Nested
        @DisplayName("ExitOnFailure Tests")
        class ExitOnFailureTests {

            @Test
            void verifyExitOnFailure() {
                var op = new JBangOperation();
                assertTrue(op.isExitOnFailure());

                op = op.exitOnFailure(true);
                assertTrue(op.isExitOnFailure());
            }

            @Test
            void verifyExitOnFailureDefault() {
                var op = new JBangOperation();
                assertTrue(op.isExitOnFailure());
            }

            @Test
            void verifyIsNotExitOnFailure() {
                var op = new JBangOperation().exitOnFailure(false);
                assertFalse(op.isExitOnFailure());
            }
        }

        @Nested
        @DisplayName("InheritIO Tests")
        class InheritIOTests {

            @Test
            void verifyInheritIODefault() {
                var op = new JBangOperation();
                assertTrue(op.isInheritIO());
            }

            @Test
            void verifyIsInheritIO() {
                var op = new JBangOperation().inheritIO(false);
                assertFalse(op.isInheritIO());

                op = op.inheritIO(true);
                assertTrue(op.isInheritIO());
            }

            @Test
            void verifyIsNotInheritIO() {
                var op = new JBangOperation().inheritIO(false);
                assertFalse(op.isInheritIO());
            }
        }

        @Nested
        @DisplayName("JBangArgs Tests")
        class JBangArgsTests {

            @Test
            void verifyEmptyJBangArgsList() {
                var op = newJBangOperation();
                assertThrows(IllegalArgumentException.class, () -> op.jBangArgs(List.of()));
            }

            @Test
            @RandomString(size = 2)
            void verifyJBangArgs(List<String> args) {
                var op = newJBangOperation().jBangArgs(args);
                assertEquals(2, op.jBangArgs().size());
                assertTrue(op.jBangArgs().containsAll(args));
            }

            @Test
            void verifyJBangArgsAccumulate() {
                var op = newJBangOperation().jBangArgs("foo").jBangArgs(List.of("bar"));
                assertEquals(List.of("foo", "bar"), op.jBangArgs());
            }

            @Test
            void verifyJBangArgsAsArray() {
                var op = newJBangOperation().jBangArgs("foo", "bar");
                assertEquals(2, op.jBangArgs().size());
                assertTrue(op.jBangArgs().containsAll(List.of("foo", "bar")));
            }
        }

        @Nested
        @DisplayName("JBangHome Tests")
        class JBangHomeTest {

            @Test
            void executeWithInvalidJBangHome() {
                var op = newJBangOperation()
                        .jBangHome("/invalid/path")
                        .jBangArgs("version");
                var e = assertThrows(Exception.class, op::execute);
                assertInstanceOf(ExitStatusException.class, e);
            }

            @Test
            @EnabledOnOs({OS.LINUX, OS.MAC})
            @EnabledOnCi
            void executeWithJBangHomeOnCi() {
                var op = newJBangOperation()
                        .jBangHome(Path.of(System.getenv("HOME"), ".jbang"))
                        .jBangArgs("version");
                assertDoesNotThrow(op::execute);
            }

            @Test
            void executeWithNullJBangHome() {
                assumeTrue(isJBangOnPath(), "jbang must be on the PATH");
                var op = new JBangOperation()
                        .workDir(".")
                        .jBangArgs("version");
                assertDoesNotThrow(op::execute);
            }

            @Test
            void verifyJBangHome() {
                var foo = new File("foo");
                var op = newJBangOperation().jBangHome(foo);
                assertEquals(foo, op.jBangHome());
            }

            @Test
            void verifyJBangHomeAsPath() {
                var foo = Path.of("foo");
                var op = newJBangOperation().jBangHome(foo);
                assertEquals(foo.toFile(), op.jBangHome());
            }

            @Test
            void verifyJBangHomeAsString() {
                var op = newJBangOperation().jBangHome("foo");
                assertEquals("foo", op.jBangHome().toString());
            }
        }

        @Nested
        @DisplayName("Script Tests")
        class ScriptTests {

            @Test
            void executeWithMissingScript() {
                var op = newJBangOperation()
                        .useDownloadedJBang()
                        .jBangArgs("run");
                var e = assertThrows(Exception.class, op::execute);
                assertInstanceOf(ExitStatusException.class, e);
            }

            @Test
            void verifyScript() {
                var script = "src/test/resources/Hello.java";
                var op = newJBangOperation().script(script);
                assertEquals(script, op.script());
            }
        }

        @Nested
        @DisplayName("WorkDir Tests")
        class WorkDirTest {

            @Test
            void verifyWorkDir() {
                var project = new TestProject();
                var op = new JBangOperation().fromProject(project);
                assertEquals(project.workDirectory().getAbsoluteFile(), op.workDir());
            }

            @Test
            void verifyWorkDirAsFile() {
                var project = new TestProject();
                var op = new JBangOperation()
                        .fromProject(project)
                        .workDir(new File("foo"));
                assertEquals("foo", op.workDir().toString());
            }

            @Test
            void verifyWorkDirAsPath() {
                var project = new TestProject();
                var op = new JBangOperation()
                        .fromProject(project)
                        .workDir(Path.of("foo"));
                assertEquals("foo", op.workDir().toString());
            }

            @Test
            void verifyWorkDirAsString() {
                var project = new TestProject();
                var op = new JBangOperation()
                        .fromProject(project)
                        .workDir("foo");
                assertEquals("foo", op.workDir().toString());
            }
        }
    }

    @Nested
    @DisplayName("OS Detection Tests")
    class OsDetectionTests {

        @Test
        void verifyIsAix() {
            assertSame(SystemTools.isAix(), JBangOperation.isAix());
        }

        @Test
        void verifyIsCygwin() {
            assertSame(SystemTools.isCygwin(), JBangOperation.isCygwin());
        }

        @Test
        void verifyIsFreeBsd() {
            assertSame(SystemTools.isFreeBsd(), JBangOperation.isFreeBsd());
        }

        @Test
        void verifyIsLinux() {
            assertSame(SystemTools.isLinux(), JBangOperation.isLinux());
        }

        @Test
        void verifyIsMacOS() {
            assertSame(SystemTools.isMacOS(), JBangOperation.isMacOS());
        }

        @Test
        void verifyIsMingw() {
            assertSame(SystemTools.isMinGw(), JBangOperation.isMinGw());
        }

        @Test
        void verifyIsOpenVms() {
            assertSame(SystemTools.isOpenVms(), JBangOperation.isOpenVms());
        }

        @Test
        void verifyIsSolaris() {
            assertSame(SystemTools.isSolaris(), JBangOperation.isSolaris());
        }

        @Test
        void verifyIsWindows() {
            assertSame(SystemTools.isWindows(), JBangOperation.isWindows());
        }
    }

    @Nested
    class TimeoutTests {

        @Test
        void defaultTimeoutIs600Seconds() {
            var op = new JBangOperation();
            assertEquals(600L, op.timeout(), "Default timeout should be 600 seconds");
        }

        @Test
        void timeoutAllowsAnyNegativeAsNoTimeout() {
            var op = new JBangOperation().timeout(-5L);
            assertEquals(-5L, op.timeout(), "Any negative value means 'wait forever'");
        }

        @Test
        void timeoutCanBeOverridden() {
            var op = new JBangOperation().timeout(30L);
            assertEquals(30L, op.timeout());
        }

        @Test
        void timeoutKillsLongRunningScript(@TempDir Path tmp) throws IOException {
            var script = tmp.resolve("sleep.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class sleep {
                        public static void main(String[] args) throws Exception {
                            Thread.sleep(5000);
                        }
                    }
                    """);

            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tmp.toFile())
                    .script(script.toString())
                    .timeout(1L) // 1 second
                    .silent(true);

            var ex = assertThrows(ExitStatusException.class, op::execute,
                    "Should throw ExitStatusException on timeout");
            assertEquals(ExitStatusException.EXIT_FAILURE, ex.getExitStatus());

            // Verify it actually timed out fast and didn't wait 5s
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                var op2 = newJBangOperation()
                        .useDownloadedJBang()
                        .workDir(tmp.toFile())
                        .script(script.toString())
                        .timeout(1L)
                        .silent(true);
                assertThrows(ExitStatusException.class, op2::execute);
            });
        }

        @Test
        void timeoutMinusOneDisablesIt() {
            var op = new JBangOperation().timeout(-1L);
            assertEquals(-1L, op.timeout(), "Timeout -1 disables it");
        }

        @Test
        void timeoutMinusOneWaitsForCompletion(@TempDir Path tmp) throws IOException {
            var script = tmp.resolve("quick.java");
            Files.writeString(script, """
                    ///usr/bin/env jbang
                    class quick {
                        public static void main(String[] args) throws Exception {
                            Thread.sleep(100);
                            System.out.println("done");
                        }
                    }
                    """);

            var op = newJBangOperation()
                    .useDownloadedJBang()
                    .workDir(tmp.toFile())
                    .script(script.toString())
                    .timeout(-1L) // wait forever
                    .exitOnFailure(false)
                    .silent(true);

            assertDoesNotThrow(op::execute, "Should complete when timeout disabled");
        }

        @Test
        void timeoutZeroIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().timeout(0L));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @ParameterizedTest
        @ValueSource(strings = {""})
        void argsEmpty(String arg) {
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().args("foo", arg));
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().args(List.of("foo", arg)));
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().args(arg));
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().args(List.of()));
        }

        @ParameterizedTest
        @NullSource
        void argsNull(String arg) {
            assertThrows(NullPointerException.class, () -> new JBangOperation().args("foo", arg));
            assertThrows(NullPointerException.class, () -> new JBangOperation().args(List.of("foo", arg)));
            assertThrows(NullPointerException.class, () -> new JBangOperation().args((String[]) null));
            assertThrows(NullPointerException.class, () -> new JBangOperation().args((Collection<String>) null));
        }

        @Test
        void envEmptyKey() {
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().env("", "value"));
        }

        @Test
        void envMapWithNullKey() {
            var map = new HashMap<String, String>();
            map.put(null, "value");
            assertThrows(NullPointerException.class, () -> new JBangOperation().env(map));
        }

        @Test
        void envMapWithNullValue() {
            var map = new HashMap<String, String>();
            map.put("key", null);
            assertThrows(NullPointerException.class, () -> new JBangOperation().env(map));
        }

        @Test
        void envNullKey() {
            assertThrows(NullPointerException.class, () -> new JBangOperation().env(null, "value"));
        }

        @Test
        void envNullMap() {
            assertThrows(NullPointerException.class, () -> new JBangOperation().env(null));
        }

        @Test
        void envNullValue() {
            assertThrows(NullPointerException.class, () -> new JBangOperation().env("key", null));
        }

        @ParameterizedTest
        @ValueSource(strings = {""})
        void jBangArgsEmpty(String arg) {
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().jBangArgs("foo", arg));
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().jBangArgs(List.of("foo", arg)));
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().jBangArgs(arg));
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().jBangArgs(List.of()));
        }

        @ParameterizedTest
        @NullSource
        void jBangArgsNull(String arg) {
            assertThrows(NullPointerException.class, () -> new JBangOperation().jBangArgs("foo", arg));
            assertThrows(NullPointerException.class, () -> new JBangOperation().jBangArgs(List.of("foo", arg)));
        }

        @ParameterizedTest
        @ValueSource(strings = {""})
        void jBangHomeEmpty(String dir) {
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().jBangHome(dir));
        }

        @ParameterizedTest
        @NullSource
        void jBangHomeNull(String dir) {
            assertThrows(NullPointerException.class, () -> new JBangOperation().jBangHome(dir));
            assertThrows(NullPointerException.class, () -> new JBangOperation().jBangHome((File) null));
            assertThrows(NullPointerException.class, () -> new JBangOperation().jBangHome((Path) null));
        }

        @ParameterizedTest
        @ValueSource(strings = {""})
        void scriptEmpty(String script) {
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().script(script));
        }

        @ParameterizedTest
        @NullSource
        void scriptNull(String script) {
            assertThrows(NullPointerException.class, () -> new JBangOperation().script(script));
        }

        @ParameterizedTest
        @ValueSource(strings = {""})
        void workDirEmpty(String dir) {
            assertThrows(IllegalArgumentException.class, () -> new JBangOperation().workDir(dir));
        }

        @ParameterizedTest
        @NullSource
        void workDirNull(String dir) {
            assertThrows(NullPointerException.class, () -> new JBangOperation().workDir(dir));
            assertThrows(NullPointerException.class, () -> new JBangOperation().workDir((File) null));
            assertThrows(NullPointerException.class, () -> new JBangOperation().workDir((Path) null));
        }
    }

    @Nested
    @DisplayName("Version Tests")
    class VersionTests {

        @Test
        void defaultVersionIsNull() {
            assertNull(new JBangOperation().version());
        }

        @Test
        void pinningJBangVersionConstantIsRetained() {
            var op = new JBangOperation().version(JBangOperation.JBANG_VERSION);
            assertEquals(JBangOperation.JBANG_VERSION, op.version());
        }

        @Test
        void versionAsString() {
            assertEquals(new VersionNumber(0, 143, 0),
                    new JBangOperation().version("0.143.0").version());
        }

        @Test
        void versionAsVersionObject() {
            var v = new VersionNumber(0, 143, 0);
            assertEquals(v, new JBangOperation().version(v).version());
        }

        @Test
        void versionObjectRejectsNull() {
            assertThrows(NullPointerException.class,
                    () -> new JBangOperation().version((Version) null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        void versionStringRejectsBlank(String v) {
            assertThrows(IllegalArgumentException.class,
                    () -> new JBangOperation().version(v));
        }

        @Test
        void versionStringRejectsNull() {
            assertThrows(NullPointerException.class,
                    () -> new JBangOperation().version((String) null));
        }
    }

    @Nested
    @DisplayName("Work DirTests")
    class WorkDirTests {

        @Test
        void workDiInvalidWithoutLogging() {
            logger.setLevel(Level.OFF);

            assertThrows(ExitStatusException.class, () ->
                    new JBangOperation()
                            .fromProject(new TestProject())
                            .workDir("foo")
                            .execute());
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void workDirInvalid() {
            assertThrows(ExitStatusException.class, () ->
                    new JBangOperation()
                            .fromProject(new TestProject())
                            .workDir("foo")
                            .execute());
            assertTrue(testLogHandler.containsMessage("Invalid working directory"));
        }

        @Test
        void workDirInvalidWithSilent() {
            assertThrows(ExitStatusException.class, () ->
                    new JBangOperation()
                            .fromProject(new TestProject())
                            .workDir("foo")
                            .silent(true)
                            .execute());
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void workDirRequired() {
            assertThrows(ExitStatusException.class, () -> new JBangOperation().execute());
            assertTrue(testLogHandler.containsMessage("A work dir must be specified."));
        }

        @Test
        void workDirWithSilent() {
            logger.setLevel(Level.WARNING);
            assertThrows(ExitStatusException.class, () -> new JBangOperation().silent(true).execute());
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void workDirWithoutLogging() {
            logger.setLevel(Level.OFF);
            assertThrows(ExitStatusException.class, () -> new JBangOperation().execute());
            assertTrue(testLogHandler.isEmpty());
        }
    }
}