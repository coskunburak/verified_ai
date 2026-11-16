@ApplicationModule(
        displayName = "Exam",
        allowedDependencies = {
                "curriculum::application",
                "mastery",
                "mistake",
                "security",
                "sharedkernel::privacy",
                "sharedkernel::error",
                "sharedkernel::observability"
        }
)
package com.verifiedai.exam;

import org.springframework.modulith.ApplicationModule;
