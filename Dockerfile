FROM azul/zulu-openjdk:8

COPY ./ ./
RUN ./prepare-deps.sh