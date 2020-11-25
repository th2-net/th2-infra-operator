# infra-operator

infra-operator is java implementation of Kubernetes 
[custom resource controller](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#custom-controllers). 
It is part of _th2 infrastructure_. Together with [infra-mgr](https://github.com/th2-net/th2-infra-mgr) and 
[helm-operator](https://github.com/fluxcd/helm-operator) 
it ensures synchronization of custom resource files from GitHub and actual resources 
in Kubernetes. infra-operator uses [fabric8](https://fabric8.io/guide/) library for communication with Kubernetes. 

[Custom resources](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/) 
allow us to extend Kubernetes API with custom components specifically designed for our needs. 
However, as such custom components are not part of default Kubernetes installation It is infra-operator's 
task to look over them. infra-operator monitors 5 kind of custom resources, which are defined in 
[th2-infra](https://github.com/th2-net/th2-infra/blob/master/values/CRD) repository.

For more information on custom resources used in th2, and their configuration, 
please refer to [th2-documentation](https://github.com/th2-net/th2-documentation)

infra-operator is also responsible for queues and user permission management on [RabbitMQ](https://www.rabbitmq.com/documentation.html).

List below covers main responsibilities and objectives of this component.

#### Main objectives
* Monitor Kubernetes events related to the _Th2CustomResources_ and Generate or modify 
corresponding Helm Releases.
* Based on config map `rabbit-mq-app-config` which is deployed by infra-mgr, creates Vhost in RabbitMQ for every schema namespace.
* For each Vhost create user in RabbitMQ and configure its permissions. 
* Based on pins described in CRs and pins described in _Th2Link_ resources declare queues in RabbitMQ. 
* Bind queues in RabbitMQ according to _Th2Link_ resources. 
* Generate RabbitMQ configs for each resource that needs it.
* Generate [gRPC](https://grpc.io/docs/) configs for each resource that needs it.

## Configuration
TODO

## Deployment
infra-operator is a cluster-wide resource, meaning that there should be only one instance of it deployed on the cluster.
 infra-operator is deployed in `service` namespace. As it needs specific configurations attached to its pod,
 deployment is done through charts by applying `/values/service.helmrelease.yaml` 
 (from [th2-infra](https://gitlab.exactpro.com/vivarium/th2/th2-core-open-source/th2-infra)) 
 (FIXME: update with github link when repository is moved).
