rootProject.name = "BlueMap"

// setup workspace
val releaseNotesFile = file("release.md")
if (!releaseNotesFile.exists()) releaseNotesFile.createNewFile();

// bluemap
includeBuild("BlueMapCore")
includeBuild("BlueMapCommon")

// implementations
includeBuild("implementations/cli")
includeBuild("implementations/cli-fake-plugin")

includeBuild("implementations/paper")
