FROM alpine:latest

# 1) Instalar dependencias base, Java, utilidades
RUN apk update && apk add --no-cache \
    inetutils-telnet \
    bash \
    curl \
    vim \
    nano \
    postgresql-client \
    python3 \
    py3-pip \
    aws-cli \
    openjdk17-jdk \
    openjdk17 \
    openssl \
    ca-certificates && update-ca-certificates

# 2) Crear directorios
RUN mkdir -p /home/monitor /etc/truststore /opt/libs

# 3) Copiar archivos locales
COPY monitor /home/monitor
COPY truststore /etc/truststore
#COPY TestConexionSSL.java /etc/truststore/TestConexionSSL.java

# 4) Normalizar saltos de línea
RUN for file in /home/monitor/monitorVPN.sh \
                /home/monitor/paramsApifiDEV \
                /home/monitor/paramsApoloQA \
                /home/monitor/paramsApoloPROD \
                /home/monitor/paramsApoloInt \
                /home/monitor/paramsAforeDEV \
                /home/monitor/paramsAresQA; do \
      [ -f "$file" ] && sed -i 's/\r$//' "$file" || true; \
    done

# 5) Descargar librerías (SLF4J, HikariCP, JDBC)
RUN set -eux; \
    cd /opt/libs; \
    curl -L -o slf4j-api-2.0.13.jar \
      "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar"; \
    curl -L -o slf4j-simple-2.0.13.jar \
      "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar"; \
    curl -L -o HikariCP-5.1.0.jar \
      "https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar"; \
    curl -L -o jdbc-4.50.11.jar \
      "https://repo1.maven.org/maven2/com/ibm/informix/jdbc/4.50.11/jdbc-4.50.11.jar"; \
    cp /opt/libs/*.jar /etc/truststore/

# 6) Importar certificado al truststore
RUN keytool -importcert -trustcacerts -alias bancoppel2_label \
    -file /etc/truststore/bancoppel2_shm.crt \
    -keystore /etc/truststore/client_truststore.jks \
    -storepass CLIENTPASS -noprompt

# 7) Compilar en la misma ruta /etc/truststore
WORKDIR /etc/truststore
ENV CLASSPATH=.:/opt/libs/slf4j-api-2.0.13.jar:/opt/libs/slf4j-simple-2.0.13.jar:/opt/libs/HikariCP-5.1.0.jar:/opt/libs/jdbc-4.50.11.jar

RUN javac -cp ".:/opt/libs/*" /etc/truststore/TestConexionSSL.java

# 8) Comando por defecto
CMD ["/bin/sh"]