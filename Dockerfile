# 三个阶段：打包前端 → 打包后端（前端文件放进 jar）→ 只带 JRE 运行，最终镜像不含 Node、Maven 和源码

# 1. 前端：输出到 src/main/resources/static（见 askdb-web/vite.config.ts）
FROM node:22-alpine AS web
WORKDIR /build/askdb-web
COPY askdb-web/package.json askdb-web/package-lock.json ./
RUN npm ci
COPY askdb-web/ ./
RUN npm run build

# 2. 后端：先单独下载依赖，只改代码时这一层可以复用，不用重新下载
FROM maven:3.9-eclipse-temurin-17 AS app
WORKDIR /build
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=web /build/src/main/resources/static ./src/main/resources/static
RUN mvn -q -B package -DskipTests

# 3. 运行
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=app /build/target/askdb-*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
