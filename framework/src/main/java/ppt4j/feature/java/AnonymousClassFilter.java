package ppt4j.feature.java;

import spoon.reflect.code.CtNewClass;
import spoon.reflect.visitor.filter.TypeFilter;

public class AnonymousClassFilter extends TypeFilter<CtNewClass<?>> {

    public AnonymousClassFilter() {
        super(CtNewClass.class);
    }

    @Override
    public boolean matches(CtNewClass element) {
        return element.getAnonymousClass() != null;
    }

}
