dependencies {
    api(project(":"))
    runtimeOnly(project(":transport:socketio"))

    api("org.slf4j:slf4j-api:2.0.17")
    api("org.apache.logging.log4j:log4j-api:2.25.3")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
}