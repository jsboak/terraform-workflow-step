package com.plugin.terraform

import com.dtolabs.rundeck.plugins.PluginLogger
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import spock.lang.Specification
import java.nio.file.Files

class DefaultCommandRunnerSpec extends Specification {

    def "run returns ProcessResult with exit code 0 and correct output"() {
        given:
        def workDir = Files.createTempDirectory("testCmdRunner").toFile()
        def terraformPath = "/usr/bin/terraform"
        def env = [:]
        // Create a PluginStepContext stub inside the feature.
        def context = Stub(PluginStepContext) {
            getLogger() >> Stub(PluginLogger)
        }
        // Override ProcessBuilder.start so that no real process is launched.
        ProcessBuilder.metaClass.start = { ->
            return new FakeProcess(0, "fake output")
        }
        def runner = new DefaultCommandRunner()

        when:
        def result = runner.run(context, workDir, env as Map<String, String>, terraformPath, ["cmdArg1", "cmdArg2"])

        then:
        result.exitValue == 0
        result.output.trim() == "fake output"

        cleanup:
        workDir.deleteDir()
        // Remove our metaClass override to avoid side effects
        GroovySystem.metaClassRegistry.removeMetaClass(ProcessBuilder)
    }

    def "run returns non-zero ProcessResult on failure"() {
        given:
        def workDir = Files.createTempDirectory("testCmdRunner2").toFile()
        def terraformPath = "/usr/bin/terraform"
        def env = [:]
        def context = Stub(PluginStepContext) {
            getLogger() >> Stub(PluginLogger)
        }
        ProcessBuilder.metaClass.start = { ->
            return new FakeProcess(1, "error occurred")
        }
        def runner = new DefaultCommandRunner()

        when:
        def result = runner.run(context, workDir, env as Map<String, String>, terraformPath, ["cmdArg1", "cmdArg2"])

        then:
        result.exitValue == 1
        result.output.trim() == "error occurred"

        cleanup:
        workDir.deleteDir()
        GroovySystem.metaClassRegistry.removeMetaClass(ProcessBuilder)
    }

    // A fake Process implementation created within the spec.
    static class FakeProcess extends Process {
        int exitCode
        String output

        FakeProcess(int exitCode, String output) {
            this.exitCode = exitCode
            this.output = output
        }

        @Override
        InputStream getInputStream() {
            return new ByteArrayInputStream(output.getBytes("UTF-8"))
        }

        @Override
        OutputStream getOutputStream() {
            return new ByteArrayOutputStream()
        }

        @Override
        InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0])
        }

        @Override
        int waitFor() {
            return exitCode
        }

        @Override
        int exitValue() {
            return exitCode
        }

        @Override
        void destroy() { }
    }
}
