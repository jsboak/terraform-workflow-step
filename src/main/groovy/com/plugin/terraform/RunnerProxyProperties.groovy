package com.plugin.terraform

import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.core.execution.proxy.ProxyRunnerPlugin
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder

abstract class RunnerProxyProperties implements NodeStepPlugin, ProxyRunnerPlugin, DescriptionBuilder.Collaborator{

    @Override
    void buildWith(DescriptionBuilder builder) {
        builder.build()
    }

    @Override
    public Map<String, String> getRuntimeProperties(ExecutionContext context) {

        return context.getIFramework().getFrameworkProjectMgr().loadProjectConfig(context.getFrameworkProject()).getProjectProperties();
    }
    @Override
    public Map<String, String> getRuntimeFrameworkProperties(ExecutionContext context) {

        return context.getIFramework().getPropertyLookup().getPropertiesMap();
    }

    //This is so that the Runner can use the necessary secrets from the project/framework config
    //Overriding from rundeck/core/src/main/java/com/dtolabs/rundeck/core/execution/proxy/ProxyRunnerPlugin.java
    @Override
    public List<String> listSecretsPathWorkflowStep(ExecutionContext context, Map<String, Object> configuration) {

        List<String> list = new ArrayList<String>()

        String awsSecretProject = context.getIFramework().frameworkProjectMgr.getFrameworkProject(context.getFrameworkProject()).getProperty("project.plugin.PluginGroup.AWS.secretKey")
        String azureSecretProject = context.getIFramework().frameworkProjectMgr.getFrameworkProject(context.getFrameworkProject()).getProperty("project.plugin.PluginGroup.Azure.apiKey")
        String gcpSecretProject = context.getIFramework().frameworkProjectMgr.getFrameworkProject(context.getFrameworkProject()).getProperty("project.plugin.PluginGroup.GCP.secretKey")
        String awsSecret = context.getIFramework().getPropertyLookup().getProperty("framework.plugin.PluginGroup.AWS.secretKey")
        String azureSecret = context.getIFramework().getPropertyLookup().getProperty("framework.plugin.PluginGroup.Azure.apiKey")
        String gcpSecret = context.getIFramework().getPropertyLookup().getProperty("framework.plugin.PluginGroup.GCP.secretKey")

        String azureClientIdProject = context.getIFramework().frameworkProjectMgr.getFrameworkProject(context.getFrameworkProject()).getProperty("project.plugin.PluginGroup.Azure.clientId")
        String awsAccessKeyProject = context.getIFramework().frameworkProjectMgr.getFrameworkProject(context.getFrameworkProject()).getProperty("project.plugin.PluginGroup.AWS.accessKey")
        String azureClientId = context.getIFramework().getPropertyLookup().getProperty("framework.plugin.PluginGroup.Azure.clientId")
        String awsAccessKey = context.getIFramework().getPropertyLookup().getProperty("framework.plugin.PluginGroup.AWS.accessKey")

        if(gcpSecret) {
            list.add(gcpSecret)
        }
        if(gcpSecretProject) {
            list.add(gcpSecretProject)
        }
        if(secretPath) {
            list.add(secretPath)
        }
        if(awsSecretProject) {
            list.add(awsSecretProject)
        }
        if(awsSecret) {
            list.add(awsSecret)
        }
        if(azureSecret) {
            list.add(azureSecret)
        }
        if(azureSecretProject) {
            list.add(azureSecretProject)
        }
        if(azureClientIdProject) {
            list.add(azureClientIdProject)
        }
        if(awsAccessKeyProject) {
            list.add(awsAccessKeyProject)
        }
        if(azureClientId) {
            list.add(azureClientId)
        }
        if(awsAccessKey) {
            list.add(awsAccessKey)
        }
        return list
    }
}
