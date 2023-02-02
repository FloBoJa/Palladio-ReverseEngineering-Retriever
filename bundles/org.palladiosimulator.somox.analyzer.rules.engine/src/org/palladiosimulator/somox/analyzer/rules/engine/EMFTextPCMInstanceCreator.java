package org.palladiosimulator.somox.analyzer.rules.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.emftext.language.java.arrays.ArrayDimension;
import org.emftext.language.java.classifiers.Class;
import org.emftext.language.java.classifiers.Classifier;
import org.emftext.language.java.classifiers.ConcreteClassifier;
import org.emftext.language.java.classifiers.Interface;
import org.emftext.language.java.containers.impl.CompilationUnitImpl;
import org.emftext.language.java.generics.QualifiedTypeArgument;
import org.emftext.language.java.generics.TypeArgument;
import org.emftext.language.java.members.Method;
import org.emftext.language.java.parameters.Parameter;
import org.emftext.language.java.parameters.impl.OrdinaryParameterImpl;
import org.emftext.language.java.parameters.impl.VariableLengthParameterImpl;
import org.emftext.language.java.types.Boolean;
import org.emftext.language.java.types.Byte;
import org.emftext.language.java.types.Char;
import org.emftext.language.java.types.Double;
import org.emftext.language.java.types.Float;
import org.emftext.language.java.types.Int;
import org.emftext.language.java.types.Long;
import org.emftext.language.java.types.PrimitiveType;
import org.emftext.language.java.types.Short;
import org.emftext.language.java.types.TypeReference;
import org.emftext.language.java.types.TypedElement;
import org.emftext.language.java.types.Void;
import org.emftext.language.java.variables.Variable;
import org.palladiosimulator.generator.fluent.repository.api.Repo;
import org.palladiosimulator.generator.fluent.repository.factory.FluentRepositoryFactory;
import org.palladiosimulator.generator.fluent.repository.structure.components.BasicComponentCreator;
import org.palladiosimulator.generator.fluent.repository.structure.interfaces.OperationInterfaceCreator;
import org.palladiosimulator.generator.fluent.repository.structure.interfaces.OperationSignatureCreator;
import org.palladiosimulator.generator.fluent.repository.structure.internals.Primitive;
import org.palladiosimulator.generator.fluent.repository.structure.types.CompositeDataTypeCreator;
import org.palladiosimulator.pcm.repository.BasicComponent;
import org.palladiosimulator.pcm.repository.CollectionDataType;
import org.palladiosimulator.pcm.repository.DataType;
import org.palladiosimulator.pcm.repository.ParameterModifier;
import org.palladiosimulator.pcm.repository.Repository;
import org.palladiosimulator.somox.analyzer.rules.blackboard.CompilationUnitWrapper;
import org.palladiosimulator.somox.analyzer.rules.blackboard.RuleEngineBlackboard;

// Class to create a pcm instance out of all results from the detector class
public class EMFTextPCMInstanceCreator {
    private static final Logger LOG = Logger.getLogger(EMFTextPCMInstanceCreator.class);

    private static final String REPO_NAME = "Software Architecture Repository";
    private final FluentRepositoryFactory create;
    private final Repo repository;
    private final RuleEngineBlackboard blackboard;
    private final Map<String, CompositeDataTypeCreator> existingDataTypesMap;
    private final Map<String, DataType> existingCollectionDataTypes;

    public EMFTextPCMInstanceCreator(RuleEngineBlackboard blackboard) {
        existingDataTypesMap = new HashMap<>();
        existingCollectionDataTypes = new HashMap<>();
        create = new FluentRepositoryFactory();
        repository = create.newRepository()
            .withName(REPO_NAME);
        this.blackboard = blackboard;
    }

    /**
     * Returns a PCM Repository model. It first creates the interfaces, then the components.
     *
     * @param mapping
     *            a mapping between microservice names and java model instances
     * @return the PCM repository model
     */
    public Repository createPCM(Map<String, List<CompilationUnitWrapper>> mapping) {
        final List<CompilationUnitImpl> components = blackboard.getEMFTextPCMDetector()
            .getComponents();
        final List<Classifier> interfaces = blackboard.getEMFTextPCMDetector()
            .getOperationInterfaces();

        createPCMInterfaces(interfaces);

        createPCMComponents(components);

        return repository.createRepositoryNow();
    }

    private void createPCMInterfaces(List<Classifier> interfaces) {
        interfaces.forEach(inter -> {
            final ConcreteClassifier concreteInter = (ConcreteClassifier) inter;
            final String interfaceName = wrapName(concreteInter);

            LOG.info("Current PCM Interface: " + interfaceName);

            OperationInterfaceCreator pcmInterface = create.newOperationInterface()
                .withName(interfaceName);

            for (final Method m : concreteInter.getMethods()) {
                OperationSignatureCreator signature = create.newOperationSignature()
                    .withName(m.getName());

                // parameter type
                for (final Parameter p : m.getParameters()) {
                    signature = handleSignatureDataType(signature, p.getClass(), p.getName(), p.getTypeReference(),
                            p.getArrayDimensionsBefore(), false);
                }

                // Return type: Cast Method Return Type to Variable
                // OrdinaryParameterImpl is sufficient since return types cannot be varargs.
                signature = handleSignatureDataType(signature, OrdinaryParameterImpl.class, "", m.getTypeReference(),
                        m.getArrayDimensionsBefore(), true);

                pcmInterface.withOperationSignature(signature);
            }

            repository.addToRepository(pcmInterface);
        });
    }

