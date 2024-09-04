plugins {
    id("java-library")
}

subprojects {
    apply(plugin = "java-library")

    group = "org.allaymc"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.opencollab.dev/maven-releases/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
        maven("https://storehouse.okaeri.eu/repository/maven-public/")
        maven("https://www.jitpack.io/")
    }

    dependencies {
        compileOnly(rootProject.libs.lombok)
        annotationProcessor(rootProject.libs.lombok)

        testCompileOnly(rootProject.libs.lombok)
        testAnnotationProcessor(rootProject.libs.lombok)
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            configureEach {
                options.isFork = true
            }
        }

        withType<Test> {
            useJUnitPlatform()
            workingDir = file("${rootProject.projectDir}/.run/")
        }

        withType<Copy> {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}