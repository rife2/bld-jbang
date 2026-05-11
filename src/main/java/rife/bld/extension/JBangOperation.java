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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import rife.bld.BaseProject;
import rife.bld.extension.tools.ObjectTools;
import rife.bld.extension.tools.ProcessExecutor;
import rife.bld.extension.tools.SystemTools;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Run JBang with the specified arguments.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "Builder pattern intentionally exposes mutable collections; callers may add to them directly"
)
public class JBangOperation extends AbstractOperation<JBangOperation> {

    private static final Logger logger = Logger.getLogger(JBangOperation.class.getName());
    private static final Consumer<String> defaultOutputConsumer = logger::info;
    private final List<String> args_ = new ArrayList<>();
    private final Map<String, String> env_ = new HashMap<>();
    private final List<String> jBangArgs_ = new ArrayList<>();
    private boolean exitOnFailure_ = true;
    private boolean inheritIO_ = true;
    private File jBangHome_;
    @NonNull
    private Consumer<String> outputConsumer_ = defaultOutputConsumer;
    private String script_;
    private long timeout_ = 600L;
    private File workDir_;

    /**
     * Performs the operation
     *
     * @throws Exception           if an error occurs
     * @throws ExitStatusException if workDir is null or invalid, JBang execution fails or times out
     */
    @Override
    public void execute() throws Exception {
        if (workDir_ == null) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("A work dir must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        } else if (!workDir_.isDirectory()) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("Invalid working directory: " + workDir_.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }

        var command = new ArrayList<String>();
        command.add(findJBangExec());
        command.addAll(jBangArgs_);
        if (script_ != null) {
            command.add(script_);
        }
        command.addAll(args_);

        if (logger.isLoggable(Level.INFO) && !silent()) {
            logger.info(String.join(" ", command));
        }

        var executor = new ProcessExecutor()
                .command(command)
                .workDir(workDir_)
                .timeout(timeout_)
                .inheritIO(inheritIO_);

        if (!env_.isEmpty()) {
            executor.env(env_);
        }

        if (!inheritIO_) {
            executor.outputConsumer(outputConsumer_);
        }

        var result = executor.execute();

        if (result.timedOut()) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("JBang execution timed out after " + timeout_ + " seconds.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }

        if (exitOnFailure_ && !result.isSuccess()) {
            ExitStatusException.throwOnFailure(result.exitCode());
        }
    }

    /**
     * Determines if the current operating system is AIX.
     *
     * @return {@code true} if the operating system is identified as AIX, {@code false} otherwise
     * @see SystemTools#isAix()
     */
    public static boolean isAix() {
        return SystemTools.isAix();
    }

    /**
     * Determines if the current operating system is Cygwin.
     *
     * @return {@code true} if the operating system is identified as Cygwin, {@code false} otherwise
     * @see SystemTools#isCygwin()
     */
    public static boolean isCygwin() {
        return SystemTools.isCygwin();
    }

    /**
     * Determines if the current operating system is FreeBSD.
     *
     * @return {@code true} if the operating system is FreeBSD, {@code false} otherwise
     * @see SystemTools#isFreeBsd()
     */
    public static boolean isFreeBsd() {
        return SystemTools.isFreeBsd();
    }

    /**
     * Determines if the operating system is Linux.
     *
     * @return {@code true} if the operating system is Linux, {@code false} otherwise
     * @see SystemTools#isLinux()
     */
    public static boolean isLinux() {
        return SystemTools.isLinux();
    }

    /**
     * Determines if the current operating system is macOS.
     *
     * @return {@code true} if the OS is macOS, {@code false} otherwise
     * @see SystemTools#isMacOS()
     */
    public static boolean isMacOS() {
        return SystemTools.isMacOS();
    }

    /**
     * Determines if the current operating system is MinGW.
     *
     * @return {@code true} if the operating system is identified as MinGW, {@code false} otherwise
     * @see SystemTools#isMinGw()
     */
    public static boolean isMinGw() {
        return SystemTools.isMinGw();
    }

    /**
     * Determines if the current operating system is OpenVMS.
     *
     * @return {@code true} if the operating system is OpenVMS, {@code false} otherwise
     * @see SystemTools#isOpenVms()
     */
    public static boolean isOpenVms() {
        return SystemTools.isOpenVms();
    }

    /**
     * Determines if the current operating system is Solaris.
     *
     * @return {@code true} if the operating system is Solaris, {@code false} otherwise
     * @see SystemTools#isSolaris()
     */
    public static boolean isSolaris() {
        return SystemTools.isSolaris();
    }

    /**
     * Determines if the current operating system is Windows.
     *
     * @return {@code true} if the operating system is Windows, {@code false} otherwise
     * @see SystemTools#isWindows()
     */
    public static boolean isWindows() {
        return SystemTools.isWindows();
    }

    /**
     * Sets the arguments to be used in the {@link #script(String) script}.
     *
     * @param args the arguments to use in the script
     * @return this operation instance
     * @throws IllegalArgumentException if the {@code args} elements are {@code null} or empty
     * @throws NullPointerException     if the {@code args} collection is {@code null}
     */
    public JBangOperation args(@NonNull Collection<String> args) {
        args_.addAll(ObjectTools.requireNotEmpty(args, "args"));
        return this;
    }

    /**
     * Sets the arguments to be used in the {@link #script(String) script}.
     *
     * @param args the arguments to use in the script
     * @return this operation instance
     * @throws IllegalArgumentException if the {@code args} elements are {@code null} or empty
     * @throws NullPointerException     if the {@code args} collection is {@code null}
     */
    public JBangOperation args(@NonNull String... args) {
        args_.addAll(List.of(ObjectTools.requireNotEmpty(args, "args")));
        return this;
    }

    /**
     * Retrieves the live collection of arguments to be passed to the script.
     * <p>
     * The returned list is the operation's internal list. Callers may add to it directly;
     * this is intentional by design (builder pattern).
     *
     * @return the mutable list of script arguments
     */
    public List<String> args() {
        return args_;
    }

    /**
     * Adds an environment variable.
     * <p>
     * These variables are merged with the current process environment. Existing variables
     * with the same name are overridden.
     *
     * @param name  the variable name, must not be null
     * @param value the variable value, must not be null
     * @return this operation instance
     * @throws IllegalArgumentException if {@code name} is empty or null
     * @throws NullPointerException     if {@code value} is null
     * @see #env(Map)
     */
    public JBangOperation env(@NonNull String name, @NonNull String value) {
        env_.put(ObjectTools.requireNotEmpty(name, "env name"),
                ObjectTools.requireNonNull(value, "env value"));
        return this;
    }

    /**
     * Adds environment variables.
     * <p>
     * These variables are merged with the current process environment. Existing variables
     * with the same name are overridden.
     *
     * @param vars the map of environment variables, must not be null and must not contain null keys or values
     * @return this operation instance
     * @throws NullPointerException if {@code vars} is null, or if {@code vars} contains a null key or value
     * @see #env(String, String)
     */
    public JBangOperation env(@NonNull Map<String, String> vars) {
        env_.putAll(ObjectTools.requireNonNull(vars, "env"));
        return this;
    }

    /**
     * Returns the environment variables.
     * <p>
     * The returned map is mutable and can be modified directly before calling {@link #execute()}.
     *
     * @return the mutable environment variables map, never null
     */
    public Map<String, String> env() {
        return env_;
    }

    /**
     * Configures whether the operation should exit upon a JBang execution failure.
     * <p>
     * Default value is {@code true}
     *
     * @param exitOnFailure {@code true} if the operation should exit on failure, {@code false} otherwise
     * @return this operation instance
     */
    public JBangOperation exitOnFailure(boolean exitOnFailure) {
        exitOnFailure_ = exitOnFailure;
        return this;
    }

    /**
     * Configures a JBang operation from a {@link BaseProject}.
     * <p>
     * Sets the following from the project:
     * <ul>
     *     <li>{@link #workDir() workDir} to the project's directory, if not already set</li>
     *     <li>{@link #jBangHome() jBangHome} to the {@code JBANG_HOME} environment variable, if not
     *     already set. A caller-set value always takes precedence over the environment variable.</li>
     * </ul>
     *
     * @param project the project to configure the operation from
     * @return this operation instance
     * @throws NullPointerException if the {@code project} is {@code null}
     */
    public JBangOperation fromProject(@NonNull BaseProject project) {
        ObjectTools.requireNonNull(project, "fromProject");
        if (workDir_ == null) {
            workDir_ = project.workDirectory().getAbsoluteFile();
        }

        if (jBangHome_ == null) {
            var jbangHomeEnv = System.getenv("JBANG_HOME");
            if (jbangHomeEnv != null) {
                jBangHome_ = new File(jbangHomeEnv);
            }
        }
        return this;
    }

    /**
     * Configures whether the child process should inherit the I/O streams of the current JVM.
     * <p>
     * When {@code true}, the child process uses the same stdin, stdout, and stderr as the current
     * Java process. This enables interactive commands, preserves ANSI colors, and allows progress
     * bars to display correctly. Output is <em>not</em> captured by the logger and cannot be asserted in tests.
     * <p>
     * When {@code false}, stdout and stderr are merged and captured through the logger. This makes
     * output testable and keeps it in the build log, but breaks interactive prompts and ANSI formatting.
     * <p>
     * Default is {@code TRUE}
     *
     * @param inheritIO {@code true} to inherit I/O, {@code false} to capture output
     * @return this operation instance
     */
    public JBangOperation inheritIO(boolean inheritIO) {
        inheritIO_ = inheritIO;
        return this;
    }

    /**
     * Checks whether the operation is configured to exit upon a JBang execution failure.
     * <p>
     * Default value is {@code true}
     *
     * @return {@code true} if the operation is set to exit on failure, {@code false} otherwise.
     */
    public boolean isExitOnFailure() {
        return exitOnFailure_;
    }

    /**
     * Returns whether the child process inherits the I/O streams of the current JVM.
     *
     * @return {@code true} if I/O is inherited (default), {@code false} if {@code stderr} is redirected
     * to {@code stdout}
     * @see #inheritIO(boolean)
     */
    public boolean isInheritIO() {
        return inheritIO_;
    }

    /**
     * Sets the arguments to be used when running the {@link #script(String) script}
     *
     * @param args the arguments to use when running the script
     * @return this operation instance
     * @throws IllegalArgumentException if the {@code args} elements are {@code null} or empty
     * @throws NullPointerException     if the {@code args} collection is {@code null}
     */
    public JBangOperation jBangArgs(@NonNull Collection<String> args) {
        jBangArgs_.addAll(ObjectTools.requireNotEmpty(args, "jBangArgs"));
        return this;
    }

    /**
     * Sets one or more arguments to be used when running the script.
     *
     * @param args the arguments to use when running the script
     * @return this operation instance
     * @throws IllegalArgumentException if the {@code args} elements are {@code null} or empty
     * @throws NullPointerException     if the {@code args} collection is {@code null}
     */
    public JBangOperation jBangArgs(@NonNull String... args) {
        jBangArgs_.addAll(List.of(ObjectTools.requireNotEmpty(args, "jBangArgs")));
        return this;
    }

    /**
     * Retrieves the live collection of arguments to be used when running the script.
     * <p>
     * The returned list is the operation's internal list. Callers may add to it directly;
     * this is intentional by design (builder pattern).
     *
     * @return the mutable list of JBang arguments
     */
    public List<String> jBangArgs() {
        return jBangArgs_;
    }

    /**
     * Sets the JBang home directory.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     * @throws NullPointerException     if {@code jBangHome} is null
     * @throws IllegalArgumentException if {@code jBangHome} is empty
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JBangOperation jBangHome(@NonNull String jBangHome) {
        ObjectTools.requireNotEmpty(jBangHome, "jBangHome");
        jBangHome_ = new File(jBangHome);
        return this;
    }

    /**
     * Sets the JBang home directory.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     * @throws NullPointerException if {@code jBangHome} is null
     */
    public JBangOperation jBangHome(@NonNull File jBangHome) {
        ObjectTools.requireNonNull(jBangHome, "jBangHome");
        jBangHome_ = jBangHome;
        return this;
    }

    /**
     * Sets the JBang home directory.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     * @throws NullPointerException if {@code jBangHome} is null
     */
    public JBangOperation jBangHome(@NonNull Path jBangHome) {
        ObjectTools.requireNonNull(jBangHome, "jBangHome");
        jBangHome_ = jBangHome.toFile();
        return this;
    }

    /**
     * Retrieves the JBang home directory.
     *
     * @return the JBang home directory
     */
    public File jBangHome() {
        return jBangHome_;
    }

    /**
     * Sets a consumer to receive output lines when not inheriting I/O.
     * <p>
     * Only called when {@link #isInheritIO()} is {@code false}. Default logs at INFO level.
     *
     * @param outputConsumer the output consumer, must not be null
     * @return this operation instance
     * @throws NullPointerException if outputConsumer is null
     */
    public JBangOperation outputConsumer(@NonNull Consumer<String> outputConsumer) {
        ObjectTools.requireNonNull(outputConsumer, "outputConsumer");
        outputConsumer_ = outputConsumer;
        return this;
    }

    /**
     * Resets the script-related state of the operation to its default values.
     * <p>
     * Specifically, this method:
     * <ul>
     *     <li>Clears all {@link #args() script arguments}</li>
     *     <li>Clears all {@link #env() environment variables}</li>
     *     <li>Clears all {@link #jBangArgs() JBang arguments}</li>
     *     <li>Resets the {@link #isExitOnFailure() exit on failure flag} to {@code true}</li>
     *     <li>Clears the assigned {@link #script() script}</li>
     * </ul>
     * <p>
     * The following are intentionally preserved across resets, as they are typically set once
     * via {@link #fromProject(BaseProject) fromProject} and shared across multiple script
     * executions: {@link #workDir() workDir}, {@link #jBangHome() jBangHome},
     * {@link #timeout() timeout}, {@link #isInheritIO() inheritIO}, and the
     * {@link #outputConsumer(Consumer) outputConsumer}.
     */
    public void reset() {
        args_.clear();
        env_.clear();
        jBangArgs_.clear();
        exitOnFailure_ = true;
        script_ = null;
    }

    /**
     * Sets the script to be executed.
     *
     * @param script the script to execute
     * @return this operation instance
     * @throws IllegalArgumentException if {@code script} is null or empty
     * @throws NullPointerException     if {@code script} is null
     */
    public JBangOperation script(@NonNull String script) {
        script_ = ObjectTools.requireNotEmpty(script, "script");
        return this;
    }

    /**
     * Retrieves the script that has been set for execution.
     *
     * @return the script to be executed or {@code null}
     */
    public String script() {
        return script_;
    }

    /**
     * Sets the timeout for JBang execution in seconds.
     * <p>
     * If the process does not complete within the specified timeout, it will be terminated
     * and the operation will fail. If set to any negative value, the process will wait indefinitely.
     * Passing {@code 0} is not allowed; use a negative value to indicate no timeout.
     * <p>
     * Default is {@code 600} seconds (10 minutes)
     *
     * @param seconds the timeout in seconds (positive); use a negative value for no timeout
     * @return this operation instance
     * @throws IllegalArgumentException if {@code seconds} is {@code 0}
     * @since 1.2
     */
    public JBangOperation timeout(long seconds) {
        if (seconds == 0) {
            throw new IllegalArgumentException(
                    "timeout must be a positive number of seconds, or negative for no timeout; 0 is not allowed");
        }
        timeout_ = seconds;
        return this;
    }

    /**
     * Retrieves the timeout for JBang execution in seconds.
     * <p>
     * A positive value is the timeout duration in seconds. A negative value indicates no timeout
     * (wait indefinitely). {@code 0} is not a valid state; the setter disallows it.
     *
     * @return the timeout in seconds (positive), or a negative value if no timeout is set
     * @since 1.2
     */
    public long timeout() {
        return timeout_;
    }

    /**
     * Retrieves the working directory.
     *
     * @return the directory
     */
    public File workDir() {
        return workDir_;
    }

    /**
     * Provides the working directory if it differs from the project's directory.
     *
     * @param dir the directory
     * @return this operation instance
     * @throws NullPointerException if {@code dir} is null
     */
    public JBangOperation workDir(@NonNull File dir) {
        workDir_ = ObjectTools.requireNonNull(dir, "workDir");
        return this;
    }

    /**
     * Provides the working directory if it differs from the project's directory.
     *
     * @param dir the directory
     * @return this operation instance
     * @throws NullPointerException if {@code dir} is null
     */
    public JBangOperation workDir(@NonNull Path dir) {
        ObjectTools.requireNonNull(dir, "workDir");
        workDir_ = dir.toFile();
        return this;
    }

    /**
     * Provides the working directory if it differs from the project's directory.
     *
     * @param dir the directory path
     * @return this operation instance
     * @throws IllegalArgumentException if {@code dir} is empty
     * @throws NullPointerException     if {@code dir} is null
     */
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public JBangOperation workDir(@NonNull String dir) {
        ObjectTools.requireNotEmpty(dir, "workDir");
        workDir_ = (new File(dir));
        return this;
    }

    /**
     * Finds the JBang executable path.
     * <p>
     * If {@link #jBangHome() jBangHome} is set, resolves the executable under its {@code bin/}
     * directory and verifies it is executable, failing fast with a clear message if not.
     * If {@code jBangHome} is not set, returns the bare executable name ({@code jbang} or
     * {@code jbang.cmd} on Windows) and relies on {@code PATH} resolution at process launch.
     *
     * @return the absolute path to the JBang executable, or the bare name if relying on PATH
     * @throws ExitStatusException if the resolved executable is not found or not executable
     */
    private String findJBangExec() throws ExitStatusException {
        var jbang = isWindows() ? "jbang.cmd" : "jbang";
        if (jBangHome_ == null) {
            return jbang;
        }

        var exec = Path.of(jBangHome_.getAbsolutePath(), "bin", jbang).toFile();
        if (!exec.canExecute()) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("JBang executable not found or not executable: " + exec.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        return exec.getAbsolutePath();
    }
}