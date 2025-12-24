package com.example.bff.security.annotation;

import java.lang.annotation.*;

// Mark endpoints accessible via MFE/partner path (/mfe/api/v1/...) in addition to browser path
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MfeEnabled {
}
