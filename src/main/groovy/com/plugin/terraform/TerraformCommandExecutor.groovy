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
                            String terraformPath, String variables, String variableFiles, String additionalParameters) {
        List<String> args = ["plan", "-detailed-exitcode", "-out=tfplan"]
        addVariableArgs(args, variables, variableFiles)
        args.addAll(parseAdditionalParameters(additionalParameters))

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

    static void executeWorkspace(PluginStepContext context, File workDir, Map<String, String> env,
                                 String terraformPath, String workspaceSubcommand, String additionalParameters) {
        List<String> args = ["workspace"]
        // Append the subcommand, e.g., "new", "select", or "list"
        if(workspaceSubcommand?.trim()){
            args.add(workspaceSubcommand.trim())
        } else {
            throw new StepException("No workspace subcommand provided", PluginFailureReason.TerraformError)
        }
        args.addAll(parseAdditionalParameters(additionalParameters))

        def result = runCommand(context, workDir, env, terraformPath, args)
        if (result.exitValue != 0) {
            throw new StepException("Terraform apply failed for command [${terraformPath} ${args.join(' ')}] with exit code ${result.exitValue}. Output: ${result.output}", PluginFailureReason.TerraformError)
        }

    }

    static void executeState(PluginStepContext context, File workDir, Map<String, String> env,
                             String terraformPath, String stateSubcommand, String additionalParameters) {
        List<String> args = ["state"]
        // Append the subcommand, e.g., "list", "show", or others.
        if(stateSubcommand?.trim()){
            args.add(stateSubcommand.trim())
        } else {
            throw new StepException("No state subcommand provided", PluginFailureReason.TerraformError)
        }
        args.addAll(parseAdditionalParameters(additionalParameters))

        def result = runCommand(context, workDir, env, terraformPath, args)
        if (result.exitValue != 0) {
            throw new StepException("Terraform apply failed for command [${terraformPath} ${args.join(' ')}] with exit code ${result.exitValue}. Output: ${result.output}", PluginFailureReason.TerraformError)
        }

    }


    static void executeApply(PluginStepContext context, File workDir, Map<String, String> env,
                             String terraformPath, String variables, String variableFiles, String additionalParameters) {
        List<String> args = ["apply", "-auto-approve"]
        addVariableArgs(args, variables, variableFiles)
        args.addAll(parseAdditionalParameters(additionalParameters))

        def result = runCommand(context, workDir, env, terraformPath, args)
        if (result.exitValue != 0) {
            throw new StepException("Terraform apply failed for command [${terraformPath} ${args.join(' ')}] with exit code ${result.exitValue}. Output: ${result.output}", PluginFailureReason.TerraformError)
        }

    }

    static void executeDestroy(PluginStepContext context, File workDir, Map<String, String> env,
                               String terraformPath, String variables, String variableFiles, String additionalParameters) {
        def args = ["destroy", "-auto-approve"]
        addVariableArgs(args, variables, variableFiles)
        args.addAll(parseAdditionalParameters(additionalParameters))

        def result = runCommand(context, workDir, env, terraformPath, args)
        if (result.exitValue != 0) {
            throw new StepException("Terraform apply failed for command [${terraformPath} ${args.join(' ')}] with exit code ${result.exitValue}. Output: ${result.output}", PluginFailureReason.TerraformError)
        }

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
                throw new StepException("Terraform apply failed for command [${terraformPath} ${args.join(' ')}] with exit code ${result.exitValue}. Output: ${result.output}", PluginFailureReason.TerraformError)
        }
    }

    private static Map<String, Object> parseOutputJson(String jsonOutput) {
        JsonSlurper slurper = new JsonSlurper()
        return slurper.parseText(jsonOutput) as LazyMap
    }

    static void executeValidate(PluginStepContext context, File workDir, Map<String, String> env, String terraformPath) {
        runCommand(context, workDir, env, terraformPath, ["validate"])
    }

    static void executeApplyWithPlan(PluginStepContext context, File workDir, Map<String, String> env,
                                     String terraformPath, String variables, String variableFiles, String additionalParameters) {
        // Construct command: terraform apply -auto-approve tfplan
        List<String> args = ["apply", "-auto-approve", "tfplan"]
        // Optionally add any variable arguments if needed
        addVariableArgs(args, variables, variableFiles)
        args.addAll(parseAdditionalParameters(additionalParameters))

        def result = runCommand(context, workDir, env, terraformPath, args)
        if (result.exitValue != 0) {
            throw new StepException("Terraform apply failed for command [${terraformPath} ${args.join(' ')}] with exit code ${result.exitValue}. Output: ${result.output}", PluginFailureReason.TerraformError)
        }

    }

    private static List<String> parseAdditionalParameters(String additionalParameters) {
        return additionalParameters?.trim() ? additionalParameters.trim().split(/\s+/) as List : []
    }


    private static void addVariableArgs(List<String> args, String variables, String variableFiles) {
        if (variables) {
            variables?.readLines()?.each { String line ->
                def parts = line.split('=', 2)*.trim()
                if (parts.size() == 2 && parts[1]) {
                    String key = parts[0].trim()
                    String value = parts[1].trim()
                    // If the value is a secret reference, skip adding it to the command-line.
                    if (!(value?.startsWith("keys://") || value.contains("keys/"))) {
                        args.add("-var")
                        args.add("${key}=${value}")
                    }
                    // Otherwise, do nothing since the secret value is already provided as an environment variable.
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
        List<String> cmdArgs = [terraformPath] + args

        ProcessBuilder process = new ProcessBuilder(cmdArgs)
                .directory(workDir)
                .redirectErrorStream(true)
        process.environment().clear()
        process.environment().putAll(env)

        Process proc = process.start()
        StringBuilder output = new StringBuilder()
        proc.inputStream.withReader { reader ->
            reader.eachLine { line ->
                output.append(line).append("\n")
                context.logger.log(2, line)
            }
        }
        proc.waitFor()
        return new ProcessResult(proc.exitValue(), output.toString())
    }
}