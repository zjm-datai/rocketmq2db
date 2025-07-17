# #############################################################################
# 第一阶段：构建/编译阶段 (Builder Stage)
# #############################################################################
# 使用官方的 Maven 镜像（包含 Java 17）作为构建环境
# 您也可以选择 `gradle:jdk17` 如果您使用 Gradle
FROM maven:3.9.6-eclipse-temurin-17-focal AS builder

# 设置工作目录
WORKDIR /app

# 复制 pom.xml 和 .mvn 目录（如果存在）
# 这样可以利用 Docker 的层缓存机制，只有当依赖变化时才重新下载
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# 下载所有依赖项
# `go-offline` 是一个很好的实践，可以加速后续的构建
RUN ./mvnw dependency:go-offline -B

# 复制您的 Spring Boot 应用源代码到镜像中
COPY src ./src

# 运行 Maven 命令来打包您的应用为 JAR 文件
# `-DskipTests` 可以跳过测试，加快构建速度
RUN ./mvnw package -DskipTests

# #############################################################################
# 第二阶段：运行阶段 (Runner Stage)
# #############################################################################
# 使用一个非常精简的 Java 17 运行时镜像
FROM eclipse-temurin:17-jre-focal

# 设置工作目录
WORKDIR /app

# 从第一阶段（builder）复制已经打包好的 JAR 文件到当前阶段
# 请将 `your-application-name-*.jar` 替换为您在 `pom.xml` 中定义的实际 artifactId 和版本号
# `target/*.jar` 是一个通配符，可以匹配 target 目录下的任何 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 暴露您的 Spring Boot 应用所监听的端口（例如 8080）
EXPOSE 8080

# 镜像的入口点，定义容器启动时要执行的命令
# `java -jar app.jar` 会启动您的 Spring Boot 应用
ENTRYPOINT ["java", "-jar", "app.jar"]