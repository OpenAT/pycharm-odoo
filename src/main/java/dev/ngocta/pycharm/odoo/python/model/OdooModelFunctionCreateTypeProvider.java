package dev.ngocta.pycharm.odoo.python.model;

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import dev.ngocta.pycharm.odoo.python.OdooPyNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OdooModelFunctionCreateTypeProvider extends PyTypeProviderBase {
    @Nullable
    @Override
    public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction function, @NotNull TypeEvalContext context) {
        if (OdooPyNames.CREATE.equals(function.getName())) {
            PyClass cls = function.getContainingClass();
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(cls);
            if (cls != null && (OdooPyNames.BASE_MODEL_QNAME.equals(cls.getQualifiedName()) || OdooModelInfo.readFromClass(cls) != null)) {
                PyType type = PyUnionType.union(builtinCache.getListType(), builtinCache.getDictType());
                if (type != null) {
                    return Ref.create(type);
                }
            }
        }
        return null;
    }
}