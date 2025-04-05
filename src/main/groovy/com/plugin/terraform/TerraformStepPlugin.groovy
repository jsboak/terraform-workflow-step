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
            title = "Create Working Directory",
            description = "Optionally create the working directory if it doesn't exist. Use the inline Terraform HCL property to use this option.\n Selecting this will create the working directory if it doesn't exist and then use the inline HCL from `Terraform HCL` property. You can optionally pass in the HCL as a variable from a prior step, such as through `\${data.terraform_hcl}`.",
            defaultValue = "false"
    )
    Boolean createWorkingDirectory

    @PluginProperty(
            title = "Terraform Binary Path",
            description = "Path to terraform binary",
            defaultValue = "/usr/bin/terraform"
    )
    String terraformPath

    @PluginProperty(
            title = "Terraform HCL",
            description = "Terraform HCL to be used. This will be used if a new working directory is created, but not if a working directory already exists.\n You can optionally pass in the HCL as a variable from a prior step, such as through `\${data.terraform_hcl}`.",
            required = false
    )
    @RenderingOption(key = StringRenderingConstants.DISPLAY_TYPE_KEY, value = "MULTI_LINE")
    String terraformHCL

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
        validateWorkingDirectory()

        // First ensure working directory exists or create it
        File workDir = new File(workingDirectory)
        if (!workDir.exists()) {
            if (!workDir.mkdirs()) {
                throw new StepException("Failed to create working directory: ${workingDirectory}",
                        PluginFailureReason.IOFailure)
            }
        }

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

    private void validateWorkingDirectory() throws StepException {
        File workDir = new File(workingDirectory)

        if (!workDir.exists()) {
            if (createWorkingDirectory) {
                try {
                    workDir.mkdirs()
                } catch (Exception e) {
                    throw new StepException("Failed to create working directory: ${e.message}",
                            PluginFailureReason.IOFailure)
                }
            } else {
                throw new StepException("Working directory does not exist: ${workingDirectory}",
                        PluginFailureReason.IOFailure)
            }
        }

        if (!workDir.isDirectory()) {
            throw new StepException("Specified path is not a directory: ${workingDirectory}",
                    PluginFailureReason.IOFailure)
        }

        if (!workDir.canRead() || !workDir.canWrite()) {
            throw new StepException("Insufficient permissions on working directory: ${workingDirectory}",
                    PluginFailureReason.IOFailure)
        }
    }
}