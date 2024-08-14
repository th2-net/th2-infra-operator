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
# this section includes information about git or helm repository as a source of helm charts
# you can specify either git or helm repository

  # git repository parameters 
  git: git@some.server.com:some/repository
  # git repository URL for helm charts used by Th2 Custom Resources
  
  ref: branch
  # branch for helm charts

  path: /path/to/charts
  # repository path for charts

  # helm repository parameters 
  repository: https://helm.server.com/some/repository
  # helm repository URL for helm charts used by Th2 Custom Resources

  name: components
  # the name of the Helm chart without an alias

  version: 3.2.0
  # the targeted Helm chart version

rabbitMQManagement:
  host: host
  # host used for managing vHosts and users

  managementPort: 15672
  # management port for HTTP requests 

  applicationPort: 5672
  # AMQP port

  vhostName: vHost
  # AMQP vHost name 

  exchangeName: exchange
  # topic exchange name
  
  username: username
  # username for management and AMQP 
  
  password: password
  # password for management and AMQP
  
  persistence: true
  # determines if the RabbitMQ resources are persistent or not

  cleanUpOnStart: false
  # if option is true, operator removes all queues and exchanges from RabbitMQ on start   
  
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
imagePullSecrets:
  # this section contains list of secrets to be used for pulling images
  - name-for-secret
  - name-for-another-secret
ingress:
  # this section can include custom configurations for ingress.
  # section is of an object type, meaning it can contain any structure and any fields 
  # section will be passed to final config exactly ass described here
  # example configuration can include: 
  annotations:
    default:
      key: value
    extra:
      key: value
  host: ingress-host-name
  # host name that will be used inside ingress rules
  ingressClass: ingress-class-type
  # ingress class  will indicate what kind of ingress to use
openshift:
  # this section indicates whether application is run in openshift environment or not
  enabled: true/false
  #if not indicated default values is false
```

## Release notes

### 4.7.0
+ Improved clean rubbish from RabbitMQ on start 
+ Migrated to th2 plugin `0.1.1`

+ Updated:
  + bom: `4.6.1`
  + kubernetes-client: `6.13.1`
    + force okhttp: `4.12.0`
    + force logging-interceptor: `4.12.0`
  + http-client: `5.2.0`
  + java-uuid-generator: `5.1.0`
  + kotlin-logging: `3.0.5`

### 4.6.4
+ Added `rabbitMQManagement.cleanUpOnStart` option