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

fun Project.registerComposeTask(
    taskName: String,
    taskDescription: String,
    vararg composeArgs: String,
    configure: Exec.() -> Unit = {},
) {
  tasks.register<Exec>(taskName) {
    group = "local compose"
    description = taskDescription
    commandLine("docker", "compose", *composeArgs)
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
    "k8sApplyObservabilityDev",
    "k8s-apply-observability-dev.sh",
    "Apply the local kind Prometheus and Grafana manifests.",
)

registerScriptTask(
    "k8sWaitObservabilityDev",
    "k8s-wait-observability-dev.sh",
    "Wait for the local kind Prometheus and Grafana Deployments.",
) {
  mustRunAfter("k8sApplyObservabilityDev")
}

registerScriptTask(
    "k8sStatusObservabilityDev",
    "k8s-status-observability-dev.sh",
    "Show local kind observability pod, service, and warning event status.",
) {
  mustRunAfter("k8sApplyObservabilityDev", "k8sWaitObservabilityDev")
}

registerScriptTask(
    "k8sPortForward",
    "k8s-port-forward.sh",
    "Forward the local application service port.",
)

registerScriptTask(
    "k8sPortForwardPrometheus",
    "k8s-port-forward-prometheus.sh",
    "Forward the local Prometheus service port.",
)

registerScriptTask(
    "k8sPortForwardGrafana",
    "k8s-port-forward-grafana.sh",
    "Forward the local Grafana service port.",
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

registerComposeTask("composeConfig", "Validate and print the Docker Compose configuration.", "config")

registerComposeTask("composeUp", "Build and start the local Docker Compose stack.", "up", "--build", "-d") {
  dependsOn(":service:bootJar")
}

registerComposeTask("composeDown", "Stop and remove the local Docker Compose stack.", "down", "--remove-orphans")

registerComposeTask("composeLogs", "Follow logs for the Docker Compose application service.", "logs", "-f", "app")

registerComposeTask("composePs", "Show local Docker Compose service status.", "ps")
