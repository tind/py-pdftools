plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    compileOnly("org.graalvm.sdk:nativeimage:25.0.2")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyLocking {
    lockAllConfigurations()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    providers.gradleProperty("nativeAgentOutput").orNull?.let { outputDirectory ->
        jvmArgs(
            "-agentlib:native-image-agent=" +
                "config-output-dir=${file(outputDirectory).absolutePath}",
        )
    }
}

val graalVmHome = providers.gradleProperty("graalVmHome")
    .orElse(providers.environmentVariable("GRAALVM_HOME"))
val nativeImageCommand = graalVmHome
    .map { file("$it/bin/native-image").absolutePath }
    .orElse("native-image")
val nativeOutputDirectory = layout.buildDirectory.dir("native")
val isMacOs = System.getProperty("os.name").startsWith("Mac")
val defaultMacOsDeploymentTarget = if (System.getProperty("os.arch") == "aarch64") {
    "11.0"
} else {
    "10.15"
}
val macOsDeploymentTarget = providers.gradleProperty("macOsDeploymentTarget")
    .orElse(providers.environmentVariable("MACOSX_DEPLOYMENT_TARGET"))
    .orElse(defaultMacOsDeploymentTarget)

tasks.register<Exec>("nativeCompile") {
    group = "build"
    description = "Build the GraalVM shared library"
    dependsOn(tasks.jar)

    val runtimeClasspath = configurations.runtimeClasspath
    inputs.files(tasks.jar, runtimeClasspath)
    if (isMacOs) {
        inputs.property("macOsDeploymentTarget", macOsDeploymentTarget)
    }
    outputs.dir(nativeOutputDirectory)

    doFirst {
        val outputDirectory = nativeOutputDirectory.get().asFile
        outputDirectory.mkdirs()
        workingDir(outputDirectory)
        if (isMacOs) {
            environment("MACOSX_DEPLOYMENT_TARGET", macOsDeploymentTarget.get())
        }
        val arguments = mutableListOf(
            nativeImageCommand.get(),
            "--shared",
            "-O1",
            "-Djava.awt.headless=true",
            "-H:+AddAllCharsets",
        )
        if (isMacOs) {
            arguments.add(
                "-H:NativeLinkerOption=-mmacosx-version-min=" +
                    macOsDeploymentTarget.get(),
            )
        }
        arguments.addAll(listOf(
            "-o",
            "libpy_pdftools",
            "-cp",
            (runtimeClasspath.get() + files(tasks.jar)).asPath,
        ))
        commandLine(arguments)
    }
}

val nativeSmokeOutputDirectory = layout.buildDirectory.dir("native-smoke")
val nativeSmokeExecutable = nativeSmokeOutputDirectory.map { it.file("pdftools-smoke") }
val nativeSmokeSource = rootProject.layout.projectDirectory.file(
    "native/smoke/inspect_smoke.c",
)

tasks.register<Exec>("nativeSmokeCompile") {
    group = "verification"
    description = "Compile the C smoke test against the native ABI"
    dependsOn("nativeCompile")
    inputs.file(nativeSmokeSource)
    inputs.file(rootProject.layout.projectDirectory.file("native/include/pdftools.h"))
    inputs.dir(nativeOutputDirectory)
    outputs.file(nativeSmokeExecutable)

    doFirst {
        val outputDirectory = nativeOutputDirectory.get().asFile
        nativeSmokeOutputDirectory.get().asFile.mkdirs()
        commandLine(
            "cc",
            "-std=c11",
            "-Wall",
            "-Wextra",
            "-Werror",
            "-I${rootProject.file("native/include").absolutePath}",
            "-I${outputDirectory.absolutePath}",
            nativeSmokeSource.asFile.absolutePath,
            "-L${outputDirectory.absolutePath}",
            "-lpy_pdftools",
            "-Wl,-rpath,${outputDirectory.absolutePath}",
            "-o",
            nativeSmokeExecutable.get().asFile.absolutePath,
        )
    }
}

tasks.register<Exec>("nativeSmoke") {
    group = "verification"
    description = "Run the C smoke test against the native shared library"
    dependsOn("nativeSmokeCompile")

    doFirst {
        commandLine(nativeSmokeExecutable.get().asFile.absolutePath)
    }
}
