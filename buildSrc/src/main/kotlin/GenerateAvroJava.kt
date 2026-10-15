import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Genererer Java-klasser fra Avro IDL-filer (`.avdl`) ved hjelp av det offisielle `avro-tools`.
 *
 * Genereringen skjer i to steg:
 * 1. Hver `.avdl`-fil konverteres til JSON-skjemaer (`.avsc`) med `idl2schemata`.
 * 2. Alle `.avsc`-filene kompileres til Java-klasser med `compile schema`.
 *
 * `compile protocol` brukes ikke, fordi den også genererer et Java-interface per protokoll. Flere
 * protokoller har samme navn som recorden de definerer, og resultatet ville da avhenge av rekkefølgen.
 *
 * `avro-tools` kjøres i en egen JVM, slik at Avro-versjonen er isolert fra Gradle sin egen classpath.
 */
abstract class GenerateAvroJava
    @Inject
    constructor(
        private val exec: ExecOperations,
        private val files: FileSystemOperations,
    ) : DefaultTask() {
        /** Classpath med `avro-tools`-jar. */
        @get:Classpath
        abstract val avroTools: ConfigurableFileCollection

        /** Mappe med utpakkede `.avdl`-filer. Relative stier beholdes slik at `import idl` fungerer. */
        @get:InputDirectory
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val idlDir: DirectoryProperty

        /** Ekstra argumenter til `compile`, f.eks. `-string` og `-fieldVisibility`. */
        @get:Input
        abstract val compilerArgs: ListProperty<String>

        /** Mellomlagring av genererte `.avsc`-filer. */
        @get:LocalState
        abstract val schemaDir: DirectoryProperty

        /** Mappe for genererte Java-klasser. */
        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @TaskAction
        fun generate() {
            // Slett tidligere output, slik at klasser fra fjernede skjemaer ikke blir liggende igjen.
            files.delete { delete(schemaDir, outputDir) }
            val schemaDirs = mutableListOf<String>()

            idlDir.asFileTree.matching { include("**/*.avdl") }.visit {
                if (!isDirectory) {
                    // idl2schemata navngir filene etter typenavnet uten navnerom, så hver .avdl-fil får egen mappe
                    // for å unngå at typer med samme navn (f.eks. Bruker) overskriver hverandre.
                    val target = schemaDir.dir(relativePath.pathString.removeSuffix(".avdl")).get().asFile
                    avroTool("idl2schemata", file.absolutePath, target.absolutePath)
                    schemaDirs += target.absolutePath
                }
            }

            // Kompiler alle skjemaene i ett kall, som gir én JVM-oppstart og én felles parser for alle filene.
            avroTool("compile", *compilerArgs.get().toTypedArray(), "schema", *schemaDirs.toTypedArray(), outputDir.get().asFile.path)
        }

        /** Kjører `avro-tools` med gitte argumenter i en egen JVM. */
        private fun avroTool(vararg arguments: String) {
            exec.javaexec {
                classpath = avroTools
                mainClass.set("org.apache.avro.tool.Main")
                args(*arguments)
            }
        }
    }
