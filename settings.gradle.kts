rootProject.name = "file-storage"

includeBuild("build-logic")

include("libs:persistence")
findProject(":libs:persistence")?.name = "persistence"

include("apps:api")
findProject(":apps:api")?.name = "api"

include("apps:worker")
findProject(":apps:worker")?.name = "worker"

include("libs:domain")
findProject(":libs:domain")?.name = "domain"

include("libs:application")
findProject(":libs:application")?.name = "application"

include("libs:storage-s3")
findProject(":libs:storage-s3")?.name = "storage-s3"

include("libs:util")
findProject(":libs:util")?.name = "util"

include("libs:messaging-kafka")
