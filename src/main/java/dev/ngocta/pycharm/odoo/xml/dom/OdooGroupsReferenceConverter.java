package dev.ngocta.pycharm.odoo.xml.dom;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import dev.ngocta.pycharm.odoo.OdooNames;
import dev.ngocta.pycharm.odoo.data.OdooExternalIdReferenceProvider;
import org.jetbrains.annotations.NotNull;

public class OdooGroupsReferenceConverter implements CustomReferenceConverter<String> {
    @NotNull
    @Override
    public PsiReference[] createReferences(GenericDomValue<String> value,
                                           PsiElement element,
                                           ConvertContext context) {
        return OdooExternalIdReferenceProvider.getCommaSeparatedReferences(element, OdooNames.RES_GROUPS, null, true);
    }
}
