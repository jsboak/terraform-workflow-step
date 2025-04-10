# Terraform Workflow Step Plugin

## Overview                                                                                                                                                                                                                                                                                                             
The Terraform Step Plugin for Rundeck enables you to run typical Terraform commands (e.g. `init`, `plan`, `apply`, `destroy`) as part of a Rundeck job workflow. It also provides mechanisms to inject variables (including secrets via Key Storage) and automatically sets up AWS, Azure, or GCP credentials if needed.
                                                                                                                                                                                                                                                                                                                       
You can use this plugin to orchestrate multi-step Infrastructure-as-Code deployments—such as running `terraform plan` to see changes, then `terraform apply` to execute them—directly within Rundeck.                                                                                                                  

---                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 
## Key Features                                                                                                                                                                                                                                                                                                        
1. **Select a Terraform command** such as `init`, `plan`, `apply`, `destroy`, `validate`, or `output` in a single step.                                                                                                                                                                                                
2. **Auto Init** option to automatically run `terraform init` prior to commands like `plan` or `apply`.                                                                                                                                                                                                                
3. **Inline Terraform configuration** (via the “Terraform HCL” field) if you need to generate files on the fly.                                                                                                                                                                                                        
4. **Variable injection** for standard Terraform variables and secrets (via Key Storage).                                                                                                                                                                                                                              
5. **Cloud provider credential support** (AWS, Azure, GCP) reads credentials from a project or framework-level plugin group.                                                                                                                                                                                           
6. **Optional temporary working directory** creation at runtime (and cleanup) for ephemeral usage.                                                                                                                                                                                                                     
7. **Logging level** control using Terraform’s `TF_LOG`.                                                                                                                                                                                                                                                               

