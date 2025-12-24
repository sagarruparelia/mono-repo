package com.example.bff.security.annotation;

import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.Persona;

import java.lang.annotation.*;

// value(): personas allowed (OR logic). requiredDelegates(): for DELEGATE persona only (AND logic)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiredPersona {

    Persona[] value();

    DelegateType[] requiredDelegates() default {};
}
