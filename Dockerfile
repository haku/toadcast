FROM maven

COPY . /usr/src/toadcast/
WORKDIR /usr/src/toadcast/

RUN mvn clean package

ENTRYPOINT [ "java", "-jar", "./target/toadcast-1-SNAPSHOT-jar-with-dependencies.jar" ]