    private void createPCMComponents(List<CompilationUnitImpl> components) {
        for (final CompilationUnitImpl comp : components) {
            BasicComponentCreator pcmComp = create.newBasicComponent()
                .withName(getCompName(comp));

            final List<EMFTextProvidesRelation> providedRelations = blackboard.getEMFTextPCMDetector()
                .getProvidedInterfaces(comp);

            Set<ConcreteClassifier> realInterfaces = providedRelations.stream()
                .map(relation -> (ConcreteClassifier) relation.getOperationInterface())
                .collect(Collectors.toSet());
            for (ConcreteClassifier realInterface : realInterfaces) {
                pcmComp.provides(create.fetchOfOperationInterface(wrapName(realInterface)), "dummy name");
            }

            final List<Variable> requiredIs = blackboard.getEMFTextPCMDetector()
                .getRequiredInterfaces(comp);
            Set<ConcreteClassifier> requireInterfaces = requiredIs.stream()
                .map(EMFTextPCMInstanceCreator::getConcreteFromVar)
                .collect(Collectors.toSet());

            for (ConcreteClassifier requInter : requireInterfaces) {
                pcmComp.requires(create.fetchOfOperationInterface(wrapName(requInter)), "dummy require name");
            }
            BasicComponent builtComp = pcmComp.build();
            blackboard.putRepositoryComponentLocation(builtComp, new CompilationUnitWrapper(comp));
            repository.addToRepository(builtComp);
        }
    }

    private static String wrapName(ConcreteClassifier name) {
        return name.getQualifiedName()
            .replace("\\.", "_");
    }

    private static String getCompName(CompilationUnitImpl comp) {
        return comp.getNamespacesAsString()
            .replace('.', '_') + "_" + comp.getName();
    }

    private static ConcreteClassifier getConcreteFromVar(TypedElement variable) {
        return (ConcreteClassifier) variable.getTypeReference()
            .getPureClassifierReference()
            .getTarget();
    }

    private static Primitive convertPrimitive(PrimitiveType primT) {
        if (primT instanceof Boolean) {
            return Primitive.BOOLEAN;
        }
        if (primT instanceof Byte) {
            return Primitive.BYTE;
        }
        if (primT instanceof Char) {
            return Primitive.CHAR;
        }
        if ((primT instanceof Double) || (primT instanceof Float)) {
            return Primitive.DOUBLE;
        }
        if (primT instanceof Int) {
            return Primitive.INTEGER;
        }
        if (primT instanceof Long) {
            return Primitive.LONG;
        }
        if (primT instanceof Short) {
            // TODO replace with Primitive.SHORT as soon as that works
            return Primitive.INTEGER;
        }

        return null;
    }

    private OperationSignatureCreator handleSignatureDataType(OperationSignatureCreator signature,
            java.lang.Class<? extends Parameter> varClass, String varName, TypeReference variable,
            List<ArrayDimension> varDimensions, boolean asReturnType) {

        // Parameter is a collection (extends Collection, is an array or a vararg)
        DataType collectionType = handleCollectionType(varClass, variable, varDimensions);
        if (collectionType != null) {
            if (asReturnType) {
                return signature.withReturnType(collectionType);
            }
            return signature.withParameter(varName, collectionType, ParameterModifier.IN);
        }

        // Check if type is a primitive type
        Primitive prim = handlePrimitive(variable);
        if (prim != null) {
            if (asReturnType) {
                return signature.withReturnType(prim);
            }
            return signature.withParameter(varName, prim, ParameterModifier.IN);
        }

        // Check if type is void (not part of pcm primitives)
        if ((variable instanceof Void) && asReturnType) {
            if (!create.containsDataType("Void")) {
                repository.addToRepository(create.newCompositeDataType()
                    .withName("Void"));
            }
            return signature.withReturnType(create.fetchOfDataType("Void"));
        }

        // Parameter is Composite Type
        DataType compositeType = handleCompositeType(variable);
        if (compositeType != null) {
            if (asReturnType) {
                return signature.withReturnType(compositeType);
            }
            return signature.withParameter(varName, compositeType, ParameterModifier.IN);
        }

        return null;
    }

