@ApplicationModule(
        displayName = "Mastery",
        allowedDependencies = {"curriculum::api", "security", "sharedkernel::error", "sharedkernel::ratelimit", "sharedkernel::privacy", "sharedkernel::security"}
)
package com.verifiedai.mastery;

import org.springframework.modulith.ApplicationModule;
