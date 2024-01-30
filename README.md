# Upgrade CMT Eclipse version and change Maven
The Java and Eclipse RCP versions used in CMT are low, so there are many restrictions on development and execution.
We want to change the version of Java and Eclipse RCP so that it can work without problems even in Java8 environments, and change the build tool from Ant to Maven to improve maintenance.
## Build from sources
**Prerequisites**
1. JDK 8. [OpenJDK 8](https://adoptium.net/temurin/releases/?version=8) is our default Java at the moment.
2. [Apache Maven 3.9.6](https://maven.apache.org/download.cgi)
3. git
4. Internet access

**Build**
```
git clone https://github.com/Srltas/cubrid-migration.git eclipse_version_upgrade
cd eclipse_version_upgrade

// UI build
mvn package -Dtycho.debug.resolver=true -X 

// console build
mvn package -Pconsole -DconsoleBuild -Dtycho.debug.resolver=true -X
```
