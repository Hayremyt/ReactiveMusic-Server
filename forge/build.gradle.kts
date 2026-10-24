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
val forgeLoader: String = common.project.findProperty("deps.forge_loader") as String
val mcDepForge: String = common.project.findProperty("mod.mc_dep_forgelike") as String
val loaderDepForge: String = common.project.findProperty("mod.loader_dep_forge") as String

version = "${modVersion}+$minecraft"
base {
    archivesName.set("${modId}-$loader")
}
architectury {
    platformSetupLoomIde()
    forge()
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
    get("developmentForge").extendsFrom(commonBundle)
}

repositories {
    maven("https://maven.minecraftforge.net")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings("net.fabricmc:yarn:$minecraft+build.$yarnBuild:v2")
    "forge"("net.minecraftforge:forge:$minecraft-$forgeLoader")

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionForge")) { isTransitive = false }
}

loom {
    forge.convertAccessWideners = true
    forge.mixinConfigs(
        "reactivemusic-common.mixins.json",
    )

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

tasks.jar {
    archiveClassifier = "dev"
}

tasks.remapJar {
    injectAccessWidener = true
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
    exclude("fabric.mod.json", "architectury.common.json")
}

tasks.processResources {
    properties(listOf("META-INF/mods.toml", "pack.mcmeta"),
        "id" to modId,
        "name" to modName,
        "version" to modVersion,
        "minecraft" to mcDepForge,
        "loader" to loaderDepForge
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
