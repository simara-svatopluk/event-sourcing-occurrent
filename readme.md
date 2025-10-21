# Event Sourcing Demo

## How to run

* Open this project in intelliJ, and just run tests
* run [docker.sh](docker.sh) - creates docker container with mongo
* run [SimpleProjectionApp.kt](src/main/kotlin/run/SimpleProjectionApp.kt) in intelliJ - to watch what was written to event store
  * don't stop and...
* run [WriteApp.kt](src/main/kotlin/run/WriteApp.kt) in intelliJ - writes down events
  * observe the output of [SimpleProjectionApp.kt](src/main/kotlin/run/SimpleProjectionApp.kt)
* play with projections in following order
  * simple stupid - [SimpleProjectionApp.kt](src/main/kotlin/run/SimpleProjectionApp.kt)
  * reply the whole store after every start - [InMemoryProjectionApp.kt](src/main/kotlin/run/InMemoryProjectionApp.kt)
  * reply from start, continue if reloaded - [DurablePositionProjectionApp.kt](src/main/kotlin/run/DurablePositionProjectionApp.kt)
  * also DB storage of the projection - [DBProjectionApp.kt](src/main/kotlin/run/DBProjectionApp.kt)
