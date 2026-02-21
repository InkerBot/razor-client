plugins {
    application
    id("org.springframework.boot") version "4.0.3"
}

application {
    mainClass.set("bot.inker.bc.razor.tui.MainKt")
}

dependencies {
    api(project(":application"))
    implementation("com.googlecode.lanterna:lanterna:3.1.3")
}
