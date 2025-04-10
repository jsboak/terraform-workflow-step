package com.plugin.terraform

import com.dtolabs.rundeck.plugins.step.PluginStepContext

interface CommandRunner {
    ProcessResult run(PluginStepContext context, File workDir, Map<String, String> env, String terraformPath, List<String> args)
}
