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
            title = "Terraform Binary Path",
            description = "Path to terraform binary",
            defaultValue = "/usr/bin/terraform"
    )
    String terraformPath

    @PluginProperty(
            title = "Working Directory",
            description = "Directory containing Terraform configuration files.",
            required = true
    )
    String workingDirectory

    @PluginProperty(
            title = "Create and use Temporary Working Directory",
            description = "Optionally create the working directory if it doesn't exist.\nSelecting this will create a working directory at `/tmp/\${job.execid}` and then use the inline HCL from the **Terraform HCL** property below. You can optionally pass in the HCL as a variable from a prior step, such as through `\${data.terraform_hcl}` if your HCL is retrieved via Git.",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Working Directory Options")
    @RenderingOption(key = StringRenderingConstants.GROUPING, value = "secondary")
    Boolean tempWorkingDirectory

    @PluginProperty(
            title = "Delete Temporary Working Directory",
            description = "Delete the temporary working directory after step execution.",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Working Directory Options")
    @RenderingOption(key = StringRenderingConstants.GROUPING, value = "secondary")
    Boolean deleteTempWorkingDirectory

    @PluginProperty(
            title = "Terraform HCL",
            description = "Terraform HCL to be used. This will be used if a new working directory is created, but not if a working directory already exists.\n You can optionally pass in the HCL as a variable from a prior step, such as through `\${data.terraform_hcl}`.",
            required = false
    )
    @RenderingOption(key = StringRenderingConstants.DISPLAY_TYPE_KEY, value = "CODE")
    @RenderingOption(key = StringRenderingConstants.CODE_SYNTAX_MODE, value = "yaml")
    String terraformHCL

    @PluginProperty(
            title = "Variables",
            description = "Terraform variables in format key=value (one per line).\nSecrets can be retrieved from Key Storage by using the format `keys://<path>` or `keys/<path>` - for example `PAGERDUTY_TOKEN=keys/path/to/api_token`. You can optionally pass in the variables as a variable from a prior step, such as through `\${data.terraform_vars}`.",
            required = false,
            defaultValue = "PAGERDUTY_TOKEN=keys/path/to/api_token"
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
            description = "Automatically run terraform init before main command. Note that this is not necessary if your Job includes a prior step that runs Terraform init.",
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
        File workDir = validateWorkingDirectory(context)

        // If terraformHCL is provided, create or update main.tf
        if (terraformHCL?.trim()) {
            try {
                File tfFile = new File(workDir, "main.tf")
                tfFile.text = terraformHCL.trim()
                context.logger.log(3,"Created Terraform configuration file at: ${tfFile.absolutePath}")
            } catch (Exception e) {
                throw new StepException("Failed to write Terraform configuration: ${e.message}",
                        PluginFailureReason.IOFailure)
            }
        } else {
            // Verify existing Terraform files if no HCL provided
            if (!new File(workDir, "main.tf").exists() && !new File(workDir, "*.tf").exists()) {
                throw new StepException("No Terraform configuration found in ${workingDirectory} and no configuration provided",
                        PluginFailureReason.IOFailure)
            }
        }

        def env = environmentBuilder.buildEnvironment(context, this)

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
                    //TODO: Implement output handling
                    commandExecutor.executeOutput(context, workDir, env, terraformPath, variables)
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

    private File validateWorkingDirectory(PluginStepContext context) throws StepException {
        File workDir = new File(workingDirectory)

        if (tempWorkingDirectory) {
            try {
                //Make temporary working directory at /tmp/${job.execid}
                workDir = new File("/tmp/${context.executionContext.execution.id}/terraform")
                workDir.mkdirs()
                context.logger.log(3,"Created temporary working directory at: ${workDir.absolutePath}")
            } catch (Exception e) {
                throw new StepException("Failed to create working directory: ${e.message}",
                        PluginFailureReason.IOFailure)
            }

            return workDir
        }

         else if (!workDir.isDirectory()) {
            throw new StepException("Specified path is not a directory: ${workingDirectory}",
                    PluginFailureReason.IOFailure)
        }

        else if (!workDir.canRead() || !workDir.canWrite()) {
            throw new StepException("Insufficient permissions on working directory: ${workingDirectory}",
                    PluginFailureReason.IOFailure)
        }

        context.logger.log(3,"Using working directory: ${workDir.absolutePath}")
        return workDir
    }
}