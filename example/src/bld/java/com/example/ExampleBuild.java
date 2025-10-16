package com.example;

import rife.bld.BuildCommand;
import rife.bld.Project;
import rife.bld.extension.JBangOperation;

import java.util.List;

import static rife.bld.dependencies.Repository.MAVEN_CENTRAL;
import static rife.bld.dependencies.Repository.RIFE2_RELEASES;
import static rife.bld.dependencies.Scope.test;

public class ExampleBuild extends Project {
    public ExampleBuild() {
        pkg = "com.example";
        name = "Example";
        mainClass = "com.example.Example";
        version = version(0, 1, 0);

        downloadSources = true;
        repositories = List.of(MAVEN_CENTRAL, RIFE2_RELEASES);
        scope(test)
                .include(dependency("org.junit.jupiter", "junit-jupiter", version(6, 0, 0)))
                .include(dependency("org.junit.platform", "junit-platform-console-standalone", version(6, 0, 0)));
    }

    public static void main(String[] args) {
        new ExampleBuild().start(args);
    }

    @BuildCommand(description = "Runs JBang script.")
    public void jbang() throws Exception {
        var op = new JBangOperation().fromProject(this);

        // Initialize a script. If JBang fails, the script already exists
        op.jBangArgs("init", "hello.java").exitOnFailure(false).execute();

        // Reset the JBang options
        op.reset();

        // Run the script
        op.jBangArgs("--quiet")
                .script("hello.java")
                .execute();
    }
}