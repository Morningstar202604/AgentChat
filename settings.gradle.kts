pluginManagement {
    repositories {
        // 官方仓库优先：GitHub Actions 等海外环境必须能从 google()/mavenCentral() 解析
        // 插件标记（阿里云镜像对部分 Kotlin 插件标记缺件，会导致 CI 配置阶段直接失败）。
        google()
        mavenCentral()
        gradlePluginPortal()
        // 国内镜像作为补充，本地（中国大陆网络）解析更快
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}

rootProject.name = "AgentChat"
include(":app")