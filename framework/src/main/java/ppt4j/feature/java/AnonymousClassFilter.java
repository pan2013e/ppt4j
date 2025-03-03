package ppt4j.feature.java;

import spoon.reflect.code.CtNewClass;
import spoon.reflect.visitor.filter.TypeFilter;

public class AnonymousClassFilter extends TypeFilter<CtNewClass<?>> {

    public AnonymousClassFilter() {
        super(CtNewClass.class);
    }

    /**
     * Checks if the given CtNewClass element is an anonymous class.
     * 
     * @param element the CtNewClass element to check
     * @return true if the CtNewClass element is an anonymous class, false otherwise
     */
    @Override
    public boolean matches(CtNewClass element) {
        // Check if the CtNewClass element is an anonymous class
        return element.getAnonymousClass() != null;
    }

}
