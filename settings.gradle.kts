@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral {
            mavenContent {
                releasesOnly()
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPack"
                    setUrl("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.therealbush")
                includeGroup("com.github.TeamNewPipe")
            }
        }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "JusPlayer"

// Submodules (core, lyrics/*, moriextractor, morideobfuscator) may be absent when
// the repo is checked out without `--recurse-submodules` (e.g. Dependabot's
// automatic dependency submission). Only include them when their directory exists.
fun includeIfPresent(path: String) {
    val dir = path.removePrefix(":").replace(":", "/")
    if (file("$dir/build.gradle.kts").isFile) {
        include(path)
    }
}

include(":app")
includeIfPresent(":core")
includeIfPresent(":lyrics:kugou")
includeIfPresent(":lyrics:lrclib")
includeIfPresent(":lyrics:simpmusic")
includeIfPresent(":lyrics:paxsenix")
includeIfPresent(":lyrics:betterlyrics")
includeIfPresent(":lyrics:unison")
includeIfPresent(":lyrics:youlyplus")
include(":lastfm")
include(":canvas")
include(":shazamkit")
include(":spotifycore")
includeIfPresent(":moriextractor")
includeIfPresent(":morideobfuscator")

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that JusPlayer and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in core/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.TeamNewPipe:NewPipeExtractor")
// includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.TeamNewPipe:NewPipeExtractor")).using(project(":extractor"))
//    }
// }
