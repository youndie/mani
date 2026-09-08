FROM amazoncorretto:25-alpine3.20-jdk

EXPOSE 8080:8080

RUN mkdir /app
COPY /build/libs/*.jar /app/mani-backend.jar

ENTRYPOINT ["java","-Xms512M","-Xmx2G","-jar","/app/mani-backend.jar"]