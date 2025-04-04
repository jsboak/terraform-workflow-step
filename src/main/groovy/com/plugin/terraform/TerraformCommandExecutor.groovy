// TerraformCommandExecutor.groovy
package com.plugin.terraform

import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import groovy.json.JsonSlurper

class TerraformCommandExecutor {

    static void executeInit(PluginStepContext context, File workDir, Map<String, String> env, String terraformPath) {
        runCommand(context, workDir, env, terraformPath, ["init"])
    }

    static void executePlan(PluginStepContext context, File workDir, Map<String, String> env,
                            String terraformPath, String variables, String variableFiles) {
        def args = ["plan", "-detailed-exitcode", "-out=tfplan"]
        addVariableArgs(args, variables, variableFiles)
        def result = runCommand(context, workDir, env, terraformPath, args)

        switch (result.exitValue) {
            case 0:
                context.logger.log(3,"No changes required")
                break
            case 2:
                context.logger.log(3,"Changes detected in plan")
                break
            default:
                throw new StepException("Terraform plan failed",
                        PluginFailureReason.TerraformError)
        }
    }

    static void executeApply(PluginStepContext context, File workDir, Map<String, String> env,
                             String terraformPath, String variables, String variableFiles) {
        def args = ["apply", "-auto-approve"]
        addVariableArgs(args, variables, variableFiles)
        runCommand(context, workDir, env, terraformPath, args)
    }

    static void executeDestroy(PluginStepContext context, File workDir, Map<String, String> env,
                               String terraformPath, String variables, String variableFiles) {
        def args = ["destroy", "-auto-approve"]
        addVariableArgs(args, variables, variableFiles)
        runCommand(context, workDir, env, terraformPath, args)
    }

    static void executeOutput(PluginStepContext context, File workDir, Map<String, String> env, String terraformPath) {
        def result = runCommand(context, workDir, env, terraformPath, ["output", "-json"])
        context.dataContext.put("terraform", parseOutputJson(result.output))
    }

    static void executeValidate(PluginStepContext context, File workDir, Map<String, String> env, String terraformPath) {
        runCommand(context, workDir, env, terraformPath, ["validate"])
    }

    private static void addVariableArgs(List<String> args, String variables, String variableFiles) {
        if (variables) {
            variables.readLines().each { line ->
                args.add("-var=${line}")
            }
        }
        if (variableFiles) {
            variableFiles.readLines().each { file ->
                args.add("-var-file=${file}")
            }
        }
    }

    private static Map<String, Object> parseOutputJson(String jsonOutput) {
        def slurper = new JsonSlurper()
        return slurper.parseText(jsonOutput) as Map<String, Object>
    }

    private static ProcessResult runCommand(PluginStepContext context, File workDir,
                                            Map<String, String> env, String terraformPath, List<String> args) {
        def cmdList = [terraformPath] + args
        def process = new ProcessBuilder(cmdList)
                .directory(workDir)
                .redirectErrorStream(true)

        process.environment().clear()
        process.environment().putAll(env)

        def proc = process.start()
        def output = new StringBuilder()
        def reader = new BufferedReader(new InputStreamReader(proc.inputStream))

        reader.eachLine { line ->
            output.append(line).append("\n")
            context.logger.log(3, line)
        }

        proc.waitFor()
        return new ProcessResult(proc.exitValue(), output.toString())
    }
}