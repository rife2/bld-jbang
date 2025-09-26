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
                .include(dependency("org.junit.jupiter", "junit-jupiter", version(5, 13, 4)))
                .include(dependency("org.junit.platform", "junit-platform-console-standalone", version(1, 13, 4)));
    }

    public static void main(String[] args) {
        new ExampleBuild().start(args);
    }

    @BuildCommand(description = "Runs JBang script.")
    public void jbang() throws Exception {
        // Initialize a script
        new JBangOperation()
                .fromProject(this)
                .jBangArgs("init", "hello.java")
                .execute();

        // Run the script
        new JBangOperation()
                .fromProject(this)
                .jBangArgs("--quiet")
                .script("hello.java")
                .execute();
    }
}