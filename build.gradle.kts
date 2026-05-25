fun Project.scriptFile(name: String) = layout.projectDirectory.file("scripts/$name").asFile

fun Project.registerScriptTask(
    taskName: String,
    scriptName: String,
    taskDescription: String,
    configure: Exec.() -> Unit = {},
) {
  tasks.register<Exec>(taskName) {
    group = "local kind"
    description = taskDescription
    commandLine(scriptFile(scriptName).absolutePath)
    configure()
  }
}

registerScriptTask("kindCreate", "kind-create.sh", "Create the local kind cluster.")

registerScriptTask("kindDelete", "kind-delete.sh", "Delete the local kind cluster.")

registerScriptTask(
    "kindRecreate",
    "kind-recreate.sh",
    "Delete and recreate the local kind cluster, then show node labels.",
)

registerScriptTask("k8sRenderDev", "k8s-render-dev.sh", "Render the dev Kubernetes manifests.")

registerScriptTask("k8sApplyDev", "k8s-apply-dev.sh", "Apply the dev Kubernetes manifests.")

registerScriptTask(
    "k8sWaitDev",
    "k8s-wait-dev.sh",
    "Wait for the dev PostgreSQL StatefulSet and application Deployment.",
) {
  mustRunAfter("k8sApplyDev")
}

registerScriptTask("k8sStatusDev", "k8s-status-dev.sh", "Show dev pod placement and status.") {
  mustRunAfter("k8sApplyDev", "k8sWaitDev")
}

registerScriptTask(
    "k8sPortForward",
    "k8s-port-forward.sh",
    "Forward the local application service port.",
)

registerScriptTask("appHealth", "app-health.sh", "Check the local application health endpoints.")

registerScriptTask(
    "kindLoadImage",
    "kind-load-image.sh",
    "Build and load the local application image into kind.",
) {
  dependsOn(":service:bootJar")
  environment("SKIP_BOOT_JAR", "true")
}

registerScriptTask(
    "devDeploy",
    "dev-deploy.sh",
    "Build, load, apply, wait for, and show the dev deployment.",
) {
  dependsOn(":service:bootJar")
  environment("SKIP_BOOT_JAR", "true")
}
