FROM maven:3.8-openjdk-8 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests

FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY --from=builder /app/target/code-sandbox-*.jar /app/app.jar
# fat jar 无法直接作为子进程 -cp，单独拷贝供 SandboxRunner 使用
COPY --from=builder /app/target/classes /app/security-classes

EXPOSE 8090
CMD ["java", "-Djava.security.egd=file:/dev/./urandom", "-Dsandbox.security.classpath=/app/security-classes", "-jar", "/app/app.jar"]
