@ApplicationModule(
        displayName = "Exam",
        allowedDependencies = {"billing::application", 
                "curriculum::application",
                "mastery",
                "mistake",
                "notification::application",
                "security",
                "sharedkernel::privacy",
                "sharedkernel::error",
                "sharedkernel::observability"
        }
)
package com.verifiedai.exam;

import org.springframework.modulith.ApplicationModule;
