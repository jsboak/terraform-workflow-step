// File: EnvironmentBuilderSpec.groovy
package com.plugin.terraform

import com.dtolabs.rundeck.core.common.IFramework
import com.dtolabs.rundeck.core.common.IRundeckProject
import com.dtolabs.rundeck.core.common.ProjectManager
import com.dtolabs.rundeck.core.execution.ExecutionListener
import com.dtolabs.rundeck.plugins.PluginLogger
import spock.lang.Specification
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.core.execution.ExecutionContext
import org.rundeck.storage.api.Tree
import org.rundeck.storage.api.Resource
import com.dtolabs.rundeck.core.storage.ResourceMeta

class EnvironmentBuilderSpec extends Specification {

    /**
     * Helper method modeled after MongodbWorkflowStepSpec to create a minimal PluginStepContext.
     * In this context we only stub getExecutionContext() and its StorageTree.
     * Note that we intentionally do NOT stub getIFramework() because we are not enabling cloud credentials.
     */

    IRundeckProject iRundeckProject

    def mockContext(PluginLogger logger) {
        def storageTree = Mock(Tree) {
            // Explicitly constrain the argument to String.
            getResource(_ as String) >> { String path ->
                // Create a mock Resource that writes out the secret value.
                def resource = Mock(Resource) {
                    getContents() >> Mock(ResourceMeta) {
                        writeContent(_ as ByteArrayOutputStream) >> { it.write("secretValue".bytes) }
                    }
                }
                return resource
            }
        }
        iRundeckProject = Mock(IRundeckProject) {
        }

        ProjectManager projectManager = Mock(ProjectManager) {
            getFrameworkProject(_ as String) >> iRundeckProject
        }
        IFramework iFramework = Mock(IFramework) {
            getFrameworkProjectMgr() >> projectManager
        }
        def executionListener = Mock(ExecutionListener)

        def executionContext = Mock(ExecutionContext) {
            getExecutionListener() >> executionListener  // listener not used here
            getIFramework() >> iFramework
            getStorageTree() >> storageTree
        }
        def context = Mock(PluginStepContext) {
            getExecutionContext() >> executionContext
            getLogger() >> logger
            getFrameworkProject() >> "frameworkProject"
        }
        return context
    }

    def "should build environment with TF_LOG and resolve secret variable"() {
        given:
        def pluginLogger = Mock(PluginLogger)
        def ctx = mockContext(pluginLogger)
        def plugin = new TerraformStepPlugin()
        // Set the HCL/variables properties as needed.
        plugin.logLevel = "DEBUG"
        plugin.variables = """VAR1=value1
SECRET=keys://some/path
VAR2=value2"""
        // Avoid calling getIFramework() by not enabling any cloud credentials.
        plugin.useAws = false
        plugin.useAzure = false
        plugin.useGcp = false

        def builder = new EnvironmentBuilder()  // :contentReference[oaicite:1]{index=1}

        // Stub static secret lookup using GroovyMock (global:true) so that secret retrieval works.
        GroovyMock(Util, global: true)
        Util.getPasswordFromKeyStorage(_ as String, ctx) >> "secretValue"  // :contentReference[oaicite:2]{index=2}

        when:
        Map<String, String> env = builder.buildEnvironment(ctx, plugin)

        then:
        env["TF_LOG"] == "DEBUG"
        env["TF_VAR_VAR1"] == "value1"
        env["TF_VAR_VAR2"] == "value2"
        env["TF_VAR_SECRET"] == "secretValue"
    }

    def "should throw StepException when secret resolution fails"() {
        given:
        def ctx = mockContext()
        def plugin = new TerraformStepPlugin()
        plugin.variables = "BAD_SECRET=keys://nonexistent"
        plugin.useAws = false
        plugin.useAzure = false
        plugin.useGcp = false

        // Stub static secret lookup so that it throws an exception.
        GroovyMock(Util, global: true)
        Util.getPasswordFromKeyStorage(_ as String, ctx) >> { String path, PluginStepContext context ->
            throw new Exception("Key not found")
        }
        def builder = new EnvironmentBuilder()

        when:
        builder.buildEnvironment(ctx, plugin)

        then:
        def e = thrown(StepException)
        e.message.contains("Failed to retrieve secret for variable 'BAD_SECRET'")
    }
}
