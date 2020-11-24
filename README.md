# infra-operator

Infra operator is java implementation of Kubernetes 
[custom resource controller](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#custom-controllers). 
It is part of _th2 infrastructure_. Together with [infra-mgr](https://github.com/th2-net/th2-infra-mgr) and 
[helm-operator](https://github.com/fluxcd/helm-operator) 
it ensures synchronization of custom resource files from GitHub and actual resources 
in Kubernetes. Infra operator uses [fabric8](https://fabric8.io/guide/) library for communication with Kubernetes. 

[Custom resources](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/) 
allow us to extend Kubernetes API with custom components specifically designed for our needs. 
However, as such custom components are not part of default Kubernetes installation It is infra-operator's 
task to look over them. Below you can see one of the examples of custom resource used in th2.

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2GenericBox //FIXME change kind to Th2Generic
metadata:
  name: read-log
spec:
  image-name: ghcr.io/th2-net/th2-read-log
  image-version: 2.3.0
  type: th2-read
  custom-config:
    log-file: "/logsToRead/demo_log.txt"
    regexp: "8=FIX.+10=.+\\b\\u0001"
    regexp-groups: [0]
  pins:
    - name: read_log_demo_out
      connection-type: mq
      attributes:
        - raw
        - publish
        - store
  extended-settings:
    chart-cfg:
      ref: schema-stable
      path: custom-component
    service:
      enabled: false
    envVariables:
      JAVA_TOOL_OPTIONS: "-XX:+ExitOnOutOfMemoryError"
    mounting:
      - path: "/logsToRead"
        pvcName: components
    resources:
      limits:
        memory: 200Mi
        cpu: 200m
      requests:
        memory: 100Mi
        cpu: 20m
```  

For more information on what kind of custom resources are used ind th2 and what each section of their configuration represents
please refer to (TODO: where ?) 

infra-operator is also responsible for queues and user permission management on [RabbitMQ](https://www.rabbitmq.com/documentation.html).
List below covers main responsibilities and objectives of this component.

#### Main objectives
* Monitor Kubernetes events related to the _Th2CustomResources_ and Generate or modify 
corresponding Helm Releases.
* Create Vhost in RabbitMQ for each deployed schema.
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