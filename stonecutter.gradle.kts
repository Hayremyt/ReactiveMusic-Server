plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.13-SNAPSHOT" apply false
    id("architectury-plugin") version "3.4-SNAPSHOT" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}
stonecutter active "1.21.1" /* [SC] DO NOT EDIT */

// Builds every version into `build/libs/{mod.version}/{loader}`
stonecutter registerChiseled tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("buildAndCollect")
}

// Builds loader-specific versions into `build/libs/{mod.version}/{loader}`
for (it in stonecutter.tree.branches) {
    if (it.id.isEmpty()) continue
    val loader = it.id.replaceFirstChar { it.uppercase() }
    stonecutter registerChiseled tasks.register("chiseledBuild$loader", stonecutter.chiseled) {
        group = "project"
        versions { branch, _ -> branch == it.id }
        ofTask("buildAndCollect")
    }
}

// Runs active versions for each loader (Server only)
for (branch in stonecutter.tree.branches) {
    if (branch.id.isEmpty()) continue
    val loader = branch.id.replaceFirstChar { it.uppercase() }

    val activeNode = branch.nodes.find { it.metadata == stonecutter.current }
    if (activeNode != null) {
        tasks.register("runActiveServer$loader") {
            group = "project"
            dependsOn("${activeNode.hierarchy}:runServer")
        }
    } else {
        stonecutter registerChiseled tasks.register("runActiveServer$loader", stonecutter.chiseled) {
            group = "project"
            versions { branchId, _ -> branchId == branch.id }
            ofTask("runServer")
        }
    }
}
