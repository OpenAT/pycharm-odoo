package dev.ngocta.pycharm.odoo.data;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Processor;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import dev.ngocta.pycharm.odoo.OdooUtils;
import dev.ngocta.pycharm.odoo.csv.OdooCsvUtils;
import dev.ngocta.pycharm.odoo.python.model.OdooModelIndex;
import dev.ngocta.pycharm.odoo.python.module.OdooModule;
import dev.ngocta.pycharm.odoo.python.module.OdooModuleUtils;
import dev.ngocta.pycharm.odoo.xml.OdooXmlUtils;
import dev.ngocta.pycharm.odoo.xml.dom.OdooDomRecordLike;
import dev.ngocta.pycharm.odoo.xml.dom.OdooDomRoot;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OdooExternalIdIndex extends FileBasedIndexExtension<String, OdooRecord> {
    private static final ID<String, OdooRecord> NAME = ID.create("odoo.external.id");
    private static final OdooRecordCache RECORD_CACHE = new OdooRecordCache();
    private static final Key<Set<String>> IDS = new Key<>("ODOO_EXTERNAL_IDS");

    @NotNull
    @Override
    public ID<String, OdooRecord> getName() {
        return NAME;
    }

    @NotNull
    @Override
    public DataIndexer<String, OdooRecord, FileContent> getIndexer() {
        return inputData -> {
            Map<String, OdooRecord> result = new HashMap<>();
            Project project = inputData.getProject();
            VirtualFile file = inputData.getFile();
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile instanceof XmlFile) {
                OdooDomRoot root = OdooXmlUtils.getOdooDomRoot((XmlFile) psiFile);
                if (root != null) {
                    List<OdooDomRecordLike> items = root.getAllRecordLikeItems();
                    items.forEach(item -> {
                        OdooRecord record = item.getRecord();
                        if (record != null) {
                            String id = record.getId().trim();
                            if (!id.isEmpty()) {
                                result.put(record.getId(), record.withoutDataFile());
                                RECORD_CACHE.add(record);
                            }
                        }
                    });
                }
            } else if (OdooCsvUtils.isCsvFile(file)) {
                OdooCsvUtils.processRecordInCsvFile(file, project, (record, lineNumber) -> {
                    result.put(record.getId(), record.withoutDataFile());
                    RECORD_CACHE.add(record);
                    return true;
                });
            }
            Set<String> ids = file.putUserDataIfAbsent(IDS, ConcurrentHashMap.newKeySet());
            Set<String> currentIds = result.keySet();
            for (String id : ids) {
                if (!currentIds.contains(id)) {
                    RECORD_CACHE.clearCache(id, file);
                }
            }
            ids.clear();
            ids.addAll(currentIds);
            return result;
        };
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<OdooRecord> getValueExternalizer() {
        return new DataExternalizer<OdooRecord>() {
            @Override
            public void save(@NotNull DataOutput out,
                             OdooRecord value) throws IOException {
                out.writeUTF(value.getName());
                out.writeUTF(value.getModel());
                out.writeUTF(value.getModule());
                if (value.getSubType() == null) {
                    out.writeByte(0);
                } else {
                    out.writeByte(value.getSubType().getId());
                }
            }

            @Override
            public OdooRecord read(@NotNull DataInput in) throws IOException {
                String name = in.readUTF();
                String model = in.readUTF();
                String module = in.readUTF();
                int subTypeId = in.readUnsignedByte();
                OdooRecordSubType type = OdooRecordSubType.getById(subTypeId);
                return new OdooRecordImpl(name, model, module, type, null);
            }
        };
    }

    @Override
    public int getVersion() {
        return 9;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return file -> {
            return (OdooCsvUtils.isCsvFile(file) || OdooXmlUtils.isXmlFile(file)) && OdooModuleUtils.isInOdooModule(file);
        };
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @NotNull
    public static Collection<String> getAllIds(@NotNull Project project,
                                               @NotNull GlobalSearchScope scope) {
        List<String> ids = new LinkedList<>(getIds(scope));
        ids.addAll(getImplicitIds(project, scope));
        return ids;
    }

    @NotNull
    private static Collection<String> getIds(@NotNull GlobalSearchScope scope) {
        List<String> ids = new LinkedList<>();
        FileBasedIndex.getInstance().processAllKeys(NAME, ids::add, scope, null);
        return ids;
    }

    @NotNull
    private static Collection<String> getImplicitIds(@NotNull Project project,
                                                     @NotNull GlobalSearchScope scope) {
        List<String> ids = new LinkedList<>();
        processImplicitRecords(project, scope, record -> {
            ids.add(record.getId());
            return true;
        });
        return ids;
    }

    private static boolean processImplicitRecords(@NotNull Project project,
                                                  @NotNull GlobalSearchScope scope,
                                                  @NotNull Processor<OdooRecord> processor) {
        return OdooModelIndex.processIrModelRecords(project, scope, processor);
    }

    private static boolean processRecordsByIds(@NotNull Project project,
                                               @NotNull GlobalSearchScope scope,
                                               @NotNull Processor<OdooRecord> processor,
                                               @NotNull Collection<String> ids) {
        FileBasedIndex index = FileBasedIndex.getInstance();
        GlobalSearchScope everythingScope = new EverythingGlobalScope(project);
        for (String id : ids) {
            if (!RECORD_CACHE.processRecords(id, processor, scope)) {
                if (!index.processValues(NAME, id, null, (file, value) -> {
                    file.putUserDataIfAbsent(IDS, ConcurrentHashMap.newKeySet()).add(id);
                    OdooRecord record = value.withDataFile(file);
                    RECORD_CACHE.add(record);
                    if (scope.contains(file)) {
                        return processor.process(record);
                    }
                    return true;
                }, everythingScope)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean processRecords(@NotNull Project project,
                                          @NotNull GlobalSearchScope scope,
                                          @NotNull Processor<OdooRecord> processor) {
        Collection<String> ids = getIds(scope);
        return processRecordsByIds(project, scope, processor, ids);
    }

    public static boolean processAllRecords(@NotNull Project project,
                                            @NotNull GlobalSearchScope scope,
                                            @NotNull Processor<OdooRecord> processor) {
        if (!processRecords(project, scope, processor)) {
            return false;
        }
        return processImplicitRecords(project, scope, processor);
    }

    @NotNull
    public static List<OdooRecord> findRecordsById(@NotNull String id,
                                                   @NotNull PsiElement anchor) {
        Project project = anchor.getProject();
        OdooModule odooModule = OdooModuleUtils.getContainingOdooModule(anchor);
        if (odooModule != null) {
            return findRecordsById(id, project, odooModule.getSearchScope());
        }
        return findRecordsById(id, project, OdooUtils.getProjectModuleAndDependenciesScope(anchor));
    }

    @NotNull
    public static List<OdooRecord> findRecordsById(@NotNull String id,
                                                   @NotNull Project project,
                                                   @NotNull GlobalSearchScope scope) {
        List<OdooRecord> records = new LinkedList<>();
        processRecordsByIds(project, scope, records::add, Collections.singleton(id));
        processImplicitRecords(project, scope, record -> {
            if (id.equals(record.getId())) {
                records.add(record);
            }
            return true;
        });
        return records;
    }
}
