Table of Content
- [Quick Start](#quick-start)
- [Deploy a basic Flink sample](#deploy-a-basic-flink-sample)
- [Compile, build image, push image, deploy custom flink program](#compile-build-image-push-image-deploy-custom-flink-program)
- [Checkpoint/Savepoint Directory Example](#checkpointsavepoint-directory-example)
	- [S3 Bucket](#s3-bucket)
	- [PVC (TODO)](#pvc-todo)
	- [EmptyDir](#emptydir)
- [Troubleshooting Tips](#troubleshooting-tips)
- [Notes / Challenges](#notes--challenges)
- [TODO](#todo)


### Quick Start

```
git clone https://github.com/cs-tsui/Confluent-Platform-Flink-Quick-Start.git
cd Confluent-Platform-Flink-Quick-Start
```

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

### Deploy a basic Flink sample
```
# Deploy sample job
kubectl apply -f ./deployment-resources/basic-flink.yaml

# Should see job manager and task manager pods
kubectl get pods -w

kubectl logs basic-example-taskmanager-1-1 -f

# Cleanup
kubectl delete -f ./deployment-resources/basic-flink.yaml
```


### Compile, build image, push image, deploy custom flink program

This step is semi-optional, you can either use my image or your own image
```
cd ./java-basic-kafka-reader
mvn clean package

# Push to docker hub - run `docker login` first if necessary
# docker login
docker build -t cstsuiapi/flink-kafka-reader:0.4 .
docker push cstsuiapi/flink-kafka-reader:0.4
```

Update the values in these files in `deployment-resources` directory before applying
* `kafka-reader-config.yaml`
* `kafka-secret.yaml`

```
# Delete only if you need to "reset" your secrets for whatever reason
# kubectl delete cm kafka-reader-config
# kubectl delete secret kafka-credentials

# Create secrets and config maps (change the values in these files before applying)
kubectl apply -f ./deployment-resources/kafka-reader-config.yaml
kubectl apply -f ./deployment-resources/kafka-secret.yaml

# Apply FlinkDeployment
kubectl apply -f ./deployment-resources/flink-kafka-deployment.yaml

# Should see job manager and task manager pods
kubectl get pods -w

# Tail logs of task manager pod. We should see topic data get printed out
kubectl logs cst-example-taskmanager-1-1 -f

# cleanup
kubectl delete flinkdeployment cst-example
```


### Checkpoint/Savepoint Directory Example

#### S3 Bucket

Update the values in these files in `deployment-resources` directory before applying
* `./deployment-resources/basic-checkpoint-ha-s3.yaml`
    * Update your bucket location in the flinkConfig
      ```
      state.savepoints.dir: s3://cst-bucket-east-2/flink-state/savepoints
      state.checkpoints.dir: s3://cst-bucket-east-2/flink-state/checkpoints
      high-availability.type: kubernetes
      high-availability.storageDir: s3://cst-bucket-east-2/flink-state/ha
      ```

      Note: pay attention to the config thats needed to enable S3 checkpointing
      
      ```
      env:
          - name: ENABLE_BUILT_IN_PLUGINS
            value: flink-s3-fs-hadoop-1.19.1-cp1.jar;flink-s3-fs-presto-1.19.1-cp1.jar
      ```

* `./deployment-resources/kafka-aws-secret.yaml`
    * Update the AWS credentials (make sure your user has access to s3)

Apply YAMLs

```
kubectl apply -f ./deployment-resources/kafka-aws-secret.yaml

kubectl apply -f ./deployment-resources/basic-checkpoint-ha-s3.yaml
```

Navigate to your S3 bucket and you should see the state.

#### PVC (TODO)


#### EmptyDir

Alternatively, for a temporary testing of checkpoints, we can use `emptyDir` to use a temporary directory in the pod

```
kubectl apply -f ./deployment-resources/basic-checkpoint-ha-emptydir.yaml
```

Cleanup

```
kubectl delete -f ./deployment-resources/basic-checkpoint-ha-s3.yaml

or

kubectl delete -f ./deployment-resources/basic-checkpoint-ha-emptydir.yaml
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
```



### Notes / Challenges 

- kafka connector maven version - compilation would fail if its not 1.18 (where the rest is 1.19) 
   - [maven repo - flink-connector-kafka-parent](https://mvnrepository.com/artifact/io.confluent.flink/flink-connector-kafka-parent)

- needed "classloader.resolve-order: parent-first" in the flink config
  - https://nightlies.apache.org/flink/flink-docs-release-1.9/monitoring/debugging_classloading.html#x-cannot-be-cast-to-x-exceptions

- Only local jars are supported. Remote JARS don't work right now for application mode (the only mode we support). This means that we have to bake the jar into the image (for now)
  - https://confluent.slack.com/archives/C06H8AGRC4E/p1723734293136979
  - can test it out easily by changing `file:///` to `http` and the flink job will fail



### TODO

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

