package com.plugin.terraform

import com.dtolabs.rundeck.core.common.IRundeckProject
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.plugins.step.PluginStepContext

class CloudCredentialsManager {

    static void configureAwsCredentials(IRundeckProject project, PluginStepContext context, Map<String, String> env) {
        String accessKeyId = project ("project.plugin.PluginGroup.AWS.accessKeyId") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.AWS.accessKeyId")
        String secretKeyPath = project.getProperty("project.plugin.PluginGroup.AWS.secretKeyPath") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.AWS.secretKeyPath")
        String region = project.getProperty("project.plugin.PluginGroup.AWS.region") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.AWS.region")

        if (!accessKeyId || !secretKeyPath) {
            throw new StepException("AWS credentials not configured in Project Plugin Group or Framework",
                    PluginFailureReason.KeyStorageError)
        }

        env["AWS_ACCESS_KEY_ID"] = accessKeyId
        try {
            env.put("AWS_SECRET_ACCESS_KEY", Util.getPasswordFromKeyStorage(secretKeyPath, context))
            if (region) {
                env.put("AWS_DEFAULT_REGION", region)
            }
        } catch (Exception e) {
            throw new StepException("Failed to retrieve AWS credentials from key storage: ${e.message}",
                    PluginFailureReason.KeyStorageError)
        }

        if (region) {
            env["AWS_DEFAULT_REGION"] = region
        }
    }

    static void configureAzureCredentials(IRundeckProject project, PluginStepContext context, Map<String, String> env) {
        String clientId = project.getProperty("project.plugin.PluginGroup.Azure.clientId") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.Azure.clientId")
        String clientSecretPath = project.getProperty("project.plugin.PluginGroup.Azure.apiKey") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.Azure.apiKey")
        String tenantId = project.getProperty("project.plugin.PluginGroup.Azure.tenantId") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.Azure.tenantId")
        String subscriptionId = project.getProperty("project.plugin.PluginGroup.Azure.subscriptionId") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.Azure.subscriptionId")

        if (!clientId || !clientSecretPath || !tenantId || !subscriptionId) {
            throw new StepException("Azure credentials not configured in Project Plugin Group or Framework",
                    PluginFailureReason.KeyStorageError)
        }

        env["ARM_CLIENT_ID"] = clientId
        try {
            env.put("ARM_CLIENT_SECRET", Util.getPasswordFromKeyStorage(clientSecretPath, context))
        } catch (Exception e) {
            throw new StepException("Failed to retrieve Azure credentials from key storage: ${e.message}",
                    PluginFailureReason.KeyStorageError)
        }
        env["ARM_TENANT_ID"] = tenantId
        env["ARM_SUBSCRIPTION_ID"] = subscriptionId
    }

    static void configureGcpCredentials(IRundeckProject project, PluginStepContext context, Map<String, String> env) {
        String credentialsPath = project.getProperty("project.plugin.PluginGroup.GCP.credentialsPath") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.GCP.credentialsPath")
        String projectId = project.getProperty("project.plugin.PluginGroup.GCP.projectId") ?:
                context.getFramework().getProperty("framework.plugin.PluginGroup.GCP.projectId")

        if (!credentialsPath) {
            throw new StepException("GCP credentials not configured in Project Plugin Group or Framework",
                    PluginFailureReason.KeyStorageError)
        }

        try {
            env.put("GOOGLE_APPLICATION_CREDENTIALS", Util.getPasswordFromKeyStorage(credentialsPath, context))
        } catch (Exception e) {
            throw new StepException("Failed to retrieve GCP credentials from key storage: ${e.message}",
                    PluginFailureReason.KeyStorageError)
        }
        if (projectId) {
            env["GOOGLE_PROJECT"] = projectId
        }
    }
}