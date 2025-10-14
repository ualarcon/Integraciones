FROM alpine:latest

# Actualizar la lista de paquetes e instalar dependencias
RUN apk update && \
    apk add --no-cache \
        inetutils-telnet \
        bash \
        curl \
        vim \
        nano \
        postgresql-client \
        python3 \
        py3-pip \
        aws-cli

# Crear el directorio monitor en el home del usuario
RUN mkdir -p /home/monitor
RUN mkdir -p /etc/truststore

# Copiar la carpeta monitor y su contenido al directorio /home/monitor
COPY monitor /home/monitor
COPY truststore /etc/truststore

# Convertir múltiples archivos a formato Unix usando un bucle
RUN for file in /home/monitor/monitorVPN.sh \
                /home/monitor/paramsApifiDEV \
                /home/monitor/paramsApoloQA \
                /home/monitor/paramsApoloPROD \
                /home/monitor/paramsApoloInt \
                /home/monitor/paramsAforeDEV \
                /home/monitor/paramsAresQA; do \
        sed -i 's/\r$//' "$file"; \
    done

# Comando por defecto al iniciar el contenedor
CMD ["/bin/sh"]