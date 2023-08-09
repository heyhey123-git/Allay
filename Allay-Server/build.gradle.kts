import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    id("application")
    id("me.champeau.jmh") version ("0.7.1")
    id("com.gorylenko.gradle-git-properties") version ("2.4.1")
}

description = "Allay-Server"

application {
    mainClass.set("cn.allay.server.Allay")
}

dependencies {
    implementation(project(":Allay-API"))
    implementation(libs.bundles.terminal)
    implementation(libs.progressbar)
    implementation(libs.bundles.logging)
    implementation(libs.bytebuddy)
    implementation(libs.libdeflate)
    testImplementation(libs.bytebuddy)
}

tasks.processResources {
    dependsOn("generateGitProperties")
    // input directory
    from("${rootProject.projectDir}/Data")
    // exclude unpacked folder and block palette.nbt
    exclude("**/unpacked/**")
    exclude("**/mappings/**")
}

//disable ,please use `java -jar` to start directly
tasks.startScripts {
    group = ""
    enabled = false
}
//disable
tasks.startShadowScripts {
    group = ""
    enabled = false
}
//disable
tasks.distTar {
    group = ""
    enabled = false
}
//disable
tasks.distZip {
    group = ""
    enabled = false
}

tasks.named<Delete>("clean") {
    delete("logs", "cache")
}

tasks.sourcesJar {
    dependsOn("generateGitProperties")
}

gitProperties {
    gitPropertiesName = "git.properties"
    gitPropertiesResourceDir.set(file("${rootProject.projectDir}/Data"))
}

tasks.jmh {
    //Add the executed test case
    includes.add("AABBTreeJMHTest")
//    includes.add("BlockStateUpdateJMHTest")
    //includes.add("ChunkJMHTest")
//    includes.add("ThroughList2Array")
    jvmArgs.add("--enable-preview")
}

//Hiding this task should use runShadow
tasks.named("run") {
    group = ""
}

tasks.runShadow {
    this.jarFile = file("build/libs/Allay-Server-all.jar")
    jvmArgs?.add("--enable-preview")
}

tasks.installShadowDist {
    enabled = false
}

tasks.shadowJar {
    transform(Log4j2PluginsCacheFileTransformer())
}