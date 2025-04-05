// TerraformStepPlugin.groovy
package com.plugin.terraform

import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants
import com.dtolabs.rundeck.plugins.ServiceNameConstants
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.descriptions.RenderingOption
import com.dtolabs.rundeck.plugins.descriptions.SelectValues
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.plugins.step.StepPlugin

@Plugin(name = "terraform-step", service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = "Terraform", description = "Executes Terraform commands")
class TerraformStepPlugin implements StepPlugin {

    @PluginProperty(
            title = "Terraform Command",
            description = "The Terraform command to execute",
            required = true,
            defaultValue = "plan"
    )
    @SelectValues(values = ["init", "plan", "apply", "destroy", "output", "validate",
            "workspace", "state"], freeSelect = false)
    String terraformCommand

    @PluginProperty(
            title = "Working Directory",
            description = "Directory containing Terraform configuration files",
            required = true
    )
    String workingDirectory

    @PluginProperty(
            title = "Terraform Binary Path",
            description = "Path to terraform binary",
            defaultValue = "/usr/bin/terraform"
    )
    String terraformPath

    @PluginProperty(
            title = "Variables",
            description = "Terraform variables in format key=value (one per line)",
            required = false
    )
    @RenderingOption(key = StringRenderingConstants.DISPLAY_TYPE_KEY, value = "MULTI_LINE")
    String variables

    @PluginProperty(
            title = "Variable Files",
            description = "Paths to variable files (one per line)",
            required = false
    )
    String variableFiles

    @PluginProperty(
            title = "Use AWS Credentials",
            description = "Use AWS credentials from Project Plugin Group",
            defaultValue = "false"
    )
    Boolean useAws

    @PluginProperty(
            title = "Use Azure Credentials",
            description = "Use Azure credentials from Project Plugin Group",
            defaultValue = "false"
    )
    Boolean useAzure

    @PluginProperty(
            title = "Use GCP Credentials",
            description = "Use GCP credentials from Project Plugin Group",
            defaultValue = "false"
    )
    Boolean useGcp

    @PluginProperty(
            title = "Auto Init",
            description = "Automatically run terraform init before main command",
            defaultValue = "true"
    )
    boolean autoInit

    @PluginProperty(
            title = "Plan Only",
            description = "Run plan only (no apply)",
            defaultValue = "false"
    )
    boolean planOnly

    @PluginProperty(
            title = "Log Level",
            description = "Terraform log level",
            defaultValue = "INFO"
    )
    @SelectValues(values = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"], freeSelect = false)
    String logLevel

//    @PluginProperty(
//            title = "Clean Terraform Cache",
//            description = "Remove .terraform directory and state files before init. WARNING: This will remove existing state!",
//            defaultValue = "false"
//    )
//    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Advanced")
//    @RenderingOption(key = StringRenderingConstants.GROUPING, value = "Secondary")
//    Boolean cleanTerraformCache

    private final TerraformCommandExecutor commandExecutor
    private final CloudCredentialsManager credentialsManager
    private final EnvironmentBuilder environmentBuilder

    TerraformStepPlugin() {
        this.commandExecutor = new TerraformCommandExecutor()
        this.credentialsManager = new CloudCredentialsManager()
        this.environmentBuilder = new EnvironmentBuilder()
    }

    @Override
    void executeStep(PluginStepContext context, Map<String, Object> configuration) throws StepException {
        validateWorkingDirectory()

        def env = environmentBuilder.buildEnvironment(context, this)
        def workDir = new File(workingDirectory)

        try {
            if (autoInit && terraformCommand != "init") {
                commandExecutor.executeInit(context, workDir, env, terraformPath)
            }

            switch (terraformCommand.toLowerCase()) {
                case "init":
                    commandExecutor.executeInit(context, workDir, env, terraformPath)
                    break
                case "plan":
                    commandExecutor.executePlan(context, workDir, env, terraformPath, variables, variableFiles)
                    break
                case "apply":
                    if (planOnly) {
                        commandExecutor.executePlan(context, workDir, env, terraformPath, variables, variableFiles)
                    } else {
                        commandExecutor.executeApply(context, workDir, env, terraformPath, variables, variableFiles)
                    }
                    break
                case "destroy":
                    commandExecutor.executeDestroy(context, workDir, env, terraformPath, variables, variableFiles)
                    break
                case "output":
                    commandExecutor.executeOutput(context, workDir, env, terraformPath)
                    break
                case "validate":
                    commandExecutor.executeValidate(context, workDir, env, terraformPath)
                    break
                default:
                    throw new StepException("Unsupported Terraform command: ${terraformCommand}",
                            PluginFailureReason.TerraformError)
            }
        } catch (Exception e) {
            throw new StepException("Terraform execution failed: ${e.message}",
                    PluginFailureReason.TerraformError)
        }
    }

    private void validateWorkingDirectory() throws StepException {
        def workDir = new File(workingDirectory)
        if (!workDir.exists() || !workDir.isDirectory()) {
            throw new StepException("Working directory does not exist or is not a directory: ${workingDirectory}",
                    PluginFailureReason.TerraformError)
        }
    }
}