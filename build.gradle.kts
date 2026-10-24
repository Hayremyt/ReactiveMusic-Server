plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
}

val minecraft = stonecutter.current.version

val modVersion: String = project.findProperty("mod.version") as String
val modGroup: String = project.findProperty("mod.group") as String
val modId: String = project.findProperty("mod.id") as String
val modName: String = project.findProperty("mod.name") as String
val yarnBuild: String = project.findProperty("deps.yarn_build") as String

version = "${modVersion}+$minecraft"
group = modGroup
base {
    archivesName.set("${modId}-common")
}

repositories {
    maven("https://maven.terraformersmc.com/releases/") { name = "Terraformers" }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings("net.fabricmc:yarn:$minecraft+build.$yarnBuild:v2")
}

java {
    withSourcesJar()
    val java = if (stonecutter.eval(minecraft, ">=1.20.5"))
        JavaVersion.VERSION_21 else JavaVersion.VERSION_17
    targetCompatibility = java
    sourceCompatibility = java
}

tasks.build {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
}
