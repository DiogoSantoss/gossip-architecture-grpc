# Turmas

Distributed Systems Project 2021/2022

## Authors

**Group A11**

### Code Identification

In all source files (namely in the *groupId*s of the POMs), replace __GXX__ with your group identifier. The group
identifier consists of a G and the group number - always two digits. This change is important for code dependency
management, to ensure your code runs using the correct components and not someone else's.

### Team Members

| Number | Name              | User         | Email                              |
|--------|-------------------|--------------|------------------------------------|
| 95562  | Diogo Santos | DiogoSantoss | diogosilvasantos@tecnico.ulisboa.pt |
| 95641  | Miguel Porfírio | miguelporfirio19 | miguel.porfirio@tecnico.ulisboa.pt |
| 90115  | João Ivo | Jivo99 | joao.ivo@tecnico.ulisboa.pt |

## Getting Started

The overall system is made up of several modules. The main server is the _ClassServer_. The clients are the _Student_,
the _Professor_ and the _Admin_. The definition of messages and services is in the _Contract_. The future naming server
is the _NamingServer_.

See the [Project Statement](https://github.com/tecnico-distsys/Turmas) or a complete domain and system description.

### Prerequisites

The Project is configured with Java 17 (which is only compatible with Maven >= 3.8), but if you want to use Java 11 you
can too, just downgrade the version in the POMs.

To confirm that you have them installed and which versions they are, run in the terminal:

```s
javac -version
mvn -version
```

### Installation

To compile and install all modules:

```s
mvn clean install
```

### Execution

To run the naming server module:

```s
cd NamingServer/
mvn compile exec:java -Dexec.args="localhost 5000"
```

or to run in debug mode:

```s
cd NamingServer/
mvn compile exec:java -Dexec.args="localhost 5000 -debug"
```

To run the class server module:  

```s
cd ClassServer/
mvn compile exec:java -Dexec.args="<serviceName> localhost <port> <P/S>"
```

or to run in debug mode:

```s
cd ClassServer/
mvn compile exec:java -Dexec.args="<serviceName> localhost <port> <P/S> -debug"
```

To run the student module:

```s
cd Student/
mvn compile exec:java -Dexec.args="alunoXXXX <studentName>"
```  

or to run in debug mode:

```s
cd Student/
mvn compile exec:java -Dexec.args="alunoXXXX <studentName> -debug"
``` 
  
To run the professor module:

```s
cd Professor/
mvn compile exec:java
```

or to run in debug mode:

```s
cd Professor/
mvn compile exec:java -Dexec.args="-debug"
```

To run the admin module:

```s
cd Admin/
mvn compile exec:java
```

or to run in debug mode:

```s
cd Admin/
mvn compile exec:java -Dexec.args="-debug"
```


## Built With

* [Maven](https://maven.apache.org/) - Build and dependency management tool;
* [gRPC](https://grpc.io/) - RPC framework.
