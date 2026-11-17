@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common::exception",
                "common::web",
                "common::demo"
        }
)
package com.acme.sportplatform.identity;