# flex-arbeidssokerregister-oppdatering

Applikasjon for integrasjon mellom sykepengesoknad-backend og paw-arbeidssokerregisteret-api (Arbeidssøkerregisteret).

## Generering av Avro-klasser

Java-klasser for Avro-skjemaene til arbeidssøkerregisteret genereres ved bygg med det offisielle
[avro-tools](https://avro.apache.org/docs/current/getting-started-java/) biblioteket fra Apache.

Funksjonaliteten er laget som en gradle-plugin: `avro-codegen`. Koden ligger i `buildSrc/`, som inneholder disse filene:

1. `extractAvroSchemas` pakker ut skjema-arkivene (`.avdl`-filer) fra `schema`-konfigurasjonen til
   `build/avro/idl`, med én undermappe per arkiv.
2. `generateAvroJava` konverterer hver `.avdl`-fil til `.avsc` med `idl2schemata`, og genererer
   Java-klasser med `compile schema` til `build/generated/sources/avro/java/main`.