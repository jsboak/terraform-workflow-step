// ProcessResult.groovy
package com.plugin.terraform

class ProcessResult {
    int exitValue
    String output

    ProcessResult(int exitValue, String output) {
        this.exitValue = exitValue
        this.output = output
    }
}