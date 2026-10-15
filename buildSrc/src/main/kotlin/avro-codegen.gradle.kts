plugins {
    java
}

val schema =
    configurations.create("schema") {
        isTransitive = false
    }

val avroToolsClasspath =
    configurations.create("avroTools") {
        isTransitive = false
    }

configurations.named("implementation") {
    extendsFrom(schema)
}

val extractAvroSchemas =
    tasks.register<Sync>("extractAvroSchemas") {
        description = "Extracts Avro schemas from the schema dependencies."
        duplicatesStrategy = DuplicatesStrategy.FAIL
        schema.forEach { archive ->
            from(zipTree(archive)) {
                into(archive.nameWithoutExtension)
            }
        }
        into(layout.buildDirectory.dir("avro/idl"))
    }

val generateAvroJava =
    tasks.register<GenerateAvroJava>("generateAvroJava") {
        description = "Generates Java classes from Avro IDL files."
        avroTools.from(avroToolsClasspath)
        idlDir.set(layout.dir(extractAvroSchemas.map { it.destinationDir }))
        compilerArgs.addAll("-string", "-fieldVisibility", "public")
        schemaDir.set(layout.buildDirectory.dir("avro/schema"))
        outputDir.set(layout.buildDirectory.dir("generated/sources/avro/java/main"))
    }

sourceSets.main {
    java.srcDir(generateAvroJava.flatMap { it.outputDir })
}
