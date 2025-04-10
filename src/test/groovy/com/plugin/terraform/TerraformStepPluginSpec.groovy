package com.plugin.terraform

import com.dtolabs.rundeck.core.common.IFramework
import com.dtolabs.rundeck.core.common.IRundeckProject
import com.dtolabs.rundeck.core.common.ProjectManager
import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.plugins.PluginLogger
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import spock.lang.Specification
import java.nio.file.Files

class TerraformStepPluginSpec extends Specification {

    def "should create main.tf when terraformHCL is provided"() {
        given:
        // Create a temporary working directory.
        def tempDir = Files.createTempDirectory("testTerraformPlugin").toFile()
        def plugin = new TerraformStepPlugin()
        plugin.workingDirectory = tempDir.absolutePath
        plugin.terraformHCL = 'resource "null_resource" "example" {}'
        plugin.terraformCommand = "init"
        // Override the executor’s executeInit method so that no external process runs.
        plugin.commandExecutor.metaClass.executeInit = { PluginStepContext ctx, File wd, Map<String, String> env, String tfPath -> }

        // Create the PluginStepContext stub entirely within the feature.
        def project = Stub(IRundeckProject)
        def projectManager = Stub(ProjectManager) {
            getFrameworkProject(_ as String) >> project
        }
        def framework = Stub(IFramework) {
            getFrameworkProjectMgr() >> projectManager
        }
        def executionContext = Stub(ExecutionContext) {
            getIFramework() >> framework
        }
        def context = Stub(PluginStepContext) {
            getExecutionContext() >> executionContext
            getLogger() >> Stub(PluginLogger)
            getFrameworkProject() >> "frameworkProject"
        }

        when:
        plugin.executeStep(context, [:])

        then:
        def tfFile = new File(tempDir, "main.tf")
        tfFile.exists()
        tfFile.text == 'resource "null_resource" "example" {}'

        cleanup:
        tempDir.deleteDir()
    }

    def "should throw StepException when no terraform configuration is available"() {
        given:
        def tempDir = Files.createTempDirectory("testTerraformPlugin2").toFile()
        def plugin = new TerraformStepPlugin()
        plugin.workingDirectory = tempDir.absolutePath
        // Empty terraformHCL and no existing .tf files will trigger the error.
        plugin.terraformHCL = ""
        plugin.terraformCommand = "init"
        plugin.commandExecutor.metaClass.executeInit = { PluginStepContext ctx, File wd, Map<String, String> env, String tfPath -> }

        def project = Stub(IRundeckProject)
        def projectManager = Stub(ProjectManager) {
            getFrameworkProject(_ as String) >> project
        }
        def framework = Stub(IFramework) {
            getFrameworkProjectMgr() >> projectManager
        }
        def executionContext = Stub(ExecutionContext) {
            getIFramework() >> framework
        }
        def context = Stub(PluginStepContext) {
            getExecutionContext() >> executionContext
            getLogger() >> Stub(PluginLogger)
            getFrameworkProject() >> "frameworkProject"
        }

        when:
        plugin.executeStep(context, [:])

        then:
        def e = thrown(StepException)
        e.message.contains("No Terraform configuration found")

        cleanup:
        tempDir.deleteDir()
    }
}
