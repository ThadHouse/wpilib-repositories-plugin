package edu.wpi.first.wpilib.repositories.offline;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;

public abstract class DirectMetadataAccessVariantRule implements ComponentMetadataRule {
    @javax.inject.Inject
    public abstract ObjectFactory getObjects();

    @Override
    public void execute(ComponentMetadataContext context) {
        ModuleVersionIdentifier id = context.getDetails().getId();
        context.getDetails().addVariant("platform-metadata", variant -> {
            variant.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE,
                        getObjects().named(Category.class, Category.REGULAR_PLATFORM));
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                        getObjects().named(DocsType.class, "platform-metadata"));
            });
            variant.withFiles(files -> {
                files.removeAllFiles();
                files.addFile(id.getName() + "-" + id.getVersion() + ".module");
                files.addFile(id.getName() + "-" + id.getVersion() + ".pom");
            });
        });

        context.getDetails().addVariant("allFiles", variant -> {
            variant.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE,
                        getObjects().named(Category.class, Category.DOCUMENTATION));
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                        getObjects().named(DocsType.class, "all-files"));
            });
            variant.withFiles(files -> {
                files.addFile(id.getName() + "-" + id.getVersion() + ".pom");
                files.addFile(id.getName() + "-" + id.getVersion() + ".module");
                files.addFile(id.getName() + "-" + id.getVersion() + "-javadoc.jar");
                files.addFile(id.getName() + "-" + id.getVersion() + "-sources.jar");
            });
        });
    }
}
