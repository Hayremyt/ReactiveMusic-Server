@file:Suppress("UnstableApiUsage")

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.github.johnrengelman.shadow")
}

val loader = prop("loom.platform")!!
val minecraft: String = stonecutter.current.version
val common: Project = requireNotNull(stonecutter.node.sibling("")?.project) {
    "No common project for $project"
}

val modVersion: String = common.project.findProperty("mod.version") as String
val modGroup: String = common.project.findProperty("mod.group") as String
val modId: String = common.project.findProperty("mod.id") as String
val modName: String = common.project.findProperty("mod.name") as String
val yarnBuild: String = common.project.findProperty("deps.yarn_build") as String
val fabricLoader: String = common.project.findProperty("deps.fabric_loader") as String
val fabricApi: String = common.project.findProperty("deps.fabric_api") as String
val mcDepFabric: String = common.project.findProperty("mod.mc_dep_fabric") as String

version = "${modVersion}+$minecraft"
base {
    archivesName.set("${modId}-$loader")
}
architectury {
    platformSetupLoomIde()
    fabric()
}

val commonBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations {
    compileClasspath.get().extendsFrom(commonBundle)
    runtimeClasspath.get().extendsFrom(commonBundle)
    get("developmentFabric").extendsFrom(commonBundle)
}

repositories {
    maven("https://maven.terraformersmc.com/releases/") { name = "Terraformers" }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings("net.fabricmc:yarn:$minecraft+build.$yarnBuild:v2")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoader")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApi")

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionFabric")) { isTransitive = false }
}

loom {
    runConfigs.all {
        isIdeConfigGenerated = true
        runDir = "../../../run/$minecraft/$loader"
        vmArgs("-Dmixin.debug.export=true", "-Dreactivemusic.performanceLogging=true")
    }
}

java {
    withSourcesJar()
    val java = if (stonecutter.eval(minecraft, ">=1.20.5"))
        JavaVersion.VERSION_21 else JavaVersion.VERSION_17
    targetCompatibility = java
    sourceCompatibility = java
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
}

tasks.remapJar {
    injectAccessWidener = true
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier = "dev"
}

tasks.processResources {
    properties(listOf("fabric.mod.json"),
        "id" to modId,
        "name" to modName,
        "version" to modVersion,
        "minecraft" to mcDepFabric
    )
}

tasks.build {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
}

tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
    from(tasks.remapJar.get().archiveFile, tasks.remapSourcesJar.get().archiveFile)
    into(rootProject.layout.buildDirectory.file("libs/${modVersion}/$loader"))
    dependsOn("build")
}
