# infra-operator

Infra operator is java implementation of Kubernetes custom resource controller. It is part of _th2 infrastructure_. 
Together with _infra-mgr_ and _helm-operator_ it ensures synchronization of CR files from GitHub and actual resources 
in Kubernetes. Infra operator uses __fabric8__ library for communication with Kubernetes. 

Infra-operator is also responsible for queues and user permission management on RabbitMQ. List below covers main responsibilities and objectives of this component.

#### Main objectives
* Monitor Kubernetes events related to the _Th2CustomResources_ and Generate or modify 
corresponding Helm Releases.
* Manage Vhosts, exchanges, queues, channels and user permissions on RabbitMQ
* Bind queues on RabbitMQ according to on _Th2Link_ resources that are deployed on the cluster. 
* Generate RabbitMQ configs for each resource that needs it.
* Generate GRPC configs for each resource that needs it.

## Configuration
TODO

## Deployment
infra-operator is a cluster-wide resource, meaning that there should be only one instance of it deployed on the cluster.
 Infra operator is deployed in `service` namespace. As it needs specific configurations attached to its pod,
 deployment is done through charts by applying `/values/service.helmrelease.yaml` 
 (from [th2-infra](https://gitlab.exactpro.com/vivarium/th2/th2-core-open-source/th2-infra)) 
 (FIXME: update with github link when repository is moved).