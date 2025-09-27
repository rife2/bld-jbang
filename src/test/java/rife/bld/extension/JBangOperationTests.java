/*
 * Copyright 2023-2025 the original author or authors.
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
import rife.bld.extension.testing.TestLogHandler;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(LoggingExtension.class)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class JBangOperationTests {
    @SuppressWarnings("LoggerInitializedWithForeignClass")
    private static final Logger LOGGER = Logger.getLogger(JBangOperation.class.getName());
    private static final TestLogHandler TEST_LOG_HANDLER = new TestLogHandler();

    @RegisterExtension
    @SuppressWarnings("unused")
    private static final LoggingExtension LOGGING_EXTENSION = new LoggingExtension(
            LOGGER,
            TEST_LOG_HANDLER,
            Level.ALL
    );

    @Nested
    @DisplayName("Execute Tests")
    class ExecuteTests {
        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        void executeWithInvalidOs() {
            var originalOsName = System.getProperty("os.name");
            try {
                System.setProperty("os.name", "windows");
                assertThrows(ExitStatusException.class, () ->
                        new JBangOperation()
                                .fromProject(new BaseProject())
                                .jBangArgs("version")
                                .execute());
            } finally {
                System.setProperty("os.name", originalOsName);
            }
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        void executeWithInvalidOsNoLogging() {
            LOGGER.setLevel(Level.OFF);
            var originalOsName = System.getProperty("os.name");
            try {
                System.setProperty("os.name", "windows");
                assertThrows(ExitStatusException.class, () ->
                        new JBangOperation()
                                .fromProject(new BaseProject())
                                .jBangArgs("version")
                                .execute());
            } finally {
                System.setProperty("os.name", originalOsName);
            }

            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        void executeWithInvalidOsSilent() {
            var originalOsName = System.getProperty("os.name");
            try {
                System.setProperty("os.name", "windows");
                assertThrows(ExitStatusException.class, () ->
                        new JBangOperation()
                                .fromProject(new BaseProject())
                                .silent(true)
                                .jBangArgs("version")
                                .execute());
            } finally {
                System.setProperty("os.name", originalOsName);
            }

            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

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
        void executeWithSilent() {
            try {
                new JBangOperation()
                        .fromProject(new BaseProject())
                        .silent(true)
                        .jBangArgs("version")
                        .execute();
            } catch (Exception e) {
                assertInstanceOf(ExitStatusException.class, e);
            }
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        void executeWithSuppressAllLogs() {
            LOGGER.setLevel(Level.OFF);
            var op = new JBangOperation()
                    .fromProject(new BaseProject())
                    .silent(true)
                    .jBangArgs("version");
            try {
                op.execute();
            } catch (Exception e) {
                assertInstanceOf(ExitStatusException.class, e);
            }
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        void executeWithoutCommandLogging() throws Exception {
            LOGGER.setLevel(Level.WARNING);
            new JBangOperation()
                    .fromProject(new BaseProject())
                    .jBangArgs("version")
                    .execute();
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        void helloWorld(@TempDir Path tempDir) throws IOException {
            var helloTxt = tempDir.resolve("hello.txt");
            var op = new JBangOperation()
                    .fromProject(new BaseProject())
                    .jBangArgs("--quiet")
                    .script("src/test/resources/hello.java")
                    .args(helloTxt.toString());
            assertDoesNotThrow(op::execute);
            assertEquals("Hello World", Files.readString(helloTxt));
        }
    }

    @Nested
    @DisplayName("Project Required Tests")
    class ProjectRequiredTests {
        @Test
        void projectRequired() {
            try {
                new JBangOperation().execute();
            } catch (Exception e) {
                assertInstanceOf(ExitStatusException.class, e);
            }
            assertTrue(TEST_LOG_HANDLER.containsMessage("A project must be specified."));
        }

        @Test
        void projectRequiredWithSilent() {
            LOGGER.setLevel(Level.WARNING);
            try {
                new JBangOperation().silent(true).execute();
            } catch (Exception e) {
                assertInstanceOf(ExitStatusException.class, e);
            }
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        void projectRequiredWithoutLogging() {
            LOGGER.setLevel(Level.OFF);
            try {
                new JBangOperation().execute();
            } catch (Exception e) {
                assertInstanceOf(ExitStatusException.class, e);
            }
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }
    }

    @Nested
    @DisplayName("WorkDir Required Tests")
    class WorDirRequiredTests {
        @Test
        void workDirRequired() {
            try {
                new JBangOperation()
                        .fromProject(new BaseProject())
                        .workDir("foo")
                        .execute();
            } catch (Exception e) {
                assertInstanceOf(ExitStatusException.class, e);
            }
            assertTrue(TEST_LOG_HANDLER.containsMessage("Invalid working directory"));
        }

        @Test
        void workDirRequiredWithSilent() {
            try {
                new JBangOperation()
                        .fromProject(new BaseProject())
                        .workDir("foo")
                        .silent(true)
                        .execute();
            } catch (Exception e) {
                assertInstanceOf(ExitStatusException.class, e);
            }
            assertTrue(TEST_LOG_HANDLER.isEmpty());
        }

        @Test
        void workDirRequiredWithoutLogging() {
            LOGGER.setLevel(Level.OFF);
            try {
                new JBangOperation()
                        .fromProject(new BaseProject())
                        .workDir("foo")
                        .execute();
            } catch (Exception e) {
                assertInstanceOf(ExitStatusException.class, e);
            }
            assertTrue(TEST_LOG_HANDLER.isEmpty());
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
                    .script("hello.java")
                    .args("foo", "bar");

            assertFalse(op.isExitOnFailure(), "exitOnFailure should be false");
            assertEquals(1, op.jBangArgs().size(), "jBangArgs should have 1 element");
            assertEquals("hello.java", op.script(), "script should be hello.java");
            assertEquals(2, op.args().size(), "args should have 2 elements");

            op.reset();

            assertTrue(op.isExitOnFailure(), "exitOnFailure should be true");
            assertTrue(op.jBangArgs().isEmpty(), "jBangArgs should be empty");
            assertNull(op.script(), "script should be null");
            assertTrue(op.args().isEmpty(), "args should be empty");
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
                var script = "src/test/resources/hello.java";
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .script(script);
                assertEquals(script, op.script());
            }
        }

        @Nested
        @DisplayName("Args Tests")
        class ArgsTests {
            @Test
            void verifyArgs() {
                var args = List.of("foo", "bar");
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .args(args);
                assertEquals(2, op.args().size());
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
                        .fromProject(new BaseProject())
                        .args(List.of());
                assertTrue(op.args().isEmpty());
            }
        }

        @Nested
        @DisplayName("OS Tests")
        class OsTests {
            @Test
            @EnabledOnOs(OS.LINUX)
            void verifyIsLinux() {
                assertTrue(JBangOperation.isLinux());
                assertFalse(JBangOperation.isWindows());
                assertFalse(JBangOperation.isMacOS());
            }

            @Test
            @EnabledOnOs(OS.MAC)
            void verifyIsMacOS() {
                assertTrue(JBangOperation.isMacOS());
                assertFalse(JBangOperation.isLinux());
                assertFalse(JBangOperation.isWindows());
            }

            @Test
            @EnabledOnOs(OS.WINDOWS)
            void verifyIsWindows() {
                assertTrue(JBangOperation.isWindows());
                assertFalse(JBangOperation.isLinux());
                assertFalse(JBangOperation.isMacOS());
            }

            @Test
            void verifyOsNameDarwin() {
                var originalOsName = System.getProperty("os.name");
                try {
                    System.setProperty("os.name", "darwin");
                    assertTrue(JBangOperation.isMacOS(), "os.name should be darwin");
                } finally {
                    System.setProperty("os.name", originalOsName);
                }
            }

            @Test
            void verifyOsNameIsNull() {
                var originalOsName = System.getProperty("os.name");
                try {
                    System.clearProperty("os.name");
                    assertFalse(JBangOperation.isLinux(), "isLinux should be false");
                    assertFalse(JBangOperation.isWindows(), "isWindows should be false");
                    assertFalse(JBangOperation.isMacOS(), "isMacOS should be false");
                } finally {
                    System.setProperty("os.name", originalOsName);
                }
            }

            @Test
            void verifyOsNameLinux() {
                var originalOsName = System.getProperty("os.name");
                try {
                    System.setProperty("os.name", "linux");
                    assertTrue(JBangOperation.isLinux(), "os.name should be linux");
                } finally {
                    System.setProperty("os.name", originalOsName);
                }
            }

            @Test
            void verifyOsNameMacOS() {
                var originalOsName = System.getProperty("os.name");
                try {
                    System.setProperty("os.name", "macos");
                    assertTrue(JBangOperation.isMacOS(), "os.name should be macos");
                } finally {
                    System.setProperty("os.name", originalOsName);
                }
            }

            @Test
            void verifyOsNameUnix() {
                var originalOsName = System.getProperty("os.name");
                try {
                    System.setProperty("os.name", "unix");
                    assertTrue(JBangOperation.isLinux(), "os.name should be unix");
                } finally {
                    System.setProperty("os.name", originalOsName);
                }
            }

            @Test
            void verifyOsNameWindows() {
                var originalOsName = System.getProperty("os.name");
                try {
                    System.setProperty("os.name", "windows");
                    assertTrue(JBangOperation.isWindows(), "os.name should be windows");
                } finally {
                    System.setProperty("os.name", originalOsName);
                }
            }
        }

        @Nested
        @DisplayName("JBangArgs Tests")
        class JBangArgsTests {
            @Test
            void verifyEmptyJBangArgsList() {
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangArgs(List.of());
                assertTrue(op.jBangArgs().isEmpty());
            }

            @Test
            void verifyJBangArgs() {
                var args = List.of("foo", "bar");
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
        @DisplayName("WorkDir Tests")
        class WorkDirTest {
            @Test
            void verifyWorkDir() {
                var project = new BaseProject();
                var op = new JBangOperation().fromProject(project);
                assertEquals(project.workDirectory(), op.workDir());
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
}
