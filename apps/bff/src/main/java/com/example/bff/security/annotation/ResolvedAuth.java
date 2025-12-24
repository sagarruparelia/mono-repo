package com.example.bff.security.annotation;

import java.lang.annotation.*;

// Inject resolved AuthContext into controller method parameter
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ResolvedAuth {
}
