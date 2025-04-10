package com.plugin.terraform

import com.dtolabs.rundeck.core.common.IRundeckProject
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.plugins.step.PluginStepContext

class EnvironmentBuilder {

    private final CloudCredentialsManager credentialsManager

    EnvironmentBuilder() {
        this.credentialsManager = new CloudCredentialsManager()
    }

    Map<String, String> buildEnvironment(PluginStepContext context, TerraformStepPlugin plugin) {
        Map<String, String> env = [:] as Map<String, String>
        env.putAll(System.getenv())

        IRundeckProject project = context.getExecutionContext().getIFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject())

        // Configure cloud provider credentials
        if (plugin.useAws) {
            credentialsManager.configureAwsCredentials(project, context, env)
        }
        if (plugin.useAzure) {
            credentialsManager.configureAzureCredentials(project, context, env)
        }
        if (plugin.useGcp) {
            credentialsManager.configureGcpCredentials(project, context, env)
        }

        // Set log level
        env["TF_LOG"] = plugin.logLevel

        // Add terraform variables as environment variables
        if (plugin.variables) {
            plugin.variables.readLines().each { String line ->
                String[] parts = line.split('=', 2)
                if (parts.length == 2) {
                    String key = parts[0].trim()
                    String value = parts[1].trim()
                    // Check if the variable value is a secret reference
                    if (value?.startsWith("keys://") || value.contains("keys/")) {
                        try {
                            value = Util.getPasswordFromKeyStorage(value, context)
                        } catch (Exception e) {
                            throw new StepException("Failed to retrieve secret for variable '${key}': ${e.message}",
                                    PluginFailureReason.KeyStorageError)
                        }
                    }
                    env.put("TF_VAR_" + key, value)
                }
            }
        }
        return env
    }
}