    private DataType handleCollectionType(java.lang.Class<? extends Parameter> varClass, TypeReference ref,
            List<ArrayDimension> dimensions) {
        // Base for the name of the collection data type
        String typeName = ref.getClass()
            .getName();
        if (ref.getPureClassifierReference() != null) {
            typeName = ref.getPureClassifierReference()
                .getTarget()
                .getName();
        }
        CollectionDataType collectionType = null;
        String collectionTypeName = null;

        if (varClass == VariableLengthParameterImpl.class) {
            if (ref instanceof PrimitiveType) {
                typeName = convertPrimitive((PrimitiveType) ref).name();
            }

            collectionTypeName = typeName + "...";
            if (existingCollectionDataTypes.containsKey(collectionTypeName)) {
                return existingCollectionDataTypes.get(collectionTypeName);
            }

            collectionType = createCollectionWithTypeArg(collectionTypeName, ref, dimensions);
        } else if ((dimensions != null) && !dimensions.isEmpty()) {
            if (ref instanceof PrimitiveType) {
                typeName = convertPrimitive((PrimitiveType) ref).name();
            }

            collectionTypeName = typeName + "[]";
            if (existingCollectionDataTypes.containsKey(collectionTypeName)) {
                return existingCollectionDataTypes.get(collectionTypeName);
            }

            collectionType = createCollectionWithTypeArg(collectionTypeName, ref,
                    dimensions.subList(1, dimensions.size()));
        } else if ((ref.getPureClassifierReference() != null) && isCollectionType(ref.getPureClassifierReference()
            // TODO: I do not think this works properly for deeper collection types (e.g.
            // List<String>[]), especially the naming.
            .getTarget())) {
            typeName = ref.getPureClassifierReference()
                .getTarget()
                .getName();
            for (TypeArgument typeArg : ref.getPureClassifierReference()
                .getTypeArguments()) {
                if (typeArg instanceof QualifiedTypeArgument) {
                    QualifiedTypeArgument qualiType = (QualifiedTypeArgument) typeArg;
                    String argumentTypeName = qualiType.getTypeReference()
                        .getPureClassifierReference()
                        .getTarget()
                        .getName();
                    collectionTypeName = typeName + "<" + argumentTypeName + ">";

                    LOG.info("Current Argument type name: " + argumentTypeName);

                    if (existingCollectionDataTypes.containsKey(collectionTypeName)) {
                        return existingCollectionDataTypes.get(collectionTypeName);
                    }

                    collectionType = createCollectionWithTypeArg(collectionTypeName, qualiType.getTypeReference(),
                            qualiType.getArrayDimensionsBefore());
                    break;
                }
            }
        }
        if (collectionType != null) {
            existingCollectionDataTypes.put(collectionTypeName, collectionType);
            repository.addToRepository(collectionType);
        }
        return collectionType;
    }

    private CollectionDataType createCollectionWithTypeArg(String collectionTypeName, TypeReference typeArg,
            List<ArrayDimension> typeArgDimensions) {
        // Type argument is primitive
        Primitive primitiveArg = handlePrimitive(typeArg);
        if (primitiveArg != null) {
            return create.newCollectionDataType(collectionTypeName, primitiveArg);
        }

        // Type argument is a collection again
        // A type argument cannot be a vararg, therefore it is "ordinary"
        DataType collectionArg = handleCollectionType(OrdinaryParameterImpl.class, typeArg, typeArgDimensions);
        if (collectionArg != null) {
            return FluentRepositoryFactory.newCollectionDataType(collectionTypeName, collectionArg);
        }

        // Type argument is a composite data type
        DataType compositeArg = handleCompositeType(typeArg);
        if (compositeArg != null) {
            return FluentRepositoryFactory.newCollectionDataType(collectionTypeName, compositeArg);
        }

        return null;
    }

    private static boolean isCollectionType(Classifier varClassifier) {

        List<TypeReference> refs = new ArrayList<>();

        if (varClassifier instanceof Class) {

            Class varClass = (Class) varClassifier;
            refs = varClass.getImplements();
        } else if (varClassifier instanceof Interface) {

            Interface varInterf = (Interface) varClassifier;
            if ("Collection"
                .equals(varInterf.getName())) {
                return true;
            }
            refs = varInterf.getExtends();
        }

        for (TypeReference ref : refs) {
            String interfaceName = ref.getPureClassifierReference()
                .getTarget()
                .getName();

            if ("Collection".equals(interfaceName)) {
                return true;
            }
        }

        return false;
    }

    private static Primitive handlePrimitive(TypeReference variable) {
        if (variable instanceof PrimitiveType) {
            return convertPrimitive((PrimitiveType) variable);
        }
        // Parameter is String, which counts for PCM as Primitive
        if (variable.getTarget()
            .toString()
            .contains("(name: String)")) {
            return Primitive.STRING;
        }
        return null;
    }

    private DataType handleCompositeType(TypeReference ref) {
        Classifier classifier = ref.getPureClassifierReference()
            .getTarget();
        String classifierName = classifier.getName();

        if (!existingDataTypesMap.containsKey(classifierName)) {
            existingDataTypesMap.put(classifierName, create.newCompositeDataType()
                .withName(classifierName));
            repository.addToRepository(existingDataTypesMap.get(classifierName));
        }

        return create.fetchOfCompositeDataType(classifierName);
    }
}