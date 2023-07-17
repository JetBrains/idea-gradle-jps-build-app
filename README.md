[![JetBrains team project](https://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

# IntelliJ JPS build testing utility

This is a tool for importing projects into IntelliJ IDEA, building the project using JPS (JetBrains Project System), and saving the project model.
It's implemented using the [Gradle IntelliJ Plugin](https://github.com/JetBrains/gradle-intellij-plugin).

It may be useful for testing and benchmarking purposes.

The main entry points are:
* [ImportAndSave.kt](src%2Fmain%2Fkotlin%2Forg%2Fjetbrains%2Fkotlin%2Ftools%2Fgradleimportcmd%2FImportAndSave.kt)
* [JpsImportAndBuild.kt](src%2Fmain%2Fkotlin%2Forg%2Fjetbrains%2Fkotlin%2Ftools%2Fgradleimportcmd%2FJpsImportAndBuild.kt)
* [MeasureModelBuildersStatistics.kt](src%2Fmain%2Fkotlin%2Forg%2Fjetbrains%2Fkotlin%2Ftools%2Fgradleimportcmd%2FMeasureModelBuildersStatistics.kt)
* [TestSequentialImports.kt](src%2Fmain%2Fkotlin%2Forg%2Fjetbrains%2Fkotlin%2Ftools%2Fgradleimportcmd%2FTestSequentialImports.kt)

These entry points are called via the `runIde` Gradle task.
To understand how the parameters are passed, please refer to the [build.gradle](build.gradle) file.