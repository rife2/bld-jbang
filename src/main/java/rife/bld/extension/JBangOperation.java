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

import rife.bld.BaseProject;
import rife.bld.operations.AbstractOperation;
import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Run JBang with the specified arguments.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 1.0
 */
public class JBangOperation extends AbstractOperation<JBangOperation> {
    private static final Logger LOGGER = Logger.getLogger(JBangOperation.class.getName());
    private final Collection<String> args_ = new ArrayList<>();
    private final Collection<String> jBangArgs_ = new ArrayList<>();
    private boolean exitOnFailure_ = true;
    private File jBangHome_;
    private BaseProject project_;
    private String script_;
    private File workDir_;

    private static List<String> createShellCommand() {
        var command = new ArrayList<String>();

        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
        } else {
            command.add("sh");
            command.add("-c");
        }
        return command;
    }

    /**
     * Determines if the operating system is Linux.
     *
     * @return true if the operating system is Linux, false otherwise.
     */
    public static boolean isLinux() {
        var osName = osNameProperty();
        return osName != null && (osName.contains("linux") || osName.contains("unix")); // Consider Unix-like systems as well.
    }

    /**
     * s
     * Determines if the current operating system is macOS.
     *
     * @return true if the OS is macOS, false otherwise.
     */
    public static boolean isMacOS() {
        var osName = osNameProperty();
        return osName != null && (osName.contains("mac") || osName.contains("darwin") || osName.contains("osx"));
    }

    /**
     * Determines if the current operating system is Windows.
     *
     * @return true if the operating system is Windows, false otherwise.
     */
    public static boolean isWindows() {
        var osName = osNameProperty();
        return osName != null && osName.contains("win");
    }

    private static String osNameProperty() {
        return System.getProperty("os.name") != null ? System.getProperty("os.name").toLowerCase(Locale.ENGLISH) : null;
    }

    /**
     * Sets the arguments to be used in the {@link #script(String) script}.
     *
     * @param args the arguments to use in the script
     * @return this operation instance
     */
    public JBangOperation args(Collection<String> args) {
        this.args_.addAll(args);
        return this;
    }

    /**
     * Sets the arguments to be used in the {@link #script(String) script}.
     *
     * @param args the arguments to use in the script
     * @return this operation instance
     */
    public JBangOperation args(String... args) {
        this.args_.addAll(List.of(args));
        return this;
    }

    /**
     * Retrieves the collection of arguments to be passed to the script.
     *
     * @return a collection of arguments
     */
    public Collection<String> args() {
        return args_;
    }

    /**
     * Performs the operation
     *
     * @throws Exception if an error occurs
     */
    @Override
    @SuppressWarnings("PMD.PreserveStackTrace")
    public void execute() throws Exception {
        if (project_ == null) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("A project must be specified.");
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        } else if (!workDir_.isDirectory()) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe("Invalid working directory: " + workDir_.getAbsolutePath());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }

        var shellCommand = new ArrayList<>(createShellCommand());
        var shellCommandArgs = new ArrayList<String>();
        shellCommandArgs.add(findJBangExec());
        shellCommandArgs.addAll(jBangArgs_);
        if (script_ != null) {
            shellCommandArgs.add(script_);
        }
        shellCommandArgs.addAll(args_);

        var jBandCommand = String.join(" ", shellCommandArgs);
        if (LOGGER.isLoggable(Level.INFO) && !silent()) {
            LOGGER.info(jBandCommand);
        }
        shellCommand.add(jBandCommand);

        try {
            // run the command
            var pb = new ProcessBuilder();
            pb.inheritIO();
            pb.command(shellCommand);
            pb.directory(workDir_);

            var proc = pb.start();
            proc.waitFor();
            if (exitOnFailure_) {
                ExitStatusException.throwOnFailure(proc.exitValue());
            }
        } catch (Error | IOException | InterruptedException e) {
            if (LOGGER.isLoggable(Level.SEVERE) && !silent()) {
                LOGGER.severe(e.getLocalizedMessage());
            }
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }
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
        this.exitOnFailure_ = exitOnFailure;
        return this;
    }

    private String findJBangExec() {
        if (jBangHome_ != null) {
            if (isWindows()) {
                return Path.of(jBangHome_.getAbsolutePath(), "bin", "jbang.cmd").toString();
            } else {
                return Path.of(jBangHome_.getAbsolutePath(), "bin", "jbang").toString();
            }
        } else {
            if (isWindows()) {
                return "jbang.cmd";
            } else {
                return "jbang";
            }
        }
    }

    /**
     * Configures a compile operation from a {@link BaseProject}.
     * <p>
     * Sets the following from the project:
     * <ul>
     *     <li>{@link #workDir() workDir} to the project's directory.</li>
     *     <li>{@link #jBangHome() jBangHome} to the {@code JBANG_HOME} environment variable if set</li>
     * </ul>
     *
     * @param project the project to configure the compile operation from
     * @return this operation instance
     */
    public JBangOperation fromProject(BaseProject project) {
        project_ = project;
        workDir_ = project.workDirectory().getAbsoluteFile();

        var jBangHomeEnv = System.getenv("JBANG_HOME");
        if (jBangHomeEnv != null) {
            jBangHome(jBangHomeEnv);
        }
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
     * Sets the arguments to be used when running the {@link #script(String) script}
     *
     * @param jBangArgs the arguments to use when running the script
     * @return this operation instance
     */
    public JBangOperation jBangArgs(Collection<String> jBangArgs) {
        this.jBangArgs_.addAll(jBangArgs);
        return this;
    }

    /**
     * Sets one or more arguments to be used when running the script.
     *
     * @param jBangArgs the arguments to use when running the script
     * @return this operation instance
     */
    public JBangOperation jBangArgs(String... jBangArgs) {
        this.jBangArgs_.addAll(List.of(jBangArgs));
        return this;
    }

    /**
     * Retrieves the collection of arguments to be used when running the script.
     *
     * @return a collection of script arguments
     */
    public Collection<String> jBangArgs() {
        return jBangArgs_;
    }

    /**
     * Sets the JBang home directory.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     */
    public JBangOperation jBangHome(String jBangHome) {
        return jBangHome(new File(jBangHome));
    }

    /**
     * Sets the JBang home directory.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     */
    public JBangOperation jBangHome(File jBangHome) {
        this.jBangHome_ = jBangHome;
        return this;
    }

    /**
     * Sets the JBang home directory.
     *
     * @param jBangHome the JBang home directory
     * @return this operation instance
     */
    public JBangOperation jBangHome(Path jBangHome) {
        return jBangHome(jBangHome.toFile());
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
     * Resets the state of the operation to its default values.
     * <p>
     * Clears all {@link #jBangArgs() jBang arguments} used for execution, resets the
     * {@link #isExitOnFailure()  exit on failure flag} to {@code true}, removes the assigned
     * {@link #script() script} and {@link #args() arguments}.
     */
    public void reset() {
        args_.clear();
        jBangArgs_.clear();
        exitOnFailure_ = true;
        script_ = null;
    }

    /**
     * Sets the script to be executed.
     *
     * @param script the script to execute
     * @return this operation instance
     */
    public JBangOperation script(String script) {
        this.script_ = script;
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
     */
    public JBangOperation workDir(File dir) {
        workDir_ = dir;
        return this;
    }

    /**
     * Provides the working directory if it differs from the project's directory.
     *
     * @param dir the directory
     * @return this operation instance
     */
    public JBangOperation workDir(Path dir) {
        return workDir(dir.toFile());
    }

    /**
     * Provides the working directory if it differs from the project's directory.
     *
     * @param dir the directory path
     * @return this operation instance
     */
    public JBangOperation workDir(String dir) {
        return workDir(new File(dir));
    }

}