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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import rife.bld.BaseProject;
import rife.bld.dependencies.Version;
import rife.bld.dependencies.VersionNumber;
import rife.bld.extension.tools.*;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Run JBang with the specified arguments.
 * <p>
 * JBang is launched via its wrapper, resolved from {@link #jBangHome(File) jBangHome} (or
 * {@code JBANG_HOME}), a sandbox-downloaded distribution, or the {@code PATH}. When a
 * specific {@link #version(Version) version} is configured, that version is downloaded
 * into the sandbox and used regardless of any local JBang installation.
 * {@link #useDownloadedJBang() useDownloadedJBang()} forces the downloaded distribution
 * even when a local JBang is available.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
@NullMarked
@SuppressFBWarnings(value = "EI_EXPOSE_REP",
        justification = "Builder pattern intentionally exposes mutable collections; callers may add to them directly")
public class JBangOperation extends AbstractOperation<JBangOperation> {

    /**
     * A known stable JBang version.
     * <p>
     * This constant is <em>not</em> used as a default; when no version is configured the
     * latest release is resolved (and cached). Use this constant to pin a reproducible
     * version via {@link #version(Version)} or {@link #version(String)}.
     *
     * @since 1.0
     */
    public static final Version JBANG_VERSION = new VersionNumber(0, 143, 0);
    /**
     * URL of the text file that reports the latest JBang version.
     */
    private static final String JBANG_LATEST_VERSION_URL =
            "https://www.jbang.dev/releases/latest/download/version.txt";
    /**
     * URL template for the JBang distribution zip, with the version substituted twice.
     */
    private static final String JBANG_RELEASES_URL =
            "https://github.com/jbangdev/jbang/releases/download/v%s/jbang-%s.zip";
    private static final String LATEST_VERSION_CACHE_FILE = ".jbang-latest-version";
    private static final int VERSION_FETCH_TIMEOUT_MILLIS = 10_000;
    private static final Logger logger = Logger.getLogger(JBangOperation.class.getName());
    private static final Consumer<String> defaultOutputConsumer = logger::info;
    private final List<String> args_ = new ArrayList<>();
    private final Map<String, String> env_ = new HashMap<>();
    private final List<String> jBangArgs_ = new ArrayList<>();
    private boolean exitOnFailure_ = true;
    private boolean inheritIO_ = true;
    private @Nullable File jBangHome_;
    private Consumer<String> outputConsumer_ = defaultOutputConsumer;
    private @Nullable BaseProject project_;
    private @Nullable String resolvedLatestVersion_;
    private @Nullable String script_;
    private long timeout_ = 600L;
    private boolean useDownloadedJBang_;
    private @Nullable Version version_;
    private @Nullable File workDir_;

    /**
     * Enables or disables verbose output.
     * <p>
     * Corresponds to {@code --verbose}.
     *
     * @param verbose {@code true} to enable verbose output
     * @return this operation instance
     * @since 1.1
     */
    @Override
    public JBangOperation verbose(boolean verbose) {
        super.verbose(verbose);
        return optBool("--verbose", verbose);
    }

    /**
     * Performs the operation.
     *
     * @throws ExitStatusException if workDir is missing or invalid, execution fails, or times out
     */
    @Override
    @SuppressWarnings({"PMD.PreserveStackTrace", "PMD.DoNotUseThreads"})
    @SuppressFBWarnings("LEST_LOST_EXCEPTION_STACK_TRACE")
    public void execute() throws ExitStatusException {
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

        var command = buildJBangCommand();
        command.addAll(jBangArgs_);
        if (script_ != null) {
            command.add(script_);
        }
        command.addAll(args_);

        if (logger.isLoggable(Level.INFO) && !silent()) {
            logger.info(PathTools.formatCommandLine(command));
        }

        try {
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
        } catch (IOException e) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
    }

    /**
     * Determines if the current operating system is AIX.
     *
     * @return {@code true} if the operating system is identified as AIX
     * @see SystemTools#isAix()
     */
    public static boolean isAix() {
        return SystemTools.isAix();
    }

    /**
     * Determines if the current operating system is Cygwin.
     *
     * @return {@code true} if the operating system is identified as Cygwin
     * @see SystemTools#isCygwin()
     */
    public static boolean isCygwin() {
        return SystemTools.isCygwin();
    }

    /**
     * Determines if the current operating system is FreeBSD.
     *
     * @return {@code true} if the operating system is identified as FreeBSD
     * @see SystemTools#isFreeBsd()
     */
    public static boolean isFreeBsd() {
        return SystemTools.isFreeBsd();
    }

    /**
     * Determines if the current operating system is Linux.
     *
     * @return {@code true} if the operating system is Linux
     * @see SystemTools#isLinux()
     */
    public static boolean isLinux() {
        return SystemTools.isLinux();
    }

    /**
     * Determines if the current operating system is macOS.
     *
     * @return {@code true} if the operating system is macOS
     * @see SystemTools#isMacOS()
     */
    public static boolean isMacOS() {
        return SystemTools.isMacOS();
    }

    /**
     * Determines if the current operating system is MinGW.
     *
     * @return {@code true} if the operating system is identified as MinGW
     * @see SystemTools#isMinGw()
     */
    public static boolean isMinGw() {
        return SystemTools.isMinGw();
    }

    /**
     * Determines if the current operating system is OpenVMS.
     *
     * @return {@code true} if the operating system is OpenVMS
     * @see SystemTools#isOpenVms()
     */
    public static boolean isOpenVms() {
        return SystemTools.isOpenVms();
    }

    /**
     * Determines if the current operating system is Solaris.
     *
     * @return {@code true} if the operating system is Solaris
     * @see SystemTools#isSolaris()
     */
    public static boolean isSolaris() {
        return SystemTools.isSolaris();
    }

    /**
     * Determines if the current operating system is Windows.
     *
     * @return {@code true} if the operating system is Windows
     * @see SystemTools#isWindows()
     */
    public static boolean isWindows() {
        return SystemTools.isWindows();
    }

    /**
     * Adds the arguments passed to the script.
     *
     * @param args the script arguments
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     */
    public JBangOperation args(Collection<String> args) {
        args_.addAll(TextTools.requireNotEmpty(args, "args"));
        return this;
    }

    /**
     * Adds the arguments passed to the script.
     *
     * @param args the script arguments
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     */
    public JBangOperation args(String... args) {
        args_.addAll(List.of(TextTools.requireNotEmpty("args", args)));
        return this;
    }

    /**
     * Returns the live list of script arguments.
     *
     * @return the mutable script arguments list
     */
    public List<String> args() {
        return args_;
    }

    /**
     * Sets the JBang configuration file.
     * <p>
     * Corresponds to {@code --config}.
     *
     * @param file the configuration file path
     * @return this operation instance
     * @throws NullPointerException     if {@code file} is {@code null}
     * @throws IllegalArgumentException if {@code file} is blank
     * @since 1.1
     */
    public JBangOperation config(String file) {
        TextTools.requireNotBlank(file, "config");
        return config(new File(file));
    }

    /**
     * Sets the JBang configuration file.
     * <p>
     * Corresponds to {@code --config}.
     *
     * @param file the configuration file
     * @return this operation instance
     * @throws NullPointerException if {@code file} is {@code null}
     * @since 1.1
     */
    public JBangOperation config(File file) {
        ObjectTools.requireNonNull(file, "config");
        return opt("--config", file.getAbsolutePath());
    }

    /**
     * Sets the JBang configuration file.
     * <p>
     * Corresponds to {@code --config}.
     *
     * @param file the configuration file path
     * @return this operation instance
     * @throws NullPointerException if {@code file} is {@code null}
     * @since 1.1
     */
    public JBangOperation config(Path file) {
        ObjectTools.requireNonNull(file, "config");
        return config(file.toFile());
    }

    /**
     * Adds an environment variable, merged with the current process environment.
     *
     * @param name  the variable name
     * @param value the variable value
     * @return this operation instance
     * @throws IllegalArgumentException if {@code name} is blank
     * @throws NullPointerException     if {@code name} or {@code value} is {@code null}
     */
    public JBangOperation env(String name, String value) {
        env_.put(TextTools.requireNotBlank(name, "env name"),
                ObjectTools.requireNonNull(value, "env value"));
        return this;
    }

    /**
     * Adds environment variables, merged with the current process environment.
     *
     * @param vars the environment variables
     * @return this operation instance
     * @throws NullPointerException if {@code vars} is {@code null} or contains {@code null}
     */
    public JBangOperation env(Map<String, String> vars) {
        env_.putAll(ObjectTools.requireNonNull(vars, "env"));
        return this;
    }

    /**
     * Returns the live environment variables map.
     *
     * @return the mutable environment variables map
     */
    public Map<String, String> env() {
        return env_;
    }

    /**
     * Sets whether to exit upon JBang execution failure.
     * <p>
     * Default is {@code true}.
     *
     * @param exitOnFailure {@code true} to exit on failure
     * @return this operation instance
     */
    public JBangOperation exitOnFailure(boolean exitOnFailure) {
        exitOnFailure_ = exitOnFailure;
        return this;
    }

    /**
     * Enables or disables fresh (non-cached) resource resolution.
     * <p>
     * Corresponds to {@code --fresh}.
     *
     * @param fresh {@code true} to force fresh resolution
     * @return this operation instance
     * @since 1.1
     */
    public JBangOperation fresh(boolean fresh) {
        return optBool("--fresh", fresh);
    }

    /**
     * Configures the operation from a {@link BaseProject}.
     * <p>
     * Sets {@link #workDir() workDir} from the project if not already configured. When
     * neither {@link #jBangHome(File) jBangHome} nor {@link #useDownloadedJBang() useDownloadedJBang()}
     * has been configured, the {@code JBANG_HOME} environment variable is consulted and,
     * when set, used as the JBang home directory. The project is retained for sandbox
     * downloads.
     *
     * @param project the project
     * @return this operation instance
     * @throws NullPointerException if {@code project} is {@code null}
     */
    public JBangOperation fromProject(BaseProject project) {
        project_ = ObjectTools.requireNonNull(project, "fromProject");
        if (workDir_ == null) {
            workDir_ = project.workDirectory().getAbsoluteFile();
        }

        if (jBangHome_ == null && !useDownloadedJBang_) {
            var jbangHomeEnv = System.getenv("JBANG_HOME");
            if (jbangHomeEnv != null) {
                jBangHome_ = new File(jbangHomeEnv);
            }
        }
        return this;
    }

    /**
     * Sets whether the child process inherits the current JVM's I/O streams.
     * <p>
     * When {@code true}, output is not captured. When {@code false}, stdout and stderr are
     * merged and passed to the {@link #outputConsumer(Consumer) output consumer}.
     * <p>
     * Default is {@code true}.
     *
     * @param inheritIO {@code true} to inherit I/O
     * @return this operation instance
     */
    public JBangOperation inheritIO(boolean inheritIO) {
        inheritIO_ = inheritIO;
        return this;
    }

    /**
     * Enables or disables trusting all SSL certificates.
     * <p>
     * Corresponds to {@code --insecure}.
     *
     * @param insecure {@code true} to trust all certificates
     * @return this operation instance
     * @since 1.1
     */
    public JBangOperation insecure(boolean insecure) {
        return optBool("--insecure", insecure);
    }

    /**
     * Returns whether to exit upon JBang execution failure.
     *
     * @return {@code true} if the operation exits on failure
     */
    public boolean isExitOnFailure() {
        return exitOnFailure_;
    }

    /**
     * Returns whether the child process inherits the current JVM's I/O streams.
     *
     * @return {@code true} if I/O is inherited
     * @see #inheritIO(boolean)
     */
    public boolean isInheritIO() {
        return inheritIO_;
    }

    /**
     * Adds the arguments passed to JBang itself, before the script.
     *
     * @param args the JBang arguments
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     */
    public JBangOperation jBangArgs(Collection<String> args) {
        jBangArgs_.addAll(TextTools.requireNotEmpty(args, "jBangArgs"));
        return this;
    }

    /**
     * Adds the arguments passed to JBang itself, before the script.
     *
     * @param args the JBang arguments
     * @return this operation instance
     * @throws NullPointerException     if {@code args} is {@code null} or contains {@code null}
     * @throws IllegalArgumentException if {@code args} is empty or contains empty elements
     */
    public JBangOperation jBangArgs(String... args) {
        jBangArgs_.addAll(List.of(TextTools.requireNotEmpty("jBangArgs", args)));
        return this;
    }

    /**
     * Returns the live list of JBang arguments.
     *
     * @return the mutable JBang arguments list
     */
    public List<String> jBangArgs() {
        return jBangArgs_;
    }

    /**
     * Sets the JBang home directory, resolved under its {@code bin/} directory.
     * <p>
     * Used as the launcher when no explicit {@link #version(Version) version} is configured.
     * Takes precedence over the sandbox-downloaded latest distribution, and cancels any
     * previous {@link #useDownloadedJBang() useDownloadedJBang()} call.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     * @throws NullPointerException     if {@code jBangHome} is {@code null}
     * @throws IllegalArgumentException if {@code jBangHome} is blank
     */
    public JBangOperation jBangHome(String jBangHome) {
        TextTools.requireNotBlank(jBangHome, "jBangHome");
        jBangHome_ = new File(jBangHome);
        useDownloadedJBang_ = false;
        return this;
    }

    /**
     * Sets the JBang home directory, resolved under its {@code bin/} directory.
     * <p>
     * Used as the launcher when no explicit {@link #version(Version) version} is configured.
     * Takes precedence over the sandbox-downloaded latest distribution, and cancels any
     * previous {@link #useDownloadedJBang() useDownloadedJBang()} call.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     * @throws NullPointerException if {@code jBangHome} is {@code null}
     */
    public JBangOperation jBangHome(File jBangHome) {
        ObjectTools.requireNonNull(jBangHome, "jBangHome");
        jBangHome_ = jBangHome;
        useDownloadedJBang_ = false;
        return this;
    }

    /**
     * Sets the JBang home directory, resolved under its {@code bin/} directory.
     * <p>
     * Used as the launcher when no explicit {@link #version(Version) version} is configured.
     * Takes precedence over the sandbox-downloaded latest distribution, and cancels any
     * previous {@link #useDownloadedJBang() useDownloadedJBang()} call.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     * @throws NullPointerException if {@code jBangHome} is {@code null}
     */
    public JBangOperation jBangHome(Path jBangHome) {
        ObjectTools.requireNonNull(jBangHome, "jBangHome");
        jBangHome_ = jBangHome.toFile();
        useDownloadedJBang_ = false;
        return this;
    }

    /**
     * Returns the JBang home directory.
     *
     * @return the JBang home directory
     */
    @Nullable
    public File jBangHome() {
        return jBangHome_;
    }

    /**
     * Sets the {@code .netrc} file used for HTTP authentication.
     * <p>
     * Corresponds to {@code --netrc}.
     *
     * @param file the {@code .netrc} file path
     * @return this operation instance
     * @throws NullPointerException     if {@code file} is {@code null}
     * @throws IllegalArgumentException if {@code file} is blank
     * @since 1.1
     */
    public JBangOperation netrc(String file) {
        TextTools.requireNotBlank(file, "netrc");
        return netrc(new File(file));
    }

    /**
     * Sets the {@code .netrc} file used for HTTP authentication.
     * <p>
     * Corresponds to {@code --netrc}.
     *
     * @param file the {@code .netrc} file
     * @return this operation instance
     * @throws NullPointerException if {@code file} is {@code null}
     * @since 1.1
     */
    public JBangOperation netrc(File file) {
        ObjectTools.requireNonNull(file, "netrc");
        return opt("--netrc", file.getAbsolutePath());
    }

    /**
     * Sets the {@code .netrc} file used for HTTP authentication.
     * <p>
     * Corresponds to {@code --netrc}.
     *
     * @param file the {@code .netrc} file path
     * @return this operation instance
     * @throws NullPointerException if {@code file} is {@code null}
     * @since 1.1
     */
    public JBangOperation netrc(Path file) {
        ObjectTools.requireNonNull(file, "netrc");
        return netrc(file.toFile());
    }

    /**
     * Enables or disables {@code .netrc} authentication lookup.
     * <p>
     * Corresponds to {@code --no-netrc}.
     *
     * @param noNetrc {@code true} to disable {@code .netrc} lookup
     * @return this operation instance
     * @since 1.1
     */
    public JBangOperation noNetrc(boolean noNetrc) {
        return optBool("--no-netrc", noNetrc);
    }

    /**
     * Enables or disables offline mode.
     * <p>
     * Corresponds to {@code --offline}.
     *
     * @param offline {@code true} to work offline
     * @return this operation instance
     * @since 1.1
     */
    public JBangOperation offline(boolean offline) {
        return optBool("--offline", offline);
    }

    /**
     * Sets the consumer receiving output lines when not inheriting I/O.
     * <p>
     * Default logs at INFO level.
     *
     * @param outputConsumer the output consumer
     * @return this operation instance
     * @throws NullPointerException if {@code outputConsumer} is {@code null}
     */
    public JBangOperation outputConsumer(Consumer<String> outputConsumer) {
        ObjectTools.requireNonNull(outputConsumer, "outputConsumer");
        outputConsumer_ = outputConsumer;
        return this;
    }

    /**
     * Enables or disables quiet mode.
     * <p>
     * Corresponds to {@code --quiet}.
     *
     * @param quiet {@code true} to suppress non-error output
     * @return this operation instance
     */
    public JBangOperation quiet(boolean quiet) {
        return optBool("--quiet", quiet);
    }

    /**
     * Resets the script-related state: {@link #args() args}, {@link #env() env},
     * {@link #jBangArgs() JBang arguments}, {@link #isExitOnFailure() exitOnFailure}, and
     * {@link #script() script}.
     * <p>
     * {@link #workDir() workDir}, {@link #jBangHome() jBangHome},
     * {@link #useDownloadedJBang() useDownloadedJBang}, {@link #timeout() timeout},
     * {@link #isInheritIO() inheritIO}, the
     * {@link #outputConsumer(Consumer) output consumer}, {@link #version() version}, and the
     * project configured via {@link #fromProject(BaseProject) fromProject} are preserved.
     *
     * @since 1.1
     */
    public void reset() {
        args_.clear();
        env_.clear();
        jBangArgs_.clear();
        exitOnFailure_ = true;
        script_ = null;
    }

    /**
     * Sets the script to execute.
     *
     * @param script the script
     * @return this operation instance
     * @throws NullPointerException     if {@code script} is {@code null}
     * @throws IllegalArgumentException if {@code script} is blank
     */
    public JBangOperation script(String script) {
        script_ = TextTools.requireNotBlank(script, "script");
        return this;
    }

    /**
     * Returns the script to execute.
     *
     * @return the script, or {@code null}
     */
    @Nullable
    public String script() {
        return script_;
    }

    /**
     * Enables or disables printing stack traces to stderr, even when quiet.
     * <p>
     * Corresponds to {@code --stacktrace}.
     *
     * @param stacktrace {@code true} to print stack traces
     * @return this operation instance
     * @since 1.1
     */
    public JBangOperation stacktrace(boolean stacktrace) {
        return optBool("--stacktrace", stacktrace);
    }

    /**
     * Sets the execution timeout in seconds.
     * <p>
     * A negative value means no timeout. {@code 0} is rejected. Default is {@code 600}.
     *
     * @param seconds the timeout in seconds
     * @return this operation instance
     * @throws IllegalArgumentException if {@code seconds} is {@code 0}
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
     * Returns the execution timeout in seconds.
     *
     * @return the timeout in seconds; negative for no timeout
     */
    public long timeout() {
        return timeout_;
    }

    /**
     * Forces the use of a sandbox-downloaded JBang distribution, ignoring any local JBang
     * installation (including one inherited from the {@code JBANG_HOME} environment
     * variable).
     * <p>
     * When combined with {@link #version(Version) version}, the specified version is
     * downloaded. Otherwise, the latest version is fetched.
     * <p>
     * The setting is canceled by a subsequent {@link #jBangHome(File) jBangHome} call.
     * A {@link BaseProject} must be configured via
     * {@link #fromProject(BaseProject) fromProject} for the download to have a destination;
     * without one, the operation falls back to the {@code PATH}-resolved JBang.
     *
     * @return this operation instance
     * @since 1.1
     */
    public JBangOperation useDownloadedJBang() {
        useDownloadedJBang_ = true;
        jBangHome_ = null;
        return this;
    }

    /**
     * Sets the JBang version to download into the sandbox.
     * <p>
     * When set, that version is downloaded and used instead of any local JBang installation.
     * When left {@code null} (the default), the latest version is resolved (and cached)
     * unless a local JBang is available, in which case it is used unless
     * {@link #useDownloadedJBang() useDownloadedJBang()} forces the download.
     *
     * @param version the version
     * @return this operation instance
     * @throws NullPointerException if {@code version} is {@code null}
     * @since 1.1
     */
    public JBangOperation version(Version version) {
        version_ = ObjectTools.requireNonNull(version, "version");
        return this;
    }

    /**
     * Sets the JBang version to download into the sandbox.
     * <p>
     * When set, that version is downloaded and used instead of any local JBang installation.
     * When left {@code null} (the default), the latest version is resolved (and cached)
     * unless a local JBang is available, in which case it is used unless
     * {@link #useDownloadedJBang() useDownloadedJBang()} forces the download.
     *
     * @param version the version
     * @return this operation instance
     * @throws NullPointerException     if {@code version} is {@code null}
     * @throws IllegalArgumentException if {@code version} is blank or malformed
     * @since 1.1
     */
    public JBangOperation version(String version) {
        version_ = VersionNumber.parse(TextTools.requireNotBlank(version, "version"));
        return this;
    }

    /**
     * Returns the JBang version to download into the sandbox.
     *
     * @return the version, or {@code null} when the latest version is resolved automatically
     * @since 1.1
     */
    @Nullable
    public Version version() {
        return version_;
    }

    /**
     * Returns the working directory.
     *
     * @return the working directory, or {@code null}
     */
    @Nullable
    public File workDir() {
        return workDir_;
    }

    /**
     * Sets the working directory.
     *
     * @param dir the directory
     * @return this operation instance
     * @throws NullPointerException if {@code dir} is {@code null}
     */
    public JBangOperation workDir(File dir) {
        workDir_ = ObjectTools.requireNonNull(dir, "workDir");
        return this;
    }

    /**
     * Sets the working directory.
     *
     * @param dir the directory
     * @return this operation instance
     * @throws NullPointerException if {@code dir} is {@code null}
     */
    public JBangOperation workDir(Path dir) {
        ObjectTools.requireNonNull(dir, "workDir");
        workDir_ = dir.toFile();
        return this;
    }

    /**
     * Sets the working directory.
     *
     * @param dir the directory path
     * @return this operation instance
     * @throws NullPointerException     if {@code dir} is {@code null}
     * @throws IllegalArgumentException if {@code dir} is blank
     */
    public JBangOperation workDir(String dir) {
        TextTools.requireNotBlank(dir, "workDir");
        workDir_ = (new File(dir));
        return this;
    }

    /**
     * Builds the launcher prefix of the JBang command.
     * <p>
     * Resolution order:
     * <ol>
     *     <li>A specific {@link #version(Version) version}, when configured and a project is
     *     available: downloads that version into the sandbox and uses it, regardless of any
     *     local JBang installation.</li>
     *     <li>{@link #jBangHome() jBangHome}, when set.</li>
     *     <li>{@link #useDownloadedJBang() useDownloadedJBang()} with a project: downloads
     *     the latest JBang distribution into the sandbox.</li>
     *     <li>When a project is available, a {@code jbang} executable found on the
     *     {@code PATH}, falling back to the latest JBang distribution downloaded into
     *     the sandbox.</li>
     *     <li>Otherwise, the bare {@code jbang} name, resolved via {@code PATH} by the
     *     operating system when the process is spawned.</li>
     * </ol>
     * <p>
     * When an explicit version is requested but cannot be applied (no project), a warning
     * is logged and the fallback is used.
     *
     * @return the launcher command prefix
     * @throws ExitStatusException if a configured launcher is missing or not executable
     */
    List<String> buildJBangCommand() throws ExitStatusException {
        var command = new ArrayList<String>();
        var launcher = isWindows() ? "jbang.cmd" : "jbang";

        if (version_ != null) {
            if (project_ != null) {
                command.add(resolveExecFromHome(downloadJBangDistribution(), launcher));
            } else if (jBangHome_ != null) {
                warnExplicitVersionIgnored("the configured JBang home");
                command.add(resolveExecFromHome(jBangHome_, launcher));
            } else {
                warnExplicitVersionIgnored("the PATH-resolved JBang");
                command.add(pathLauncherOrFallback(launcher));
            }
        } else if (jBangHome_ != null) {
            command.add(resolveExecFromHome(jBangHome_, launcher));
        } else if (useDownloadedJBang_) {
            if (project_ != null) {
                command.add(resolveExecFromHome(downloadJBangDistribution(), launcher));
            } else {
                if (logger.isLoggable(Level.WARNING) && !silent()) {
                    logger.warning("A downloaded JBang was requested but no project is available; "
                            + "falling back to the PATH-resolved JBang.");
                }
                command.add(pathLauncherOrFallback(launcher));
            }
        } else if (project_ != null) {
            var pathExec = findOnPath(launcher);
            if (pathExec != null) {
                command.add(pathExec);
            } else {
                command.add(resolveExecFromHome(downloadJBangDistribution(), launcher));
            }
        } else {
            command.add(launcher);
        }

        if (logger.isLoggable(Level.INFO) && !silent()) {
            logger.info("Using JBang executable: " + command.get(0));
        }

        return command;
    }

    /**
     * Downloads and extracts the JBang distribution into the project sandbox.
     *
     * @return the extracted JBang home directory
     * @throws ExitStatusException if the version cannot be resolved, or download or extraction fails
     */
    @SuppressWarnings("PMD.PreserveStackTrace")
    @SuppressFBWarnings("LEST_LOST_EXCEPTION_STACK_TRACE")
    private File downloadJBangDistribution() throws ExitStatusException {
        var project = ObjectTools.requireNonNull(project_, "project");
        var version = resolveJBangVersion(project);
        try {
            var url = URI.create(String.format(JBANG_RELEASES_URL, version, version)).toURL();
            var sandbox = new Sandbox("bld-jbang", project, silent());
            var extracted = sandbox.downloadZip(url, version);
            var home = new File(extracted.toFile(), "jbang-" + version);
            if (!home.isDirectory()) {
                if (logger.isLoggable(Level.SEVERE) && !silent()) {
                    logger.severe("JBang distribution layout not recognized: " + home.getAbsolutePath());
                }
                throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
            }
            return home;
        } catch (IOException e) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
    }

    /**
     * Fetches the latest JBang version from the release server, with connect and read
     * timeouts, validating the result.
     *
     * @return the validated latest version
     * @throws IOException if the fetch fails, the response is empty, or the version is malformed
     */
    private String fetchLatestJBangVersion() throws IOException {
        var connection = URI.create(JBANG_LATEST_VERSION_URL).toURL().openConnection();
        connection.setConnectTimeout(VERSION_FETCH_TIMEOUT_MILLIS);
        connection.setReadTimeout(VERSION_FETCH_TIMEOUT_MILLIS);
        String version;
        try (var in = connection.getInputStream()) {
            version = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        if (version.isEmpty()) {
            throw new IOException("Empty version response from " + JBANG_LATEST_VERSION_URL);
        }
        try {
            return VersionNumber.parse(version).toString();
        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "Invalid JBang version from " + JBANG_LATEST_VERSION_URL + ": " + version, e);
        }
    }

    /**
     * Searches the {@code PATH} for an executable.
     *
     * @param launcher the executable name
     * @return the absolute path to the executable, or {@code null} when not found
     */
    @Nullable
    private String findOnPath(String launcher) {
        var path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (var entry : path.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                var candidate = Path.of(entry, launcher);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            } catch (InvalidPathException ignored) {
                // malformed PATH entry (e.g. quoted entries on Windows): skip it
            }
        }
        return null;
    }

    /**
     * Returns whether the operation is configured to run offline.
     *
     * @return {@code true} if {@code --offline} is among the JBang arguments
     */
    private boolean isOffline() {
        return jBangArgs_.contains("--offline");
    }

    /**
     * Adds or replaces a {@code --option=value} argument.
     *
     * @param option the option name
     * @param value  the option value
     * @return this operation instance
     */
    private JBangOperation opt(String option, String value) {
        jBangArgs_.removeIf(arg -> arg.equals(option) || arg.startsWith(option + "="));
        jBangArgs_.add(option + "=" + value);
        return this;
    }

    /**
     * Adds or removes a flag argument.
     *
     * @param option the option name
     * @param enable {@code true} to add the flag, {@code false} to remove it
     * @return this operation instance
     */
    private JBangOperation optBool(String option, boolean enable) {
        jBangArgs_.remove(option);
        if (enable) {
            jBangArgs_.add(option);
        }
        return this;
    }

    /**
     * Resolves the launcher via the {@code PATH}, falling back to the bare name.
     *
     * @param launcher the launcher name
     * @return the absolute path if found on the {@code PATH}, otherwise {@code launcher}
     */
    private String pathLauncherOrFallback(String launcher) {
        var found = findOnPath(launcher);
        return found != null ? found : launcher;
    }

    /**
     * Reads the cached latest JBang version from the project build directory.
     *
     * @param project the non-null project whose build directory hosts the version cache
     * @return the cached version, or {@code null} when absent or unreadable
     */
    @Nullable
    private String readCachedLatestVersion(BaseProject project) {
        var cacheFile = project.buildDirectory().toPath().resolve(LATEST_VERSION_CACHE_FILE);
        if (!Files.isRegularFile(cacheFile)) {
            return null;
        }
        try {
            var value = Files.readString(cacheFile, StandardCharsets.UTF_8).trim();
            if (value.isEmpty()) {
                return null;
            }
            return VersionNumber.parse(value).toString();
        } catch (IOException | IllegalArgumentException e) {
            if (logger.isLoggable(Level.FINE) && !silent()) {
                logger.log(Level.FINE, "Ignoring unreadable JBang version cache: " + cacheFile, e);
            }
            return null;
        }
    }

    /**
     * Resolves the JBang launcher under the given home's {@code bin/} directory, verifying
     * it is executable.
     *
     * @param home  the JBang home directory
     * @param jbang the launcher name
     * @return the absolute path to the launcher
     * @throws ExitStatusException if the launcher is missing or not executable
     */
    private String resolveExecFromHome(File home, String jbang) throws ExitStatusException {
        var exec = Path.of(home.getAbsolutePath(), "bin", jbang).toFile();
        if (!exec.isFile()) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("JBang executable not found: " + exec.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        if (!exec.canExecute() && !exec.setExecutable(true)) {
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("JBang executable not executable and could not be made executable: "
                        + exec.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        return exec.getAbsolutePath();
    }

    /**
     * Resolves the JBang version to download.
     * <p>
     * When a version is explicitly configured, it is returned. Otherwise, the latest version
     * is fetched from the release server, using the persistent cache when offline or when
     * the fetch fails.
     *
     * @param project the non-null project whose build directory hosts the version cache
     * @return the configured version, or the latest from the release server
     * @throws ExitStatusException if the latest version cannot be resolved
     */
    @SuppressWarnings("PMD.PreserveStackTrace")
    private String resolveJBangVersion(BaseProject project) throws ExitStatusException {
        if (version_ != null) {
            return version_.toString();
        }
        if (resolvedLatestVersion_ != null) {
            return resolvedLatestVersion_;
        }
        var cached = readCachedLatestVersion(project);
        if (isOffline()) {
            if (cached != null) {
                resolvedLatestVersion_ = cached;
                return cached;
            }
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.severe("Cannot resolve the latest JBang version while offline; configure an "
                        + "explicit version or run online once to populate the cache.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
        try {
            var fetched = fetchLatestJBangVersion();
            resolvedLatestVersion_ = fetched;
            writeCachedLatestVersion(project, fetched);
            return fetched;
        } catch (IOException e) {
            if (cached != null) {
                if (logger.isLoggable(Level.WARNING) && !silent()) {
                    logger.log(Level.WARNING, "Failed to fetch the latest JBang version; "
                            + "using cached version " + cached, e);
                }
                resolvedLatestVersion_ = cached;
                return cached;
            }
            if (logger.isLoggable(Level.SEVERE) && !silent()) {
                logger.log(Level.SEVERE, e.getLocalizedMessage(), e);
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
    }

    /**
     * Logs that an explicit version was requested but could not be applied.
     *
     * @param fallback the fallback that will be used
     */
    private void warnExplicitVersionIgnored(String fallback) {
        if (logger.isLoggable(Level.WARNING) && !silent()) {
            logger.warning("An explicit JBang version was requested but no project is available "
                    + "for downloading; falling back to " + fallback + ".");
        }
    }

    /**
     * Persists the resolved latest JBang version to the project build directory.
     *
     * @param project the non-null project whose build directory hosts the version cache
     * @param version the resolved version
     */
    private void writeCachedLatestVersion(BaseProject project, String version) {
        var buildDir = project.buildDirectory().toPath();
        var cacheFile = buildDir.resolve(LATEST_VERSION_CACHE_FILE);
        try {
            Files.createDirectories(buildDir);
            Files.writeString(cacheFile, version, StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (logger.isLoggable(Level.FINE) && !silent()) {
                logger.log(Level.FINE, "Failed to write JBang version cache: " + cacheFile, e);
            }
        }
    }
}