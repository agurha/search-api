# load-test

The **SearchAPI** uses [Gatling](http://gatling.io) to executes load tests. 

## Setup

The [application.conf](src/gatling/resources/conf/application.conf) file that provides all configuration to load tests.

You can override each config above using Java property. For example, if you can override `gatling.users` property you must use `-Dgatling.users=<value>`.

For more details about how to do override, see [How To Run](#how-to-run) section.

## How To Build

To build this project, you need to execute this command:

```sh
make build
```

This command generates the `vivareal/search-api-v2:load-test` docker image.

## How To Run

[Gatling](http://gatling.io) works with a [Simulation](http://gatling.io/docs/current/quickstart/#a-word-on-scala) concept and for **SearchAPI** we creates the [SearchAPIv2Simulation](src/gatling/scala/com/vivareal/search/simulation/SearchAPIv2Simulation.scala).

There some steps when you run the load tests:
1. If not already built, builds and runs `load-test` docker image.
1. Executes your simulations.
1. Uploads you simulation report on Amazon S3.
1. Notifies report status on Slack with link to access them.

There are two parameters to use:

- `LT_ENDPOINT`*: load tests target endpoint.

- `LT_EXTRA_ARGS`: gatling extra args

The environment variables with `*` are required. You can override each config using `LT_EXTRA_ARGS`.

### Local

To run local, you simple use `make run-local` with the target ip to load test, for example:

```
make run-local LT_ENDPOINT="<TARGET_IP>"
```

### Kubernetes

To run using [Kubernetes](https://kubernetes.io), you simple use `make run`:

```
make run LT_ENDPOINT="<TARGET_IP>"
```

Yon can use `K8S_RUN_ARGS` to configure [kubectl run](https://kubernetes.io/docs/user-guide/kubectl-overview) params.

### Running with Gradle

To run using Gradle too and you simple use `gatlingRun` task.

```sh
./gradlew gatlinRun
```

The upload and notification process is separated and to do this you must use `uploadReport`.

```sh
./gradlew gatlinRun uploadReport
```

### How to Test

No tests currently implemented

## How To Deploy

load-test is a tool/lib project and the deploy is subjective according to the project that uses.

You may use docker to deploy: `make push`

_To push successful docker image, make sure you setup docker credentials._
