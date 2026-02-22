plugins {
    id("org.springframework.boot") version "4.0.3"
}

springBoot {
    mainClass.set("bot.inker.bc.razor.telegram.MainKt")
}

dependencies {
    api(project(":application"))
    implementation("org.telegram:telegrambots-longpolling:9.3.0")
    implementation("org.telegram:telegrambots-client:9.3.0")
    implementation("io.undertow:undertow-core:2.3.23.Final")
}
