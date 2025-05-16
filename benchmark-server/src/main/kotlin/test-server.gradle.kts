/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

tasks.withType<AbstractTestTask>().configureEach {
    val testServerService = TestServerService.registerIfAbsent(project, port = 8081)
    usesService(testServerService)
    // Trigger server starting if it is not started yet
    doFirst("start test server") { testServerService.get() }
}
