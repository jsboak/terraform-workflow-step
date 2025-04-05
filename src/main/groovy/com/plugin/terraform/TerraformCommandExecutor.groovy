// TerraformCommandExecutor.groovy
package com.plugin.terraform

import com.dtolabs.rundeck.core.execution.workflow.steps.StepException
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import groovy.json.JsonSlurper
import org.apache.commons.collections.map.LazyMap

class TerraformCommandExecutor {

    static void executeInit(PluginStepContext context, File workDir, Map<String, String> env, String terraformPath) {
        runCommand(context, workDir, env, terraformPath, ["init"])
    }

    static void executePlan(PluginStepContext context, File workDir, Map<String, String> env,
                            String terraformPath, String variables, String variableFiles) {
        List<String> args = ["plan", "-detailed-exitcode", "-out=tfplan"]
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
        List<String> args = ["apply", "-auto-approve"]
        addVariableArgs(args, variables, variableFiles)
        runCommand(context, workDir, env, terraformPath, args)
    }

    static void executeDestroy(PluginStepContext context, File workDir, Map<String, String> env,
                               String terraformPath, String variables, String variableFiles) {
        def args = ["destroy", "-auto-approve"]
        addVariableArgs(args, variables, variableFiles)
        runCommand(context, workDir, env, terraformPath, args)
    }

    static void executeOutput(PluginStepContext context, File workDir, Map<String, String> env,
                              String terraformPath, Map<String, Map<String, Object>> outputs) {
        List<String> args = ["output", "-json"]
        def result = runCommand(context, workDir, env, terraformPath, args)

        if (result.exitValue == 0) {
            Map<String, Object> outputMap = parseOutputJson(result.output)
            outputMap.each { key, value ->
                if (value instanceof Map && value.containsKey("value")) {
                    outputs.put(key, value as Map<String, Object>)
                }
            }
        } else {
            throw new StepException("Terraform output command failed",
                    PluginFailureReason.TerraformError)
        }
    }

    private static Map<String, Object> parseOutputJson(String jsonOutput) {
        JsonSlurper slurper = new JsonSlurper()
        return slurper.parseText(jsonOutput) as LazyMap
    }

    static void executeValidate(PluginStepContext context, File workDir, Map<String, String> env, String terraformPath) {
        runCommand(context, workDir, env, terraformPath, ["validate"])
    }

    private static void addVariableArgs(List<String> args, String variables, String variableFiles) {
        if (variables) {
            variables.readLines().each { String line ->
                if (line.trim()) {
                    args.add("-var")
                    // Don't add any extra quotes - just pass the key=value pair as is
                    args.add(line.trim())
                }
            }
        }
        if (variableFiles) {
            variableFiles.readLines().each { String file ->
                if (file.trim()) {
                    args.add("-var-file=" + file.trim())
                }
            }
        }
    }

    private static ProcessResult runCommand(PluginStepContext context, File workDir,
                                            Map<String, String> env, String terraformPath,
                                            List<String> args) {
        List<String> cmdArgs = new ArrayList<String>()
        cmdArgs.add(terraformPath.toString())
        cmdArgs.addAll(args)

        def process = new ProcessBuilder(cmdArgs)
                .directory(workDir)
                .redirectErrorStream(true)

        process.environment().clear()
        process.environment().putAll(env)

        def proc = process.start()
        def output = new StringBuilder()
        def reader = new BufferedReader(new InputStreamReader(proc.inputStream))

        reader.eachLine { line ->
            output.append(line).append("\n")
            context.logger.log(2, line)  // Simply log each line as-is
        }

        proc.waitFor()
        return new ProcessResult(proc.exitValue(), output.toString())
    }
}