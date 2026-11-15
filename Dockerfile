# Build directly from a Git checkout, including the local SKU/interaction changes.
FROM node:22-alpine AS frontend-build
WORKDIR /app/vue-code
COPY vue-code/package.json vue-code/package-lock.json ./
RUN npm ci
COPY vue-code/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS backend-build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Checkouts made on Windows may contain CRLF or omit the executable bit.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/ src/
# Copy last so checked-in static files cannot overwrite the fresh frontend.
COPY --from=frontend-build /app/src/main/resources/static/ src/main/resources/static/
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    PLAYWRIGHT_BROWSERS_PATH=/app/ms-playwright \
    JAVA_OPTS="-Xms128m -Xmx512m -Dfile.encoding=UTF-8" \
    SERVER_PORT=12400
COPY --from=backend-build /app/target/XianYuAssistant-2.0.3.jar /app/app.jar
# Install Chromium matching the Playwright dependency bundled in the JAR.
RUN mkdir -p /app/dbdata /app/logs /app/ms-playwright \
    && java -Dloader.main=com.microsoft.playwright.CLI \
       -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
       install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/*
EXPOSE 12400
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=$SERVER_PORT -jar /app/app.jar \"$@\"", "--"]
