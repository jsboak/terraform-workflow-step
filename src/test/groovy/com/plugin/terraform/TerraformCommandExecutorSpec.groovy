package com.plugin.terraform

import com.dtolabs.rundeck.core.common.IFramework
import com.dtolabs.rundeck.core.common.IRundeckProject
import com.dtolabs.rundeck.core.common.ProjectManager
import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.plugins.PluginLogger
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import spock.lang.Specification

class TerraformCommandExecutorSpec extends Specification {

    IRundeckProject iRundeckProject
    ExecutionContext executionContext
    PluginStepContext context
    File workDir = File.createTempDir("terraformTest", "")
    String terraformPath = "/usr/bin/terraform"
    Map<String,String> env = [:]


    def setup() {
        iRundeckProject = Mock(IRundeckProject) {}
        def framework = Mock(IFramework) {
            getFrameworkProjectMgr() >> Mock(ProjectManager) {
                getFrameworkProject(_) >> iRundeckProject
            }
        }
        executionContext = Mock(ExecutionContext) {
            getIFramework() >> framework
        }

        context = Mock(PluginStepContext) {
            getExecutionContext() >> executionContext
            getLogger() >> Mock(PluginLogger)
            getFrameworkProject() >> "frameworkProject"
        }
    }

    def cleanup() {
        workDir.deleteDir()
    }

    // FakeCommandRunner to simulate command executions
    class FakeCommandRunner implements CommandRunner {
        Closure<ProcessResult> handler
        FakeCommandRunner(Closure<ProcessResult> handler) {
            this.handler = handler
        }
        @Override
        ProcessResult run(PluginStepContext ctx, File wd, Map<String, String> env, String tfPath, List<String> args) {
            return handler(ctx, wd, env, tfPath, args)
        }
    }

    void "executeInit should run terraform init successfully"() {
        given:
        def fakeRunner = new FakeCommandRunner({ ctx, wd, environment, tfPath, args ->
            assert tfPath == terraformPath
            assert args == ["init"]
            new ProcessResult(0, "init output")
        })
        def executor = new TerraformCommandExecutor(fakeRunner)

        when:
        executor.executeInit(context, workDir, env, terraformPath)

        then:
        // Assert based on TestLogger contents or absence of exceptions.
        true
    }

    void "executePlan logs proper message based on exit code"() {
        when: "Terraform plan returns exitValue 0 (no changes)"
        def fakeRunner0 = new FakeCommandRunner({ ctx, wd, env, tfPath, args ->
            new ProcessResult(0, "plan output")
        })
        def executor0 = new TerraformCommandExecutor(fakeRunner0)
        executor0.executePlan(context, workDir, env, terraformPath, "key=value", "plan.tfvars", " -extra")

        then:
        // Verify that the logger was called once with a message containing "No changes required"
        1 * context.getLogger().log(3, { String message -> message.contains("No changes required") })

        when: "Terraform plan returns exitValue 2 (changes detected)"
        def fakeRunner2 = new FakeCommandRunner({ ctx, wd, env, tfPath, args ->
            new ProcessResult(2, "plan output with changes")
        })
        def executor2 = new TerraformCommandExecutor(fakeRunner2)
        executor2.executePlan(context, workDir, env, terraformPath, "key=value", "plan.tfvars", " -extra")

        then:
        // Verify that the logger was called with a message containing "Changes detected in plan"
        1 * context.getLogger().log(3, { String message -> message.contains("Changes detected in plan") })

        when: "Terraform plan returns an unexpected exit code"
        def fakeRunnerError = new FakeCommandRunner({ ctx, wd, env, tfPath, args ->
            new ProcessResult(1, "error output")
        })
        def executorError = new TerraformCommandExecutor(fakeRunnerError)
        executorError.executePlan(context, workDir, env, terraformPath, "key=value", "plan.tfvars", " -extra")

        then:
        def e = thrown(StepException)
        e.message.contains("Terraform plan failed")
    }


    void "executeWorkspace throws when workspace subcommand is missing"() {
        given:
        def fakeRunner = new FakeCommandRunner({ ctx, wd, environment, tfPath, args ->
            // This closure is not expected to be called since the method
            // should throw an exception before running the command.
            new ProcessResult(0, "")
        })
        def executor = new TerraformCommandExecutor(fakeRunner)

        when:
        executor.executeWorkspace(context, workDir, env, terraformPath, "", "")
        then:
        def e = thrown(StepException)
        e.message.contains("No workspace subcommand provided")
    }

    void "executeWorkspace throws on non-zero exit code"() {
        given:
        def fakeRunner = new FakeCommandRunner({ ctx, wd, environment, tfPath, args ->
            new ProcessResult(3, "workspace error")
        })
        def executor = new TerraformCommandExecutor(fakeRunner)

        when:
        executor.executeWorkspace(context, workDir, env, terraformPath, "new", "")
        then:
        def e = thrown(StepException)
        e.message.contains("Terraform apply failed for command")
    }

    void "executeOutput parses JSON output and populates outputs map"() {
        given:
        def sampleJson = '{"outputKey": {"value": "outputValue", "sensitive": false}}'
        def fakeRunner = new FakeCommandRunner({ ctx, wd, environment, tfPath, args ->
            new ProcessResult(0, sampleJson)
        })
        def executor = new TerraformCommandExecutor(fakeRunner)
        Map<String, Map<String, Object>> outputs = [:]

        when:
        executor.executeOutput(context, workDir, env, terraformPath, outputs)
        then:
        outputs.size() == 1
        outputs["outputKey"]["value"] == "outputValue"
    }

    void "executeOutput throws exception on non-zero exit code"() {
        given:
        def fakeRunner = new FakeCommandRunner({ ctx, wd, environment, tfPath, args ->
            new ProcessResult(1, "error")
        })
        def executor = new TerraformCommandExecutor(fakeRunner)

        when:
        executor.executeOutput(context, workDir, env, terraformPath, [:])
        then:
        def e = thrown(StepException)
        e.message.contains("Terraform apply failed for command")
    }

    void "executeApplyWithPlan throws exception on non-zero exit code"() {
        given:
        def fakeRunner = new FakeCommandRunner({ ctx, wd, environment, tfPath, args ->
            new ProcessResult(1, "apply error")
        })
        def executor = new TerraformCommandExecutor(fakeRunner)

        when:
        executor.executeApplyWithPlan(context, workDir, env, terraformPath, "key=value", "plan.tfvars", "")
        then:
        def e = thrown(StepException)
        e.message.contains("Terraform apply failed for command")
    }
}
