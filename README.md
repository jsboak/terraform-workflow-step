# Terraform Workflow Step Plugin

WorkflowStep Plugin for Rundeck to orchestrate Terraform operations

![Screenshot 2025-04-04 at 12 53 25 PM](https://github.com/user-attachments/assets/9c85dcce-33ea-408b-951c-e06cc81aa336)


## Install the Plugin

1. Download the latest package from the releases
2. Upload the plugin to your Rundeck instance using either the GUI, API, CLI, or by placing the jar file in the `libext` directory of your Rundeck installation.
    * `cp build/libs/terraform-step-plugin-1.0.0.jar $RDECK_BASE/libext/`
    * You will need to restart Rundeck if you use this method.

3. (Optional) Verify the plugin is installed by going to the `System` -> `Plugins` page in the GUI and looking for the plugin in the list of installed plugins.

## Use the Plugin

### Prerequisites
Terrraform must be installed on the Rundeck server and available to the `rundeck` user.  You can confirm this with `sudo -u rundeck which terraform`.

### Setup
1. Make a directory for testing: `mkdir -p /path/to/test/terraform`
2. `cd /path/to/test/terraform`
3. Make sure the working directory is owned by the `rundeck` user and group:
    * `sudo chown -R rundeck:rundeck /path/to/test/terraform`
    * `sudo chmod -R 755 /path/to/test/terraform`

### Simple Example
1. Create a file called `text.tf` with the following contents:
    ```hcl
    # test.tf
    terraform {
      required_version = ">= 0.12"
    }
    
    resource "local_file" "test" {
      content  = "Hello, Rundeck!"
      filename = "hello.txt"
    }
    ```
2. In Rundeck:
    *   Create a new Job
    *   Add a Workflow Step
    *   Select **Terraform** from the step plugins
    *   Configure the step:
    *   Terraform Command: init
    *   Working Directory: `/path/to/test/terraform`
    *   Add another step:
    *   Terraform Command: plan
    *   Working Directory: `/path/to/test/terraform`
    *   Add a final step:
    *   Terraform Command: apply
    *   Working Directory: `/path/to/test/terraform`

3. Run the Job and verify that the `hello.txt` file is created in the working directory with the contents "Hello, Rundeck!".
    *  Optionally run the Job in Debug mode to help with troubleshooting the configuration.

### Example with Inline Variables

1. Create a file called `text.tf` with the following contents:
    ```hcl
   # test.tf
   terraform {
   required_version = ">= 0.12"
   }
   
   variable "content" {
   type = string
   description = "Content to write to file"
   }
   
   variable "filename" {
   type = string
   description = "Name of file to create"
   }
   
   resource "local_file" "test" {
   content  = var.content
   filename = var.filename
   }
    ```

2. In Rundeck:
    *   Create a new Job
    *   Add a Workflow Step
    *   Select **Terraform** from the step plugins
    *   Configure the step:
      *   Terraform Command: **init**
        *   Working Directory: `/path/to/test/terraform`
    *   Add another **Terraform** step:
      *   Terraform Command: **plan**
      *   Working Directory: `/path/to/test/terraform`
      *  Add Variables:
      ```hcl
      content=Hello from Rundeck!
      filename=hello.txt
      ```
    *   Add a final **Terraform** step:
      *   Terraform Command: **apply**
      *   Working Directory: `/path/to/test/terraform`
      *   Add  Variables:
      ```hcl
      content=Hello from Rundeck!
      filename=hello.txt                            
      ```                          
3. Run the Job and verify that the `hello.txt` file is created in the working directory with the contents "Hello from Rundeck!".
