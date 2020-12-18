# infra-operator

The infra-operator is a java implementation of Kubernetes 
[custom resource controller](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/#custom-controllers). 
It is part of _th2 infrastructure_. Together with [infra-mgr](https://github.com/th2-net/th2-infra-mgr) and 
[helm-operator](https://github.com/fluxcd/helm-operator) 
it ensures the synchronization of custom resource files from GitHub and the actual resources 
in Kubernetes. The infra-operator uses [fabric8](https://fabric8.io/guide/) library for communication with Kubernetes. 

[Custom resources](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/) 
allow us to extend Kubernetes API with custom components specifically designed for our needs. 
However, since such custom components are not part of the default Kubernetes installation, it is infra-operator's 
task to look over them. The infra-operator monitors 5 kind of custom resources, which are defined in 
[th2-infra](https://github.com/th2-net/th2-infra/blob/master/values/CRD) repository.

For more information about custom resources used in th2 and their configuration, 
please refer to [th2-documentation](https://github.com/th2-net/th2-documentation)

The infra-operator is also responsible for queues and users permission management on [RabbitMQ](https://www.rabbitmq.com/documentation.html).

The list below covers the main duties and objectives of this component.

#### Main objectives
* It monitors Kubernetes events related to the _Th2CustomResources_ and generates or modifies the corresponding Helm Releases.
* Based on the config map `rabbit-mq-app-config` which is deployed by infra-mgr, it creates Vhost in RabbitMQ for every schema namespace.
* For each Vhost it creates a user in RabbitMQ and configures its permissions. 
* Based on the pins described in CRs, and the pins described in _Th2Link_ resources it declares queues in RabbitMQ. 
* It binds queues in RabbitMQ according to _Th2Link_ resources. 
* Generate RabbitMQ configs for each resource that needs it.
* Generate [gRPC](https://grpc.io/docs/) configs for each resource that needs it.

## Configuration
The infra-operator configuration is given with infra-operator.yml file that should be on the classpath of the application

```yaml
namespacePrefixes:
  - namespace-
  - prefixes-
# these prefixes are used to filter namespaces that infra-operator will manage as a schema

chart:
  git: git@some.server.com:some/repository
  # git repository URL for helm charts used by Th2 Custom Resources
  
  ref: branch
  # branch for helm charts

  path: /path/to/charts
  # repository path for charts
  
rabbitMQManagement:
  host: host
  # RabbitMQ host used for managing vHosts and users
  
  port: 8080
  # RabbitMQ port
  
  username: username
  # RabbitMQ management username
  
  password: password
  # password for management user
  
  persistence: true
  # determines if the RabbitMQ resources are persistent or not
  
  schemaPermissions:
  # this section describes what permissions schema RabbitMQ user will have on its own resources
  # see RabbitMQ documentation to find out how permissions are described

    configure: pattern
    # configuration permissions on resources
    
    read: pattern
    # read permission on resources
    
    write: pattern
    # write permission on resources
    
configMaps:
# this section contains names of the ConfigMaps that are mounted in the boxes

  rabbitMQ: rabbit-mq-config-map
  # RabbitMQ server connectivity ConfigMap

k8sUrl: kubernetes-address
# address for kubernetes cluster. 
# this will be used as host in gRPC config for boxes that are running in node network or externally

schemaSecrets:
# this section contains secret names that are mounted in the boxes

  rabbitMQ: rabbitmq
  # secret name to connect to RabbitMQ server

  cassandra: cassandra
  # secret name to connect to cassandra database

  
```

