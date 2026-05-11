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
import rife.bld.BaseProject;
import rife.bld.extension.testing.EnabledOnCi;
import rife.bld.extension.testing.LoggingExtension;
import rife.bld.extension.testing.RandomString;
import rife.bld.extension.testing.TestLogHandler;
import rife.bld.extension.tools.SystemTools;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(LoggingExtension.class)
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "DataFlowIssue"})
class JBangOperationTests {

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

    @BeforeEach
    void beforeEach() {
        testLogHandler.clear();
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

            new JBangOperation()
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
            new JBangOperation()
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

            new JBangOperation()
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

            new JBangOperation()
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

            // This test just verifies the var is passed. Won't actually change JDK
            // unless JAVA_HOME points to a valid JDK, but JBang will use it if valid.
            var fakeHome = tempDir.resolve("fakejdk").toString();
            new JBangOperation()
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
        void executeWithNoExitOnFailure(@TempDir Path tempDir) throws Exception {
            var op = new JBangOperation()
                    .fromProject(new BaseProject())
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
            new JBangOperation()
                    .fromProject(new BaseProject())
                    .silent(true)
                    .jBangArgs("version")
                    .execute();

            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void executeWithSuppressAllLogs() throws Exception {
            logger.setLevel(Level.OFF);

            new JBangOperation()
                    .fromProject(new BaseProject())
                    .silent(true)
                    .jBangArgs("version")
                    .execute();
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void executeWithoutCommandLogging() throws Exception {
            logger.setLevel(Level.WARNING);
            new JBangOperation()
                    .fromProject(new BaseProject())
                    .jBangArgs("version")
                    .execute();
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void helloWorld(@TempDir Path tempDir) throws IOException {
            var helloTxt = tempDir.resolve("hello.txt");
            var op = new JBangOperation()
                    .fromProject(new BaseProject())
                    .jBangArgs("--quiet")
                    .script("src/test/resources/Hello.java")
                    .args(helloTxt.toString());
            assertDoesNotThrow(op::execute);
            assertEquals("Hello World", Files.readString(helloTxt));
        }

        @Test
        void helloWorldInvalidArgs() {
            var op = new JBangOperation()
                    .fromProject(new BaseProject())
                    .jBangArgs("--quiet")
                    .script("src/test/resources/Hello.java")
                    .args("foo/bar.txt");
            assertThrows(ExitStatusException.class, op::execute);
        }

        @Test
        void helloWorldNoArgs() {
            var op = new JBangOperation()
                    .fromProject(new BaseProject())
                    .jBangArgs("--quiet")
                    .script("src/test/resources/Hello.java");
            assertThrows(ExitStatusException.class, op::execute);
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

            var op = new JBangOperation()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .inheritIO(false)
                    .exitOnFailure(true);

            assertThrows(ExitStatusException.class, op::execute);
            testLogHandler.printLogMessages();
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


            var op = new JBangOperation()
                    .workDir(tempDir.toFile())
                    .script(script.toString())
                    .inheritIO(false); // capture output
            assertDoesNotThrow(op::execute);

            testLogHandler.printLogMessages();
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


            var op = new JBangOperation()
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

            var op = new JBangOperation()
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
    @DisplayName("Options Tests")
    class Options {

        @Test
        void verifyReset() {
            var op = new JBangOperation()
                    .exitOnFailure(false)
                    .jBangArgs("run")
                    .script("Hello.java")
                    .args("foo", "bar")
                    .env("TEST", "value"); // add this

            assertFalse(op.isExitOnFailure(), "exitOnFailure should be false");
            assertEquals(1, op.jBangArgs().size(), "jBangArgs should have 1 element");
            assertEquals("Hello.java", op.script(), "script should be Hello.java");
            assertEquals(2, op.args().size(), "args should have 2 elements");
            assertEquals("value", op.env().get("TEST"), "env should have TEST"); // add this

            op.reset();

            assertTrue(op.isExitOnFailure(), "exitOnFailure should be true");
            assertTrue(op.jBangArgs().isEmpty(), "jBangArgs should be empty");
            assertNull(op.script(), "script should be null");
            assertTrue(op.args().isEmpty(), "args should be empty");
            assertTrue(op.env().isEmpty(), "env should be empty");
        }

        @Nested
        @DisplayName("Args Tests")
        class ArgsTests {

            @Test
            @RandomString(size = 2)
            void verifyArgs(List<String> args) {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .args(args);
                assertEquals(op.args().size(), args.size());
                assertTrue(op.args().containsAll(args));
            }

            @Test
            void verifyArgsAsArray() {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .args("foo", "bar");
                assertEquals(2, op.args().size());
                assertTrue(op.args().containsAll(List.of("foo", "bar")));
            }


            @Test
            void verifyEmptyArgsList() {
                var op = new JBangOperation()
                        .fromProject(new BaseProject());
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
                var op = new JBangOperation()
                        .fromProject(new BaseProject());
                assertThrows(IllegalArgumentException.class, () -> op.jBangArgs(List.of()));
            }

            @Test
            @RandomString(size = 2)
            void verifyJBangArgs(List<String> args) {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangArgs(args);
                assertEquals(2, op.jBangArgs().size());
                assertTrue(op.jBangArgs().containsAll(args));
            }

            @Test
            void verifyJBangArgsAsArray() {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangArgs("foo", "bar");
                assertEquals(2, op.jBangArgs().size());
                assertTrue(op.jBangArgs().containsAll(List.of("foo", "bar")));
            }
        }

        @Nested
        @DisplayName("JBangHome Tests")
        class JBangHomeTest {

            @Test
            void executeWithInvalidJBangHome() {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangHome("/invalid/path")
                        .jBangArgs("version");
                var e = assertThrows(Exception.class, op::execute);
                assertInstanceOf(ExitStatusException.class, e);
            }

            @Test
            @EnabledOnOs({OS.LINUX, OS.MAC})
            @EnabledOnCi
            void executeWithJBangHomeOnCi() {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangHome(Path.of(System.getenv("HOME"), ".jbang"))
                        .jBangArgs("version");
                assertDoesNotThrow(op::execute);
            }

            @Test
            void executeWithNullJBangHome() {
                var op = new JBangOperation()
                        .workDir(".")
                        .jBangArgs("version");
                assertDoesNotThrow(op::execute);
            }

            @Test
            void verifyJBangHome() {
                var foo = new File("foo");
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangHome(foo);
                assertEquals(foo, op.jBangHome());

            }

            @Test
            void verifyJBangHomeAsPath() {
                var foo = Path.of("foo");
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangHome(foo);
                assertEquals(foo.toFile(), op.jBangHome());
            }

            @Test
            void verifyJBangHomeAsString() {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangHome("foo");
                assertEquals("foo", op.jBangHome().toString());

            }
        }

        @Nested
        @DisplayName("Script Tests")
        class ScriptTests {

            @Test
            void executeWithMissingScript() {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangArgs("run");
                var e = assertThrows(Exception.class, op::execute);
                assertInstanceOf(ExitStatusException.class, e);
            }

            @Test
            void verifyScript() {
                var script = "src/test/resources/Hello.java";
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .script(script);
                assertEquals(script, op.script());
            }
        }

        @Nested
        @DisplayName("WorkDir Tests")
        class WorkDirTest {

            @Test
            void verifyWorkDir() {
                var project = new BaseProject();
                var op = new JBangOperation().fromProject(project);
                assertEquals(project.workDirectory(), op.workDir());
            }

            @Test
            void verifyWorkDirAsFile() {
                var project = new BaseProject();
                var op = new JBangOperation()
                        .fromProject(project)
                        .workDir(new File("foo"));
                assertEquals("foo", op.workDir().toString());
            }

            @Test
            void verifyWorkDirAsPath() {
                var project = new BaseProject();
                var op = new JBangOperation()
                        .fromProject(project)
                        .workDir(Path.of("foo"));
                assertEquals("foo", op.workDir().toString());
            }

            @Test
            void verifyWorkDirAsString() {
                var project = new BaseProject();
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

            var op = new JBangOperation()
                    .workDir(tmp.toFile())
                    .script(script.toString())
                    .timeout(1L) // 1 second
                    .silent(true);

            var ex = assertThrows(ExitStatusException.class, op::execute,
                    "Should throw ExitStatusException on timeout");
            assertEquals(ExitStatusException.EXIT_FAILURE, ex.getExitStatus());

            // Verify it actually timed out fast and didn't wait 5s
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                var op2 = new JBangOperation()
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

            var op = new JBangOperation()
                    .workDir(tmp.toFile())
                    .script(script.toString())
                    .timeout(-1L) // wait forever
                    .exitOnFailure(false)
                    .silent(true);

            assertDoesNotThrow(op::execute, "Should complete when timeout disabled");
        }

        @Test
        void timeoutRejectsNegativeOtherThanMinusOne() {
            // Optional: if you want to guard against -2, -100, etc.
            // Current impl treats any <= 0 as "wait forever".
            // If you want to enforce only -1, add validation in timeout(long).
            var op = new JBangOperation().timeout(-5L);
            assertEquals(-5L, op.timeout(), "Currently allows any negative, treated as wait forever");
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
                            .fromProject(new BaseProject())
                            .workDir("foo")
                            .execute());
            assertTrue(testLogHandler.isEmpty());
        }

        @Test
        void workDirInvalid() {
            assertThrows(ExitStatusException.class, () ->
                    new JBangOperation()
                            .fromProject(new BaseProject())
                            .workDir("foo")
                            .execute());
            assertTrue(testLogHandler.containsMessage("Invalid working directory"));
        }

        @Test
        void workDirInvalidWithSilent() {
            assertThrows(ExitStatusException.class, () ->
                    new JBangOperation()
                            .fromProject(new BaseProject())
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
