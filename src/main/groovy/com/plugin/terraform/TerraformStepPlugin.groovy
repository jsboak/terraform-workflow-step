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
            title = "Terraform Binary Path",
            description = "Path to terraform binary",
            defaultValue = "/usr/bin/terraform"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value= "Base Configuration")
    String terraformPath

    @PluginProperty(
            title = "Working Directory",
            description = "Directory containing Terraform configuration files.",
            required = true
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value= "Base Configuration")
    String workingDirectory

    @PluginProperty(
            title = "Use Temporary Working Directory",
            description = "Optionally create the working directory if it doesn't exist.\nSelecting this will create a working directory at `/tmp/\${job.execid}` and then use the inline HCL from the **Terraform HCL** property below. You can optionally pass in the HCL as a variable from a prior step, such as through `\${data.terraform_hcl}` if your HCL is retrieved via Git.",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Base Configuration")
    @RenderingOption(key = StringRenderingConstants.GROUPING, value = "secondary")
    Boolean tempWorkingDirectory

    @PluginProperty(
            title = "Delete Temporary Working Directory",
            description = "Delete the temporary working directory after step execution.",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Base Configuration")
    @RenderingOption(key = StringRenderingConstants.GROUPING, value = "secondary")
    Boolean deleteTempWorkingDirectory

    @PluginProperty(
            title = "Use Existing Plan File",
            description = "If checked, uses an existing plan file (tfplan) from a prior step instead of generating a new plan.",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Base Configuration")
    Boolean useExistingPlanFile

    @PluginProperty(
            title = "Log Level",
            description = "Terraform log level",
            defaultValue = "INFO"
    )
    @SelectValues(values = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"], freeSelect = false)
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Base Configuration")
    String logLevel

    @PluginProperty(
            title = "Terraform Command",
            description = "The Terraform command to execute",
            required = true,
            defaultValue = "plan"
    )
    @SelectValues(values = ["init", "plan", "apply", "destroy", "output", "validate",
            "workspace", "state"], freeSelect = false)
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Command Options")
    @RenderingOption(key = StringRenderingConstants.GROUPING, value = "secondary")
    String terraformCommand

    @PluginProperty(
            title =  "Workspace Subcommand",
            description = "Terraform workspace subcommand to execute.\n This is only used if the Terraform command is set to `workspace`."
    )
    @SelectValues(values = ["list", "select", "new", "delete", "show"], freeSelect = false)
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Command Options")
    String workspaceSubcommand

    @PluginProperty(
            title = "State Subcommand",
            description = "Terraform state subcommand to execute.\n This is only used if the Terraform command is set to `state`."
    )
    @SelectValues(values = ["list", "mv", "rm", "show", "pull", "push", "replace-provider",
            "get", "import"], freeSelect = false)
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Command Options")
    String stateSubcommand

    @PluginProperty(
            title = "Additional Parameters",
            description = "Extra command-line arguments to append to the Terraform command. Use this to supply any additional flags or options.",
            required = false,
            defaultValue = ""
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Command Options")
    String additionalParameters

    @PluginProperty(
            title = "Auto Init",
            description = "Automatically run terraform init before main command. Note that this is not necessary if your Job includes a prior step that runs Terraform init.",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Command Options")
    boolean autoInit

    @PluginProperty(
            title = "Plan Only",
            description = "Run plan only (no apply)",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Command Options")
    boolean planOnly

    //TODO: allow for defining HCL inline even if working directory is specified
    @PluginProperty(
            title = "Terraform HCL",
            description = "Terraform HCL to be used. This will be used if a new working directory is created, but not if a working directory already exists.\n You can optionally pass in the HCL as a variable from a prior step, such as through `\${data.terraform_hcl}`.",
            required = false
    )
    @RenderingOption(key = StringRenderingConstants.DISPLAY_TYPE_KEY, value = "CODE")
    @RenderingOption(key = StringRenderingConstants.CODE_SYNTAX_MODE, value = "yaml")
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "HCL and Variables")
    @RenderingOption(key = StringRenderingConstants.GROUPING, value = "secondary")
    String terraformHCL

    @PluginProperty(
            title = "Variables",
            description = "Terraform variables in format key=value (one per line).\nSecrets can be retrieved from Key Storage by using the format `keys://<path>` or `keys/<path>` - for example `PAGERDUTY_TOKEN=keys/path/to/api_token`. You can optionally pass in the variables as a variable from a prior step, such as through `\${data.terraform_vars}`.",
            required = false,
            defaultValue = "PAGERDUTY_TOKEN=keys/path/to/api_token"
    )
    @RenderingOption(key = StringRenderingConstants.DISPLAY_TYPE_KEY, value = "MULTI_LINE")
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "HCL and Variables")
    String variables

    @PluginProperty(
            title = "Variable Files",
            description = "Paths to variable files (one per line)",
            required = false
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "HCL and Variables")
    String variableFiles

    @PluginProperty(
            title = "Use AWS Credentials",
            description = "Use AWS credentials from Project Plugin Group",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Cloud Credentials")
    @RenderingOption(key = StringRenderingConstants.GROUPING, value = "secondary")
    Boolean useAws

    @PluginProperty(
            title = "Use Azure Credentials",
            description = "Use Azure credentials from Project Plugin Group",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Cloud Credentials")
    Boolean useAzure

    @PluginProperty(
            title = "Use GCP Credentials",
            description = "Use GCP credentials from Project Plugin Group",
            defaultValue = "false"
    )
    @RenderingOption(key = StringRenderingConstants.GROUP_NAME, value = "Cloud Credentials")
    Boolean useGcp

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
        File workDir = null

        try {

            workDir = validateWorkingDirectory(context)

            // If terraformHCL is provided, create or update main.tf
            if (terraformHCL?.trim()) {
                try {
                    File tfFile = new File(workDir, "main.tf")
                    tfFile.text = terraformHCL.trim()
                    context.logger.log(3, "Created Terraform configuration file at: ${tfFile.absolutePath}")
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
                        commandExecutor.executePlan(context, workDir, env, terraformPath, variables, variableFiles, additionalParameters)
                        break
                    case "apply":
                        if (planOnly) {
                            commandExecutor.executePlan(context, workDir, env, terraformPath, variables, variableFiles, additionalParameters)
                        } else if (useExistingPlanFile) {
                            File planFile = new File(workDir, "tfplan")
                            if (!planFile.exists()) {
                                throw new StepException("Existing plan file tfplan not found in ${workDir.absolutePath}",
                                        PluginFailureReason.IOFailure)
                            }
                            commandExecutor.executeApplyWithPlan(context, workDir, env, terraformPath, variables, variableFiles, additionalParameters)
                        } else {
                            commandExecutor.executeApply(context, workDir, env, terraformPath, variables, variableFiles, additionalParameters)
                        }
                        break

                    case "destroy":
                        commandExecutor.executeDestroy(context, workDir, env, terraformPath, variables, variableFiles, additionalParameters)
                        break
                    case "output":
                        // Instantiate an output map to capture Terraform output values.
                        Map<String, Map<String, Object>> outputs = [:]
                        // Execute the Terraform output command, parsing JSON into the outputs map.
                        commandExecutor.executeOutput(context, workDir, env, terraformPath, outputs)

                        context.logger.log(3, "Terraform outputs: " + outputs.toString())
                        break

                    case "validate":
                        commandExecutor.executeValidate(context, workDir, env, terraformPath)
                        break
                    case "workspace":
                        // Example: pass along the selected subcommand (e.g., "new", "select") and any additional arguments.
                        commandExecutor.executeWorkspace(context, workDir, env, terraformPath, workspaceSubcommand, additionalParameters)
                        break
                    case "state":
                        // Similarly, call a method to handle state subcommands.
                        commandExecutor.executeState(context, workDir, env, terraformPath, stateSubcommand, additionalParameters)
                        break
                    default:
                        throw new StepException("Unsupported Terraform command: ${terraformCommand}",
                                PluginFailureReason.TerraformError)
                }
            } catch (Exception e) {
                throw new StepException("Terraform execution failed: ${e.message}",
                        PluginFailureReason.TerraformError)
            }
        } catch(Exception e) {
            throw new StepException("Terraform execution failed: ${e.message}",
                    PluginFailureReason.TerraformError)
        } finally {
            // Clean up temporary working directory if applicable.
            if (tempWorkingDirectory && deleteTempWorkingDirectory && workDir != null) {
                try {
                    workDir.deleteDir()  // Recursively delete the temporary working directory
                    context.logger.log(3, "Temporary working directory deleted: ${workDir.absolutePath}")
                } catch(Exception cleanupEx) {
                    context.logger.log(3, "Failed to delete temporary working directory: ${cleanupEx.message}")
                }
            }
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