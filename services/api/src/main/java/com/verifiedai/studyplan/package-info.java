@ApplicationModule(
        displayName = "Study Plan",
        allowedDependencies = {"billing::application", 
                "curriculum::api",
                "exam::events",
                "security",
                "sharedkernel::privacy",
                "sharedkernel::error",
                "sharedkernel::observability"
        }
)
package com.verifiedai.studyplan;

import org.springframework.modulith.ApplicationModule;