![Screenshot 2025-04-04 at 12 53 25 PM](https://github.com/user-attachments/assets/9c85dcce-33ea-408b-951c-e06cc81aa336)                                        
                                                                                                                                                                 
                                                                                                                                                                 
## Using the Plugin 

### Prerequisites                                                                                                                                                                                                                                                                                                                                       
1. Download the latest package from the releases                                                                                                                
2. Upload the plugin to your Rundeck instance using either the GUI, API, CLI, or by placing the jar file in the `libext` directory of your Rundeck installation.
    * `cp build/libs/terraform-step-plugin-1.0.0.jar $RDECK_BASE/libext/`                                                                                        
    * You will need to restart Rundeck if you use this method.                                                                                                  
                                                                                                                                                                
3. (Optional) Verify the plugin is installed by going to the `System` -> `Plugins` page in the GUI and looking for the plugin in the list of installed plugins. 

4. Terraform must be installed on the Rundeck server and available to the rundeck user. You can confirm this with `sudo -u rundeck which terraform `
                                                                                                                                               
## Plugin Properties

### 1. Terraform Command
Select which Terraform subcommand to run: `init`, `plan`, `apply`, `destroy`, `output`, `validate`, etc.

- **Recommended best practice**: split “plan” and “apply” into two different steps in your job, so you can review the plan results before applying. Alternatively, enable “Plan Only” if you only need a plan from an `apply` command, but typically separate steps is cleaner.

### 2. Terraform Binary Path
Path to the Terraform CLI executable on the target system. Default is `/usr/bin/terraform`. Make sure the path is correct for your environment.

### 3. Working Directory
Path to the folder containing your `.tf` files, or where you want new `.tf` files generated.

- If you enable **“Create and use Temporary Working Directory”**, the plugin will automatically create a `/tmp/<executionId>/terraform` directory at runtime, then optionally remove it afterward if **“Delete Temporary Working Directory”** is checked.

### 4. Terraform HCL
Inline Terraform configuration. If you enable the temporary working directory, this is written to a file named `main.tf`. If the directory already exists and contains `.tf` files, this property can be left empty.

### 5. Variables
Terraform variable definitions in the format `KEY=VALUE` (one per line).  
- **Secrets from Key Storage**  
  - Reference them via `keys://path/to/secret` or `keys/path/to/secret`. The plugin automatically retrieves these and sets them as environment variables so they don’t appear in plaintext on the command line.  
  - By default, these environment variables are simply put into the environment as `<KEY>=<value>`. If you need Terraform to detect them automatically, rename them to follow the `TF_VAR_<key>` pattern in your environment, or rely on `-var` arguments.  

### 6. Variable Files
(Optional) One path per line, referencing `.tfvars` or other custom variable files. The plugin will pass each file in with `-var-file=<file>`.

### 7. Use AWS / Azure / GCP Credentials
Check these boxes if you need the plugin to pull cloud credentials from your Rundeck project’s Plugin Group settings.  
- The plugin automatically sets environment variables like `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `ARM_CLIENT_ID`, or `GOOGLE_APPLICATION_CREDENTIALS` based on your project configuration.  

### 8. Auto Init
If checked (default), the plugin runs `terraform init` automatically prior to running other commands (except if you explicitly selected `init` itself).

### 9. Plan Only
When the command is set to `apply` but you only want to generate a plan, you can toggle Plan Only. This performs a Terraform plan, saving the plan to a file. *However, most users separate “plan” and “apply” as different steps for clarity.*

### 10. Log Level
Sets the `TF_LOG` environment variable to control Terraform’s verbosity (`TRACE`, `DEBUG`, `INFO`, `WARN`, or `ERROR`). Higher verbosity is useful for troubleshooting but can clutter logs.

---

## Example Usage
      
> [!TIP]
> There is an example job in the `examples` directory of this repository. You can import it into Rundeck to see how the plugin works in practice.

### A. Two-Step “Plan → Apply”
1. **Step 1**: Add plugin step, set **Terraform Command** to `plan`.
   - Fill in `Working Directory`.
   - Optionally check “Auto Init.”
   - Provide any variables or variable files you need.
   - The plugin will run `terraform init` (if enabled) and then `terraform plan -out=tfplan`.
2. **Step 2**: Add another plugin step, set **Terraform Command** to `apply`.
   - Use the same `Working Directory`.
   - Check “Auto Init” if needed (though it’s typically unnecessary if Step 1 included `init`).
   - The plugin will run `terraform apply` automatically, applying either the existing plan file or by recalculating changes.

### B. Using Inline HCL
1. **Enable “Create and use Temporary Working Directory.”**
2. Enter your HCL in “Terraform HCL”:
   ```hcl
   resource "local_file" "example" {
     content  = "Hello, world!"
     filename = "/tmp/hello_world.txt"
   }
   ```
3. Run `apply` or any other command. The plugin will create a `main.tf` under `/tmp/<executionId>/terraform` at runtime.

### C. Referencing Secrets
In the “Variables” text box:
```
MY_TOKEN=keys://path/to/secret
ENV_PASSWORD=keys/my/other/secret
```
These are fetched from Rundeck’s Key Storage automatically and set as environment variables at runtime. If you prefer Terraform’s auto-detection of environment variables, rename them to `TF_VAR_ENV_PASSWORD=keys/my/other/secret` and so on.

---

## Tips and Best Practices
- **Separate Plan/Apply**: Keep “plan” and “apply” as two distinct steps whenever possible, so you can review or validate the plan output before applying.  
- **Store .tf Files in Version Control**: If you already have `.tf` files in Git or another repository, simply point the “Working Directory” to the local checkout. The “Terraform HCL” property is only necessary if you want to generate configuration dynamically.  
- **Check Output**: Using “terraform output” helps you pass data to subsequent job steps. You can parse JSON output from the plugin logs or store it in job data.  
- **Credential Scope**: If you enable AWS/Azure/GCP credentials, ensure you’ve set up the Project Plugin Group or Framework plugin configuration. If not, the step will fail.  
- **Logging and Debugging**: If you suspect issues, set **Log Level** to `DEBUG` or higher. You can also define additional environment variables like `TF_LOG_PATH` in your job configuration if you want Terraform logs written to a file.

---

## Conclusion
The Terraform Step Plugin seamlessly integrates Terraform commands into your Rundeck job workflows. By configuring the plugin with variables, credentials, and a working directory (whether temporary or permanent), you can manage infrastructure changes safely and in a repeatable manner.  

As you grow your usage, consider multi-step pipelines (plan → check → apply) and incorporate best practices like storing your `.tf` code in version control and referencing secrets from Key Storage.  

Enjoy automated infrastructure deployments with Terraform on Rundeck!