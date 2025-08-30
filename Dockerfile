# Этап 1: сборка
FROM eclipse-temurin:24-jdk AS build

RUN apt-get update && apt-get install -y \
    maven \
    gcc \
    make \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

# Сборка jar с вшитой .so в ресурсы
RUN mvn clean install

# Этап 2: рантайм
FROM eclipse-temurin:24-jdk AS runtime

WORKDIR /app

# Копируем jar со встроенной .so
COPY --from=build /app/target/vector.jar .

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "vector.jar"]