FROM maven

COPY . /usr/src/toadcast/
WORKDIR /usr/src/toadcast/

RUN mvn clean package
