### Quick Start
```
minikube start
minikube dashboard

# Confluent Helm Repo
helm repo add confluentinc https://packages.confluent.io/helm
helm repo update

# Install cert manager
kubectl create -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml 
helm upgrade --install cp-flink-kubernetes-operator confluentinc/flink-kubernetes-operator

# Confirm Flink k8s Operator is running
kubectl get pods
```

### Deploy a basic flink sample
```
# Deploy sample job
kubectl apply -f ./deployment-resources/basic-flink.yaml

# Should see job manager and task manager pods
kubectl get pods

# Cleanup
kubectl delete -f ./deployment-resources/basic-flink.yaml
```


### Compile, build image, push image, deploy custom flink program

```
cd ./java-basic-kafka-reader
mvn clean package

# Push to docker hub - run `docker login` first if necessary
# docker login
docker build -t cstsuiapi/flink-kafka-reader:0.4 .
docker push cstsuiapi/flink-kafka-reader:0.4

# Delete only if you need to "reset" your secrets for whatever reason
# kubectl delete cm kafka-reader-config
# kubectl delete secret kafka-credentials

# Create secrets and config maps (change the values in these files before applying)
kubectl apply -f ../deployment-resources/kafka-reader-config.yaml
kubectl apply -f ../deployment-resources/kafka-secret.yaml

# Apply FlinkDeployment
kubectl apply -f ../deployment-resources/flink-kafka-deployment.yaml

# Tail logs of task manager pod. We should see topic information get printed out
kubectl logs cst-example-taskmanager-1-1 -f

# cleanup
kubectl delete flinkdeployment cst-example
```

### Troubleshooting Tips

If the flink cluster doesn't come up clean: check the various status

For example:

Tail logs of operator pod
```
kubectl logs confluent-operator-6b6b4b676c-hmnfw -f
```

Get the deployment status from the output and look for errors
```
kubectl get flinkdeployments cst-example -oyaml
```

Exec into pod to see full flink process configuration or do anything you need to

```
kubectl exec -it cst-example-taskmanager-1-1 -- /bin/bash

bash-4.4$ ps -ef | more
UID          PID    PPID  C STIME TTY          TIME CMD
flink          1       0  1 13:20 ?        00:02:25 java -Xmx161061270 -Xms161061270 -XX:MaxDirectMemorySize=201326592 -XX:Ma
xMetaspaceSize=268435456 -XX:+IgnoreUnrecognizedVMOptions -Dlog.file=/opt/flink/log/flink--kubernetes-taskmanager-0-cst-examp
le-taskmanager-1-1.log -Dlog4j.configuration=file:/opt/flink/conf/log4j-console.properties -Dlog4j.configurationFile=file:/op
t/flink/conf/log4j-console.properties -Dlogback.configurationFile=file:/opt/flink/conf/logback-console.xml -classpath /opt/fl
ink/lib/flink-cep-1.19.1-cp1.jar:/opt/flink/lib/flink-csv-1.19.1-cp1.jar:/opt/flink/lib/flink-json-1.19.1-cp1.jar:/opt/flink/
lib/flink-kafka-reader-1.1.jar:/opt/flink/lib/flink-scala_2.12-1.19.1-cp1.jar:/opt/flink/lib/flink-table-api-java-uber-1.19.1
-cp1.jar:/opt/flink/lib/flink-table-planner-loader-1.19.1-cp1.jar:/opt/flink/lib/flink-table-runtime-1.19.1-cp1.jar:/opt/flin
k/lib/log4j-1.2-api-2.17.1.jar:/opt/flink/lib/log4j-api-2.17.1.jar:/opt/flink/lib/log4j-core-2.17.1.jar:/opt/flink/lib/log4j-
slf4j-impl-2.17.1.jar:/opt/flink/lib/flink-dist-1.19.1-cp1.jar:::: org.apache.flink.kubernetes.taskmanager.KubernetesTaskExec
utorRunner --configDir /opt/flink/conf -Djobmanager.memory.jvm-overhead.min=201326592b -Dpipeline.classpaths= -Dtaskmanager.r
esource-id=cst-example-taskmanager-1-1 -Djobmanager.memory.off-heap.size=134217728b -Dexecution.target=embedded -Dweb.tmpdir=
/tmp/flink-web-af1dea6d-5df8-4286-8dbc-1df4d61410c3 -Djobmanager.rpc.port=6123 -Dpipeline.jars=file:/opt/flink/lib/flink-kafk
a-reader-1.1.jar -Djobmanager.memory.jvm-metaspace.size=268435456b -Djobmanager.memory.heap.size=469762048b -Djobmanager.memo
ry.jvm-overhead.max=201326592b -D taskmanager.memory.network.min=67108864b -D taskmanager.cpu.cores=1.0 -D taskmanager.memory
.task.off-heap.size=0b -D taskmanager.memory.jvm-metaspace.size=268435456b -D external-resources=none -D taskmanager.memory.j
vm-overhead.min=201326592b -D taskmanager.memory.framework.off-heap.size=134217728b -D taskmanager.memory.network.max=6710886
4b -D taskmanager.memory.framework.heap.size=134217728b -D taskmanager.memory.managed.size=241591914b -D taskmanager.memory.t
ask.heap.size=26843542b -D taskmanager.numberOfTaskSlots=2 -D taskmanager.memory.jvm-overhead.max=201326592b
```


### Nodeport for UI

TODO



### Challenges / Learnings 

- kafka connector maven version - compilation would fail if its not 1.18 (where the rest is 1.19) 
   - [maven repo - flink-connector-kafka-parent](https://mvnrepository.com/artifact/io.confluent.flink/flink-connector-kafka-parent)

- needed "classloader.resolve-order: parent-first" in the flink config
  - https://nightlies.apache.org/flink/flink-docs-release-1.9/monitoring/debugging_classloading.html#x-cannot-be-cast-to-x-exceptions

- Only local jars are supported. Remote JARS don't work right now for application mode (the only mode we support). This means that we have to bake the jar into the image (for now)
  - https://confluent.slack.com/archives/C06H8AGRC4E/p1723734293136979
  - can test it out easily by changing `file:///` to `http` and the flink job will fail

TODO:

- [X] Different jobs in different namespaces
  - can work, pay attention to serviceaccount existance/permissions
  - https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/resource-providers/native_kubernetes/#rbac

	```
	# project2 is the namespace
	kubectl create clusterrolebinding flink-role-binding-default --clusterrole=edit --serviceaccount=project2:default
	```

- [X] Checkpoint/Savepoint configs
  - https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/deployment/resource-providers/standalone/docker/#using-filesystem-plugins

  - Plugin location in the pod - /opt/flink/opt



- [ ] Monitoring

- [ ] Conect to a different data source/sink than Kafka


### 
