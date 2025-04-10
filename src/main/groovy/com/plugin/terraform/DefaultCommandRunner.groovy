package com.plugin.terraform

import com.dtolabs.rundeck.plugins.step.PluginStepContext

class DefaultCommandRunner implements CommandRunner {
    @Override
    ProcessResult run(PluginStepContext context, File workDir, Map<String, String> env, String terraformPath, List<String> args) {
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
