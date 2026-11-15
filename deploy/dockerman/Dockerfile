# Build from the supplied JAR; no Maven or Node.js compilation on the router.
# Ubuntu Jammy is compatible with this project's Playwright 1.40.0 driver.
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    PLAYWRIGHT_BROWSERS_PATH=/app/ms-playwright \
    JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m -Dfile.encoding=UTF-8"

COPY app.jar /app/app.jar

# Use the Playwright version inside this JAR to install matching Chromium.
RUN mkdir -p /app/dbdata /app/logs /app/ms-playwright \
    && java -Dloader.main=com.microsoft.playwright.CLI \
       -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher \
       install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 12400
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
