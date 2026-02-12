package io.github.nekonomura.applied_packaging.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.ElementType.TYPE;

public class Annotations {
    // クラスに対して付与された場合，大体AIによって書かれたコードであることを示します．
    // メソッドに対して付与された場合，そのメソッドがAI製であることを示します．
    @Retention(RetentionPolicy.RUNTIME)
    @Target(value={CONSTRUCTOR, FIELD, LOCAL_VARIABLE, METHOD, PACKAGE, MODULE, PARAMETER, TYPE})
    public @interface AICode {
        boolean checked() default false;
    }
}
