package com.example.bff.authz.annotation;

import com.example.bff.authz.model.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare required permissions for a controller method.
 * The method must have a parameter annotated with @PathVariable("dependentId")
 * for the permission check to work.
 *
 * <p>Example usage:
 * <pre>
 * {@literal @}GetMapping("/dependent/{dependentId}/profile")
 * {@literal @}RequiresPermission(permissions = {Permission.DAA, Permission.RPR})
 * public Mono<Profile> getProfile(@PathVariable String dependentId) {...}
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * The permissions required to access this method.
     * All specified permissions must be present (AND logic).
     */
    Permission[] permissions();

    /**
     * The name of the path variable containing the dependent ID.
     * Defaults to "dependentId".
     */
    String dependentIdParam() default "dependentId";
}
