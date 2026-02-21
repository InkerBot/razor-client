plugins {
    id("org.springframework.boot") version "4.0.3"
}

springBoot {
    mainClass.set("bot.inker.bc.razor.tui.MainKt")
}

dependencies {
    api(project(":application"))
    api(project(":application:tui:lanterna"))
}
