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
        void verifyScript() {
            var script = "src/test/resources/hello.java";
            var op = new JBangOperation()
                    .fromProject(new BaseProject())
                    .script(script);
            assertEquals(script, op.script());
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
        }

        @Nested
        @DisplayName("JBangArgs Tests")
        class JBangArgsTests {
            @Test
            void verifyJBanArgs() {
                var args = List.of("foo", "bar");
                var op = new JBangOperation()
                        .fromProject(new BaseProject())
                        .jBangArgs(args);
                assertEquals(2, op.jBangArgs().size());
                assertTrue(op.jBangArgs().containsAll(args));
            }

            @Test
            void verifyJBanArgsAsArray() {
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
